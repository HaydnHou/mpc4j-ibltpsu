package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Specialized BA-UPOT standalone benchmark receiver.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotReceiver extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUpotConfig config;
    /**
     * core COT receiver.
     */
    private final CoreCotReceiver coreCotReceiver;
    /**
     * bucket count.
     */
    private int bucketNum;
    /**
     * receiver COT output.
     */
    private CotReceiverOutput cotReceiverOutput;

    public BaUpotReceiver(Rpc receiverRpc, Party senderParty, BaUpotConfig config) {
        super(BaUpotPtoDesc.getInstance(), receiverRpc, senderParty, config);
        this.config = config;
        coreCotReceiver = CoreCotFactory.createReceiver(receiverRpc, senderParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
    }

    /**
     * Offline phase: init and receive fixed number of COTs.
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
        coreCotReceiver.init();
        boolean[] choices = createChoices(config.cotNum(bucketNum));
        cotReceiverOutput = coreCotReceiver.receive(choices);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime, "receiver obtains benchmark COTs");
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Online phase: receive and process fixed-shape capsules.
     *
     * @return checksum.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public long execute() throws MpcAbortException {
        checkInitialized();
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUpotCapsuleCodec codec = new BaUpotCapsuleCodec(config);
        long checksum = 0L;
        int batchSize = config.getOnlineBatchSize();
        int payloadByteLength = config.onlinePayloadByteLength();
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = receiveOtherPartyPayload(BaUpotPtoDesc.PtoStep.SENDER_SEND_CAPSULE.ordinal());
            MpcAbortPreconditions.checkArgument(payload.size() == batchEnd - batchStart);
            for (int offset = 0; offset < payload.size(); offset++) {
                int bucketIndex = batchStart + offset;
                byte[] capsule = payload.get(offset);
                MpcAbortPreconditions.checkArgument(capsule.length == payloadByteLength);
                int cotIndex = bucketIndex * config.getCotNumPerBucket();
                checksum ^= codec.accumulate(
                    bucketIndex, capsule, cotReceiverOutput.getRb(cotIndex), cotReceiverOutput.getRb(cotIndex + 1)
                );
            }
        }
        stopWatch.stop();
        long receiveTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, receiveTime,
            "receiver processes " + bucketNum + " fixed capsules of " + payloadByteLength + "B"
        );
        logPhaseInfo(PtoState.PTO_END);
        return checksum;
    }

    private static boolean[] createChoices(int num) {
        boolean[] choices = new boolean[num];
        long state = 0xBA551B17C0FFEE12L;
        for (int i = 0; i < num; i++) {
            state = mix64(state + i);
            choices[i] = (state & 1L) != 0L;
        }
        return choices;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
