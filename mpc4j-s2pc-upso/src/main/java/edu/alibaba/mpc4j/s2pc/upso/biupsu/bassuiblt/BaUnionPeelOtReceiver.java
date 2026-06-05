package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * COT-backed BA-UnionPeel-OT fixed-shape masked transport receiver.
 *
 * <p>The receiver decodes fixed-shape capsules in this transport milestone. A later milestone must replace the shared
 * mask seed with true oblivious branch selection before production secure endpoint activation.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtReceiver extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUnionPeelOtSecureConfig config;
    /**
     * Core COT receiver.
     */
    private final CoreCotReceiver coreCotReceiver;
    /**
     * bucket count.
     */
    private int bucketNum;
    /**
     * COT count.
     */
    private int cotNum;
    /**
     * COT-derived mask seed.
     */
    private byte[] maskSeed;

    public BaUnionPeelOtReceiver(Rpc receiverRpc, Party senderParty, BaUnionPeelOtSecureConfig config) {
        super(BaUnionPeelOtSecurePtoDesc.getInstance(), receiverRpc, senderParty, config);
        this.config = config;
        coreCotReceiver = CoreCotFactory.createReceiver(receiverRpc, senderParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
    }

    public void init(int bucketNum) throws MpcAbortException {
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        this.bucketNum = bucketNum;
        cotNum = config.cotNum(bucketNum);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        coreCotReceiver.init();
        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(new boolean[cotNum]);
        maskSeed = BaUnionPeelOtSecureOutputCapsuleCodec.deriveSeed(cotReceiverOutput.getRbArray());
        stopWatch.stop();
        long cotTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, cotTime, "receiver runs fixed Core-COT num = " + cotNum);

        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    public BaUnionPeelOtReceiverOutput execute() throws MpcAbortException {
        checkInitialized();
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUnionPeelOtSecureOutputCapsuleCodec codec = new BaUnionPeelOtSecureOutputCapsuleCodec(
            config.getElementByteLength(), config.getAuthTagByteLength(), maskSeed
        );
        int batchSize = config.getOnlineBatchSize();
        List<BaUpotBucketOutput> outputs = new ArrayList<>(bucketNum);
        long checksum = 0L;
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = receiveOtherPartyPayload(
                BaUnionPeelOtSecurePtoDesc.PtoStep.SENDER_SEND_MASKED_OUTPUT_CAPSULES.ordinal()
            );
            if (payload.size() != batchEnd - batchStart) {
                throw new MpcAbortException("unexpected secure capsule batch size");
            }
            for (int offset = 0; offset < payload.size(); offset++) {
                int wireIndex = batchStart + offset;
                byte[] capsule = payload.get(offset);
                checksum ^= firstLong(capsule);
                outputs.add(codec.decode(wireIndex, wireIndex, capsule));
            }
        }
        stopWatch.stop();
        long onlineTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, onlineTime,
            "receiver decodes " + bucketNum + " secure masked capsules");
        logPhaseInfo(PtoState.PTO_END);
        return new BaUnionPeelOtReceiverOutput(cotNum, outputs, checksum);
    }

    private static long firstLong(byte[] input) {
        long value = 0L;
        int length = Math.min(Long.BYTES, input.length);
        for (int i = 0; i < length; i++) {
            value = (value << Byte.SIZE) | (input[i] & 0xFFL);
        }
        return value;
    }
}
