package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Ideal BA-UPOT bucket evaluator.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotIdealEvaluator implements BaUpotBucketEvaluator {
    /**
     * singleton instance.
     */
    private static final BaUpotIdealEvaluator INSTANCE = new BaUpotIdealEvaluator();

    private BaUpotIdealEvaluator() {
        // empty
    }

    public static BaUpotIdealEvaluator getInstance() {
        return INSTANCE;
    }

    @Override
    public BaUpotBucketOutput evaluate(BaUpotBucketInput input) {
        return BaUpotIdeal.evaluate(input);
    }
}
