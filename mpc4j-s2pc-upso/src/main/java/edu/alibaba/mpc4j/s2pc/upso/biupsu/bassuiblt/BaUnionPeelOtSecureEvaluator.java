package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UnionPeel-OT secure bucket evaluator.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtSecureEvaluator {
    /**
     * private constructor.
     */
    private BaUnionPeelOtSecureEvaluator() {
        // empty
    }

    static BaUpotBucketOutput evaluate(BaSsuIbltSecureBucketInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must be non-null");
        }
        if (input.getAnchor().isValidSingleton() && input.getShadow().getCount() == 0) {
            return BaUpotBucketOutput.singleton(
                input.getBucketIndex(), BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON,
                input.getAnchor().getKeyXorReference()
            );
        }
        if (input.getShadow().isValidSingleton() && input.getAnchor().getCount() == 0) {
            return BaUpotBucketOutput.singleton(
                input.getBucketIndex(), BaUpotBucketOutput.CaseType.SHADOW_SINGLETON,
                input.getShadow().getKeyXorReference()
            );
        }
        if (input.isSharedSingleton()) {
            return BaUpotBucketOutput.singleton(
                input.getBucketIndex(), BaUpotBucketOutput.CaseType.SHARED_SINGLETON,
                input.getAnchor().getKeyXorReference()
            );
        }
        return input.isEmpty() ? BaUpotBucketOutput.empty(input.getBucketIndex()) : BaUpotBucketOutput.blocked(
            input.getBucketIndex()
        );
    }
}
