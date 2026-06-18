package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.galoisfield.zp64.Zp64;
import edu.alibaba.mpc4j.common.tool.galoisfield.zp64.Zp64Factory;
import edu.alibaba.mpc4j.common.tool.hashbin.MaxBinSizeUtils;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBin;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtSender;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pir.PirUtils;
import edu.alibaba.mpc4j.s2pc.upso.UpsoUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc.McSogsUpsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.McrgTokenSogsPadCarrierSenderOutput;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.McrgTokenSogsReleaseSender;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * MC-SOGS UPSU sender.
 *
 * <p>This sender keeps a MC-SOGS FHE relation prefix and replaces the receiver-output PM-PEQT/COT tail with
 * label-output PM-PEQT plus MCRG-token SOGS release. The release rows are one-time anonymous cuckoo-bin carriers.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuSender extends AbstractUpsuSender {
    /**
     * Single-query OPRF receiver.
     */
    private final SqOprfReceiver sqOprfReceiver;
    /**
     * Label-output PM-PEQT sender.
     */
    private final LabelPmPeqtSender labelPmPeqtSender;
    /**
     * MCRG-token SOGS release sender.
     */
    private final McrgTokenSogsReleaseSender releaseSender;
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
     * Zp64 instance.
     */
    private Zp64 zp64;
    /**
     * Cuckoo hash keys.
     */
    private byte[][] hashKeys;
    /**
     * FHE secret key.
     */
    private byte[] secretKey;
    /**
     * Partition count.
     */
    private int alpha;

    public McSogsUpsuSender(Rpc senderRpc, Party receiverParty, McSogsUpsuConfig config) {
        super(McSogsUpsuPtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
        sqOprfReceiver = SqOprfFactory.createReceiver(senderRpc, receiverParty, config.getSqOprfConfig());
        addSubPto(sqOprfReceiver);
        labelPmPeqtSender = LabelPmPeqtFactory.createSender(senderRpc, receiverParty, config.getLabelPmPeqtConfig());
        addSubPto(labelPmPeqtSender);
        releaseSender = new McrgTokenSogsReleaseSender(senderRpc, receiverParty, config.getReleaseConfig());
        addSubPto(releaseSender);
        degree = config.getDegree();
    }

    @Override
    public void init(int maxSenderElementSize, int receiverElementSize) throws MpcAbortException {
        setInitInput(maxSenderElementSize, receiverElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        params = McSogsUpsuParams.RECEIVER_16M_SENDER_MAX_1024;
        MpcAbortPreconditions.checkArgument(maxSenderElementSize <= params.maxSenderElementSize());
        MpcAbortPreconditions.checkArgument(degree > 0);
        cellNum = config.getCellNum(maxSenderElementSize);
        MpcAbortPreconditions.checkArgument(cellNum > 0);

        sqOprfReceiver.init(maxSenderElementSize);
        zp64 = Zp64Factory.createInstance(envType, params.getPlainModulus());
        int expectBinSize = MaxBinSizeUtils.expectMaxBinSize(
            receiverElementSize * params.getCuckooHashNum(), params.getBinNum()
        );
        int expectAlpha = CommonUtils.getUnitNum(expectBinSize, params.getMaxPartitionSizePerBin());
        labelPmPeqtSender.init(expectAlpha, params.getBinNum(), CommonConstants.BLOCK_BYTE_LENGTH);
        releaseSender.init();

        DataPacketHeader cuckooHashKeyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_CUCKOO_HASH_KEYS.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> hashKeyPayload = rpc.receive(cuckooHashKeyHeader).getPayload();
        MpcAbortPreconditions.checkArgument(hashKeyPayload.size() == params.getCuckooHashNum());
        hashKeys = hashKeyPayload.toArray(new byte[0][]);

        List<byte[]> keyPair = McSogsUpsuNativeUtils.keyGen(params.getEncryptionParameters());
        byte[] relinKeys = handleKeyPair(keyPair);
        DataPacketHeader keyPairHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PUBLIC_KEYS.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(keyPairHeader, Collections.singletonList(relinKeys)));
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> senderElementSet, int elementByteLength) throws MpcAbortException {
        setPtoInput(senderElementSet, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        byte[][] oprfInput = IntStream.range(0, senderElementSize)
            .mapToObj(i -> senderElementList.get(i).array())
            .toArray(byte[][]::new);
        SqOprfReceiverOutput oprfReceiverOutput = sqOprfReceiver.oprf(oprfInput);
        Map<ByteBuffer, byte[]> oprfItemMap = handleReceiverOprfOutput(oprfReceiverOutput);
        stopWatch.stop();
        long oprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 5, oprfTime, "sender executes OPRF");

        stopWatch.start();
        CuckooHashBin<ByteBuffer> cuckooHashBin = generateCuckooHashBin(oprfItemMap);
        byte[][] originalBinPayloads = collectOriginalBinPayloads(cuckooHashBin, oprfItemMap);
        List<byte[]> senderQueryPayload = encodeQuery(cuckooHashBin);
        DataPacketHeader queryHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_QUERY.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(queryHeader, senderQueryPayload));
        stopWatch.stop();
        long queryTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 5, queryTime, "sender generates query");

        DataPacketHeader receiverResponsePayloadHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_RESPONSE.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> receiverResponsePayload = rpc.receive(receiverResponsePayloadHeader).getPayload();
        MpcAbortPreconditions.checkArgument(receiverResponsePayload.size() % params.getCiphertextNum() == 0);
        alpha = receiverResponsePayload.size() / params.getCiphertextNum();

        stopWatch.start();
        int bitLength = CommonConstants.STATS_BIT_LENGTH + 2 * LongUtils.ceilLog2((long) alpha * params.getBinNum()) + 7;
        int byteLength = CommonUtils.getByteLength(bitLength);
        byte[][][] senderPeqtInput = decodeResponse(receiverResponsePayload, byteLength);
        stopWatch.stop();
        long decodeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 5, decodeTime, "sender decodes response");

        stopWatch.start();
        int[] rowPermutationMap = McSogsUpsuUtils.freshPermutation(alpha, secureRandom);
        int[] columnPermutationMap = McSogsUpsuUtils.freshPermutation(params.getBinNum(), secureRandom);
        byte[][] uLabels = labelPmPeqtSender.labelPmPeqt(
            senderPeqtInput, rowPermutationMap, columnPermutationMap, byteLength
        );
        byte[][] uBinPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(
            envType, uLabels, alpha, params.getBinNum()
        );
        stopWatch.stop();
        long labelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 5, labelTime, "sender executes label PM-PEQT and folds labels");

        stopWatch.start();
        McrgTokenSogsPadCarrierSenderOutput releaseOutput = buildReleaseOutput(
            uBinPads, columnPermutationMap, originalBinPayloads
        );
        MpcAbortPreconditions.checkArgument(releaseOutput.getRowNum() == params.getBinNum());
        releaseSender.send(releaseOutput);
        stopWatch.stop();
        long releaseTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 5, 5, releaseTime, "sender executes SOGS release");

        logPhaseInfo(PtoState.PTO_END);
    }

    private byte[] handleKeyPair(List<byte[]> keyPair) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(keyPair.size() == 3);
        secretKey = keyPair.get(1);
        return keyPair.get(2);
    }

    private Map<ByteBuffer, byte[]> handleReceiverOprfOutput(SqOprfReceiverOutput sqOprfReceiverOutput) {
        List<ByteBuffer> oprfOutput = IntStream.range(0, senderElementSize)
            .mapToObj(i -> ByteBuffer.wrap(sqOprfReceiverOutput.getPrf(i)))
            .collect(Collectors.toList());
        return IntStream.range(0, senderElementSize)
            .boxed()
            .collect(Collectors.toMap(oprfOutput::get, i -> senderElementList.get(i).array(), (a, b) -> b));
    }

    private CuckooHashBin<ByteBuffer> generateCuckooHashBin(Map<ByteBuffer, byte[]> oprfItemMap) {
        CuckooHashBin<ByteBuffer> cuckooHashBin = CuckooHashBinFactory.createCuckooHashBin(
            envType, params.getCuckooHashBinType(), senderElementSize, params.getBinNum(), hashKeys
        );
        cuckooHashBin.insertItems(new ArrayList<>(oprfItemMap.keySet()));
        assert cuckooHashBin.itemNumInStash() == 0;
        cuckooHashBin.insertPaddingItems(botElementByteBuffer);
        return cuckooHashBin;
    }

    private byte[][] collectOriginalBinPayloads(CuckooHashBin<ByteBuffer> cuckooHashBin,
                                                Map<ByteBuffer, byte[]> oprfItemMap) {
        byte[][] payloads = new byte[params.getBinNum()][];
        for (int i = 0; i < params.getBinNum(); i++) {
            ByteBuffer item = cuckooHashBin.getHashBinEntry(i).getItem();
            payloads[i] = BytesUtils.clone(
                oprfItemMap.containsKey(item) ? oprfItemMap.get(item) : botElementByteBuffer.array()
            );
        }
        return payloads;
    }

    private List<byte[]> encodeQuery(CuckooHashBin<ByteBuffer> cuckooHashBin) {
        long[][] coeffs = new long[params.getCiphertextNum()][params.getPolyModulusDegree()];
        for (int i = 0; i < params.getCiphertextNum(); i++) {
            for (int j = 0; j < params.getItemPerCiphertext(); j++) {
                long[] coeff = UpsoUtils.getHashBinEntryEncodedArray(
                    cuckooHashBin.getHashBinEntry(i * params.getItemPerCiphertext() + j), true,
                    params.getItemEncodedSlotSize(), params.getPlainModulus()
                );
                System.arraycopy(
                    coeff, 0, coeffs[i], j * params.getItemEncodedSlotSize(), params.getItemEncodedSlotSize()
                );
            }
        }
        List<long[][]> encodedQuery = IntStream.range(0, params.getCiphertextNum())
            .mapToObj(i -> UpsoUtils.computePowers(coeffs[i], zp64, params.getQueryPowers(), parallel))
            .collect(Collectors.toCollection(ArrayList::new));
        Stream<long[][]> encodeStream = parallel ? encodedQuery.stream().parallel() : encodedQuery.stream();
        return encodeStream
            .map(query -> McSogsUpsuNativeUtils.generateQuery(params.getEncryptionParameters(), secretKey, query))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    private byte[][][] decodeResponse(List<byte[]> receiverResponse, int byteLength) {
        return hashRawSlots(decodeRawResponseSlots(receiverResponse), byteLength);
    }

    private byte[][][] decodeRawResponseSlots(List<byte[]> receiverResponse) {
        Stream<byte[]> responseStream = parallel ? receiverResponse.stream().parallel() : receiverResponse.stream();
        List<long[]> coeffs = responseStream
            .map(ciphertext -> McSogsUpsuNativeUtils.decodeReply(
                params.getEncryptionParameters(), secretKey, ciphertext
            ))
            .collect(Collectors.toCollection(ArrayList::new));
        byte[][][] rawSlots = new byte[alpha][params.getBinNum()][];
        for (int i = 0; i < params.getBinNum(); i++) {
            for (int j = 0; j < alpha; j++) {
                int cipherIndex = i / params.getItemPerCiphertext();
                int coeffIndex = (i % params.getItemPerCiphertext()) * params.getItemEncodedSlotSize();
                long[] item = new long[params.getItemEncodedSlotSize()];
                System.arraycopy(
                    coeffs.get(cipherIndex * alpha + j), coeffIndex, item, 0, params.getItemEncodedSlotSize()
                );
                byte[] bytes = PirUtils.convertCoeffsToBytes(item, params.getPlainModulusSize());
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

    private McrgTokenSogsPadCarrierSenderOutput buildReleaseOutput(byte[][] uBinPads, int[] columnPermutationMap,
                                                                   byte[][] originalBinPayloads) {
        byte[][] payloads = new byte[params.getBinNum()][];
        boolean[] realRowBits = new boolean[params.getBinNum()];
        byte[] botElement = botElementByteBuffer.array();
        for (int c = 0; c < params.getBinNum(); c++) {
            int originalColumn = columnPermutationMap[c];
            payloads[c] = BytesUtils.clone(originalBinPayloads[originalColumn]);
            realRowBits[c] = !Arrays.equals(payloads[c], botElement);
        }
        return new McrgTokenSogsPadCarrierSenderOutput(uBinPads, realRowBits, payloads);
    }
}
