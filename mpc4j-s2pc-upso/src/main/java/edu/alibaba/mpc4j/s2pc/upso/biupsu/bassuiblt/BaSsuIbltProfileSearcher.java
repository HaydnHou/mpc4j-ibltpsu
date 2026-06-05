package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BA-SSU-IBLT profile searcher.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltProfileSearcher {
    /**
     * private constructor.
     */
    private BaSsuIbltProfileSearcher() {
        // empty
    }

    /**
     * Searches candidate profiles.
     *
     * @param nLarge large set size.
     * @param nShadow shadow set size.
     * @param overlapRates overlap rates.
     * @param degrees degree candidates.
     * @param alphas alpha candidates.
     * @param trials trials per profile / overlap.
     * @return profile reports.
     */
    public static List<ProfileReport> search(int nLarge, int nShadow, double[] overlapRates, int[] degrees,
                                             double[] alphas, int trials) {
        if (trials <= 0) {
            throw new IllegalArgumentException("trials must be positive");
        }
        List<ProfileReport> reports = new ArrayList<>();
        for (int degree : degrees) {
            for (double alpha : alphas) {
                reports.add(evaluateProfile(nLarge, nShadow, overlapRates, degree, alpha, trials));
            }
        }
        return Collections.unmodifiableList(reports);
    }

    private static ProfileReport evaluateProfile(int nLarge, int nShadow, double[] overlapRates, int degree,
                                                 double alpha, int trials) {
        int successCount = 0;
        int totalCount = 0;
        long scheduledBucketCount = 0L;
        long crossLayerBlockingCount = 0L;
        int maxRoundCount = 0;
        int maxRecommendedCheckBits = 0;
        for (double overlapRate : overlapRates) {
            for (int trial = 0; trial < trials; trial++) {
                BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
                    .setDegree(degree)
                    .setAlphaAnchor(alpha)
                    .setRetryCount(1)
                    .setPublicPlaceSeed((((long) trial) << 32) ^ Double.doubleToLongBits(overlapRate))
                    .build();
                BaSsuIbltSimulator.Result result = BaSsuIbltSimulator.simulate(params, overlapRate);
                if (result.isSuccess()) {
                    successCount++;
                }
                totalCount++;
                scheduledBucketCount += result.getScheduledBucketCount();
                crossLayerBlockingCount += result.getCrossLayerBlockingCount();
                maxRoundCount = Math.max(maxRoundCount, result.getAttempts()[0].getRoundCount());
                maxRecommendedCheckBits = Math.max(maxRecommendedCheckBits, result.getRecommendedCheckBits());
            }
        }
        return new ProfileReport(
            nLarge, nShadow, degree, alpha, successCount, totalCount, scheduledBucketCount,
            crossLayerBlockingCount, maxRoundCount, maxRecommendedCheckBits
        );
    }

    /**
     * Profile report.
     */
    public static class ProfileReport {
        /**
         * large set size.
         */
        private final int nLarge;
        /**
         * shadow set size.
         */
        private final int nShadow;
        /**
         * degree.
         */
        private final int degree;
        /**
         * alpha.
         */
        private final double alpha;
        /**
         * success count.
         */
        private final int successCount;
        /**
         * total count.
         */
        private final int totalCount;
        /**
         * scheduled bucket count.
         */
        private final long scheduledBucketCount;
        /**
         * cross-layer blocking count.
         */
        private final long crossLayerBlockingCount;
        /**
         * max round count.
         */
        private final int maxRoundCount;
        /**
         * max recommended check bits.
         */
        private final int maxRecommendedCheckBits;

        ProfileReport(int nLarge, int nShadow, int degree, double alpha, int successCount, int totalCount,
                      long scheduledBucketCount, long crossLayerBlockingCount, int maxRoundCount,
                      int maxRecommendedCheckBits) {
            this.nLarge = nLarge;
            this.nShadow = nShadow;
            this.degree = degree;
            this.alpha = alpha;
            this.successCount = successCount;
            this.totalCount = totalCount;
            this.scheduledBucketCount = scheduledBucketCount;
            this.crossLayerBlockingCount = crossLayerBlockingCount;
            this.maxRoundCount = maxRoundCount;
            this.maxRecommendedCheckBits = maxRecommendedCheckBits;
        }

        public int getNLarge() {
            return nLarge;
        }

        public int getNShadow() {
            return nShadow;
        }

        public int getDegree() {
            return degree;
        }

        public double getAlpha() {
            return alpha;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public double getSuccessRate() {
            return ((double) successCount) / totalCount;
        }

        public double getAverageScheduledBucketCount() {
            return ((double) scheduledBucketCount) / totalCount;
        }

        public double getAverageCrossLayerBlockingCount() {
            return ((double) crossLayerBlockingCount) / totalCount;
        }

        public int getMaxRoundCount() {
            return maxRoundCount;
        }

        public int getMaxRecommendedCheckBits() {
            return maxRecommendedCheckBits;
        }

        @Override
        public String toString() {
            return "ProfileReport{"
                + "nLarge=" + nLarge
                + ", nShadow=" + nShadow
                + ", degree=" + degree
                + ", alpha=" + alpha
                + ", successRate=" + getSuccessRate()
                + ", avgScheduled=" + getAverageScheduledBucketCount()
                + ", avgCrossBlocking=" + getAverageCrossLayerBlockingCount()
                + ", maxRound=" + maxRoundCount
                + ", maxCheckBits=" + maxRecommendedCheckBits
                + '}';
        }
    }
}
