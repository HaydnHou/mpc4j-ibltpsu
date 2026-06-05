package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Locale;

/**
 * Queue-peel focused BA-SSU-IBLT benchmark wrapper.
 *
 * <p>This wrapper reports only the QUEUE_PEEL_ALIGNED fast-target estimate and the H5 IBLT baseline. It deliberately
 * keeps the non-production warning from the fair benchmark.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltQueuePeelBenchmark {
    /**
     * private constructor.
     */
    private BaSsuIbltQueuePeelBenchmark() {
        // empty
    }

    public static void main(String[] args) throws InterruptedException {
        Result result = run(BaSsuIbltSecureFairBenchmark.Config.fromArgs(args));
        System.out.println(result.toDisplayString());
    }

    public static Result run(BaSsuIbltSecureFairBenchmark.Config config) throws InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        return new Result(BaSsuIbltSecureFairBenchmark.run(config));
    }

    /**
     * Queue-peel benchmark result.
     */
    public static class Result {
        /**
         * fair benchmark result.
         */
        private final BaSsuIbltSecureFairBenchmark.Result fairResult;

        Result(BaSsuIbltSecureFairBenchmark.Result fairResult) {
            if (fairResult == null) {
                throw new IllegalArgumentException("fairResult must be non-null");
            }
            this.fairResult = fairResult;
        }

        public long getQueuePeelBuckets() {
            return fairResult.getQueuePeelBuckets();
        }

        public long getH5BucketProbes() {
            return fairResult.getH5BucketProbes();
        }

        public double getQueuePeelVsH5ProbeRatio() {
            return fairResult.getQueuePeelVsH5ProbeRatio();
        }

        public double getQueuePeelVsH5EstimatedProbeRatio() {
            return fairResult.getQueuePeelVsH5EstimatedProbeRatio();
        }

        public long getQueuePeelOfflineBytes() {
            return fairResult.getQueuePeelOfflineBytes();
        }

        public long getQueuePeelOnlineBytes() {
            return fairResult.getQueuePeelOnlineBytes();
        }

        public long getQueuePeelOfflineTotalBytes() {
            return fairResult.getQueuePeelOfflineTotalBytes();
        }

        public long getQueuePeelOnlineTotalBytes() {
            return fairResult.getQueuePeelOnlineTotalBytes();
        }

        public long getQueuePeelOfflineTimeNanos() {
            return fairResult.getQueuePeelOfflineTimeNanos();
        }

        public long getQueuePeelOnlineTimeNanos() {
            return fairResult.getQueuePeelOnlineTimeNanos();
        }

        public long getProbeCount() {
            return getQueuePeelBuckets();
        }

        public String getRetryStatus() {
            return fairResult.getQueuePeelRetryStatus();
        }

        public String getBenchmarkKind() {
            return fairResult.getBenchmarkKind();
        }

        public boolean isMeasuredProduction() {
            return fairResult.isMeasuredProduction();
        }

        public String getBaselineName() {
            return fairResult.getBaselineName();
        }

        public boolean isProductionReady() {
            return false;
        }

        public String getSecurityNotice() {
            return BaSsuIbltSecureFairBenchmark.SECURITY_NOTICE;
        }

        public long getOfflineBytes() {
            return getQueuePeelOfflineTotalBytes();
        }

        public long getOnlineBytes() {
            return getQueuePeelOnlineTotalBytes();
        }

        public long getTotalBytes() {
            return Math.addExact(getOfflineBytes(), getOnlineBytes());
        }

        public long getOfflineTimeNanos() {
            return getQueuePeelOfflineTimeNanos();
        }

        public long getOnlineTimeNanos() {
            return getQueuePeelOnlineTimeNanos();
        }

        public long getTotalTimeNanos() {
            return Math.addExact(getOfflineTimeNanos(), getOnlineTimeNanos());
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT QUEUE_PEEL_ALIGNED benchmark estimate%n"
                    + "securityNotice=%s%n"
                    + "benchmarkKind=%s%n"
                    + "measuredProduction=%s%n"
                    + "productionReady=%s%n"
                    + "retryStatus=%s%n"
                    + "baselineName=%s%n"
                    + "probeCount=%d%n"
                    + "offlineTimeMs=%.3f%n"
                    + "onlineTimeMs=%.3f%n"
                    + "totalTimeMs=%.3f%n"
                    + "offlineTotalBytes=%d%n"
                    + "onlineTotalBytes=%d%n"
                    + "totalBytes=%d%n"
                    + "queuePeelBuckets=%d%n"
                    + "h5BucketProbes=%d%n"
                    + "queuePeelVsH5EstimatedProbeRatio=%.6f%n"
                    + "queuePeelOfflineTime=%.3f ms%n"
                    + "queuePeelOnlineTime=%.3f ms%n"
                    + "queuePeelOfflineBytes=%d%n"
                    + "queuePeelOnlineBytes=%d",
                getSecurityNotice(),
                getBenchmarkKind(),
                isMeasuredProduction(),
                isProductionReady(),
                getRetryStatus(),
                getBaselineName(),
                getProbeCount(),
                getOfflineTimeNanos() / 1_000_000.0,
                getOnlineTimeNanos() / 1_000_000.0,
                getTotalTimeNanos() / 1_000_000.0,
                getOfflineBytes(),
                getOnlineBytes(),
                getTotalBytes(),
                getQueuePeelBuckets(),
                getH5BucketProbes(),
                getQueuePeelVsH5EstimatedProbeRatio(),
                getQueuePeelOfflineTimeNanos() / 1_000_000.0,
                getQueuePeelOnlineTimeNanos() / 1_000_000.0,
                getQueuePeelOfflineBytes(),
                getQueuePeelOnlineBytes()
            );
        }
    }
}
