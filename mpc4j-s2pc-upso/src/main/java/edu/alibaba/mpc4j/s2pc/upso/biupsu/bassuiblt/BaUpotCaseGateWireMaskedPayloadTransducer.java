package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.List;

/**
 * Live case-gate plus wire-masked payload BA-UPOT transducer.
 *
 * <p>This evaluator first applies the fixed case-gate specification, then crosses the fixed wire-masked capsule
 * boundary used by the payload bridge. It is still a research harness: the final secure implementation must replace the
 * local case-gate evaluation with a real Z2/GC backend.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotCaseGateWireMaskedPayloadTransducer implements BaUpotBucketEvaluator {
    /**
     * output codec.
     */
    private final BaUpotWireMaskedOutputCapsuleCodec codec;
    /**
     * aggregate gate stats.
     */
    private final BaUpotCaseGateCircuit.GateStats aggregateGateStats;
    /**
     * evaluated bucket count.
     */
    private long bucketCount;
    /**
     * fixed capsule bytes.
     */
    private long capsuleBytes;

    public BaUpotCaseGateWireMaskedPayloadTransducer(BaUpotWireMaskedOutputCapsuleCodec codec) {
        this.codec = codec;
        aggregateGateStats = new BaUpotCaseGateCircuit.GateStats();
    }

    /**
     * Creates a transducer from BA-UPOT config.
     *
     * @param config config.
     * @param maskSeed mask seed.
     * @return transducer.
     */
    public static BaUpotCaseGateWireMaskedPayloadTransducer fromConfig(BaUpotConfig config, byte[] maskSeed) {
        return new BaUpotCaseGateWireMaskedPayloadTransducer(
            BaUpotWireMaskedOutputCapsuleCodec.fromConfig(config, maskSeed)
        );
    }

    @Override
    public BaUpotBucketOutput evaluate(BaUpotBucketInput input) {
        BaUpotCaseGateCircuit.Evaluation evaluation = BaUpotCaseGateCircuit.evaluate(input);
        aggregateGateStats.add(evaluation.getGateStats());
        byte[] capsule = codec.encode(evaluation.getOutput());
        bucketCount++;
        capsuleBytes += capsule.length;
        return codec.decode(input.getBucketIndex(), capsule);
    }

    /**
     * Evaluates a batch of buckets.
     *
     * @param inputs bucket inputs.
     * @return decoded outputs.
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

    public BaUpotCaseGateCircuit.GateStats getAggregateGateStats() {
        return aggregateGateStats;
    }
}
