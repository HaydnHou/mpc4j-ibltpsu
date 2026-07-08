package edu.alibaba.mpc4j.common.structure.sogs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Java SOGS graph-peel failure sweep with IBLT-paper-style extrapolation.
 *
 * <p>This class intentionally uses {@link SogsGraphParams}, {@link SogsPayloadGraphSketch}, and
 * {@link SogsPsuSketchPeelResult} directly so that the reliability experiment exercises the same Java SOGS backend used
 * by the protocol implementation. Payload length is set to zero because payload bytes do not affect graph peelability.</p>
 *
 * @author donghai hou
 * @date 2026/07/05
 */
public class SogsFailureExtrapolationSweep {
    /**
     * Empty payload used for graph-only peel reliability.
     */
    private static final byte[] EMPTY_PAYLOAD = new byte[0];
    /**
     * 95% rule-of-three constant for zero failures.
     */
    private static final double RULE_OF_THREE_95 = 2.995732273553991;
    /**
     * Default output directory.
     */
    private static final String DEFAULT_OUT_DIR = "/Users/haydnhou/Desktop/psux_ibltcodex/BPW";

    private SogsFailureExtrapolationSweep() {
        // empty
    }

    /**
     * Sweep row.
     */
    private record Row(
        int logTau,
        int tau,
        int degree,
        double alpha,
        int trials,
        int failures,
        double observedRate,
        double zero95Upper,
        Double minusLog2Observed,
        double minusLog2Zero95,
        double avgResidualEdgesOnFailure,
        double elapsedSeconds
    ) {
        // empty
    }

    /**
     * Command-line configuration.
     */
    private record Config(
        int[] logs,
        int[] targetLogs,
        double[] alphas,
        int degree,
        int trials,
        int baseLog,
        long seed,
        Path outDir,
        String prefix
    ) {
        // empty
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Config config = parseConfig(args);
        Files.createDirectories(config.outDir());
        List<Row> rows = run(config);
        writeRawCsv(config.outDir().resolve(config.prefix() + "_raw.csv"), rows);
        writeExtrapolatedCsv(config.outDir().resolve(config.prefix() + "_extrapolated.csv"), rows, config);
        writeReport(config.outDir().resolve(config.prefix() + ".md"), rows, config);
        System.out.println("wrote " + config.outDir().resolve(config.prefix() + "_raw.csv"));
        System.out.println("wrote " + config.outDir().resolve(config.prefix() + "_extrapolated.csv"));
        System.out.println("wrote " + config.outDir().resolve(config.prefix() + ".md"));
    }

    private static List<Row> run(Config config) {
        List<Row> rows = new ArrayList<>();
        for (int logTau : config.logs()) {
            int tau = 1 << logTau;
            for (double alpha : config.alphas()) {
                long start = System.nanoTime();
                int failures = 0;
                long residualSum = 0L;
                for (int trial = 0; trial < config.trials(); trial++) {
                    long trialSeed = trialSeed(config.seed(), logTau, alpha, trial);
                    SogsGraphParams params = SogsGraphParams.fromExpectedItemSize(tau, alpha, config.degree(), trialSeed);
                    SogsPayloadGraphSketch sketch = new SogsPayloadGraphSketch(params, 0);
                    for (int edgeId = 1; edgeId <= tau; edgeId++) {
                        long label = SogsHashUtils.label(edgeId, trialSeed);
                        sketch.insert(label, EMPTY_PAYLOAD);
                    }
                    SogsPsuSketchPeelResult result = sketch.peel();
                    if (!result.success()) {
                        failures++;
                        residualSum += result.residualEdgeCount();
                    }
                }
                double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
                double observedRate = (double) failures / config.trials();
                double zero95Upper = failures == 0 ? RULE_OF_THREE_95 / config.trials() : observedRate;
                Double minusLog2Observed = failures == 0 ? null : -log2(observedRate);
                double minusLog2Zero95 = -log2(zero95Upper);
                double avgResidual = failures == 0 ? 0.0 : (double) residualSum / failures;
                Row row = new Row(
                    logTau, tau, config.degree(), alpha, config.trials(), failures, observedRate, zero95Upper,
                    minusLog2Observed, minusLog2Zero95, avgResidual, elapsed
                );
                rows.add(row);
                System.out.printf(
                    "log_tau=%d alpha=%.3f failures=%d/%d rate=%.8f zero95=%.8f elapsed=%.3fs%n",
                    logTau, alpha, failures, config.trials(), observedRate, zero95Upper, elapsed
                );
            }
        }
        return rows;
    }

    private static long trialSeed(long seed, int logTau, double alpha, int trial) {
        long alphaKey = Math.round(alpha * 1_000_000.0);
        return SogsHashUtils.mix64(seed ^ ((long) logTau << 48) ^ (alphaKey << 16) ^ trial);
    }

    private static double extrapolate(Row base, int targetLogTau) {
        double f0 = base.failures() == 0 ? base.zero95Upper() : base.observedRate();
        double exponent = Math.log(f0) / Math.log(base.tau());
        return Math.pow(1L << targetLogTau, exponent);
    }

