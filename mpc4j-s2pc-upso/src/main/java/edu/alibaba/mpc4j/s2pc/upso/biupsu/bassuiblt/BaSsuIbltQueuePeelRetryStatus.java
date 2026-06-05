package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.List;

/**
 * Public retry status for QUEUE_PEEL_ALIGNED.
 *
 * <p>This transcript intentionally exposes retry execution, retry success/failure, and probe count, matching the
 * IBLT-PSU-aligned leakage target. It does not contain source labels, case labels, membership labels, tag/check
 * material, or OT choices.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltQueuePeelRetryStatus {
    /**
     * retry index.
     */
    private final int retryIndex;
    /**
     * whether this retry was executed.
     */
    private final boolean executed;
    /**
     * public retry success.
     */
    private final boolean success;
    /**
     * public probe count.
     */
    private final long probeCount;
    /**
     * public max probe count.
     */
    private final long maxProbeCount;

    public BaSsuIbltQueuePeelRetryStatus(int retryIndex, boolean executed, boolean success, long probeCount,
                                         long maxProbeCount) {
        if (retryIndex < 0) {
            throw new IllegalArgumentException("retryIndex must be non-negative");
        }
        if (probeCount < 0) {
            throw new IllegalArgumentException("probeCount must be non-negative");
        }
        if (maxProbeCount <= 0) {
            throw new IllegalArgumentException("maxProbeCount must be positive");
        }
        if (probeCount > maxProbeCount) {
            throw new IllegalArgumentException("probeCount must be at most maxProbeCount");
        }
        if (!executed && (success || probeCount != 0)) {
            throw new IllegalArgumentException("not-run retry status must have success=false and probeCount=0");
        }
        this.retryIndex = retryIndex;
        this.executed = executed;
        this.success = success;
        this.probeCount = probeCount;
        this.maxProbeCount = maxProbeCount;
    }

    public static BaSsuIbltQueuePeelRetryStatus executed(int retryIndex, boolean success, long probeCount,
                                                         long maxProbeCount) {
        return new BaSsuIbltQueuePeelRetryStatus(retryIndex, true, success, probeCount, maxProbeCount);
    }

    public static BaSsuIbltQueuePeelRetryStatus notRunAfterSuccess(int retryIndex, long maxProbeCount) {
        return new BaSsuIbltQueuePeelRetryStatus(retryIndex, false, false, 0L, maxProbeCount);
    }

    public static void validatePrefixTranscript(List<BaSsuIbltQueuePeelRetryStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            throw new IllegalArgumentException("statuses must be non-empty");
        }
        boolean successSeen = false;
        for (int index = 0; index < statuses.size(); index++) {
            BaSsuIbltQueuePeelRetryStatus status = statuses.get(index);
            if (status == null) {
                throw new IllegalArgumentException("statuses must not contain null");
            }
            if (status.retryIndex != index) {
                throw new IllegalArgumentException("retryIndex must be consecutive from zero");
            }
            if (!successSeen && !status.executed) {
                throw new IllegalArgumentException("not-run statuses are allowed only after the first success");
            }
            if (successSeen && status.executed) {
                throw new IllegalArgumentException("statuses after first success must be not-run-after-success");
            }
            if (status.success) {
                if (!status.executed) {
                    throw new IllegalArgumentException("successful retry must be executed");
                }
                if (successSeen) {
                    throw new IllegalArgumentException("at most one retry may be successful");
                }
                successSeen = true;
            }
        }
    }

    public int getRetryIndex() {
        return retryIndex;
    }

    public boolean isExecuted() {
        return executed;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getProbeCount() {
        return probeCount;
    }

    public long getMaxProbeCount() {
        return maxProbeCount;
    }
}
