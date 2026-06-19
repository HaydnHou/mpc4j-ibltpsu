package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
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
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pir.PirUtils;
import edu.alibaba.mpc4j.s2pc.upso.UpsoUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.ScSogsUpsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.ShareCancelSogsTailSender;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * SC-SOGS UPSU sender.
 *
 * <p>This is the standalone share-cancel profile. It keeps its own protocol layer instead of delegating to MC-SOGS:
 * TCL-style FHE relation prefix, folded share-output relation carrier, and aggregate SOGS tail.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ScSogsUpsuSender extends AbstractUpsuSender {
    /**
     * Single-query OPRF receiver.
     */
    private final SqOprfReceiver sqOprfReceiver;
    /**
     * Row-level share relation carrier sender.
     */
    private final RowShareRelationCarrierSender rowShareRelationCarrierSender;
    /**
     * Share-cancel SOGS tail sender.
     */
    private final ShareCancelSogsTailSender shareCancelTailSender;
    /**
     * Config.
     */
    private final ScSogsUpsuConfig config;
    /**
     * SOGS degree.
     */
    private final int degree;
    /**
     * SOGS cell number derived at init.
     */
    private int cellNum;
    /**
     * SC-SOGS UPSU params.
     */
    private ScSogsUpsuParams params;
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

    public ScSogsUpsuSender(Rpc senderRpc, Party receiverParty, ScSogsUpsuConfig config) {
        super(ScSogsUpsuPtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
        sqOprfReceiver = SqOprfFactory.createReceiver(senderRpc, receiverParty, config.getSqOprfConfig());
        addSubPto(sqOprfReceiver);
        rowShareRelationCarrierSender = RowShareRelationCarrierFactory.createSender(
            senderRpc, receiverParty, config.getRowShareRelationCarrierConfig()
        );
        addSubPto(rowShareRelationCarrierSender);
        shareCancelTailSender = new ShareCancelSogsTailSender(
            senderRpc, receiverParty, config.getShareCancelTailConfig()
        );
        addSubPto(shareCancelTailSender);
        degree = config.getDegree();
    }

    @Override
    public void init(int maxSenderElementSize, int receiverElementSize) throws MpcAbortException {
        setInitInput(maxSenderElementSize, receiverElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        params = config.getParams();
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
        rowShareRelationCarrierSender.init(expectAlpha, params.getBinNum());
        shareCancelTailSender.init();

        DataPacketHeader cuckooHashKeyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_CUCKOO_HASH_KEYS.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> hashKeyPayload = rpc.receive(cuckooHashKeyHeader).getPayload();
        MpcAbortPreconditions.checkArgument(hashKeyPayload.size() == params.getCuckooHashNum());
        hashKeys = hashKeyPayload.toArray(new byte[0][]);

        List<byte[]> keyPair = ScSogsUpsuNativeUtils.keyGen(params.getEncryptionParameters());
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
        int peqtBitLength = CommonConstants.STATS_BIT_LENGTH
            + 2 * LongUtils.ceilLog2((long) alpha * params.getBinNum()) + 7;
        int peqtByteLength = CommonUtils.getByteLength(peqtBitLength);
        RowShareRelationCarrierConfig.InputType inputType = config.getRowShareRelationCarrierConfig().getInputType();
        int carrierByteLength = relationCarrierByteLength(inputType, peqtByteLength);
        byte[][][] senderRawResponseSlots = inputType == RowShareRelationCarrierConfig.InputType.RAW
            ? decodeRawResponseSlots(receiverResponsePayload)
            : null;
        byte[][][] senderRelationInput = inputType == RowShareRelationCarrierConfig.InputType.RAW
            ? senderRawResponseSlots
            : decodeResponse(receiverResponsePayload, peqtByteLength);
        stopWatch.stop();
        long decodeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 5, decodeTime, "sender decodes response");

        stopWatch.start();
        int[] rowPermutationMap = ScSogsUpsuUtils.freshPermutation(alpha, secureRandom);
        int[] columnPermutationMap = ScSogsUpsuUtils.freshPermutation(params.getBinNum(), secureRandom);
        // This is a private share of folded hit, not a plaintext hit/miss bit.
        // Do not reconstruct, log, or branch on membership before the aggregate SOGS tail.
        SquareZ2Vector senderHitShare = rowShareRelationCarrierSender.rowShareRelation(
            senderRelationInput, rowPermutationMap, columnPermutationMap, carrierByteLength
        );
        stopWatch.stop();
        long carrierTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 5, carrierTime, "sender executes relation carrier");

        stopWatch.start();
        MpcAbortPreconditions.checkArgument(senderHitShare.getNum() == params.getBinNum());
        ScSogsUpsuUtils.AnonymousSogsRowCarrier rowCarrier =
            ScSogsUpsuUtils.buildAnonymousSogsRowCarrier(
                columnPermutationMap, originalBinPayloads, botElementByteBuffer.array(), secureRandom
            );
        shareCancelTailSender.send(
            toBooleanArray(senderHitShare), rowCarrier.realRowBits, rowCarrier.tokens, rowCarrier.payloads, cellNum,
            degree
        );
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
            .map(query -> ScSogsUpsuNativeUtils.generateQuery(params.getEncryptionParameters(), secretKey, query))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    private byte[][][] decodeResponse(List<byte[]> receiverResponse, int byteLength) {
        return hashRawSlots(decodeRawResponseSlots(receiverResponse), byteLength);
    }

    private int relationCarrierByteLength(RowShareRelationCarrierConfig.InputType inputType, int peqtByteLength) {
        switch (inputType) {
            case DIGEST:
                return peqtByteLength;
            case RAW:
                return CommonUtils.getByteLength(params.getL());
            default:
                throw new IllegalArgumentException("Invalid row-share relation input type: " + inputType);
        }
    }

    private byte[][][] decodeRawResponseSlots(List<byte[]> receiverResponse) {
        Stream<byte[]> responseStream = parallel ? receiverResponse.stream().parallel() : receiverResponse.stream();
        List<long[]> coeffs = responseStream
            .map(ciphertext -> ScSogsUpsuNativeUtils.decodeReply(
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

    private static boolean[] toBooleanArray(SquareZ2Vector vector) {
        BitVector bitVector = vector.getBitVector();
        boolean[] output = new boolean[vector.getNum()];
        for (int i = 0; i < output.length; i++) {
            output[i] = bitVector.get(i);
        }
        return output;
    }
}
