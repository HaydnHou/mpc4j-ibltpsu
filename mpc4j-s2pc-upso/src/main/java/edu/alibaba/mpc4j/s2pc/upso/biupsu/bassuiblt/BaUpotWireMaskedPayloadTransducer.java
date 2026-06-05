package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.List;

/**
 * Live wire-masked payload-bound BA-UPOT transducer.
 *
 * <p>This evaluator crosses a bucket-bound masked capsule format inside the live peel loop. It hides the case from the
 * wire representation but is not a complete receiver-hidden BA-UPOT implementation because the local evaluator still
 * sees the decoded case.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotWireMaskedPayloadTransducer implements BaUpotBucketEvaluator {
    /**
     * codec.
     */
    private final BaUpotWireMaskedOutputCapsuleCodec codec;
    /**
     * bucket count.
     */
    private long bucketCount;
    /**
     * capsule bytes.
     */
    private long capsuleBytes;

    public BaUpotWireMaskedPayloadTransducer(BaUpotWireMaskedOutputCapsuleCodec codec) {
        this.codec = codec;
    }

    /**
     * Creates a transducer from config.
     *
     * @param config config.
     * @param maskSeed mask seed.
     * @return transducer.
     */
    public static BaUpotWireMaskedPayloadTransducer fromConfig(BaUpotConfig config, byte[] maskSeed) {
        return new BaUpotWireMaskedPayloadTransducer(
            BaUpotWireMaskedOutputCapsuleCodec.fromConfig(config, maskSeed)
        );
    }

    @Override
    public BaUpotBucketOutput evaluate(BaUpotBucketInput input) {
        BaUpotBucketOutput idealOutput = BaUpotIdeal.evaluate(input);
        byte[] capsule = codec.encode(idealOutput);
        bucketCount++;
        capsuleBytes += capsule.length;
        return codec.decode(input.getBucketIndex(), capsule);
    }

    /**
     * Evaluates a batch.
     *
     * @param inputs inputs.
     * @return outputs.
     */
    public List<BaUpotBucketOutput> evaluateBatch(List<BaUpotBucketInput> inputs) {
        List<BaUpotBucketOutput> outputs = new ArrayList<>(inputs.size());
        for (BaUpotBucketInput input : inputs) {
            outputs.add(evaluate(input));
        }
        return outputs;
    }

    public long getBucketCount() {
        return bucketCount;
    }

    public long getCapsuleBytes() {
        return capsuleBytes;
    }

    public int getCapsuleByteLength() {
        return codec.capsuleByteLength();
    }
}
