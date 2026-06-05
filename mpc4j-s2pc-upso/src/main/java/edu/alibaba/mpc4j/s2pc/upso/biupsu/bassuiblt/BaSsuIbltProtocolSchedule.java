package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-SSU-IBLT protocol-shape accounting schedule.
 *
 * <p>This class deliberately separates the current implemented secure-core loop from the IBLT-PSU-aligned queue-peel
 * schedule. It is an accounting object, not a peel algorithm.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProtocolSchedule {
    /**
     * protocol shape.
     */
    public enum Shape {
        /**
         * Current M14a/M15 fixed loop: every retry executes every public round and every table bucket.
         */
        CURRENT_FIXED_LOOP_M14A,
        /**
         * Historical lower-bound accounting only: one fixed pass over source-split buckets per retry.
         */
        TARGET_ONE_PASS,
        /**
         * IBLT-PSU-aligned queue peel: each retry starts with all buckets and then re-probes deletion frontier buckets.
         */
        QUEUE_PEEL_ALIGNED
    }

    /**
     * shape.
     */
    private final Shape shape;
    /**
     * retry count.
     */
    private final int retryCount;
    /**
     * table length.
     */
    private final int tableLength;
    /**
     * fixed round count.
     */
    private final int fixedRoundCount;
    /**
     * scheduled bucket count.
     */
    private final long scheduledBucketCount;

    private BaSsuIbltProtocolSchedule(Shape shape, BaSsuIbltBiUpsuParams params) {
        if (shape == null) {
            throw new IllegalArgumentException("shape must be non-null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        this.shape = shape;
        retryCount = params.getRetryCount();
        tableLength = params.getTableLength();
        fixedRoundCount = fixedRoundCount(params);
        scheduledBucketCount = computeScheduledBucketCount(shape, params, fixedRoundCount);
        if (shape == Shape.QUEUE_PEEL_ALIGNED) {
            checkQueuePeelCheckBits(params);
        }
    }

    public static BaSsuIbltProtocolSchedule of(Shape shape, BaSsuIbltBiUpsuParams params) {
        return new BaSsuIbltProtocolSchedule(shape, params);
    }

    public static BaSsuIbltProtocolSchedule currentFixedLoopM14a(BaSsuIbltBiUpsuParams params) {
        return of(Shape.CURRENT_FIXED_LOOP_M14A, params);
    }

    public static BaSsuIbltProtocolSchedule targetOnePass(BaSsuIbltBiUpsuParams params) {
        return of(Shape.TARGET_ONE_PASS, params);
    }

    public static BaSsuIbltProtocolSchedule queuePeelAligned(BaSsuIbltBiUpsuParams params) {
        return of(Shape.QUEUE_PEEL_ALIGNED, params);
    }

    public static int fixedRoundCount(BaSsuIbltBiUpsuParams params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return Math.addExact(Math.addExact(params.getNLarge(), params.getNShadow()), 1);
    }

    public static long nTests(BaSsuIbltBiUpsuParams params, Shape shape) {
        if (shape == Shape.QUEUE_PEEL_ALIGNED) {
            return queuePeelNTests(params);
        }
        BaSsuIbltProtocolSchedule schedule = of(shape, params);
        return Math.multiplyExact(schedule.getScheduledBucketCount(), params.getChecksPerBucket());
    }

    public static long currentFixedLoopM14aNTests(BaSsuIbltBiUpsuParams params) {
        return nTests(params, Shape.CURRENT_FIXED_LOOP_M14A);
    }

    public static long queuePeelMaxProbes(BaSsuIbltBiUpsuParams params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        long unionCapacity = Math.addExact((long) params.getNLarge(), params.getNShadow());
        long frontierProbes = Math.multiplyExact((long) params.getDegree(), unionCapacity);
        long retryProbes = Math.addExact(params.getTableLength(), frontierProbes);
        return Math.multiplyExact(params.getRetryCount(), retryProbes);
    }

    public static long queuePeelNTests(BaSsuIbltBiUpsuParams params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return Math.multiplyExact(queuePeelMaxProbes(params), params.getChecksPerBucket());
    }

    public static int queuePeelMinCheckBits(BaSsuIbltBiUpsuParams params) {
        return BaSsuIbltBiUpsuParams.minCheckBits(
            params.getLambda(), queuePeelNTests(params), params.getMarginBits()
        );
    }

    public static int currentFixedLoopM14aMinCheckBits(BaSsuIbltBiUpsuParams params) {
        return BaSsuIbltBiUpsuParams.minCheckBits(
            params.getLambda(), currentFixedLoopM14aNTests(params), params.getMarginBits()
        );
    }

    static void checkQueuePeelCheckBits(BaSsuIbltBiUpsuParams params) {
        int minCheckBits = queuePeelMinCheckBits(params);
        if (params.getCheckBits() < minCheckBits) {
            throw new IllegalArgumentException(
                "checkBits must cover QUEUE_PEEL_ALIGNED nTests; required at least " + minCheckBits
            );
        }
    }

    private static long computeScheduledBucketCount(Shape shape, BaSsuIbltBiUpsuParams params,
                                                    int fixedRoundCount) {
        long retryBuckets = Math.multiplyExact((long) params.getRetryCount(), params.getTableLength());
        switch (shape) {
            case CURRENT_FIXED_LOOP_M14A:
                return Math.multiplyExact(retryBuckets, fixedRoundCount);
            case TARGET_ONE_PASS:
                return retryBuckets;
            case QUEUE_PEEL_ALIGNED:
                return queuePeelMaxProbes(params);
            default:
                throw new IllegalStateException("unknown shape: " + shape);
        }
    }

    public Shape getShape() {
        return shape;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getTableLength() {
        return tableLength;
    }

    public int getFixedRoundCount() {
        return fixedRoundCount;
    }

    public long getScheduledBucketCount() {
        return scheduledBucketCount;
    }
}
