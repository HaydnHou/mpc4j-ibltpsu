package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Set;

/**
 * Public-version-only skip policy for BA-SSU-IBLT queue peel.
 *
 * <p>The policy intentionally depends only on public bucket indices, public deletion versions, and the current public
 * batch schedule. It must not inspect local bucket state, local membership, tags, checks, or source-layer labels.</p>
 *
 * @author donghai hou
 * @date 2026/06/06
 */
final class BaSsuIbltQueuePeelLocalSkipPolicy {
    /**
     * never-probed marker.
     */
    static final int NEVER_PROBED = -1;

    /**
     * private constructor.
     */
    private BaSsuIbltQueuePeelLocalSkipPolicy() {
        // empty
    }

    /**
     * Decides whether a queued public bucket needs a remote UP-BA-UPOT probe.
     *
     * @param bucketIndex public bucket index.
     * @param currentVersion public deletion version for the bucket.
     * @param lastProbedVersion public version observed by the last probe of the bucket.
     * @param scheduledInCurrentBatch public bucket indices already selected in this batch.
     * @return skip decision.
     */
    static Decision decide(
        int bucketIndex, int currentVersion, int lastProbedVersion, Set<Integer> scheduledInCurrentBatch
    ) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be non-negative");
        }
        if (lastProbedVersion < NEVER_PROBED) {
            throw new IllegalArgumentException("lastProbedVersion is invalid");
        }
        if (scheduledInCurrentBatch == null) {
            throw new IllegalArgumentException("scheduledInCurrentBatch must be non-null");
        }
        if (scheduledInCurrentBatch.contains(bucketIndex)) {
            return Decision.SKIP_DUPLICATE_PUBLIC;
        }
        if (lastProbedVersion == currentVersion) {
            return Decision.SKIP_UNCHANGED_PUBLIC;
        }
        return Decision.PROBE_REMOTE;
    }

    /**
     * Public-version skip decision.
     */
    enum Decision {
        /**
         * Execute the fixed-shape remote bucket probe.
         */
        PROBE_REMOTE,
        /**
         * Bucket already appears in the current public batch.
         */
        SKIP_DUPLICATE_PUBLIC,
        /**
         * Bucket was already probed after the latest public deletion touching it.
         */
        SKIP_UNCHANGED_PUBLIC
    }
}
