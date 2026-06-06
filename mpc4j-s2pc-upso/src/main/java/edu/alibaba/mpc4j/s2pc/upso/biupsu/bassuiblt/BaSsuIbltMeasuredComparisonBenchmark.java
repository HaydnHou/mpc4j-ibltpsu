package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

import java.util.Locale;

/**
 * Same-profile measured BA-SSU-IBLT versus H5 / IBLT-PSU comparison runner.
 *
 * <p>This runner aggregates a BA-SSU measured row and the MPC4J API two-direction H5 / IBLT-PSU wrapper row. It also
 * prints a derived single-run estimate for paper-semantics IBLT-PSU comparisons, because the IBLT-PSU paper itself is
 * bi-output in one run. It does not emit a positive speedup claim.</p>
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
    /**
     * online-time-first target for the large profile.
     */
    public static final double ONLINE_TIME_TARGET_MILLIS = 5_500.0;

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
            return Double.NaN;
        }
        return (double) numerator / (double) denominator;
    }

    private static double ratio(long numerator, double denominator) {
        if (!(denominator > 0.0)) {
            return Double.NaN;
        }
        return (double) numerator / denominator;
    }

    private static String displayRatio(double ratio) {
        return Double.isFinite(ratio) ? String.format(Locale.ROOT, "%.6f", ratio) : "N/A";
    }

    private static String displayMillis(double nanos) {
        return Double.isFinite(nanos) ? String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0) : "N/A";
    }

    private static String displayBytes(double bytes) {
        return Double.isFinite(bytes) ? String.format(Locale.ROOT, "%.0f", bytes) : "N/A";
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
         * use a metadata-only H5 wrapper row.
         */
        private boolean baselineMetadataOnly;
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
            baselineMetadataOnly = false;
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
                case "baselinemetadataonly":
                case "metadataonly":
                case "baselineclaimgateonly":
                    baselineMetadataOnly = Boolean.parseBoolean(value);
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
                .setTimeoutMillis(timeoutMillis)
                .setMetadataOnly(baselineMetadataOnly);
        }

        private String toArgString() {
            return String.format(
                Locale.ROOT,
                "m=%d n=%d overlap=%d elementBytes=%d alpha=%.3f onlineBatchSize=%d baselineParallel=%s "
                    + "baselineMetadataOnly=%s seed=%d timeoutMillis=%d",
                senderSize, receiverSize, overlap, elementByteLength, alpha, onlineBatchSize, baselineParallel,
                baselineMetadataOnly, seed, timeoutMillis
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

        public Config setBaselineMetadataOnly(boolean baselineMetadataOnly) {
            this.baselineMetadataOnly = baselineMetadataOnly;
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
         * MPC4J API two-direction H5 wrapper row.
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

        public double getEstimatedPaperSingleRunOfflineTimeNanos() {
            if (!hasMeasuredBaseline()) {
                return Double.NaN;
            }
            return (double) baselineResult.getOfflineTimeNanos() / baselineResult.getWrapperRuns();
        }

        public double getEstimatedPaperSingleRunOnlineTimeNanos() {
            if (!hasMeasuredBaseline()) {
                return Double.NaN;
            }
            return (double) baselineResult.getOnlineTimeNanos() / baselineResult.getWrapperRuns();
        }

        public double getEstimatedPaperSingleRunTotalTimeNanos() {
            if (!hasMeasuredBaseline()) {
                return Double.NaN;
            }
            return (double) baselineResult.getTotalTimeNanos() / baselineResult.getWrapperRuns();
        }

        public double getEstimatedPaperSingleRunOfflineBytes() {
            if (!hasMeasuredBaseline()) {
                return Double.NaN;
            }
            return (double) baselineResult.getOfflineTotalBytes() / baselineResult.getWrapperRuns();
        }

        public double getEstimatedPaperSingleRunOnlineBytes() {
            if (!hasMeasuredBaseline()) {
                return Double.NaN;
            }
            return (double) baselineResult.getOnlineTotalBytes() / baselineResult.getWrapperRuns();
        }

        public double getEstimatedPaperSingleRunTotalBytes() {
            if (!hasMeasuredBaseline()) {
                return Double.NaN;
            }
            return (double) baselineResult.getTotalBytes() / baselineResult.getWrapperRuns();
        }

        public double getOnlineTimeRatioVsPaperSingleRunEstimate() {
            return ratio(productionResult.getOnlineTimeNanos(), getEstimatedPaperSingleRunOnlineTimeNanos());
        }

        public double getOnlineByteRatioVsPaperSingleRunEstimate() {
            return ratio(productionResult.getOnlineTotalBytes(), getEstimatedPaperSingleRunOnlineBytes());
        }

        public double getTotalTimeRatioVsPaperSingleRunEstimate() {
            return ratio(productionResult.getTotalTimeNanos(), getEstimatedPaperSingleRunTotalTimeNanos());
        }

        public double getTotalByteRatioVsPaperSingleRunEstimate() {
            return ratio(productionResult.getTotalBytes(), getEstimatedPaperSingleRunTotalBytes());
        }

        public boolean isOnlineTimeTargetMet() {
            return productionResult.getOnlineTimeNanos() / 1_000_000.0 <= ONLINE_TIME_TARGET_MILLIS;
        }

        public boolean hasMeasuredBaseline() {
            return baselineResult.isMeasuredBaseline();
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT measured production versus H5/IBLT-PSU wrapper%n"
                    + "benchmarkKind=%s%n"
                    + "securityNotice=%s%n"
                    + "productionBenchmarkKind=%s%n"
                    + "baselineBenchmarkKind=%s%n"
                    + "baselineName=%s%n"
                    + "baselinePaperSemantics=%s%n"
                    + "baselineNativePaperOneRun=%s%n"
                    + "baselineWrapperRuns=%d%n"
                    + "baselineMeasuredBaseline=%s%n"
                    + "baselineMetadataOnly=%s%n"
                    + "paperSingleRunEstimate=%s%n"
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
                    + "wrapperTotalTimeRatio=%s%n"
                    + "wrapperOnlineTimeRatio=%s%n"
                    + "wrapperTotalByteRatio=%s%n"
                    + "wrapperOnlineByteRatio=%s%n"
                    + "estimatedPaperSingleRunOfflineTimeMs=%s%n"
                    + "estimatedPaperSingleRunOnlineTimeMs=%s%n"
                    + "estimatedPaperSingleRunTotalTimeMs=%s%n"
                    + "estimatedPaperSingleRunOfflineBytes=%s%n"
                    + "estimatedPaperSingleRunOnlineBytes=%s%n"
                    + "estimatedPaperSingleRunTotalBytes=%s%n"
                    + "onlineTimeRatioVsPaperSingleRunEstimate=%s%n"
                    + "onlineByteRatioVsPaperSingleRunEstimate=%s%n"
                    + "totalTimeRatioVsPaperSingleRunEstimate=%s%n"
                    + "totalByteRatioVsPaperSingleRunEstimate=%s%n"
                    + "onlineTimeTargetMs=%.3f%n"
                    + "onlineTimeTargetMet=%s%n"
                    + "speedupClaimReady=%s%n"
                    + "rawCommand=%s%n"
                    + "rawOutputPath=N/A%n"
                    + "oomStatus=false",
                getBenchmarkKind(),
                getSecurityNotice(),
                productionResult.getBenchmarkKind(),
                baselineResult.getBenchmarkKind(),
                baselineResult.getBaselineName(),
                baselineResult.isPaperSemantics(),
                baselineResult.isNativePaperOneRunBaseline(),
                baselineResult.getWrapperRuns(),
                baselineResult.isMeasuredBaseline(),
                baselineResult.isMetadataOnly(),
                hasMeasuredBaseline(),
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
                displayRatio(getTotalTimeRatio()),
                displayRatio(getOnlineTimeRatio()),
                displayRatio(getTotalByteRatio()),
                displayRatio(getOnlineByteRatio()),
                displayMillis(getEstimatedPaperSingleRunOfflineTimeNanos()),
                displayMillis(getEstimatedPaperSingleRunOnlineTimeNanos()),
                displayMillis(getEstimatedPaperSingleRunTotalTimeNanos()),
                displayBytes(getEstimatedPaperSingleRunOfflineBytes()),
                displayBytes(getEstimatedPaperSingleRunOnlineBytes()),
                displayBytes(getEstimatedPaperSingleRunTotalBytes()),
                displayRatio(getOnlineTimeRatioVsPaperSingleRunEstimate()),
                displayRatio(getOnlineByteRatioVsPaperSingleRunEstimate()),
                displayRatio(getTotalTimeRatioVsPaperSingleRunEstimate()),
                displayRatio(getTotalByteRatioVsPaperSingleRunEstimate()),
                ONLINE_TIME_TARGET_MILLIS,
                isOnlineTimeTargetMet(),
                isSpeedupClaimReady(),
                "BaSsuIbltMeasuredComparisonBenchmark " + config.toArgString()
            );
        }
    }
}
