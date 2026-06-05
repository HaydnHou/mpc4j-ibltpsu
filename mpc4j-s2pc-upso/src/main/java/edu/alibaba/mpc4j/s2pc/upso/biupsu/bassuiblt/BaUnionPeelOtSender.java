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
 * COT-backed BA-UnionPeel-OT fixed-shape masked transport sender.
 *
 * <p>This class is a protocol-facing transport milestone. It consumes secure bucket views and sends fixed-shape masked
 * capsules, but it must not be treated as final oblivious branch selection for {@code SECURE_SEMI_HONEST}.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtSender extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUnionPeelOtSecureConfig config;
    /**
     * Core COT sender.
     */
    private final CoreCotSender coreCotSender;
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

    public BaUnionPeelOtSender(Rpc senderRpc, Party receiverParty, BaUnionPeelOtSecureConfig config) {
        super(BaUnionPeelOtSecurePtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
        coreCotSender = CoreCotFactory.createSender(senderRpc, receiverParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    public void init(int bucketNum) throws MpcAbortException {
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        this.bucketNum = bucketNum;
        cotNum = config.cotNum(bucketNum);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        coreCotSender.init(BlockUtils.randomBlock(secureRandom));
        CotSenderOutput cotSenderOutput = coreCotSender.send(cotNum);
        maskSeed = BaUnionPeelOtSecureOutputCapsuleCodec.deriveSeed(cotSenderOutput.getR0Array());
        stopWatch.stop();
        long cotTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, cotTime, "sender runs fixed Core-COT num = " + cotNum);

        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    public BaUnionPeelOtSenderOutput execute(List<BaSsuIbltSecureBucketInput> bucketInputs) {
        checkInitialized();
        checkBucketInputs(bucketInputs, bucketNum);
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUnionPeelOtSecureOutputCapsuleCodec codec = new BaUnionPeelOtSecureOutputCapsuleCodec(
            config.getElementByteLength(), config.getAuthTagByteLength(), maskSeed
        );
        int batchSize = config.getOnlineBatchSize();
        long checksum = 0L;
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = new ArrayList<>(batchEnd - batchStart);
            for (int index = batchStart; index < batchEnd; index++) {
                BaSsuIbltSecureBucketInput bucketInput = bucketInputs.get(index);
                if (bucketInput.getBucketIndex() != index) {
                    throw new IllegalArgumentException("bucketInput index must match fixed wire index");
                }
                BaUpotBucketOutput output = BaUnionPeelOtSecureEvaluator.evaluate(bucketInput);
                byte[] capsule = codec.encode(index, output);
                checksum ^= firstLong(capsule);
                payload.add(capsule);
            }
            sendOtherPartyPayload(
                BaUnionPeelOtSecurePtoDesc.PtoStep.SENDER_SEND_MASKED_OUTPUT_CAPSULES.ordinal(), payload
            );
        }
        stopWatch.stop();
        long onlineTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 1, onlineTime,
            "sender sends " + bucketNum + " secure masked capsules");
        logPhaseInfo(PtoState.PTO_END);
        return new BaUnionPeelOtSenderOutput(bucketNum, cotNum, codec.capsuleByteLength(), checksum);
    }

    static void checkBucketInputs(List<BaSsuIbltSecureBucketInput> bucketInputs, int bucketNum) {
        if (bucketInputs == null) {
            throw new IllegalArgumentException("bucketInputs must be non-null");
        }
        if (bucketInputs.size() != bucketNum) {
            throw new IllegalArgumentException("bucketInputs size must equal bucketNum");
        }
        for (BaSsuIbltSecureBucketInput bucketInput : bucketInputs) {
            if (bucketInput == null) {
                throw new IllegalArgumentException("bucketInputs must not contain null entries");
            }
        }
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
