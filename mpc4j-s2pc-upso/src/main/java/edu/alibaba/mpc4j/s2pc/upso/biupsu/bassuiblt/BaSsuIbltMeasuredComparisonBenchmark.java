package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

import java.util.Locale;

/**
 * Same-profile measured BA-SSU-IBLT versus fair H5 / IBLT-PSU comparison runner.
 *
 * <p>This runner only aggregates two measured rows. It does not emit a positive speedup claim; final speedup wording
 * remains gated by a separate audit of the recorded large-profile rows.</p>
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public final class BaSsuIbltMeasuredComparisonBenchmark {
    /**
     * comparison benchmark kind.
     */
    public static final String BENCHMARK_KIND = "MEASURED_PRODUCTION_VS_H5_BASELINE_COMPARISON";
    /**
     * comparison-only security notice.
     */
    public static final String SECURITY_NOTICE = "COMPARISON_ONLY_NO_SPEEDUP_CLAIM";

    private BaSsuIbltMeasuredComparisonBenchmark() {
        // empty
    }

    /**
     * Command line entry point.
     *
     * @param args key=value benchmark arguments.
     * @throws Exception if the benchmark aborts.
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        System.out.println(run(config).toDisplayString());
    }

    /**
     * Runs the measured production row and the fair H5 baseline row with the same profile.
     *
     * @param config comparison config.
     * @return comparison result.
     * @throws InterruptedException interrupted.
     * @throws MpcAbortException protocol abort.
     */
    public static Result run(Config config) throws InterruptedException, MpcAbortException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        config.validate();
        BaSsuIbltMeasuredProductionBenchmark.Result productionResult =
            BaSsuIbltMeasuredProductionBenchmark.run(config.toProductionConfig());
        BaSsuIbltFairMeasuredH5BaselineBenchmark.Result baselineResult =
            BaSsuIbltFairMeasuredH5BaselineBenchmark.run(config.toBaselineConfig());
        if (productionResult.getUnionSize() != baselineResult.getUnionSize()) {
            throw new IllegalStateException("comparison rows disagree on union size");
        }
        int expectedPsica = config.senderSize + config.receiverSize - productionResult.getUnionSize();
        if (baselineResult.getPsiCa() != expectedPsica) {
            throw new IllegalStateException("baseline row disagrees on PSI-CA");
        }
        return new Result(config, productionResult, baselineResult, expectedPsica);
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return Double.POSITIVE_INFINITY;
        }
        return (double) numerator / (double) denominator;
    }

    /**
     * Comparison config.
     */
    public static class Config {
        /**
         * sender set size.
         */
        private int senderSize;
        /**
         * receiver set size.
         */
        private int receiverSize;
        /**
         * exact overlap.
         */
        private int overlap;
        /**
         * element bytes.
         */
        private int elementByteLength;
        /**
         * BA-SSU table multiplier.
         */
        private double alpha;
        /**
         * parallel H5 baseline.
         */
        private boolean baselineParallel;
        /**
         * BA-SSU online probe batch size.
         */
        private int onlineBatchSize;
        /**
         * deterministic seed.
         */
        private long seed;
        /**
         * benchmark timeout.
         */
        private long timeoutMillis;

        public Config() {
            senderSize = 1 << 10;
            receiverSize = 1 << 18;
            overlap = 256;
            elementByteLength = Long.BYTES;
            alpha = 1.55;
            baselineParallel = false;
            onlineBatchSize = 1 << 14;
            seed = 20260606L;
            timeoutMillis = 30L * 60L * 1000L;
        }

        public static Config fromArgs(String[] args) {
            Config config = new Config();
            if (args == null) {
                return config;
            }
            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                String[] parts = arg.split("=", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("argument must be key=value: " + arg);
                }
                config.apply(parts[0].trim(), parts[1].trim());
            }
            return config;
        }

        private void apply(String key, String value) {
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            switch (normalizedKey) {
                case "m":
                case "sender":
                case "sendersize":
                    senderSize = parseInt(value);
                    break;
                case "n":
                case "receiver":
                case "receiversize":
                    receiverSize = parseInt(value);
                    break;
                case "overlap":
                    overlap = parseInt(value);
                    break;
                case "elementbytes":
                case "elementbytelength":
                    elementByteLength = parseInt(value);
                    break;
                case "alpha":
                    alpha = Double.parseDouble(value);
                    break;
                case "baselineparallel":
                case "parallel":
                    baselineParallel = Boolean.parseBoolean(value);
                    break;
                case "batch":
                case "batchsize":
                case "onlinebatch":
                case "onlinebatchsize":
                    onlineBatchSize = parseInt(value);
                    break;
                case "seed":
                    seed = Long.parseLong(value);
                    break;
                case "timeoutmillis":
                case "jointimeoutmillis":
                    timeoutMillis = Long.parseLong(value);
                    break;
                default:
                    throw new IllegalArgumentException("unknown argument: " + key);
            }
        }

        private static int parseInt(String value) {
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("2^")) {
                int exponent = Integer.parseInt(trimmed.substring(2));
                if (exponent < 0 || exponent >= Integer.SIZE - 1) {
                    throw new IllegalArgumentException("unsupported power-of-two int: " + value);
                }
                return 1 << exponent;
            }
            return Integer.parseInt(trimmed);
        }

        private void validate() {
            if (senderSize <= 0 || receiverSize <= 0) {
                throw new IllegalArgumentException("senderSize and receiverSize must be positive");
            }
            if (overlap < 0 || overlap > Math.min(senderSize, receiverSize)) {
                throw new IllegalArgumentException("overlap must be in [0, min(senderSize, receiverSize)]");
            }
            if (elementByteLength <= 0) {
                throw new IllegalArgumentException("elementByteLength must be positive");
            }
            if (!Double.isFinite(alpha) || alpha <= 1.0) {
                throw new IllegalArgumentException("alpha must be finite and greater than 1");
            }
            if (onlineBatchSize <= 0) {
                throw new IllegalArgumentException("onlineBatchSize must be positive");
            }
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis must be positive");
            }
        }

        private BaSsuIbltMeasuredProductionBenchmark.Config toProductionConfig() {
            return new BaSsuIbltMeasuredProductionBenchmark.Config()
                .setSenderSize(senderSize)
                .setReceiverSize(receiverSize)
                .setOverlap(overlap)
                .setElementByteLength(elementByteLength)
                .setAlpha(alpha)
                .setOnlineBatchSize(onlineBatchSize)
                .setSeed(seed)
                .setTimeoutMillis(timeoutMillis);
        }

        private BaSsuIbltFairMeasuredH5BaselineBenchmark.Config toBaselineConfig() {
            return new BaSsuIbltFairMeasuredH5BaselineBenchmark.Config()
                .setSenderSize(senderSize)
                .setReceiverSize(receiverSize)
                .setOverlap(overlap)
                .setElementByteLength(elementByteLength)
                .setParallel(baselineParallel)
                .setSeed(seed)
                .setTimeoutMillis(timeoutMillis);
        }

        private String toArgString() {
            return String.format(
                Locale.ROOT,
                "m=%d n=%d overlap=%d elementBytes=%d alpha=%.3f onlineBatchSize=%d baselineParallel=%s "
                    + "seed=%d timeoutMillis=%d",
                senderSize, receiverSize, overlap, elementByteLength, alpha, onlineBatchSize, baselineParallel, seed,
                timeoutMillis
            );
        }

        public Config setSenderSize(int senderSize) {
            this.senderSize = senderSize;
            return this;
        }

        public Config setReceiverSize(int receiverSize) {
            this.receiverSize = receiverSize;
            return this;
        }

        public Config setOverlap(int overlap) {
            this.overlap = overlap;
            return this;
        }

        public Config setAlpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Config setSeed(long seed) {
            this.seed = seed;
            return this;
        }

        public Config setOnlineBatchSize(int onlineBatchSize) {
            this.onlineBatchSize = onlineBatchSize;
            return this;
        }
    }

    /**
     * Comparison result.
     */
    public static final class Result {
        /**
         * config.
         */
        private final Config config;
        /**
         * measured production row.
         */
        private final BaSsuIbltMeasuredProductionBenchmark.Result productionResult;
        /**
         * fair H5 baseline row.
         */
        private final BaSsuIbltFairMeasuredH5BaselineBenchmark.Result baselineResult;
        /**
         * PSI-CA.
         */
        private final int psica;

        private Result(Config config, BaSsuIbltMeasuredProductionBenchmark.Result productionResult,
                       BaSsuIbltFairMeasuredH5BaselineBenchmark.Result baselineResult, int psica) {
            this.config = config;
            this.productionResult = productionResult;
            this.baselineResult = baselineResult;
            this.psica = psica;
        }

        public String getBenchmarkKind() {
            return BENCHMARK_KIND;
        }

        public String getSecurityNotice() {
            return SECURITY_NOTICE;
        }

        public boolean isSpeedupClaimReady() {
            return false;
        }

        public int getUnionSize() {
            return productionResult.getUnionSize();
        }

        public int getPsiCa() {
            return psica;
        }

        public double getTotalTimeRatio() {
            return ratio(productionResult.getTotalTimeNanos(), baselineResult.getTotalTimeNanos());
        }

        public double getOnlineTimeRatio() {
            return ratio(productionResult.getOnlineTimeNanos(), baselineResult.getOnlineTimeNanos());
        }

        public double getTotalByteRatio() {
            return ratio(productionResult.getTotalBytes(), baselineResult.getTotalBytes());
        }

        public double getOnlineByteRatio() {
            return ratio(productionResult.getOnlineTotalBytes(), baselineResult.getOnlineTotalBytes());
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT measured production versus fair H5/IBLT-PSU baseline%n"
                    + "benchmarkKind=%s%n"
                    + "securityNotice=%s%n"
                    + "productionBenchmarkKind=%s%n"
                    + "baselineBenchmarkKind=%s%n"
                    + "productionMeasuredProduction=%s%n"
                    + "productionReady=%s%n"
                    + "baselineMeasuredProduction=%s%n"
                    + "baselineProductionReady=%s%n"
                    + "senderSize=%d%n"
                    + "receiverSize=%d%n"
                    + "overlap=%d%n"
                    + "unionSize=%d%n"
                    + "psica=%d%n"
                    + "productionOfflineTimeMs=%.3f%n"
                    + "productionOnlineTimeMs=%.3f%n"
                    + "productionTotalTimeMs=%.3f%n"
                    + "baselineOfflineTimeMs=%.3f%n"
                    + "baselineOnlineTimeMs=%.3f%n"
                    + "baselineTotalTimeMs=%.3f%n"
                    + "productionOfflineTotalBytes=%d%n"
                    + "productionOnlineTotalBytes=%d%n"
                    + "productionTotalBytes=%d%n"
                    + "productionLogicalProbeCount=%d%n"
                    + "productionOnlineBatchCount=%d%n"
                    + "productionAvgLogicalProbesPerBatch=%.3f%n"
                    + "productionMaxProbeBatchSize=%d%n"
                    + "productionPublicDuplicateSkipCount=%d%n"
                    + "productionPublicUnchangedSkipCount=%d%n"
                    + "productionPublicSkipCount=%d%n"
                    + "baselineOfflineTotalBytes=%d%n"
                    + "baselineOnlineTotalBytes=%d%n"
                    + "baselineTotalBytes=%d%n"
                    + "totalTimeRatio=%.6f%n"
                    + "onlineTimeRatio=%.6f%n"
                    + "totalByteRatio=%.6f%n"
                    + "onlineByteRatio=%.6f%n"
                    + "speedupClaimReady=%s%n"
                    + "rawCommand=%s%n"
                    + "rawOutputPath=N/A%n"
                    + "oomStatus=false",
                getBenchmarkKind(),
                getSecurityNotice(),
                productionResult.getBenchmarkKind(),
                baselineResult.getBenchmarkKind(),
                productionResult.isMeasuredProduction(),
                productionResult.isProductionReady(),
                baselineResult.isMeasuredProduction(),
                baselineResult.isProductionReady(),
                config.senderSize,
                config.receiverSize,
                config.overlap,
                getUnionSize(),
                getPsiCa(),
                productionResult.getOfflineTimeNanos() / 1_000_000.0,
                productionResult.getOnlineTimeNanos() / 1_000_000.0,
                productionResult.getTotalTimeNanos() / 1_000_000.0,
                baselineResult.getOfflineTimeNanos() / 1_000_000.0,
                baselineResult.getOnlineTimeNanos() / 1_000_000.0,
                baselineResult.getTotalTimeNanos() / 1_000_000.0,
                productionResult.getOfflineTotalBytes(),
                productionResult.getOnlineTotalBytes(),
                productionResult.getTotalBytes(),
                productionResult.getLogicalProbeCount(),
                productionResult.getOnlineBatchCount(),
                productionResult.getAverageProbeBatchSize(),
                productionResult.getMaxProbeBatchSize(),
                productionResult.getPublicDuplicateSkipCount(),
                productionResult.getPublicUnchangedSkipCount(),
                productionResult.getPublicSkipCount(),
                baselineResult.getOfflineTotalBytes(),
                baselineResult.getOnlineTotalBytes(),
                baselineResult.getTotalBytes(),
                getTotalTimeRatio(),
                getOnlineTimeRatio(),
                getTotalByteRatio(),
                getOnlineByteRatio(),
                isSpeedupClaimReady(),
                "BaSsuIbltMeasuredComparisonBenchmark " + config.toArgString()
            );
        }
    }
}
