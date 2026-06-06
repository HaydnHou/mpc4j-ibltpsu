package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiverOutput;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * PISF-IBLT enhanced UPSU receiver.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltUpsuReceiver extends AbstractUpsuReceiver {
    /**
     * Z2 circuit receiver.
     */
    private final Z2cParty z2cReceiver;
    /**
     * MP-OPRF receiver.
     */
    private final MpOprfReceiver mpOprfReceiver;
    /**
     * max element byte length.
     */
    private final int maxElementByteLength;
    /**
     * config.
     */
    private final PisIbltUpsuConfig config;
    /**
     * public fixed profile.
     */
    private PisIbltUpsuParams params;
    /**
     * Real receiver elements before fixed-N padding.
     */
    private List<ByteBuffer> realReceiverElementList;
    /**
     * receiver real flags after fixed-N padding.
     */
    private boolean[] receiverReal;

    public PisIbltUpsuReceiver(Rpc receiverRpc, Party senderParty, PisIbltUpsuConfig config) {
        super(PisIbltUpsuPtoDesc.getInstance(), receiverRpc, senderParty, config);
        this.config = config;
        if (config.getCanonicalMode() == PisIbltUpsuMode.STRICT_Z2_SORT) {
            z2cReceiver = Z2cFactory.createReceiver(receiverRpc, senderParty, config.getZ2cConfig());
            addSubPto(z2cReceiver);
            mpOprfReceiver = null;
        } else {
            z2cReceiver = null;
            mpOprfReceiver = OprfFactory.createMpOprfReceiver(receiverRpc, senderParty, config.getMpOprfConfig());
            addSubPto(mpOprfReceiver);
        }
        maxElementByteLength = config.getMaxElementByteLength();
    }

    @Override
    public void init(Set<ByteBuffer> receiverElementSet, int maxSenderElementSize, int elementByteLength)
        throws MpcAbortException {
        PisIbltUpsuUtils.checkElementByteLength(elementByteLength, maxElementByteLength);
        params = config.createParams(maxSenderElementSize, receiverElementSet.size());
        setInitInput(receiverElementSet, params.getSenderCapacity(), elementByteLength);
        realReceiverElementList = new ArrayList<>(receiverElementList);
        if (config.getCanonicalMode() == PisIbltUpsuMode.STRICT_Z2_SORT) {
            PisIbltUpsuUtils.ReceiverSlots receiverSlots = PisIbltUpsuUtils.createReceiverSlots(
                realReceiverElementList, params.getReceiverCapacity(), elementByteLength, secureRandom
            );
            receiverElementList = receiverSlots.elements;
            receiverReal = receiverSlots.real;
            receiverElementSize = params.getReceiverCapacity();
        } else {
            receiverReal = null;
        }
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        if (config.getCanonicalMode() == PisIbltUpsuMode.STRICT_Z2_SORT) {
            int maxElementBitLength = maxElementByteLength * Byte.SIZE;
            int z2cBitNum = PisIbltUpsuUtils.z2cInitBitNum(
                params.getSenderCapacity(), receiverElementSize, maxElementBitLength
            );
            z2cReceiver.init(z2cBitNum);
        } else {
            mpOprfReceiver.init(params.getReceiverCapacity());
        }
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public UpsuReceiverOutput psu(int senderElementSize) throws MpcAbortException {
        setPtoInput(senderElementSize);
        logPhaseInfo(PtoState.PTO_BEGIN);

        if (config.getCanonicalMode() == PisIbltUpsuMode.PISF_FAST) {
            UpsuReceiverOutput output = psuFast();
            logPhaseInfo(PtoState.PTO_END);
            return output;
        }

        int elementBitLength = elementByteLength * Byte.SIZE;

        stopWatch.start();
        PisIbltUpsuUtils.SortFallbackOutput output = PisIbltUpsuUtils.receiverSortGroupFallback(
            z2cReceiver, envType, parallel, receiverElementList, receiverReal, maxSenderElementSize, elementBitLength
        );
        stopWatch.stop();
        long fallbackTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, fallbackTime, "receiver runs fixed secret sort/group fallback");

        stopWatch.start();
        BitVector emitBits = z2cReceiver.revealOwn(output.emit);
        BitVector[] selectedPayloadBits = z2cReceiver.revealOwn((MpcZ2Vector[]) output.payloadShares);
        byte[][] selectedPayloads = PisIbltUpsuUtils.combinePayloadBitVectors(envType, parallel, selectedPayloadBits);
        Set<ByteBuffer> union = new HashSet<>(realReceiverElementList.size() + maxSenderElementSize);
        PisIbltUpsuUtils.addReceiverSet(union, realReceiverElementList);
        for (int i = 0; i < maxSenderElementSize; i++) {
            if (emitBits.get(i)) {
                union.add(ByteBuffer.wrap(BytesUtils.clone(selectedPayloads[i])));
            }
        }
        stopWatch.stop();
        long outputTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, outputTime, "receiver opens fixed capsules");

        logPhaseInfo(PtoState.PTO_END);
        return new UpsuReceiverOutput(union, UpsuReceiverOutput.UNKNOWN_PSICA);
    }

    private UpsuReceiverOutput psuFast() throws MpcAbortException {
        stopWatch.start();
        byte[][] receiverElements = realReceiverElementList.stream()
            .map(ByteBuffer::array)
            .map(BytesUtils::clone)
            .toArray(byte[][]::new);
        MpOprfReceiverOutput oprfReceiverOutput = mpOprfReceiver.oprf(receiverElements);
        byte[][] receiverTokens = new byte[receiverElements.length][];
        for (int i = 0; i < receiverElements.length; i++) {
            receiverTokens[i] = oprfReceiverOutput.getPrf(i);
        }
        stopWatch.stop();
        long oprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, oprfTime, "receiver runs MP-OPRF");

        stopWatch.start();
        PisIbltFastPeeler.Result successResult = null;
        long nextExtraInfo = extraInfo;
        try {
            for (int retryIndex = 0; retryIndex < params.getRepetitionNum(); retryIndex++) {
                long retryExtraInfo = nextExtraInfo++;
                byte[] salt = receiveSalt(retryExtraInfo);
                PisIbltFastTable table = receiveFastTable(retryExtraInfo);
                PisIbltFastPeeler.Result result = runFastPeel(salt, table, receiverElements, receiverTokens);
                sendRetryResult(retryExtraInfo, result.isSuccess());
                if (result.isSuccess()) {
                    successResult = result;
                    break;
                }
            }
        } finally {
            extraInfo = nextExtraInfo;
        }
        MpcAbortPreconditions.checkArgument(successResult != null);
        Set<ByteBuffer> union = new HashSet<>(realReceiverElementList.size() + senderElementSize);
        PisIbltUpsuUtils.addReceiverSet(union, realReceiverElementList);
        union.addAll(successResult.getDeltaSet());
        int psica = senderElementSize + realReceiverElementList.size() - union.size();
        stopWatch.stop();
        long peelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, peelTime, "receiver peels PISF fast table");
        return new UpsuReceiverOutput(union, psica);
    }

    private PisIbltFastPeeler.Result runFastPeel(byte[] salt, PisIbltFastTable table, byte[][] receiverElements,
                                                byte[][] receiverTokens) {
        try {
            Map<ByteBuffer, PisIbltFastPeeler.ReceiverItem> receiverItemMap = PisIbltFastPeeler.createReceiverItemMap(
                envType, params, salt, receiverElements, receiverTokens, table
            );
            return PisIbltFastPeeler.peel(envType, params, salt, table, receiverItemMap);
        } catch (IllegalStateException e) {
            return new PisIbltFastPeeler.Result(false, new HashSet<>(), 0);
        }
    }

    private byte[] receiveSalt(long retryExtraInfo) throws MpcAbortException {
        DataPacketHeader saltHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PISF_SALT.ordinal(), retryExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> saltPayload = rpc.receive(saltHeader).getPayload();
        MpcAbortPreconditions.checkArgument(saltPayload.size() == 1);
        return saltPayload.get(0);
    }

    private PisIbltFastTable receiveFastTable(long retryExtraInfo) {
        DataPacketHeader tableHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PISF_TABLE.ordinal(), retryExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> tablePayload = rpc.receive(tableHeader).getPayload();
        return PisIbltFastTable.fromSenderPayload(params, elementByteLength, tablePayload);
    }

    private void sendRetryResult(long retryExtraInfo, boolean success) {
        DataPacketHeader resultHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_RETRY_RESULT.ordinal(), retryExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        byte[] result = new byte[]{(byte) (success ? 1 : 0)};
        rpc.send(DataPacket.fromByteArrayList(resultHeader, java.util.Collections.singletonList(result)));
    }
}
