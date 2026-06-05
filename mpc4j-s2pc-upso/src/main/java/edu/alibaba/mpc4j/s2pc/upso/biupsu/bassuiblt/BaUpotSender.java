package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Specialized BA-UPOT standalone benchmark sender.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotSender extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUpotConfig config;
    /**
     * core COT sender.
     */
    private final CoreCotSender coreCotSender;
    /**
     * bucket count.
     */
    private int bucketNum;
    /**
     * sender COT output.
     */
    private CotSenderOutput cotSenderOutput;

    public BaUpotSender(Rpc senderRpc, Party receiverParty, BaUpotConfig config) {
        super(BaUpotPtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
        coreCotSender = CoreCotFactory.createSender(senderRpc, receiverParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    /**
     * Offline phase: init and generate fixed number of COTs.
     *
     * @param bucketNum bucket count.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public void init(int bucketNum) throws MpcAbortException {
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        this.bucketNum = bucketNum;
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        coreCotSender.init(delta);
        cotSenderOutput = coreCotSender.send(config.cotNum(bucketNum));
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime, "sender generates benchmark COTs");
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Online phase: send fixed-shape capsules.
     *
     * @return checksum.
     */
    public long execute() {
        checkInitialized();
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUpotCapsuleCodec codec = new BaUpotCapsuleCodec(config);
        long checksum = 0L;
        int batchSize = config.getOnlineBatchSize();
        int payloadByteLength = config.onlinePayloadByteLength();
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = new ArrayList<>(batchEnd - batchStart);
            for (int bucketIndex = batchStart; bucketIndex < batchEnd; bucketIndex++) {
                int cotIndex = bucketIndex * config.getCotNumPerBucket();
                byte[] capsule = codec.encode(bucketIndex, cotSenderOutput.getR0(cotIndex), cotSenderOutput.getR0(cotIndex + 1));
                checksum ^= firstLong(capsule);
                payload.add(capsule);
            }
            sendOtherPartyPayload(BaUpotPtoDesc.PtoStep.SENDER_SEND_CAPSULE.ordinal(), payload);
        }
        stopWatch.stop();
        long sendTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, sendTime,
            "sender sends " + bucketNum + " fixed capsules of " + payloadByteLength + "B"
        );
        logPhaseInfo(PtoState.PTO_END);
        return checksum;
    }

    private static long firstLong(byte[] bytes) {
        long value = 0L;
        int length = Math.min(Long.BYTES, bytes.length);
        for (int i = 0; i < length; i++) {
            value |= (bytes[i] & 0xFFL) << (i * Byte.SIZE);
        }
        return value;
    }
}
