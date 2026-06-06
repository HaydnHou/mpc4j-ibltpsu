package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Public context for one IBLT-style queue-peel bucket probe.
 *
 * <p>The context contains only IBLT-PSU-aligned public transcript data. It deliberately does not carry source labels,
 * case labels, membership labels, local deletion bits, raw tags/checks, or OT choices.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltQueuePeelProbeContext {
    /**
     * retry index.
     */
    private final int retryIndex;
    /**
     * public bucket index.
     */
    private final int bucketIndex;
    /**
     * public probe ordinal within the retry.
     */
    private final int probeOrdinal;

    BaSsuIbltQueuePeelProbeContext(int retryIndex, int bucketIndex, int probeOrdinal) {
        if (retryIndex < 0) {
            throw new IllegalArgumentException("retryIndex must be non-negative");
        }
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (probeOrdinal < 0) {
            throw new IllegalArgumentException("probeOrdinal must be non-negative");
        }
        this.retryIndex = retryIndex;
        this.bucketIndex = bucketIndex;
        this.probeOrdinal = probeOrdinal;
    }

    int getRetryIndex() {
        return retryIndex;
    }

    int getBucketIndex() {
        return bucketIndex;
    }

    int getProbeOrdinal() {
        return probeOrdinal;
    }
}
