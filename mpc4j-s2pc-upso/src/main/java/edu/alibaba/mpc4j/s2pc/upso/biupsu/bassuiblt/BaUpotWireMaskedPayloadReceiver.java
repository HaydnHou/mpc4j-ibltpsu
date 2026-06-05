package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
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
 * Wire-masked payload-bound BA-UPOT receiver.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotWireMaskedPayloadReceiver extends AbstractTwoPartyPto {
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
    /**
     * decoded outputs.
     */
    private List<BaUpotBucketOutput> outputs;
    /**
     * decoded output count.
     */
    private int decodedOutputCount;

    public BaUpotWireMaskedPayloadReceiver(Rpc receiverRpc, Party senderParty, BaUpotConfig config, byte[] maskSeed) {
        super(BaUpotWireMaskedPayloadPtoDesc.getInstance(), receiverRpc, senderParty, config);
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
        outputs = null;
        decodedOutputCount = 0;
        logPhaseInfo(PtoState.INIT_BEGIN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Receives masked output capsules.
     *
     * @param bucketIndices bucket indices.
     * @return checksum.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public long execute(List<Integer> bucketIndices) throws MpcAbortException {
        if (bucketIndices == null) {
            throw new IllegalArgumentException("bucketIndices must be non-null");
        }
        return execute(bucketIndices.iterator(), true);
    }

    /**
     * Receives masked output capsules.
     *
     * @param bucketIndices bucket indices.
     * @param retainOutputs true if decoded outputs should be retained.
     * @return checksum.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public long execute(Iterator<Integer> bucketIndices, boolean retainOutputs) throws MpcAbortException {
        checkInitialized();
        if (bucketIndices == null) {
            throw new IllegalArgumentException("bucketIndices must be non-null");
        }
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUpotWireMaskedOutputCapsuleCodec codec = BaUpotWireMaskedOutputCapsuleCodec.fromConfig(config, maskSeed);
        outputs = retainOutputs ? new ArrayList<>(bucketNum) : null;
        decodedOutputCount = 0;
        int batchSize = config.getOnlineBatchSize();
        long checksum = 0L;
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = receiveOtherPartyPayload(
                BaUpotWireMaskedPayloadPtoDesc.PtoStep.SENDER_SEND_MASKED_OUTPUT_CAPSULE.ordinal()
            );
            MpcAbortPreconditions.checkArgument(payload.size() == batchEnd - batchStart);
            for (int offset = 0; offset < payload.size(); offset++) {
                if (!bucketIndices.hasNext()) {
                    throw new IllegalArgumentException("bucketIndices shorter than bucketNum");
                }
                Integer boxedBucketIndex = bucketIndices.next();
                if (boxedBucketIndex == null) {
                    throw new IllegalArgumentException("bucketIndices must not contain null entries");
                }
                int bucketIndex = boxedBucketIndex;
                byte[] capsule = payload.get(offset);
                MpcAbortPreconditions.checkArgument(capsule.length == codec.capsuleByteLength());
                BaUpotBucketOutput output = codec.decode(bucketIndex, batchStart + offset, capsule);
                if (retainOutputs) {
                    outputs.add(output);
                }
                decodedOutputCount++;
                checksum ^= BaUpotPlainPayloadSender.firstLong(capsule);
            }
        }
        if (bucketIndices.hasNext()) {
            throw new IllegalArgumentException("bucketIndices longer than bucketNum");
        }
        stopWatch.stop();
        long receiveTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, receiveTime,
            "receiver decodes " + bucketNum + " wire-masked output capsules of " + codec.capsuleByteLength() + "B"
        );
        logPhaseInfo(PtoState.PTO_END);
        return checksum;
    }

    public List<BaUpotBucketOutput> getOutputs() {
        return outputs == null ? List.of() : List.copyOf(outputs);
    }

    public int getDecodedOutputCount() {
        return decodedOutputCount;
    }
}
