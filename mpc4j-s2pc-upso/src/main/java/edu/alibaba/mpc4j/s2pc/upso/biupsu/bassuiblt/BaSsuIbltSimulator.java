package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Local BA-SSU-IBLT peel simulator.
 *
 * <p>This is a structural simulator. It does not implement OPRF, OT, COT, or BA-UPOT security. Instead, it evaluates
 * the ideal BA-UPOT case table to decide whether a parameter profile is worth implementing cryptographically.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltSimulator {
    /**
     * bottom marker.
     */
    private static final int BOTTOM = 0;

    /**
     * private constructor.
     */
    private BaSsuIbltSimulator() {
        // empty
    }

    /**
     * Simulates one protocol execution using generated integer sets.
     *
     * @param params parameters.
     * @param overlapRate overlap rate with respect to the shadow set.
     * @return simulation result.
     */
    public static Result simulate(BaSsuIbltBiUpsuParams params, double overlapRate) {
        if (overlapRate < 0.0 || overlapRate > 1.0) {
            throw new IllegalArgumentException("overlapRate must be in [0, 1]");
        }
        int overlap = (int) Math.round(params.getNShadow() * overlapRate);
        return simulate(params, overlap);
    }

    /**
     * Simulates one protocol execution using generated integer sets.
     *
     * @param params parameters.
     * @param overlap overlap size.
     * @return simulation result.
     */
    public static Result simulate(BaSsuIbltBiUpsuParams params, int overlap) {
        if (overlap < 0 || overlap > params.getNShadow()) {
            throw new IllegalArgumentException("overlap must be in [0, nShadow]");
        }
        int[] anchorElements = createAnchorElements(params.getNLarge());
        int[] shadowElements = createShadowElements(params.getNLarge(), params.getNShadow(), overlap);
        int unionSize = params.getNLarge() + params.getNShadow() - overlap;
        AttemptResult[] attempts = new AttemptResult[params.getRetryCount()];
        int selectedRetryIndex = -1;
        long scheduledBucketCount = 0L;
        long crossLayerBlockingCount = 0L;
        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            attempts[retryIndex] = simulateAttempt(params, retryIndex, anchorElements, shadowElements, unionSize);
            scheduledBucketCount += attempts[retryIndex].scheduledBucketCount;
            crossLayerBlockingCount += attempts[retryIndex].crossLayerBlockingCount;
            if (selectedRetryIndex < 0 && attempts[retryIndex].success) {
                selectedRetryIndex = retryIndex;
            }
        }
        long nTests = Math.max(1L, scheduledBucketCount * params.getChecksPerBucket());
        int recommendedCheckBits = BaSsuIbltBiUpsuParams.minCheckBits(
            params.getLambda(), nTests, params.getMarginBits()
        );
        int[] union = selectedRetryIndex >= 0 ? attempts[selectedRetryIndex].union : new int[0];
        return new Result(
            selectedRetryIndex >= 0, selectedRetryIndex, unionSize, union, attempts,
            scheduledBucketCount, crossLayerBlockingCount, nTests, recommendedCheckBits
        );
    }

    private static AttemptResult simulateAttempt(BaSsuIbltBiUpsuParams params, int retryIndex, int[] anchorElements,
                                                 int[] shadowElements, int unionSize) {
        BaSsuIbltCell[] table = new BaSsuIbltCell[params.getTableLength()];
        insertElements(params, retryIndex, table, anchorElements, true);
        insertElements(params, retryIndex, table, shadowElements, false);
        BitSet anchorRemaining = new BitSet(unionSize + 1);
        BitSet shadowRemaining = new BitSet(unionSize + 1);
        for (int element : anchorElements) {
            anchorRemaining.set(element);
        }
        for (int element : shadowElements) {
            shadowRemaining.set(element);
        }
        DuplicateSafeUnionListCoalescer coalescer = new DuplicateSafeUnionListCoalescer(unionSize);
        long scheduledBucketCount = 0L;
        long crossLayerBlockingCount = 0L;
        int roundCount = 0;
        int[] schedule = null;
        int maxRounds = Math.max(1, unionSize + 1);
        while (roundCount < maxRounds) {
            int scheduleSize = schedule == null ? params.getTableLength() : schedule.length;
            if (scheduleSize == 0) {
                break;
            }
            scheduledBucketCount += scheduleSize;
            IntList roundNew = new IntList();
            if (schedule == null) {
                for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
                    crossLayerBlockingCount += evaluateBucket(table[bucketIndex], coalescer, roundNew);
                }
            } else {
                for (int bucketIndex : schedule) {
                    crossLayerBlockingCount += evaluateBucket(table[bucketIndex], coalescer, roundNew);
                }
            }
            if (roundNew.isEmpty()) {
                break;
            }
            for (int i = 0; i < roundNew.size(); i++) {
                int element = roundNew.get(i);
                if (anchorRemaining.get(element)) {
                    deleteElement(params, retryIndex, table, element, true);
                    anchorRemaining.clear(element);
                }
                if (shadowRemaining.get(element)) {
                    deleteElement(params, retryIndex, table, element, false);
                    shadowRemaining.clear(element);
                }
            }
            schedule = touchedPositions(params, retryIndex, roundNew);
            roundCount++;
            if (anchorRemaining.isEmpty() && shadowRemaining.isEmpty()) {
                break;
            }
        }
        boolean success = anchorRemaining.isEmpty() && shadowRemaining.isEmpty();
        return new AttemptResult(
            retryIndex, success, roundCount, coalescer.toCanonicalArray(), scheduledBucketCount,
            crossLayerBlockingCount, anchorRemaining.cardinality(), shadowRemaining.cardinality()
        );
    }

    private static long evaluateBucket(BaSsuIbltCell cell, DuplicateSafeUnionListCoalescer coalescer,
                                       IntList roundNew) {
        if (cell == null) {
            return 0L;
        }
        long crossLayerBlocking = cell.isCrossLayerBlocking() ? 1L : 0L;
        int output = cell.unionSingletonOrBottom();
        if (output != BOTTOM && coalescer.insert(output)) {
            roundNew.add(output);
        }
        return crossLayerBlocking;
    }

    private static void insertElements(BaSsuIbltBiUpsuParams params, int retryIndex, BaSsuIbltCell[] table,
                                       int[] elements, boolean anchor) {
        for (int element : elements) {
            for (int position : BaSsuIbltPlacement.positions(params, retryIndex, element)) {
                if (table[position] == null) {
                    table[position] = new BaSsuIbltCell();
                }
                if (anchor) {
                    table[position].insertAnchor(element);
                } else {
                    table[position].insertShadow(element);
                }
            }
        }
    }

    private static void deleteElement(BaSsuIbltBiUpsuParams params, int retryIndex, BaSsuIbltCell[] table, int element,
                                      boolean anchor) {
        for (int position : BaSsuIbltPlacement.positions(params, retryIndex, element)) {
            if (anchor) {
                table[position].deleteAnchor(element);
            } else {
                table[position].deleteShadow(element);
            }
        }
    }

    private static int[] touchedPositions(BaSsuIbltBiUpsuParams params, int retryIndex, IntList roundNew) {
        BitSet touched = new BitSet(params.getTableLength());
        for (int i = 0; i < roundNew.size(); i++) {
            for (int position : BaSsuIbltPlacement.positions(params, retryIndex, roundNew.get(i))) {
                touched.set(position);
            }
        }
        int[] positions = new int[touched.cardinality()];
        int index = 0;
        for (int position = touched.nextSetBit(0); position >= 0; position = touched.nextSetBit(position + 1)) {
            positions[index++] = position;
        }
        return positions;
    }

    private static int[] createAnchorElements(int nLarge) {
        int[] elements = new int[nLarge];
        for (int i = 0; i < nLarge; i++) {
            elements[i] = i + 1;
        }
        return elements;
    }

    private static int[] createShadowElements(int nLarge, int nShadow, int overlap) {
        int[] elements = new int[nShadow];
        for (int i = 0; i < overlap; i++) {
            elements[i] = i + 1;
        }
        for (int i = overlap; i < nShadow; i++) {
            elements[i] = nLarge + i - overlap + 1;
        }
        return elements;
    }

    /**
     * Attempt result.
     */
    public static class AttemptResult {
        /**
         * retry index.
         */
        private final int retryIndex;
        /**
         * success.
         */
        private final boolean success;
        /**
         * round count.
         */
        private final int roundCount;
        /**
         * canonical union.
         */
        private final int[] union;
        /**
         * scheduled buckets.
         */
        private final long scheduledBucketCount;
        /**
         * cross-layer blocking count.
         */
        private final long crossLayerBlockingCount;
        /**
         * anchor residual count.
         */
        private final int anchorResidualCount;
        /**
         * shadow residual count.
         */
        private final int shadowResidualCount;

        AttemptResult(int retryIndex, boolean success, int roundCount, int[] union, long scheduledBucketCount,
                      long crossLayerBlockingCount, int anchorResidualCount, int shadowResidualCount) {
            this.retryIndex = retryIndex;
            this.success = success;
            this.roundCount = roundCount;
            this.union = Arrays.copyOf(union, union.length);
            this.scheduledBucketCount = scheduledBucketCount;
            this.crossLayerBlockingCount = crossLayerBlockingCount;
            this.anchorResidualCount = anchorResidualCount;
            this.shadowResidualCount = shadowResidualCount;
        }

        public int getRetryIndex() {
            return retryIndex;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getRoundCount() {
            return roundCount;
        }

        public int[] getUnion() {
            return Arrays.copyOf(union, union.length);
        }

        public long getScheduledBucketCount() {
            return scheduledBucketCount;
        }

        public long getCrossLayerBlockingCount() {
            return crossLayerBlockingCount;
        }

        public int getAnchorResidualCount() {
            return anchorResidualCount;
        }

        public int getShadowResidualCount() {
            return shadowResidualCount;
        }
    }

    /**
     * Simulation result.
     */
    public static class Result {
        /**
         * success.
         */
        private final boolean success;
        /**
         * selected retry index.
         */
        private final int selectedRetryIndex;
        /**
         * expected union size.
         */
        private final int expectedUnionSize;
        /**
         * canonical union.
         */
        private final int[] union;
        /**
         * attempts.
         */
        private final AttemptResult[] attempts;
        /**
         * scheduled bucket count.
         */
        private final long scheduledBucketCount;
        /**
         * cross-layer blocking count.
         */
        private final long crossLayerBlockingCount;
        /**
         * false test budget.
         */
        private final long nTests;
        /**
         * recommended check bits.
         */
        private final int recommendedCheckBits;

        Result(boolean success, int selectedRetryIndex, int expectedUnionSize, int[] union, AttemptResult[] attempts,
               long scheduledBucketCount, long crossLayerBlockingCount, long nTests, int recommendedCheckBits) {
            this.success = success;
            this.selectedRetryIndex = selectedRetryIndex;
            this.expectedUnionSize = expectedUnionSize;
            this.union = Arrays.copyOf(union, union.length);
            this.attempts = Arrays.copyOf(attempts, attempts.length);
            this.scheduledBucketCount = scheduledBucketCount;
            this.crossLayerBlockingCount = crossLayerBlockingCount;
            this.nTests = nTests;
            this.recommendedCheckBits = recommendedCheckBits;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getSelectedRetryIndex() {
            return selectedRetryIndex;
        }

        public int getExpectedUnionSize() {
            return expectedUnionSize;
        }

        public int[] getUnion() {
            return Arrays.copyOf(union, union.length);
        }

        public AttemptResult[] getAttempts() {
            return Arrays.copyOf(attempts, attempts.length);
        }

        public long getScheduledBucketCount() {
            return scheduledBucketCount;
        }

        public long getCrossLayerBlockingCount() {
            return crossLayerBlockingCount;
        }

        public long getNTests() {
            return nTests;
        }

        public int getRecommendedCheckBits() {
            return recommendedCheckBits;
        }
    }

    /**
     * Lightweight int list.
     */
    private static class IntList {
        /**
         * elements.
         */
        private int[] elements;
        /**
         * size.
         */
        private int size;

        IntList() {
            elements = new int[16];
            size = 0;
        }

        void add(int element) {
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, elements.length << 1);
            }
            elements[size++] = element;
        }

        int get(int index) {
            return elements[index];
        }

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }
    }
}
