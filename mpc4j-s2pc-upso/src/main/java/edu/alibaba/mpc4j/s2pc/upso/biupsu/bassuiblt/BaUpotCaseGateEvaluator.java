package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Gate-counting BA-UPOT case evaluator.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotCaseGateEvaluator implements BaUpotBucketEvaluator {
    /**
     * aggregate gate stats.
     */
    private final BaUpotCaseGateCircuit.GateStats aggregateGateStats;
    /**
     * evaluated bucket count.
     */
    private long bucketCount;

    public BaUpotCaseGateEvaluator() {
        aggregateGateStats = new BaUpotCaseGateCircuit.GateStats();
    }

    @Override
    public BaUpotBucketOutput evaluate(BaUpotBucketInput input) {
        BaUpotCaseGateCircuit.Evaluation evaluation = BaUpotCaseGateCircuit.evaluate(input);
        aggregateGateStats.add(evaluation.getGateStats());
        bucketCount++;
        return evaluation.getOutput();
    }

    public long getBucketCount() {
        return bucketCount;
    }

    public BaUpotCaseGateCircuit.GateStats getAggregateGateStats() {
        return aggregateGateStats;
    }
}
