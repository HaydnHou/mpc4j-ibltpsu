package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Fixed BA-SSU-IBLT bucket schedule.
 *
 * <p>The current schedule mode visits every bucket in canonical order for every public retry. The schedule depends only
 * on public parameters and is therefore independent of overlap, peel success, or bucket contents.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltFixedBucketSchedule {
    /**
     * schedule mode.
     */
    public enum ScheduleMode {
        /**
         * Visit all table buckets in increasing bucket-index order for every retry.
         */
        DIRECT_ALL_BUCKETS
    }

    /**
     * mode.
     */
    private final ScheduleMode mode;
    /**
     * retry count.
     */
    private final int retryCount;
    /**
     * table length.
     */
    private final int tableLength;

    private BaSsuIbltFixedBucketSchedule(ScheduleMode mode, int retryCount, int tableLength) {
        if (retryCount <= 0) {
            throw new IllegalArgumentException("retryCount must be positive");
        }
        if (tableLength <= 0) {
            throw new IllegalArgumentException("tableLength must be positive");
        }
        this.mode = mode;
        this.retryCount = retryCount;
        this.tableLength = tableLength;
    }

    /**
     * Creates a direct all-buckets schedule from BA-SSU parameters.
     *
     * @param params parameters.
     * @return fixed schedule.
     */
    public static BaSsuIbltFixedBucketSchedule directAllBuckets(BaSsuIbltBiUpsuParams params) {
        return new BaSsuIbltFixedBucketSchedule(
            ScheduleMode.DIRECT_ALL_BUCKETS, params.getRetryCount(), params.getTableLength()
        );
    }

    /**
     * Returns canonical bucket indices for a retry.
     *
     * @param retryIndex retry index.
     * @return bucket indices.
     */
    public int[] getBucketIndices(int retryIndex) {
        checkRetryIndex(retryIndex);
        int[] indices = new int[tableLength];
        for (int bucketIndex = 0; bucketIndex < tableLength; bucketIndex++) {
            indices[bucketIndex] = bucketIndex;
        }
        return indices;
    }

    /**
     * Returns the fixed global ordinal for a retry-local offset.
     *
     * @param retryIndex retry index.
     * @param retryOffset offset inside this retry.
     * @return global ordinal.
     */
    public long globalOrdinal(int retryIndex, int retryOffset) {
        checkRetryIndex(retryIndex);
        if (retryOffset < 0 || retryOffset >= tableLength) {
            throw new IllegalArgumentException("retryOffset out of range");
        }
        return (long) retryIndex * tableLength + retryOffset;
    }

    public ScheduleMode getMode() {
        return mode;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getTableLength() {
        return tableLength;
    }

    public long getTotalBucketCount() {
        return (long) retryCount * tableLength;
    }

    private void checkRetryIndex(int retryIndex) {
        if (retryIndex < 0 || retryIndex >= retryCount) {
            throw new IllegalArgumentException("retryIndex must be in [0, retryCount)");
        }
    }
}
