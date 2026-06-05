package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wire-masked payload-bound BA-UPOT sender.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotWireMaskedPayloadSender extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUpotConfig config;
    /**
     * mask seed.
     */
    private final byte[] maskSeed;
    /**
     * bucket count.
     */
    private int bucketNum;

    public BaUpotWireMaskedPayloadSender(Rpc senderRpc, Party receiverParty, BaUpotConfig config, byte[] maskSeed) {
        super(BaUpotWireMaskedPayloadPtoDesc.getInstance(), senderRpc, receiverParty, config);
        if (maskSeed == null || maskSeed.length == 0) {
            throw new IllegalArgumentException("maskSeed must be non-empty");
        }
        this.config = config;
        this.maskSeed = Arrays.copyOf(maskSeed, maskSeed.length);
    }

    /**
     * Initializes the transport.
     *
     * @param bucketNum bucket count.
     */
    public void init(int bucketNum) {
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        this.bucketNum = bucketNum;
        logPhaseInfo(PtoState.INIT_BEGIN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Sends masked output capsules.
     *
     * @param outputs outputs.
     * @return checksum.
     */
    public long execute(List<BaUpotBucketOutput> outputs) {
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        return execute(outputs.iterator());
    }

    /**
     * Sends masked output capsules from a streaming output iterator.
     *
     * @param outputs outputs.
     * @return checksum.
     */
    public long execute(Iterator<BaUpotBucketOutput> outputs) {
        checkInitialized();
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUpotWireMaskedOutputCapsuleCodec codec = BaUpotWireMaskedOutputCapsuleCodec.fromConfig(config, maskSeed);
        int batchSize = config.getOnlineBatchSize();
        long checksum = 0L;
        boolean outputsShorterThanBucketNum = false;
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = new ArrayList<>(batchEnd - batchStart);
            try {
                for (int index = batchStart; index < batchEnd; index++) {
                    if (!outputs.hasNext()) {
                        outputsShorterThanBucketNum = true;
                        break;
                    }
                    BaUpotBucketOutput output = outputs.next();
                    if (output == null) {
                        throw new IllegalArgumentException("outputs must not contain null entries");
                    }
                    byte[] capsule = codec.encode(output, index);
                    checksum ^= BaUpotPlainPayloadSender.firstLong(capsule);
                    payload.add(capsule);
                }
            } catch (RuntimeException e) {
                sendOtherPartyPayload(
                    BaUpotWireMaskedPayloadPtoDesc.PtoStep.SENDER_SEND_MASKED_OUTPUT_CAPSULE.ordinal(), payload
                );
                throw e;
            }
            sendOtherPartyPayload(
                BaUpotWireMaskedPayloadPtoDesc.PtoStep.SENDER_SEND_MASKED_OUTPUT_CAPSULE.ordinal(), payload
            );
            if (outputsShorterThanBucketNum) {
                throw new IllegalArgumentException("outputs shorter than bucketNum");
            }
        }
        if (outputs.hasNext()) {
            throw new IllegalArgumentException("outputs longer than bucketNum");
        }
        stopWatch.stop();
        long sendTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, sendTime,
            "sender sends " + bucketNum + " wire-masked output capsules of " + codec.capsuleByteLength() + "B"
        );
        logPhaseInfo(PtoState.PTO_END);
        return checksum;
    }
}
