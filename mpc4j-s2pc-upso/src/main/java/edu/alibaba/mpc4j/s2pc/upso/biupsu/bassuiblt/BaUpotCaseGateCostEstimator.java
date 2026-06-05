package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UPOT case-gate circuit cost estimator.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotCaseGateCostEstimator {
    /**
     * AND gates per bucket.
     */
    public static final int AND_GATES_PER_BUCKET = 8;
    /**
     * XOR gates per bucket.
     */
    public static final int XOR_GATES_PER_BUCKET = 6;
    /**
     * NOT gates per bucket.
     */
    public static final int NOT_GATES_PER_BUCKET = 1;

    /**
     * private constructor.
     */
    private BaUpotCaseGateCostEstimator() {
        // empty
    }

    /**
     * Estimates gate cost.
     *
     * @param bucketCount bucket count.
     * @return estimate.
     */
    public static Estimate estimate(long bucketCount) {
        if (bucketCount < 0) {
            throw new IllegalArgumentException("bucketCount must be non-negative");
        }
        return new Estimate(
            bucketCount,
            Math.multiplyExact(bucketCount, AND_GATES_PER_BUCKET),
            Math.multiplyExact(bucketCount, XOR_GATES_PER_BUCKET),
            Math.multiplyExact(bucketCount, NOT_GATES_PER_BUCKET)
        );
    }

    /**
     * Gate estimate.
     */
    public static class Estimate {
        /**
         * bucket count.
         */
        private final long bucketCount;
        /**
         * AND gates.
         */
        private final long andGateCount;
        /**
         * XOR gates.
         */
        private final long xorGateCount;
        /**
         * NOT gates.
         */
        private final long notGateCount;

        Estimate(long bucketCount, long andGateCount, long xorGateCount, long notGateCount) {
            this.bucketCount = bucketCount;
            this.andGateCount = andGateCount;
            this.xorGateCount = xorGateCount;
            this.notGateCount = notGateCount;
        }

        public long getBucketCount() {
            return bucketCount;
        }

        public long getAndGateCount() {
            return andGateCount;
        }

        public long getXorGateCount() {
            return xorGateCount;
        }

        public long getNotGateCount() {
            return notGateCount;
        }

        public long getTotalGateCount() {
            return andGateCount + xorGateCount + notGateCount;
        }
    }
}
