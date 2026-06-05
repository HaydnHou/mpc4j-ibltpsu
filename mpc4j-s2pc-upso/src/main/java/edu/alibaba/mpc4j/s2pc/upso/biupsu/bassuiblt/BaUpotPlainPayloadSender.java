package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Plain payload-bound BA-UPOT sender.
 *
 * <p>This transport sends ideal bucket outputs in fixed-shape capsules. It is not case-hiding and is only used as the
 * payload-bound bridge before implementing secure BA-UPOT selection.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadSender extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUpotConfig config;
    /**
     * bucket count.
     */
    private int bucketNum;

    public BaUpotPlainPayloadSender(Rpc senderRpc, Party receiverParty, BaUpotConfig config) {
        super(BaUpotPlainPayloadPtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
    }

    /**
     * Initializes the plain transport.
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
     * Sends fixed-shape output capsules.
     *
     * @param outputs output list.
     * @return checksum.
     */
    public long execute(List<BaUpotBucketOutput> outputs) {
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        return execute(outputs.iterator());
    }

    /**
     * Sends fixed-shape output capsules from a streaming output iterator.
     *
     * @param outputs output iterator.
     * @return checksum.
     */
    public long execute(Iterator<BaUpotBucketOutput> outputs) {
        checkInitialized();
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUpotPlainOutputCapsuleCodec codec = BaUpotPlainOutputCapsuleCodec.fromConfig(config);
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
                    byte[] capsule = codec.encode(output);
                    checksum ^= firstLong(capsule);
                    payload.add(capsule);
                }
            } catch (RuntimeException e) {
                sendOtherPartyPayload(BaUpotPlainPayloadPtoDesc.PtoStep.SENDER_SEND_OUTPUT_CAPSULE.ordinal(), payload);
                throw e;
            }
            sendOtherPartyPayload(BaUpotPlainPayloadPtoDesc.PtoStep.SENDER_SEND_OUTPUT_CAPSULE.ordinal(), payload);
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
            "sender sends " + bucketNum + " plain output capsules of " + codec.capsuleByteLength() + "B"
        );
        logPhaseInfo(PtoState.PTO_END);
        return checksum;
    }

    static long firstLong(byte[] bytes) {
        long value = 0L;
        int length = Math.min(Long.BYTES, bytes.length);
        for (int i = 0; i < length; i++) {
            value |= (bytes[i] & 0xFFL) << (i * Byte.SIZE);
        }
        return value;
    }
}