    private static void writeRawCsv(Path path, List<Row> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("log_tau,tau,degree,alpha,trials,failures,observed_rate,zero95_upper,"
                + "minus_log2_observed,minus_log2_zero95,avg_residual_edges_on_failure,elapsed_s");
            writer.newLine();
            for (Row row : rows) {
                writer.write(String.format(
                    "%.0f,%d,%d,%.6f,%d,%d,%.12g,%.12g,%s,%.6f,%.6f,%.6f",
                    (double) row.logTau(), row.tau(), row.degree(), row.alpha(), row.trials(), row.failures(),
                    row.observedRate(), row.zero95Upper(),
                    row.minusLog2Observed() == null ? "" : String.format("%.6f", row.minusLog2Observed()),
                    row.minusLog2Zero95(), row.avgResidualEdgesOnFailure(), row.elapsedSeconds()
                ));
                writer.newLine();
            }
        }
    }

    private static void writeExtrapolatedCsv(Path path, List<Row> rows, Config config) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("base_log_tau,target_log_tau,degree,alpha,base_trials,base_failures,base_source,"
                + "base_minus_log2,extrapolated_failure,extrapolated_minus_log2");
            writer.newLine();
            for (Row base : rows) {
                if (base.logTau() != config.baseLog()) {
                    continue;
                }
                String source = base.failures() == 0 ? "zero95_upper" : "observed";
                double baseMinus = base.failures() == 0 ? base.minusLog2Zero95() : base.minusLog2Observed();
                for (int targetLog : config.targetLogs()) {
                    double f = extrapolate(base, targetLog);
                    writer.write(String.format(
                        "%d,%d,%d,%.6f,%d,%d,%s,%.6f,%.12g,%.6f",
                        config.baseLog(), targetLog, base.degree(), base.alpha(), base.trials(), base.failures(),
                        source, baseMinus, f, -log2(f)
                    ));
                    writer.newLine();
                }
            }
        }
    }

    private static void writeReport(Path path, List<Row> rows, Config config) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("# Java SOGS Failure Sweep and Extrapolation");
            writer.newLine();
            writer.newLine();
            writer.write("This report was generated by `SogsFailureExtrapolationSweep` and uses the Java SOGS backend.");
            writer.newLine();
            writer.newLine();
            writer.write("## Raw Sweep");
            writer.newLine();
            writer.newLine();
            writer.write("| tau | d | alpha | trials | failures | observed | zero95/rate | -log2 bound | avg residual |");
            writer.newLine();
            writer.write("|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
            writer.newLine();
            for (Row row : rows) {
                writer.write(String.format(
                    "| 2^%d | %d | %.3f | %d | %d | %.6g | %.6g | %.3f | %.2f |",
                    row.logTau(), row.degree(), row.alpha(), row.trials(), row.failures(),
                    row.observedRate(), row.zero95Upper(), row.minusLog2Zero95(),
                    row.avgResidualEdgesOnFailure()
                ));
                writer.newLine();
            }
            writer.newLine();
            writer.write("## Extrapolated From tau0 = 2^" + config.baseLog());
            writer.newLine();
            writer.newLine();
            writer.write("| target tau | alpha | base source | base -log2 | extrapolated failure | extrapolated -log2 |");
            writer.newLine();
            writer.write("|---:|---:|---|---:|---:|---:|");
            writer.newLine();
            for (Row base : rows) {
                if (base.logTau() != config.baseLog()) {
                    continue;
                }
                String source = base.failures() == 0 ? "zero95_upper" : "observed";
                double baseMinus = base.failures() == 0 ? base.minusLog2Zero95() : base.minusLog2Observed();
                for (int targetLog : config.targetLogs()) {
                    double f = extrapolate(base, targetLog);
                    writer.write(String.format(
                        "| 2^%d | %.3f | %s | %.3f | %.6g | %.3f |",
                        targetLog, base.alpha(), source, baseMinus, f, -log2(f)
                    ));
                    writer.newLine();
                }
            }
        }
    }

    private static Config parseConfig(String[] args) {
        int[] logs = parseInts("10,12,14,16");
        int[] targetLogs = parseInts("14,16,18,20,22");
        double[] alphas = parseDoubles("1.23,1.24,1.25");
        int degree = 3;
        int trials = 1000;
        int baseLog = 14;
        long seed = 0x20260705L;
        Path outDir = Path.of(DEFAULT_OUT_DIR);
        String prefix = "sogs_java_failure_d3";
        for (String arg : args) {
            int index = arg.indexOf('=');
            if (!arg.startsWith("--") || index < 0) {
                continue;
            }
            String key = arg.substring(2, index);
            String value = arg.substring(index + 1);
            switch (key) {
                case "logs" -> logs = parseInts(value);
                case "targets" -> targetLogs = parseInts(value);
                case "alphas" -> alphas = parseDoubles(value);
                case "degree" -> degree = Integer.parseInt(value);
                case "trials" -> trials = Integer.parseInt(value);
                case "base-log" -> baseLog = Integer.parseInt(value);
                case "seed" -> seed = Long.decode(value);
                case "out-dir" -> outDir = Path.of(value);
                case "prefix" -> prefix = value;
                default -> throw new IllegalArgumentException("unknown argument: " + arg);
            }
        }
        return new Config(logs, targetLogs, alphas, degree, trials, baseLog, seed, outDir, prefix);
    }

    private static int[] parseInts(String value) {
        String[] tokens = value.split(",");
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = Integer.parseInt(tokens[i].trim());
        }
        return result;
    }

    private static double[] parseDoubles(String value) {
        String[] tokens = value.split(",");
        double[] result = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = Double.parseDouble(tokens[i].trim());
        }
        return result;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
