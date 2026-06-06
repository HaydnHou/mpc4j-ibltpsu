package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BA-SSU-IBLT measured production profile sweep.
 *
 * <p>This runner sweeps BA-SSU profile knobs that do not change the protocol security surface: table multiplier,
 * public degree, and online probe batch size. It intentionally runs the production endpoint only; compare the best row
 * against H5 / IBLT-PSU with {@link BaSsuIbltMeasuredComparisonBenchmark} after the sweep.</p>
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public final class BaSsuIbltProfileSweepBenchmark {
    /**
     * benchmark kind.
     */
    public static final String BENCHMARK_KIND = "MEASURED_PRODUCTION_PROFILE_SWEEP";
    /**
     * no speedup claim notice.
     */
    public static final String SECURITY_NOTICE = "PROFILE_SWEEP_ONLY_NO_SPEEDUP_CLAIM";

    private BaSsuIbltProfileSweepBenchmark() {
        // empty
    }

    /**
     * Command line entry point.
     *
     * @param args key=value benchmark arguments.
     * @throws Exception if all rows fail unexpectedly.
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        System.out.println(run(config).toDisplayString());
    }

    /**
     * Runs a measured production profile sweep.
     *
     * @param config sweep config.
     * @return sweep result.
     */
    public static Result run(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        config.validate();
        List<Row> rows = new ArrayList<>();
        Row bestObjectiveRow = null;
        Row bestOnlineRow = null;
        Row bestTotalRow = null;
        int rowIndex = 0;
        for (double alpha : config.alphas) {
            for (int degree : config.degrees) {
                for (int onlineBatchSize : config.onlineBatchSizes) {
                    Row row = runRow(config, rowIndex++, alpha, degree, onlineBatchSize);
                    rows.add(row);
                    if (row.success
                        && (bestObjectiveRow == null
                        || config.objective.value(row) < config.objective.value(bestObjectiveRow))) {
                        bestObjectiveRow = row;
                    }
                    if (row.success && (bestOnlineRow == null || row.onlineTimeMs() < bestOnlineRow.onlineTimeMs())) {
                        bestOnlineRow = row;
                    }
                    if (row.success && (bestTotalRow == null || row.totalTimeMs() < bestTotalRow.totalTimeMs())) {
                        bestTotalRow = row;
                    }
                }
            }
        }
        return new Result(config, rows, bestObjectiveRow, bestOnlineRow, bestTotalRow);
    }

    private static Row runRow(Config config, int rowIndex, double alpha, int degree, int onlineBatchSize) {
        try {
            BaSsuIbltMeasuredProductionBenchmark.Config productionConfig =
                BaSsuIbltMeasuredProductionBenchmark.Config.fromArgs(new String[]{
                    "m=" + config.senderSize,
                    "n=" + config.receiverSize,
                    "overlap=" + config.overlap,
                    "elementBytes=" + config.elementByteLength,
                    "degree=" + degree,
                    "alpha=" + alpha,
                    "retry=" + config.retryCount,
                    "lambda=" + config.lambda,
                    "marginBits=" + config.marginBits,
                    "checksPerBucket=" + config.checksPerBucket,
                    "onlineBatchSize=" + onlineBatchSize,
                    "seed=" + config.seed,
                    "timeoutMillis=" + config.timeoutMillis,
                });
            BaSsuIbltMeasuredProductionBenchmark.Result result =
                BaSsuIbltMeasuredProductionBenchmark.run(productionConfig);
            return Row.success(rowIndex, alpha, degree, onlineBatchSize, result);
        } catch (Exception e) {
            return Row.failure(rowIndex, alpha, degree, onlineBatchSize, e);
        }
    }

    private static String formatList(double[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.3f", values[i]));
        }
        return builder.toString();
    }

    private static String formatList(int[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
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

    private static double[] parseDoubleList(String value) {
        String[] parts = value.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }

    private static int[] parseIntList(String value) {
        String[] parts = value.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parseInt(parts[i]);
        }
        return result;
    }

    /**
     * Sweep objective.
     */
    public enum Objective {
        /**
         * minimize online time.
         */
        ONLINE_TIME,
        /**
         * minimize total time.
         */
        TOTAL_TIME,
        /**
         * minimize online communication.
         */
        ONLINE_BYTES,
        /**
         * minimize total communication.
         */
        TOTAL_BYTES;

        private double value(Row row) {
            switch (this) {
                case ONLINE_TIME:
                    return row.onlineTimeMs();
                case TOTAL_TIME:
                    return row.totalTimeMs();
                case ONLINE_BYTES:
                    return row.result.getOnlineTotalBytes();
                case TOTAL_BYTES:
                    return row.result.getTotalBytes();
                default:
                    throw new IllegalStateException("unknown objective: " + this);
            }
        }

        private static Objective parse(String value) {
            String normalized = value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "online":
                case "onlinetime":
                case "onlinems":
                    return ONLINE_TIME;
                case "total":
                case "totaltime":
                case "totalms":
                    return TOTAL_TIME;
                case "onlinebytes":
                case "onlinecommunication":
                case "onlinecomm":
                    return ONLINE_BYTES;
                case "totalbytes":
                case "totalcommunication":
                case "totalcomm":
                    return TOTAL_BYTES;
                default:
                    throw new IllegalArgumentException("unknown sweep objective: " + value);
            }
        }
    }

    /**
     * Sweep config.
     */
    public static final class Config {
        /**
         * protocol sender set size.
         */
        private int senderSize;
        /**
         * protocol receiver set size.
         */
        private int receiverSize;
        /**
         * exact overlap.
         */
        private int overlap;
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * table multipliers.
         */
        private double[] alphas;
        /**
         * public degrees.
         */
        private int[] degrees;
        /**
         * online probe batch sizes.
         */
        private int[] onlineBatchSizes;
        /**
         * optimization objective.
         */
        private Objective objective;
        /**
         * retry count.
         */
        private int retryCount;
        /**
         * statistical security parameter.
         */
        private int lambda;
        /**
         * margin bits.
         */
        private int marginBits;
        /**
         * checks per bucket.
         */
        private int checksPerBucket;
        /**
         * deterministic seed.
         */
        private long seed;
        /**
         * row timeout.
         */
        private long timeoutMillis;

        private Config() {
            senderSize = 1 << 10;
            receiverSize = 1 << 18;
            overlap = 256;
            elementByteLength = Long.BYTES;
            alphas = new double[]{1.45, 1.55, 1.65};
            degrees = new int[]{3};
            onlineBatchSizes = new int[]{1 << 13, 1 << 14, 1 << 15};
            objective = Objective.ONLINE_TIME;
            retryCount = 1;
            lambda = BaSsuIbltBiUpsuParams.DEFAULT_LAMBDA;
            marginBits = BaSsuIbltBiUpsuParams.DEFAULT_MARGIN_BITS;
            checksPerBucket = BaSsuIbltBiUpsuParams.DEFAULT_CHECKS_PER_BUCKET;
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
                case "alphas":
                    alphas = parseDoubleList(value);
                    break;
                case "degree":
                case "degrees":
                    degrees = parseIntList(value);
                    break;
                case "batch":
                case "batchsize":
                case "onlinebatch":
                case "onlinebatchsize":
                case "onlinebatchsizes":
                    onlineBatchSizes = parseIntList(value);
                    break;
                case "objective":
                    objective = Objective.parse(value);
                    break;
                case "retry":
                case "retrycount":
                    retryCount = parseInt(value);
                    break;
                case "lambda":
                    lambda = parseInt(value);
                    break;
                case "marginbits":
                    marginBits = parseInt(value);
                    break;
                case "checksperbucket":
                    checksPerBucket = parseInt(value);
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
            if (alphas.length == 0 || degrees.length == 0 || onlineBatchSizes.length == 0) {
                throw new IllegalArgumentException("sweep lists must be non-empty");
            }
            if (objective == null) {
                throw new IllegalArgumentException("objective must be non-null");
            }
            for (double alpha : alphas) {
                if (!Double.isFinite(alpha) || alpha <= 1.0) {
                    throw new IllegalArgumentException("all alpha values must be finite and greater than 1");
                }
            }
            for (int degree : degrees) {
                if (degree != 3 && degree != 4) {
                    throw new IllegalArgumentException("degree must be 3 or 4");
                }
            }
            for (int onlineBatchSize : onlineBatchSizes) {
                if (onlineBatchSize <= 0) {
                    throw new IllegalArgumentException("onlineBatchSize must be positive");
                }
            }
            if (retryCount <= 0) {
                throw new IllegalArgumentException("retryCount must be positive");
            }
            if (lambda <= 0) {
                throw new IllegalArgumentException("lambda must be positive");
            }
            if (marginBits < 0) {
                throw new IllegalArgumentException("marginBits must be non-negative");
            }
            if (checksPerBucket <= 0) {
                throw new IllegalArgumentException("checksPerBucket must be positive");
            }
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis must be positive");
            }
        }

        private String toArgString() {
            return String.format(
                Locale.ROOT,
                "m=%d n=%d overlap=%d elementBytes=%d alphas=%s degrees=%s onlineBatchSizes=%s retry=%d "
                    + "objective=%s lambda=%d marginBits=%d checksPerBucket=%d seed=%d timeoutMillis=%d",
                senderSize, receiverSize, overlap, elementByteLength, formatList(alphas), formatList(degrees),
                formatList(onlineBatchSizes), retryCount, objective.name().toLowerCase(Locale.ROOT), lambda,
                marginBits, checksPerBucket, seed, timeoutMillis
            );
        }
    }

    /**
     * Sweep row.
     */
    public static final class Row {
        /**
         * row index.
         */
        private final int rowIndex;
        /**
         * table multiplier.
         */
        private final double alpha;
        /**
         * public degree.
         */
        private final int degree;
        /**
         * online batch size.
         */
        private final int onlineBatchSize;
        /**
         * success flag.
         */
        private final boolean success;
        /**
         * measured result, if successful.
         */
        private final BaSsuIbltMeasuredProductionBenchmark.Result result;
        /**
         * failure message, if failed.
         */
        private final String failureMessage;

        private Row(int rowIndex, double alpha, int degree, int onlineBatchSize, boolean success,
                    BaSsuIbltMeasuredProductionBenchmark.Result result, String failureMessage) {
            this.rowIndex = rowIndex;
            this.alpha = alpha;
            this.degree = degree;
            this.onlineBatchSize = onlineBatchSize;
            this.success = success;
            this.result = result;
            this.failureMessage = failureMessage;
        }

        private static Row success(int rowIndex, double alpha, int degree, int onlineBatchSize,
                                   BaSsuIbltMeasuredProductionBenchmark.Result result) {
            return new Row(rowIndex, alpha, degree, onlineBatchSize, true, result, "");
        }

        private static Row failure(int rowIndex, double alpha, int degree, int onlineBatchSize, Exception e) {
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            return new Row(rowIndex, alpha, degree, onlineBatchSize, false, null, message);
        }

        private double offlineTimeMs() {
            return result.getOfflineTimeNanos() / 1_000_000.0;
        }

        private double onlineTimeMs() {
            return result.getOnlineTimeNanos() / 1_000_000.0;
        }

        private double totalTimeMs() {
            return result.getTotalTimeNanos() / 1_000_000.0;
        }

        private String toDisplayLine(boolean best) {
            if (!success) {
                return String.format(
                    Locale.ROOT,
                    "row=%d best=%s status=FAILED alpha=%.3f degree=%d onlineBatchSize=%d failure=%s",
                    rowIndex, best, alpha, degree, onlineBatchSize, failureMessage
                );
            }
            return String.format(
                Locale.ROOT,
                "row=%d best=%s status=OK alpha=%.3f degree=%d onlineBatchSize=%d offlineMs=%.3f "
                    + "onlineMs=%.3f totalMs=%.3f offlineBytes=%d onlineBytes=%d totalBytes=%d logicalProbes=%d "
                    + "onlineBatches=%d avgBatch=%.3f maxBatch=%d publicSkips=%d retryStatus=%s",
                rowIndex, best, alpha, degree, onlineBatchSize, offlineTimeMs(), onlineTimeMs(), totalTimeMs(),
                result.getOfflineTotalBytes(), result.getOnlineTotalBytes(), result.getTotalBytes(),
                result.getLogicalProbeCount(), result.getOnlineBatchCount(), result.getAverageProbeBatchSize(),
                result.getMaxProbeBatchSize(), result.getPublicSkipCount(), result.getRetryStatus()
            );
        }
    }

    /**
     * Sweep result.
     */
    public static final class Result {
        /**
         * sweep config.
         */
        private final Config config;
        /**
         * rows.
         */
        private final List<Row> rows;
        /**
         * objective-best successful row.
         */
        private final Row bestObjectiveRow;
        /**
         * online-time-best successful row.
         */
        private final Row bestOnlineRow;
        /**
         * total-time-best successful row.
         */
        private final Row bestTotalRow;

        private Result(Config config, List<Row> rows, Row bestObjectiveRow, Row bestOnlineRow, Row bestTotalRow) {
            this.config = config;
            this.rows = List.copyOf(rows);
            this.bestObjectiveRow = bestObjectiveRow;
            this.bestOnlineRow = bestOnlineRow;
            this.bestTotalRow = bestTotalRow;
        }

        public String toDisplayString() {
            StringBuilder builder = new StringBuilder();
            builder.append("BA-SSU-IBLT measured production profile sweep").append(System.lineSeparator());
            builder.append("benchmarkKind=").append(BENCHMARK_KIND).append(System.lineSeparator());
            builder.append("securityNotice=").append(SECURITY_NOTICE).append(System.lineSeparator());
            builder.append("rowCount=").append(rows.size()).append(System.lineSeparator());
            builder.append("successCount=").append(rows.stream().filter(row -> row.success).count())
                .append(System.lineSeparator());
            builder.append("objective=").append(config.objective.name().toLowerCase(Locale.ROOT))
                .append(System.lineSeparator());
            if (bestObjectiveRow == null) {
                builder.append("bestObjectiveRow=N/A").append(System.lineSeparator());
            } else {
                builder.append(String.format(
                    Locale.ROOT,
                    "bestObjectiveRow=%d alpha=%.3f degree=%d onlineBatchSize=%d objectiveValue=%.3f "
                        + "totalMs=%.3f onlineMs=%.3f onlineBytes=%d totalBytes=%d%n",
                    bestObjectiveRow.rowIndex, bestObjectiveRow.alpha, bestObjectiveRow.degree,
                    bestObjectiveRow.onlineBatchSize, config.objective.value(bestObjectiveRow),
                    bestObjectiveRow.totalTimeMs(), bestObjectiveRow.onlineTimeMs(),
                    bestObjectiveRow.result.getOnlineTotalBytes(), bestObjectiveRow.result.getTotalBytes()
                ));
            }
            appendBestRow(builder, "bestOnlineRow", bestOnlineRow);
            appendBestRow(builder, "bestTotalRow", bestTotalRow);
            for (Row row : rows) {
                builder.append(row.toDisplayLine(row == bestObjectiveRow)).append(System.lineSeparator());
            }
            builder.append("rawCommand=BaSsuIbltProfileSweepBenchmark ").append(config.toArgString());
            return builder.toString();
        }

        private static void appendBestRow(StringBuilder builder, String label, Row row) {
            if (row == null) {
                builder.append(label).append("=N/A").append(System.lineSeparator());
                return;
            }
            builder.append(String.format(
                Locale.ROOT,
                "%s=%d alpha=%.3f degree=%d onlineBatchSize=%d totalMs=%.3f onlineMs=%.3f onlineBytes=%d "
                    + "totalBytes=%d%n",
                label, row.rowIndex, row.alpha, row.degree, row.onlineBatchSize, row.totalTimeMs(),
                row.onlineTimeMs(), row.result.getOnlineTotalBytes(), row.result.getTotalBytes()
            ));
        }
    }
}
