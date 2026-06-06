package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * PISF-IBLT enhanced UPSU sender.
 *
 * <p>This class lands the strict fallback execution profile: all equality and output decisions remain secret-shared,
 * and the sender only learns protocol completion. No peeled element, probe frontier, membership vector, success bit, or
 * output length is sent to the sender.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltUpsuSender extends AbstractUpsuSender {
    /**
     * Z2 circuit sender.
     */
    private final Z2cParty z2cSender;
    /**
     * MP-OPRF sender.
     */
    private final MpOprfSender mpOprfSender;
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

    public PisIbltUpsuSender(Rpc senderRpc, Party receiverParty, PisIbltUpsuConfig config) {
        super(PisIbltUpsuPtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
        if (config.getCanonicalMode() == PisIbltUpsuMode.STRICT_Z2_SORT) {
            z2cSender = Z2cFactory.createSender(senderRpc, receiverParty, config.getZ2cConfig());
            addSubPto(z2cSender);
            mpOprfSender = null;
        } else {
            z2cSender = null;
            mpOprfSender = OprfFactory.createMpOprfSender(senderRpc, receiverParty, config.getMpOprfConfig());
            addSubPto(mpOprfSender);
        }
        maxElementByteLength = config.getMaxElementByteLength();
    }

    @Override
    public void init(int maxSenderElementSize, int receiverElementSize) throws MpcAbortException {
        params = config.createParams(maxSenderElementSize, receiverElementSize);
        setInitInput(params.getSenderCapacity(), params.getReceiverCapacity());
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        if (config.getCanonicalMode() == PisIbltUpsuMode.STRICT_Z2_SORT) {
            int maxElementBitLength = maxElementByteLength * Byte.SIZE;
            int z2cBitNum = PisIbltUpsuUtils.z2cInitBitNum(
                params.getSenderCapacity(), params.getReceiverCapacity(), maxElementBitLength
            );
            z2cSender.init(z2cBitNum);
        } else {
            mpOprfSender.init(params.getReceiverCapacity());
        }
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> senderElementSet, int elementByteLength) throws MpcAbortException {
        PisIbltUpsuUtils.checkElementByteLength(elementByteLength, maxElementByteLength);
        setPtoInput(senderElementSet, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        if (config.getCanonicalMode() == PisIbltUpsuMode.PISF_FAST) {
            psuFast();
            logPhaseInfo(PtoState.PTO_END);
            return;
        }

        int elementBitLength = elementByteLength * Byte.SIZE;
        PisIbltUpsuUtils.SenderSlots senderSlots = PisIbltUpsuUtils.createSenderSlots(
            senderElementList, maxSenderElementSize, elementByteLength, secureRandom
        );

        stopWatch.start();
        PisIbltUpsuUtils.SortFallbackOutput output = PisIbltUpsuUtils.senderSortGroupFallback(
            z2cSender, envType, parallel, senderSlots, receiverElementSize, elementBitLength
        );
        stopWatch.stop();
        long fallbackTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, fallbackTime, "sender runs fixed secret sort/group fallback");

        stopWatch.start();
        z2cSender.revealOther(output.emit);
        z2cSender.revealOther((MpcZ2Vector[]) output.payloadShares);
        stopWatch.stop();
        long outputTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, outputTime, "sender opens fixed capsules to receiver");

        logPhaseInfo(PtoState.PTO_END);
    }

    private void psuFast() throws MpcAbortException {
        stopWatch.start();
        MpOprfSenderOutput oprfSenderOutput = mpOprfSender.oprf(receiverElementSize);
        byte[][] senderElements = senderElementList.stream()
            .map(ByteBuffer::array)
            .map(BytesUtils::clone)
            .toArray(byte[][]::new);
        byte[][] senderTokens = new byte[senderElements.length][];
        for (int i = 0; i < senderElements.length; i++) {
            senderTokens[i] = oprfSenderOutput.getPrf(senderElements[i]);
        }
        stopWatch.stop();
        long oprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, oprfTime, "sender runs MP-OPRF");

        stopWatch.start();
        boolean success = false;
        long nextExtraInfo = extraInfo;
        try {
            for (int retryIndex = 0; retryIndex < params.getRepetitionNum(); retryIndex++) {
                long retryExtraInfo = nextExtraInfo++;
                byte[] salt = BlockUtils.randomBlock(secureRandom);
                PisIbltFastTable table = buildFastSenderTable(salt, senderElements, senderTokens);
                sendSalt(retryExtraInfo, salt);
                sendFastTable(retryExtraInfo, table);
                if (receiveRetryResult(retryExtraInfo)) {
                    success = true;
                    break;
                }
            }
        } finally {
            extraInfo = nextExtraInfo;
        }
        MpcAbortPreconditions.checkArgument(success);
        stopWatch.stop();
        long tableTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, tableTime, "sender sends PISF fast table retries");
    }

    private PisIbltFastTable buildFastSenderTable(byte[] salt, byte[][] senderElements, byte[][] senderTokens) {
        PisIbltFastTable table = new PisIbltFastTable(params, elementByteLength);
        for (int i = 0; i < senderElements.length; i++) {
            byte[] key = PisIbltHashUtils.fastKey(envType, salt, senderTokens[i], params.getKeyByteLength());
            byte[] tag = PisIbltHashUtils.fastTag(envType, salt, key, params.getTagByteLength());
            int[] positions = PisIbltHashUtils.fastPositions(envType, salt, key, params);
            table.insertSender(key, tag, senderElements[i], positions);
        }
        return table;
    }

    private void sendSalt(long retryExtraInfo, byte[] salt) {
        DataPacketHeader saltHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PISF_SALT.ordinal(), retryExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(saltHeader, Collections.singletonList(salt)));
    }

    private void sendFastTable(long retryExtraInfo, PisIbltFastTable table) {
        DataPacketHeader tableHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PISF_TABLE.ordinal(), retryExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(tableHeader, table.createSenderPayload()));
    }

    private boolean receiveRetryResult(long retryExtraInfo) throws MpcAbortException {
        DataPacketHeader resultHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_RETRY_RESULT.ordinal(), retryExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> resultPayload = rpc.receive(resultHeader).getPayload();
        MpcAbortPreconditions.checkArgument(resultPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(resultPayload.get(0).length == 1);
        return resultPayload.get(0)[0] == 1;
    }
}
