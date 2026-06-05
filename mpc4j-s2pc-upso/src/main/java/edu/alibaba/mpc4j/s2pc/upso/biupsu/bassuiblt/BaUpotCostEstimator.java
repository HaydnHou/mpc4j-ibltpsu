package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UPOT fixed-bucket cost estimator.
 *
 * <p>The estimator separates two implementation routes. The specialized route follows the IBLT-PSU FUnionPeel style
 * using OPRF + OT case handling. The generic route treats BA-UPOT as a fixed bucket Boolean circuit and is expected to
 * be much heavier.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotCostEstimator {
    /**
     * H5 IBLT-PSU hash count.
     */
    public static final int H5_HASH_NUM = 5;
    /**
     * default H5 multiplier.
     */
    public static final double H5_MULTIPLIER = 3.5;
    /**
     * half-gates bytes per AND gate, two 128-bit ciphertexts.
     */
    public static final int HALF_GATES_BYTES_PER_AND = 32;
    /**
     * COT/GMW rough bytes per AND gate.
     */
    public static final int COT_BYTES_PER_AND = 16;

    /**
     * implementation mode.
     */
    public enum Mode {
        /**
         * Specialized OPRF + OT FUnionPeel-style implementation.
         */
        SPECIALIZED_FUNIONPEEL_STYLE,
        /**
         * Generic fixed-bucket Boolean 2PC implementation.
         */
        GENERIC_FIXED_BUCKET_2PC,
    }

    /**
     * private constructor.
     */
    private BaUpotCostEstimator() {
        // empty
    }

    /**
     * Estimates generic fixed-bucket circuit AND gates.
     *
     * @param elementBits element bits.
     * @param checkBits check bits.
     * @return AND gate estimate.
     */
    public static int estimateGenericAndGates(int elementBits, int checkBits) {
        if (elementBits <= 0) {
            throw new IllegalArgumentException("elementBits must be positive");
        }
        if (checkBits <= 0) {
            throw new IllegalArgumentException("checkBits must be positive");
        }
        int countClassAnds = 16;
        int validCaseAnds = 12;
        int equalityAnds = Math.max(1, checkBits - 1);
        int payloadMuxAnds = 3 * elementBits;
        int validMaskAnds = elementBits;
        int overheadAnds = 32;
        return countClassAnds + validCaseAnds + equalityAnds + payloadMuxAnds + validMaskAnds + overheadAnds;
    }

    /**
     * Estimates bytes per generic fixed-bucket circuit.
     *
     * @param elementBits element bits.
     * @param checkBits check bits.
     * @param bytesPerAnd bytes per AND.
     * @return bytes per bucket.
     */
    public static long estimateGenericBytesPerBucket(int elementBits, int checkBits, int bytesPerAnd) {
        return (long) estimateGenericAndGates(elementBits, checkBits) * bytesPerAnd;
    }

    /**
     * H5 fair two-output bucket probe estimate.
     *
     * @param n0 first set size.
     * @param n1 second set size.
     * @return bucket probe estimate.
     */
    public static long estimateH5BucketProbes(int n0, int n1) {
        int unionUpperBound = n0 + n1;
        long tableLength = (long) Math.ceil(H5_MULTIPLIER * unionUpperBound);
        return tableLength + (long) unionUpperBound * (H5_HASH_NUM - 1);
    }

    /**
     * H5 cell estimate.
     *
     * @param n0 first set size.
     * @param n1 second set size.
     * @return cell estimate.
     */
    public static long estimateH5Cells(int n0, int n1) {
        return (long) Math.ceil(H5_MULTIPLIER * (n0 + n1));
    }

    /**
     * BA anchor cell estimate.
     *
     * @param params parameters.
     * @return anchor cells.
     */
    public static long estimateBaCells(BaSsuIbltBiUpsuParams params) {
        return params.getTableLength();
    }

    /**
     * Estimates relative specialized cost against H5 FUnionPeel-style bucket handling.
     *
     * @param baScheduledBuckets scheduled BA buckets.
     * @param h5BucketProbes H5 bucket probes.
     * @param baPerBucketRelativeCost BA per-bucket cost relative to H5.
     * @return relative cost.
     */
    public static double estimateSpecializedRelativeCost(long baScheduledBuckets, long h5BucketProbes,
                                                         double baPerBucketRelativeCost) {
        if (h5BucketProbes <= 0) {
            throw new IllegalArgumentException("h5BucketProbes must be positive");
        }
        return baPerBucketRelativeCost * ((double) baScheduledBuckets) / h5BucketProbes;
    }

    /**
     * Estimates total generic 2PC bytes.
     *
     * @param baScheduledBuckets scheduled BA buckets.
     * @param elementBits element bits.
     * @param checkBits check bits.
     * @param bytesPerAnd bytes per AND.
     * @return total bytes.
     */
    public static long estimateGenericTotalBytes(long baScheduledBuckets, int elementBits, int checkBits,
                                                 int bytesPerAnd) {
        return baScheduledBuckets * estimateGenericBytesPerBucket(elementBits, checkBits, bytesPerAnd);
    }

    /**
     * Creates an estimate report.
     *
     * @param n0 first set size.
     * @param n1 second set size.
     * @param params BA parameters.
     * @param scheduledBuckets BA scheduled bucket count.
     * @param elementBits element bits.
     * @return estimate report.
     */
    public static EstimateReport estimate(int n0, int n1, BaSsuIbltBiUpsuParams params, long scheduledBuckets,
                                          int elementBits) {
        long h5Cells = estimateH5Cells(n0, n1);
        long h5BucketProbes = estimateH5BucketProbes(n0, n1);
        long baCells = estimateBaCells(params);
        int genericAndGates = estimateGenericAndGates(elementBits, params.getCheckBits());
        long genericHalfGateBytes = estimateGenericTotalBytes(
            scheduledBuckets, elementBits, params.getCheckBits(), HALF_GATES_BYTES_PER_AND
        );
        long genericCotBytes = estimateGenericTotalBytes(
            scheduledBuckets, elementBits, params.getCheckBits(), COT_BYTES_PER_AND
        );
        double specializedEqualCost = estimateSpecializedRelativeCost(scheduledBuckets, h5BucketProbes, 1.0);
        double specializedOnePointFiveCost = estimateSpecializedRelativeCost(scheduledBuckets, h5BucketProbes, 1.5);
        return new EstimateReport(
            h5Cells, h5BucketProbes, baCells, scheduledBuckets, genericAndGates, genericHalfGateBytes,
            genericCotBytes, specializedEqualCost, specializedOnePointFiveCost
        );
    }

    /**
     * Estimate report.
     */
    public static class EstimateReport {
        /**
         * H5 cells.
         */
        private final long h5Cells;
        /**
         * H5 bucket probes.
         */
        private final long h5BucketProbes;
        /**
         * BA cells.
         */
        private final long baCells;
        /**
         * BA scheduled buckets.
         */
        private final long baScheduledBuckets;
        /**
         * generic AND gates.
         */
        private final int genericAndGatesPerBucket;
        /**
         * generic half-gates bytes.
         */
        private final long genericHalfGateBytes;
        /**
         * generic COT bytes.
         */
        private final long genericCotBytes;
        /**
         * specialized equal per-bucket relative cost.
         */
        private final double specializedEqualRelativeCost;
        /**
         * specialized 1.5x per-bucket relative cost.
         */
        private final double specializedOnePointFiveRelativeCost;

        EstimateReport(long h5Cells, long h5BucketProbes, long baCells, long baScheduledBuckets,
                       int genericAndGatesPerBucket, long genericHalfGateBytes, long genericCotBytes,
                       double specializedEqualRelativeCost, double specializedOnePointFiveRelativeCost) {
            this.h5Cells = h5Cells;
            this.h5BucketProbes = h5BucketProbes;
            this.baCells = baCells;
            this.baScheduledBuckets = baScheduledBuckets;
            this.genericAndGatesPerBucket = genericAndGatesPerBucket;
            this.genericHalfGateBytes = genericHalfGateBytes;
            this.genericCotBytes = genericCotBytes;
            this.specializedEqualRelativeCost = specializedEqualRelativeCost;
            this.specializedOnePointFiveRelativeCost = specializedOnePointFiveRelativeCost;
        }

        public long getH5Cells() {
            return h5Cells;
        }

        public long getH5BucketProbes() {
            return h5BucketProbes;
        }

        public long getBaCells() {
            return baCells;
        }

        public long getBaScheduledBuckets() {
            return baScheduledBuckets;
        }

        public int getGenericAndGatesPerBucket() {
            return genericAndGatesPerBucket;
        }

        public long getGenericHalfGateBytes() {
            return genericHalfGateBytes;
        }

        public long getGenericCotBytes() {
            return genericCotBytes;
        }

        public double getSpecializedEqualRelativeCost() {
            return specializedEqualRelativeCost;
        }

        public double getSpecializedOnePointFiveRelativeCost() {
            return specializedOnePointFiveRelativeCost;
        }
    }
}
