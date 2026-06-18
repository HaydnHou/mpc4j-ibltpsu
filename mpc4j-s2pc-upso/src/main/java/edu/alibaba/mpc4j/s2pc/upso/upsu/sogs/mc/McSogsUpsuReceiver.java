package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.MaxBinSizeUtils;
import edu.alibaba.mpc4j.common.tool.hashbin.object.HashBinEntry;
import edu.alibaba.mpc4j.common.tool.hashbin.object.RandomPadHashBin;
import edu.alibaba.mpc4j.common.tool.polynomial.zp64.Zp64Poly;
import edu.alibaba.mpc4j.common.tool.polynomial.zp64.Zp64PolyFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfKey;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfSender;
import edu.alibaba.mpc4j.s2pc.upso.UpsoUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiverOutput;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc.McSogsUpsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.McrgTokenSogsPadCarrierReceiverOutput;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.McrgTokenSogsReleaseReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.TokenKeyedSogsSketch;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * MC-SOGS UPSU receiver.
 *
 * <p>This receiver keeps a MC-SOGS FHE relation prefix and replaces receiver-output PM-PEQT/COT release with
 * label-output PM-PEQT plus MCRG-token SOGS release. It outputs {@code Y union (X \ Y)}.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuReceiver extends AbstractUpsuReceiver {
    /**
     * Single-query OPRF sender.
     */
    private final SqOprfSender sqOprfSender;
    /**
     * Label-output PM-PEQT receiver.
     */
    private final LabelPmPeqtReceiver labelPmPeqtReceiver;
    /**
     * MCRG-token SOGS release receiver.
     */
    private final McrgTokenSogsReleaseReceiver releaseReceiver;
    /**
     * Config.
     */
    private final McSogsUpsuConfig config;
    /**
     * SOGS degree.
     */
    private final int degree;
    /**
     * SOGS cell number derived at init.
     */
    private int cellNum;
    /**
     * MC-SOGS UPSU params.
     */
    private McSogsUpsuParams params;
    /**
     * FHE relinearization keys.
     */
    private byte[] relinKeys;
    /**
     * Zp64 polynomial.
     */
    private Zp64Poly zp64Poly;
    /**
     * Cuckoo hash keys.
     */
    private byte[][] hashKeys;
    /**
     * Partition count.
     */
    private int alpha;
    /**
     * Encoded database.
     */
    private List<List<byte[]>> encodedDatabase;

    public McSogsUpsuReceiver(Rpc receiverRpc, Party senderParty, McSogsUpsuConfig config) {
        super(McSogsUpsuPtoDesc.getInstance(), receiverRpc, senderParty, config);
        this.config = config;
        sqOprfSender = SqOprfFactory.createSender(receiverRpc, senderParty, config.getSqOprfConfig());
        addSubPto(sqOprfSender);
        labelPmPeqtReceiver = LabelPmPeqtFactory.createReceiver(receiverRpc, senderParty, config.getLabelPmPeqtConfig());
        addSubPto(labelPmPeqtReceiver);
        releaseReceiver = new McrgTokenSogsReleaseReceiver(receiverRpc, senderParty, config.getReleaseConfig());
        addSubPto(releaseReceiver);
        degree = config.getDegree();
    }

    @Override
    public void init(Set<ByteBuffer> receiverElementSet, int maxSenderElementSize, int elementByteLength)
        throws MpcAbortException {
        setInitInput(receiverElementSet, maxSenderElementSize, elementByteLength);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        params = McSogsUpsuParams.RECEIVER_16M_SENDER_MAX_1024;
        MpcAbortPreconditions.checkArgument(maxSenderElementSize <= params.maxSenderElementSize());
        MpcAbortPreconditions.checkArgument(degree > 0);
        cellNum = config.getCellNum(maxSenderElementSize);
        MpcAbortPreconditions.checkArgument(cellNum > 0);

        SqOprfKey sqOprfKey = sqOprfSender.keyGen();
        sqOprfSender.init(maxSenderElementSize, sqOprfKey);
        zp64Poly = Zp64PolyFactory.createInstance(envType, params.getPlainModulus());
        int expectBinSize = MaxBinSizeUtils.expectMaxBinSize(
            receiverElementSize * params.getCuckooHashNum(), params.getBinNum()
        );
        int expectAlpha = CommonUtils.getUnitNum(expectBinSize, params.getMaxPartitionSizePerBin());
        labelPmPeqtReceiver.init(expectAlpha, params.getBinNum(), CommonConstants.BLOCK_BYTE_LENGTH);
        releaseReceiver.init();

        hashKeys = BlockUtils.randomBlocks(params.getCuckooHashNum(), secureRandom);
        DataPacketHeader hashKeyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_CUCKOO_HASH_KEYS.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(hashKeyHeader, Arrays.stream(hashKeys).collect(Collectors.toList())));

        DataPacketHeader relinKeysPayloadHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PUBLIC_KEYS.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> relinKeysPayload = rpc.receive(relinKeysPayloadHeader).getPayload();
        MpcAbortPreconditions.checkArgument(relinKeysPayload.size() == 1);
        relinKeys = relinKeysPayload.get(0);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 2, initTime, "receiver init params");

        stopWatch.start();
        List<long[][]> coeffs = encodeDatabase(sqOprfKey);
        IntStream intStream = IntStream.range(0, coeffs.size());
        intStream = parallel ? intStream.parallel() : intStream;
        encodedDatabase = intStream
            .mapToObj(i -> McSogsUpsuNativeUtils.preprocessDatabase(
                params.getEncryptionParameters(), coeffs.get(i), params.getPsLowDegree()
            ))
            .collect(Collectors.toList());
        stopWatch.stop();
        long encodeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 2, encodeTime, "receiver encodes database");

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public UpsuReceiverOutput psu(int senderElementSize) throws MpcAbortException {
        setPtoInput(senderElementSize);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        sqOprfSender.oprf(senderElementSize);
        stopWatch.stop();
        long oprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 5, oprfTime, "receiver executes OPRF");

        DataPacketHeader senderQueryPayloadHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_QUERY.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> queryPayload = rpc.receive(senderQueryPayloadHeader).getPayload();
        MpcAbortPreconditions.checkArgument(
            queryPayload.size() == params.getCiphertextNum() * params.getQueryPowers().length,
            "The size of query is incorrect"
        );

        stopWatch.start();
        List<long[]> mask = generateRandomMask();
        List<byte[]> responsePayload = computeResponse(queryPayload, mask);
        DataPacketHeader receiverResponsePayloadHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_RESPONSE.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(receiverResponsePayloadHeader, responsePayload));
        stopWatch.stop();
        long responseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 5, responseTime, "receiver generates response");

        stopWatch.start();
        int bitLength = CommonConstants.STATS_BIT_LENGTH + 2 * LongUtils.ceilLog2((long) alpha * params.getBinNum()) + 7;
        int byteLength = CommonUtils.getByteLength(bitLength);
        byte[][][] receiverPeqtInput = generatePeqtInput(mask, byteLength);
        byte[][] vLabels = labelPmPeqtReceiver.labelPmPeqt(receiverPeqtInput, byteLength, alpha, params.getBinNum());
        byte[][] vBinPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(
            envType, vLabels, alpha, params.getBinNum()
        );
        stopWatch.stop();
        long labelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 5, labelTime, "receiver executes label PM-PEQT and folds labels");

        stopWatch.start();
        McrgTokenSogsPadCarrierReceiverOutput carrierOutput = new McrgTokenSogsPadCarrierReceiverOutput(vBinPads);
        MpcAbortPreconditions.checkArgument(carrierOutput.getRowNum() == params.getBinNum());
        TokenKeyedSogsSketch.PeelResult peelResult =
            releaseReceiver.receive(carrierOutput, elementByteLength, cellNum, degree);
        MpcAbortPreconditions.checkArgument(peelResult.success);
        MpcAbortPreconditions.checkArgument(peelResult.residualCells == 0);
        Set<ByteBuffer> difference = new HashSet<>(peelResult.recovered);
        difference.remove(botElementByteBuffer);
        int psica = senderElementSize - difference.size();
        MpcAbortPreconditions.checkArgument(psica >= 0);
        Set<ByteBuffer> union = new HashSet<>(receiverElementList);
        union.addAll(difference);
        stopWatch.stop();
        long releaseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 5, releaseTime, "receiver executes SOGS release");

        logPhaseInfo(PtoState.PTO_END);
        return new UpsuReceiverOutput(union, psica);
    }

    private List<long[][]> encodeDatabase(SqOprfKey sqOprfKey) {
        Stream<ByteBuffer> receiverInputStream = receiverElementList.stream();
        receiverInputStream = parallel ? receiverInputStream.parallel() : receiverInputStream;
        List<ByteBuffer> inputPrfs = receiverInputStream
            .map(ByteBuffer::array)
            .map(sqOprfKey::getPrf)
            .map(ByteBuffer::wrap)
            .collect(Collectors.toList());
        RandomPadHashBin<ByteBuffer> completeHash = new RandomPadHashBin<>(
            envType, params.getBinNum(), receiverElementSize, hashKeys
        );
        completeHash.insertItems(inputPrfs);
        int maxBinSize = IntStream.range(0, params.getBinNum()).map(completeHash::binSize).max().orElse(0);
        alpha = CommonUtils.getUnitNum(maxBinSize, params.getMaxPartitionSizePerBin());
        List<List<HashBinEntry<ByteBuffer>>> completeHashBins = new ArrayList<>();
        HashBinEntry<ByteBuffer> paddingEntry = HashBinEntry.fromEmptyItem(botElementByteBuffer);
        for (int i = 0; i < completeHash.binNum(); i++) {
            List<HashBinEntry<ByteBuffer>> binItems = new ArrayList<>(completeHash.getBin(i));
            int paddingNum = maxBinSize - completeHash.binSize(i);
            IntStream.range(0, paddingNum).mapToObj(j -> paddingEntry).forEach(binItems::add);
            completeHashBins.add(binItems);
        }
        inputPrfs.clear();
        return UpsoUtils.encodeDatabase(
            zp64Poly, completeHashBins, maxBinSize, params.getPlainModulus(), params.getMaxPartitionSizePerBin(),
            params.getItemEncodedSlotSize(), params.getItemPerCiphertext(), params.getBinNum(),
            params.getCiphertextNum(), params.getPolyModulusDegree(), parallel
        );
    }

    private List<byte[]> computeResponse(List<byte[]> queryList, List<long[]> mask) {
        int[][] powerDegree = UpsoUtils.computePowerDegree(
            params.getPsLowDegree(), params.getQueryPowers(), params.getMaxPartitionSizePerBin()
        );
        IntStream intStream = IntStream.range(0, params.getCiphertextNum());
        intStream = parallel ? intStream.parallel() : intStream;
        List<byte[]> queryPowers = intStream
            .mapToObj(i -> McSogsUpsuNativeUtils.computeEncryptedPowers(
                params.getEncryptionParameters(),
                relinKeys,
                queryList.subList(i * params.getQueryPowers().length, (i + 1) * params.getQueryPowers().length),
                powerDegree,
                params.getQueryPowers(),
                params.getPsLowDegree())
            )
            .flatMap(Collection::stream)
            .collect(Collectors.toCollection(ArrayList::new));
        if (params.getPsLowDegree() > 0) {
            return IntStream.range(0, params.getCiphertextNum())
                .mapToObj(i ->
                    (parallel ? IntStream.range(0, alpha).parallel() : IntStream.range(0, alpha))
                        .mapToObj(j -> McSogsUpsuNativeUtils.optComputeMatches(
                            params.getEncryptionParameters(),
                            relinKeys,
                            encodedDatabase.get(i * alpha + j),
                            queryPowers.subList(i * powerDegree.length, (i + 1) * powerDegree.length),
                            params.getPsLowDegree(),
                            mask.get(i * alpha + j))
                        )
                        .toArray(byte[][]::new))
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());
        } else {
            return IntStream.range(0, params.getCiphertextNum())
                .mapToObj(i ->
                    (parallel ? IntStream.range(0, alpha).parallel() : IntStream.range(0, alpha))
                        .mapToObj(j -> McSogsUpsuNativeUtils.naiveComputeMatches(
                            params.getEncryptionParameters(),
                            encodedDatabase.get(i * alpha + j),
                            queryPowers.subList(i * powerDegree.length, (i + 1) * powerDegree.length),
                            mask.get(i * alpha + j))
                        )
                        .toArray(byte[][]::new))
                .flatMap(Arrays::stream)
                .collect(Collectors.toList());
        }
    }

    private List<long[]> generateRandomMask() {
        List<long[]> coeffList = new ArrayList<>();
        for (int i = 0; i < params.getCiphertextNum(); i++) {
            for (int j = 0; j < alpha; j++) {
                long[] r = IntStream.range(0, params.getPolyModulusDegree())
                    .mapToLong(l -> Math.abs(secureRandom.nextLong()) % params.getPlainModulus())
                    .toArray();
                coeffList.add(r);
            }
        }
        return coeffList;
    }

    private byte[][][] generatePeqtInput(List<long[]> mask, int byteLength) {
        return hashRawSlots(generateRawMaskSlots(mask), byteLength);
    }

    private byte[][][] generateRawMaskSlots(List<long[]> mask) {
        byte[][][] rawSlots = new byte[alpha][params.getBinNum()][];
        for (int i = 0; i < params.getBinNum(); i++) {
            for (int j = 0; j < alpha; j++) {
                int cipherIndex = i / params.getItemPerCiphertext();
                int coeffIndex = (i % params.getItemPerCiphertext()) * params.getItemEncodedSlotSize();
                long[] item = new long[params.getItemEncodedSlotSize()];
                System.arraycopy(mask.get(cipherIndex * alpha + j), coeffIndex, item, 0, params.getItemEncodedSlotSize());
                byte[] bytes = UpsoUtils.convertCoeffsToBytes(item, params.getPlainModulusSize());
                BytesUtils.reduceByteArray(bytes, params.getL());
                rawSlots[j][i] = bytes;
            }
        }
        return rawSlots;
    }

    private byte[][][] hashRawSlots(byte[][][] rawSlots, int byteLength) {
        Hash peqtHash = HashFactory.createInstance(envType, byteLength);
        byte[][][] pmPeqtInput = new byte[alpha][params.getBinNum()][];
        for (int j = 0; j < alpha; j++) {
            for (int i = 0; i < params.getBinNum(); i++) {
                pmPeqtInput[j][i] = peqtHash.digestToBytes(rawSlots[j][i]);
            }
        }
        return pmPeqtInput;
    }
}
