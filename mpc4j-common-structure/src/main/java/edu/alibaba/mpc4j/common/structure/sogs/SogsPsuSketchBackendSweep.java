package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Parameter sweep for PSU sketch backend benchmark.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchBackendSweep {
    /**
     * Default seed.
     */
    private static final long DEFAULT_SEED = 20260609L;

    private SogsPsuSketchBackendSweep() {
        // empty
    }

    /**
     * Sweep config.
     *
     * @param largeSize      large set size.
     * @param smallSize      small set size.
     * @param overlaps       overlap sizes.
     * @param payloadLengths payload byte lengths.
     * @param alphas         SOGS alphas.
     * @param degrees        SOGS degrees.
     * @param trials         trials.
     * @param seed           seed.
     * @param ibltMultiplier H5 multiplier.
     */
    public record Config(
        int largeSize, int smallSize, int[] overlaps, int[] payloadLengths,
        double[] alphas, int[] degrees, int trials, long seed, double ibltMultiplier
    ) {
        /**
         * Default target sweep.
         *
         * @return config.
         */
        public static Config defaultTargetSweep() {
            return new Config(
                1 << 18, 1 << 10,
                new int[]{0, 1 << 8, 1 << 9, 1 << 10},
                new int[]{16, 32},
                new double[]{1.23, 1.25, 1.35, 1.45},
                new int[]{3, 4, 5},
                3, DEFAULT_SEED, H5LongIblt.DEFAULT_MULTIPLIER
            );
        }

        public Config {
            overlaps = overlaps.clone();
            payloadLengths = payloadLengths.clone();
            alphas = alphas.clone();
            degrees = degrees.clone();
        }
    }

    /**
     * Sweep row.
     *
     * @param overlapSize     overlap size.
     * @param payloadLength   payload length.
     * @param alpha           alpha.
     * @param degree          degree.
     * @param trials          trials.
     * @param unionSize       union size.
     * @param sogsSuccess     SOGS success count.
     * @param sogsPayload     SOGS payload match count.
     * @param h5Success       H5 success count.
     * @param h5Payload       H5 payload match count.
     * @param sogsTableSize   SOGS table size.
     * @param h5TableSize     H5 table size.
     * @param sogsBuildNanos  SOGS build time.
     * @param sogsPeelNanos   SOGS peel time.
     * @param h5BuildNanos    H5 build time.
     * @param h5PeelNanos     H5 peel time.
     */
    public record Row(
        int overlapSize, int payloadLength, double alpha, int degree, int trials, int unionSize,
        int sogsSuccess, int sogsPayload, int h5Success, int h5Payload,
        int sogsTableSize, int h5TableSize,
        long sogsBuildNanos, long sogsPeelNanos, long h5BuildNanos, long h5PeelNanos
    ) {
        /**
         * Returns whether all trials succeeded and matched.
         *
         * @return true if fully successful.
         */
        public boolean fullSuccess() {
            return sogsSuccess == trials && sogsPayload == trials && h5Success == trials && h5Payload == trials;
        }

        /**
         * Gets table ratio.
         *
         * @return ratio.
         */
        public double tableRatio() {
            return sogsTableSize / (double) h5TableSize;
        }

        /**
         * Returns display line.
         *
         * @return line.
         */
        public String toDisplayLine() {
            return String.format(
                Locale.ROOT,
                "overlap=%d payload=%d alpha=%.2f d=%d | SOGS ok=%d/%d payload=%d/%d table=%d "
                    + "build=%.2fms peel=%.2fms | H5 ok=%d/%d payload=%d/%d table=%d build=%.2fms peel=%.2fms "
                    + "| tableRatio=%.3f hashRatio=%.3f",
                overlapSize, payloadLength, alpha, degree,
                sogsSuccess, trials, sogsPayload, trials, sogsTableSize,
                sogsBuildNanos / 1_000_000.0, sogsPeelNanos / 1_000_000.0,
                h5Success, trials, h5Payload, trials, h5TableSize,
                h5BuildNanos / 1_000_000.0, h5PeelNanos / 1_000_000.0,
                tableRatio(), degree / (double) H5LongIblt.HASH_NUM
            );
        }
    }

    /**
     * Runs sweep.
     *
     * @param config config.
     * @return rows.
     */
    public static List<Row> run(Config config) {
        List<Row> rows = new ArrayList<>();
        for (int overlap : config.overlaps()) {
            for (int payloadLength : config.payloadLengths()) {
                for (int degree : config.degrees()) {
                    for (double alpha : config.alphas()) {
                        rows.add(runPoint(config, overlap, payloadLength, alpha, degree));
                    }
                }
            }
        }
        return rows;
    }

    /**
     * Selects the smallest full-success row by table size.
     *
     * @param rows rows.
     * @return row.
     */
    public static Optional<Row> bestFullSuccessByTableSize(List<Row> rows) {
        return rows.stream()
            .filter(Row::fullSuccess)
            .min(Comparator.comparingInt(Row::sogsTableSize).thenComparingLong(Row::sogsPeelNanos));
    }

    private static Row runPoint(Config config, int overlap, int payloadLength, double alpha, int degree) {
        SogsPsuSketchBackendBenchmark.Config benchmarkConfig = new SogsPsuSketchBackendBenchmark.Config(
            config.largeSize(), config.smallSize(), overlap, payloadLength,
            alpha, degree, config.seed(), config.ibltMultiplier(), config.trials()
        );
        SogsPsuSketchBackendBenchmark.Summary summary = SogsPsuSketchBackendBenchmark.run(benchmarkConfig);
        return new Row(
            overlap, payloadLength, alpha, degree, config.trials(), summary.unionSize(),
            summary.sogsSuccessCount(), summary.sogsPayloadMatchCount(),
            summary.h5SuccessCount(), summary.h5PayloadMatchCount(),
            summary.sogsTableSize(), summary.h5TableSize(),
            summary.avgSogsBuildNanos(), summary.avgSogsPeelNanos(),
            summary.avgH5BuildNanos(), summary.avgH5PeelNanos()
        );
    }

    /**
     * Main entry.
     *
     * @param args key=value arguments.
     */
    public static void main(String[] args) {
        List<Row> rows = run(parseArgs(args));
        for (Row row : rows) {
            System.out.println(row.toDisplayLine());
        }
        bestFullSuccessByTableSize(rows).ifPresent(row -> System.out.println("best=" + row.toDisplayLine()));
    }

    private static Config parseArgs(String[] args) {
        Map<String, String> options = new TreeMap<>();
        for (String arg : args) {
            int split = arg.indexOf('=');
            if (split <= 0 || split == arg.length() - 1) {
                throw new IllegalArgumentException("argument must be key=value: " + arg);
            }
            options.put(arg.substring(0, split), arg.substring(split + 1));
        }
        Config defaults = Config.defaultTargetSweep();
        return new Config(
            getInt(options, "largeSize", defaults.largeSize()),
            getInt(options, "smallSize", defaults.smallSize()),
            getIntArray(options, "overlaps", defaults.overlaps()),
            getIntArray(options, "payloadLengths", defaults.payloadLengths()),
            getDoubleArray(options, "alphas", defaults.alphas()),
            getIntArray(options, "degrees", defaults.degrees()),
            getInt(options, "trials", defaults.trials()),
            getLong(options, "seed", defaults.seed()),
            getDouble(options, "ibltMultiplier", defaults.ibltMultiplier())
        );
    }

    private static int getInt(Map<String, String> options, String key, int defaultValue) {
        return options.containsKey(key) ? Integer.parseInt(options.get(key)) : defaultValue;
    }

    private static long getLong(Map<String, String> options, String key, long defaultValue) {
        return options.containsKey(key) ? Long.parseLong(options.get(key)) : defaultValue;
    }

    private static double getDouble(Map<String, String> options, String key, double defaultValue) {
        return options.containsKey(key) ? Double.parseDouble(options.get(key)) : defaultValue;
    }

    private static int[] getIntArray(Map<String, String> options, String key, int[] defaultValue) {
        if (!options.containsKey(key)) {
            return defaultValue;
        }
        String[] tokens = options.get(key).split(",");
        int[] values = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Integer.parseInt(tokens[i]);
        }
        return values;
    }

    private static double[] getDoubleArray(Map<String, String> options, String key, double[] defaultValue) {
        if (!options.containsKey(key)) {
            return defaultValue;
        }
        String[] tokens = options.get(key).split(",");
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            values[i] = Double.parseDouble(tokens[i]);
        }
        return values;
    }
}
