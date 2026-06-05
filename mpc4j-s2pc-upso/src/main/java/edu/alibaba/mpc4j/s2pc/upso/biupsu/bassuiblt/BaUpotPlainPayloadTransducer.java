package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.List;

/**
 * Live plain payload-bound BA-UPOT transducer.
 *
 * <p>The transducer evaluates the ideal BA-UPOT bucket functionality, serializes the result as a fixed-shape plain
 * output capsule, and decodes it again. It gives the live peel loop the same payload boundary as the two-party plain
 * transport without pretending to be case-hiding.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadTransducer implements BaUpotBucketEvaluator {
    /**
     * codec.
     */
    private final BaUpotPlainOutputCapsuleCodec codec;
    /**
     * evaluated bucket count.
     */
    private long bucketCount;
    /**
     * fixed capsule bytes.
     */
    private long capsuleBytes;

    public BaUpotPlainPayloadTransducer(BaUpotPlainOutputCapsuleCodec codec) {
        this.codec = codec;
    }

    /**
     * Creates a transducer from BA-UPOT config.
     *
     * @param config config.
     * @return transducer.
     */
    public static BaUpotPlainPayloadTransducer fromConfig(BaUpotConfig config) {
        return new BaUpotPlainPayloadTransducer(BaUpotPlainOutputCapsuleCodec.fromConfig(config));
    }

    /**
     * Evaluates one bucket through the payload boundary.
     *
     * @param input bucket input.
     * @return decoded bucket output.
     */
    @Override
    public BaUpotBucketOutput evaluate(BaUpotBucketInput input) {
        BaUpotBucketOutput idealOutput = BaUpotIdeal.evaluate(input);
        byte[] capsule = codec.encode(idealOutput);
        bucketCount++;
        capsuleBytes += capsule.length;
        return codec.decode(input.getBucketIndex(), capsule);
    }

    /**
     * Evaluates a batch of buckets through the payload boundary.
     *
     * @param inputs bucket inputs.
     * @return decoded bucket outputs.
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
