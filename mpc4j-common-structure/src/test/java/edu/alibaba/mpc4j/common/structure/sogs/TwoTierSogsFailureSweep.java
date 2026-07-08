package edu.alibaba.mpc4j.common.structure.sogs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Failure sweep for two-tier SOGS.
 *
 * <p>The experiment is intentionally graph-only: every item is inserted into a primary layer and, optionally, a fixed
 * independent auxiliary layer. The decoder performs coupled peeling. Whenever either layer opens an item, the item is
 * removed from all layers. The goal is to check whether an auxiliary layer can suppress the d = 3 parallel-edge
 * failure floor without falling back to d = 5.</p>
 *
 * <p>This class uses primitive arrays rather than {@link SogsPayloadGraphSketch}. The existing object-backed sketch is
 * still the protocol backend; this sweep is a data-structure gate where object allocation would dominate the result.</p>
 *
 * @author donghai hou
 * @date 2026/07/06
 */
public class TwoTierSogsFailureSweep {
    /**
     * Golden-ratio constant copied from {@link SogsHashUtils} for allocation-free position generation.
     */
    private static final long PHI = 0x9E3779B97F4A7C15L;
    /**
     * Check-seed constant copied from {@link SogsGraphParams}.
     */
    private static final long CHECK_SEED_MASK = 0x6A09E667F3BCC909L;
    /**
     * 95% rule-of-three constant for zero failures.
     */
    private static final double RULE_OF_THREE_95 = 2.995732273553991;
    /**
     * Default output directory.
     */
    private static final String DEFAULT_OUT_DIR = "/Users/haydnhou/Desktop/psux_ibltcodex/BPW";

    private TwoTierSogsFailureSweep() {
        // empty
    }

    /**
     * Profile to test.
     */
    private enum DecodeMode {
        /**
         * Primary and auxiliary layers are peeled together.
         */
        EAGER,
        /**
         * Peel the primary layer first. The auxiliary layer is used only if primary peeling fails.
         */
        MAIN_FIRST
    }

    /**
     * Profile to test.
     */
    private record Profile(
        String name,
        int mainDegree,
        double mainAlpha,
        int auxiliaryDegree,
        int auxiliaryCells
    ) {
        boolean hasAuxiliary() {
            return auxiliaryCells > 0;
        }
    }

    /**
     * Sweep row.
     */
    private record Row(
        int logN,
        int n,
        Profile profile,
        DecodeMode decodeMode,
        int mainCells,
        int auxiliaryCells,
        int totalCells,
        int trials,
        int failures,
        double observedRate,
        double zero95Upper,
        double minusLog2Bound,
        double avgMainResidualEdgesOnFailure,
        double avgAuxiliaryResidualEdgesOnFailure,
        double avgPeeledItems,
        double avgAuxiliaryUsed,
        double avgPeelMillis,
        double elapsedSeconds
    ) {
        // empty
    }

    /**
     * Command-line configuration.
     */
    private record Config(
        int[] logs,
        int trials,
        long seed,
        Path outDir,
        String prefix,
        DecodeMode decodeMode,
        Profile[] profiles
    ) {
        // empty
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Config config = parseConfig(args);
        Files.createDirectories(config.outDir());
        List<Row> rows = run(config);
        writeCsv(config.outDir().resolve(config.prefix() + "_raw.csv"), rows);
        writeReport(config.outDir().resolve(config.prefix() + ".md"), rows, config);
        System.out.println("wrote " + config.outDir().resolve(config.prefix() + "_raw.csv"));
        System.out.println("wrote " + config.outDir().resolve(config.prefix() + ".md"));
    }

    private static List<Row> run(Config config) {
        List<Row> rows = new ArrayList<>();
        for (int logN : config.logs()) {
            int n = 1 << logN;
            for (Profile profile : config.profiles()) {
                long start = System.nanoTime();
                int failures = 0;
                long mainResidualSum = 0L;
                long auxiliaryResidualSum = 0L;
                long peeledItemSum = 0L;
                long auxiliaryUsedSum = 0L;
                long peelNanosSum = 0L;
                int mainCells = roundUp((int) Math.ceil(n * profile.mainAlpha()), profile.mainDegree());
                int auxiliaryCells = profile.hasAuxiliary() ? roundUp(profile.auxiliaryCells(), profile.auxiliaryDegree()) : 0;
                int totalCells = mainCells + auxiliaryCells;
                for (int trial = 0; trial < config.trials(); trial++) {
                    long trialSeed = trialSeed(config.seed(), logN, trial);
                    TwoTierSketch sketch = TwoTierSketch.create(n, profile, trialSeed);
                    for (int itemId = 1; itemId <= n; itemId++) {
                        long label = SogsHashUtils.label(itemId, trialSeed);
                        sketch.insert(label);
                    }
                    PeelStats stats = sketch.peel(config.decodeMode());
                    if (!stats.success()) {
                        failures++;
                        mainResidualSum += stats.mainResidualEdges();
                        auxiliaryResidualSum += stats.auxiliaryResidualEdges();
                    }
                    peeledItemSum += stats.peeledItems();
                    auxiliaryUsedSum += stats.auxiliaryUsed() ? 1L : 0L;
                    peelNanosSum += stats.peelNanos();
                }
                double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
                double observedRate = (double) failures / config.trials();
                double zero95Upper = failures == 0 ? RULE_OF_THREE_95 / config.trials() : observedRate;
                double minusLog2Bound = -log2(zero95Upper);
                double avgMainResidual = failures == 0 ? 0.0 : (double) mainResidualSum / failures;
                double avgAuxiliaryResidual = failures == 0 ? 0.0 : (double) auxiliaryResidualSum / failures;
                double avgPeeledItems = (double) peeledItemSum / config.trials();
                double avgAuxiliaryUsed = (double) auxiliaryUsedSum / config.trials();
                double avgPeelMillis = peelNanosSum / 1_000_000.0 / config.trials();
                Row row = new Row(
                    logN, n, profile, config.decodeMode(), mainCells, auxiliaryCells, totalCells, config.trials(), failures,
                    observedRate, zero95Upper, minusLog2Bound, avgMainResidual, avgAuxiliaryResidual,
                    avgPeeledItems, avgAuxiliaryUsed, avgPeelMillis, elapsed
                );
                rows.add(row);
                System.out.printf(
                    "log_n=%d mode=%s profile=%s cells=%d+%d failures=%d/%d auxiliary_used=%.6f "
                        + "bound=2^-%.3f avg_peel=%.3fms elapsed=%.3fs%n",
                    logN, config.decodeMode(), profile.name(), mainCells, auxiliaryCells, failures, config.trials(),
                    avgAuxiliaryUsed, minusLog2Bound, avgPeelMillis, elapsed
                );
            }
        }
        return rows;
    }

    private static void writeCsv(Path path, List<Row> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("log_n,n,decode_mode,profile,main_degree,main_alpha,auxiliary_degree,auxiliary_cells,main_cells,total_cells,"
                + "cell_ratio,trials,failures,observed_rate,zero95_upper,minus_log2_bound,"
                + "avg_main_residual_edges_on_failure,avg_auxiliary_residual_edges_on_failure,"
                + "avg_peeled_items,avg_auxiliary_used,avg_peel_ms,elapsed_s");
            writer.newLine();
            for (Row row : rows) {
                Profile profile = row.profile();
                writer.write(String.format(
                    "%d,%d,%s,%s,%d,%.6f,%d,%d,%d,%d,%.9f,%d,%d,%.12g,%.12g,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    row.logN(), row.n(), row.decodeMode(), profile.name(), profile.mainDegree(), profile.mainAlpha(),
                    profile.auxiliaryDegree(), row.auxiliaryCells(), row.mainCells(), row.totalCells(),
                    (double) row.totalCells() / row.n(), row.trials(), row.failures(), row.observedRate(),
                    row.zero95Upper(), row.minusLog2Bound(), row.avgMainResidualEdgesOnFailure(),
                    row.avgAuxiliaryResidualEdgesOnFailure(), row.avgPeeledItems(), row.avgAuxiliaryUsed(),
                    row.avgPeelMillis(), row.elapsedSeconds()
                ));
                writer.newLine();
            }
        }
    }

    private static void writeReport(Path path, List<Row> rows, Config config) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("# Fixed Two-Tier SOGS Failure Sweep");
            writer.newLine();
            writer.newLine();
            writer.write("Generated by `TwoTierSogsFailureSweep`.");
            writer.newLine();
            writer.newLine();
            writer.write("This is a data-structure-only experiment. It does not prove protocol security.");
            writer.newLine();
            writer.newLine();
            writer.write("## Configuration");
            writer.newLine();
            writer.newLine();
            writer.write("```text");
            writer.newLine();
            writer.write("logs = " + Arrays.toString(config.logs()));
            writer.newLine();
            writer.write("trials = " + config.trials());
            writer.newLine();
            writer.write("decode_mode = " + config.decodeMode());
            writer.newLine();
            writer.write("seed = " + config.seed());
            writer.newLine();
            writer.write("```");
            writer.newLine();
            writer.newLine();
            writer.write("## Results");
            writer.newLine();
            writer.newLine();
            writer.write("| n | profile | cells | cell ratio | failures | auxiliary used | zero95/rate | -log2 bound | avg main residual | avg auxiliary residual | avg peel ms |");
            writer.newLine();
            writer.write("|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
            writer.newLine();
            for (Row row : rows) {
                writer.write(String.format(
                    "| 2^%d | `%s` | %d | %.3f | %d/%d | %.6f | %.6g | %.3f | %.2f | %.2f | %.3f |",
                    row.logN(), row.profile().name(), row.totalCells(), (double) row.totalCells() / row.n(),
                    row.failures(), row.trials(), row.avgAuxiliaryUsed(), row.zero95Upper(), row.minusLog2Bound(),
                    row.avgMainResidualEdgesOnFailure(), row.avgAuxiliaryResidualEdgesOnFailure(), row.avgPeelMillis()
                ));
                writer.newLine();
            }
            writer.newLine();
            writer.write("## Interpretation Rule");
            writer.newLine();
            writer.newLine();
            writer.write("An auxiliary profile is only useful if it beats `d4_alpha135` / `d4_alpha140` at similar cell budget,");
            writer.newLine();
            writer.write("and remains clearly cheaper than `d5_alpha150`.");
            writer.newLine();
        }
    }

    private static Config parseConfig(String[] args) {
        int[] logs = parseInts("14,16,18");
        int trials = 1000;
        long seed = 0x20260706L;
        Path outDir = Path.of(DEFAULT_OUT_DIR);
        String prefix = "two_tier_sogs_failure";
        DecodeMode decodeMode = DecodeMode.EAGER;
        Profile[] profiles = defaultProfiles();
        for (String arg : args) {
            int index = arg.indexOf('=');
            if (!arg.startsWith("--") || index < 0) {
                continue;
            }
            String key = arg.substring(2, index);
            String value = arg.substring(index + 1);
            switch (key) {
                case "logs" -> logs = parseInts(value);
                case "trials" -> trials = Integer.parseInt(value);
                case "seed" -> seed = Long.decode(value);
                case "out-dir" -> outDir = Path.of(value);
                case "prefix" -> prefix = value;
                case "mode" -> decodeMode = DecodeMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
                case "profiles" -> profiles = parseProfiles(value);
                default -> throw new IllegalArgumentException("unknown argument: " + arg);
            }
        }
        return new Config(logs, trials, seed, outDir, prefix, decodeMode, profiles);
    }

    private static Profile[] defaultProfiles() {
        return new Profile[]{
            new Profile("d3_alpha125", 3, 1.25, 0, 0),
            new Profile("d3_alpha125_auxiliary1024", 3, 1.25, 3, 1024),
            new Profile("d3_alpha125_auxiliary2048", 3, 1.25, 3, 2048),
            new Profile("d3_alpha125_auxiliary4096", 3, 1.25, 3, 4096),
            new Profile("d4_alpha135", 4, 1.35, 0, 0),
            new Profile("d4_alpha140", 4, 1.40, 0, 0),
            new Profile("d5_alpha150", 5, 1.50, 0, 0),
        };
    }

    private static Profile[] parseProfiles(String value) {
        String[] tokens = value.split(",");
        Profile[] profiles = new Profile[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            profiles[i] = switch (tokens[i].trim()) {
                case "d3" -> new Profile("d3_alpha125", 3, 1.25, 0, 0);
                case "aux1024" -> new Profile("d3_alpha125_auxiliary1024", 3, 1.25, 3, 1024);
                case "aux2048" -> new Profile("d3_alpha125_auxiliary2048", 3, 1.25, 3, 2048);
                case "aux4096" -> new Profile("d3_alpha125_auxiliary4096", 3, 1.25, 3, 4096);
                case "d4a135" -> new Profile("d4_alpha135", 4, 1.35, 0, 0);
                case "d4a140" -> new Profile("d4_alpha140", 4, 1.40, 0, 0);
                case "d5" -> new Profile("d5_alpha150", 5, 1.50, 0, 0);
                default -> throw new IllegalArgumentException("unknown profile token: " + tokens[i]);
            };
        }
        return profiles;
    }

    private static int[] parseInts(String value) {
        String[] tokens = value.split(",");
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = Integer.parseInt(tokens[i].trim());
        }
        return result;
    }

    private static long trialSeed(long seed, int logN, int trial) {
        long value = seed ^ ((long) logN << 48) ^ trial;
        return SogsHashUtils.mix64(value);
    }

    private static int roundUp(int value, int factor) {
        return (value + factor - 1) / factor * factor;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    /**
     * Coupled two-layer sketch.
     */
    private static class TwoTierSketch {
        /**
         * Primary layer.
         */
        private final Layer main;
        /**
         * Optional auxiliary layer.
         */
        private final Layer auxiliary;

        private TwoTierSketch(Layer main, Layer auxiliary) {
            this.main = main;
            this.auxiliary = auxiliary;
        }

        static TwoTierSketch create(int n, Profile profile, long seed) {
            int mainCells = roundUp((int) Math.ceil(n * profile.mainAlpha()), profile.mainDegree());
            Layer main = new Layer(profile.mainDegree(), mainCells, SogsHashUtils.mix64(seed ^ 0x128A5E95A5EL));
            Layer auxiliary = null;
            if (profile.hasAuxiliary()) {
                int auxiliaryCells = roundUp(profile.auxiliaryCells(), profile.auxiliaryDegree());
                auxiliary = new Layer(profile.auxiliaryDegree(), auxiliaryCells, SogsHashUtils.mix64(seed ^ 0x5C3A2F7B91D3L));
            }
            return new TwoTierSketch(main, auxiliary);
        }

        void insert(long label) {
            main.update(label, 1);
            if (auxiliary != null) {
                auxiliary.update(label, 1);
            }
        }

        PeelStats peel(DecodeMode mode) {
            return switch (mode) {
                case EAGER -> peelEager();
                case MAIN_FIRST -> peelMainFirst();
            };
        }

        private PeelStats peelEager() {
            long start = System.nanoTime();
            ArrayDeque<CellRef> queue = new ArrayDeque<>(main.vertexCount() + (auxiliary == null ? 0 : auxiliary.vertexCount()));
            main.enqueueSingletons(queue, 0);
            if (auxiliary != null) {
                auxiliary.enqueueSingletons(queue, 1);
            }
            Set<Long> peeled = new HashSet<>();
            while (!queue.isEmpty()) {
                CellRef ref = queue.removeFirst();
                Layer layer = ref.layerId() == 0 ? main : auxiliary;
                if (layer == null || !layer.isSingleton(ref.index())) {
                    continue;
                }
                int sign = layer.sign(ref.index());
                long label = layer.label(ref.index());
                if (!peeled.add(label)) {
                    continue;
                }
                main.update(label, -sign);
                main.enqueuePositionsIfSingleton(label, queue, 0);
                if (auxiliary != null) {
                    auxiliary.update(label, -sign);
                    auxiliary.enqueuePositionsIfSingleton(label, queue, 1);
                }
            }
            long peelNanos = System.nanoTime() - start;
            boolean success = main.isEmpty() && (auxiliary == null || auxiliary.isEmpty());
            return new PeelStats(
                success, peeled.size(), main.residualEdgeCount(), auxiliary == null ? 0 : auxiliary.residualEdgeCount(),
                auxiliary != null, peelNanos
            );
        }

        private PeelStats peelMainFirst() {
            long start = System.nanoTime();
            LocalPeelStats mainStats = peelSingleLayer(main);
            if (mainStats.success() || auxiliary == null) {
                long peelNanos = System.nanoTime() - start;
                return new PeelStats(
                    mainStats.success(), mainStats.entries().size(), main.residualEdgeCount(),
                    auxiliary == null ? 0 : auxiliary.residualEdgeCount(), false, peelNanos
                );
            }
            for (PeeledEntry entry : mainStats.entries()) {
                auxiliary.update(entry.label(), -entry.sign());
            }
            Set<Long> peeled = new HashSet<>();
            for (PeeledEntry entry : mainStats.entries()) {
                peeled.add(entry.label());
            }
            ArrayDeque<CellRef> queue = new ArrayDeque<>(main.vertexCount() + auxiliary.vertexCount());
            main.enqueueSingletons(queue, 0);
            auxiliary.enqueueSingletons(queue, 1);
            while (!queue.isEmpty()) {
                CellRef ref = queue.removeFirst();
                Layer layer = ref.layerId() == 0 ? main : auxiliary;
                if (!layer.isSingleton(ref.index())) {
                    continue;
                }
                int sign = layer.sign(ref.index());
                long label = layer.label(ref.index());
                if (!peeled.add(label)) {
                    continue;
                }
                main.update(label, -sign);
                main.enqueuePositionsIfSingleton(label, queue, 0);
                auxiliary.update(label, -sign);
                auxiliary.enqueuePositionsIfSingleton(label, queue, 1);
            }
            long peelNanos = System.nanoTime() - start;
            boolean success = main.isEmpty() && auxiliary.isEmpty();
            return new PeelStats(
                success, peeled.size(), main.residualEdgeCount(), auxiliary.residualEdgeCount(), true, peelNanos
            );
        }

        private LocalPeelStats peelSingleLayer(Layer layer) {
            ArrayDeque<CellRef> queue = new ArrayDeque<>(layer.vertexCount());
            layer.enqueueSingletons(queue, 0);
            List<PeeledEntry> entries = new ArrayList<>();
            Set<Long> peeled = new HashSet<>();
            while (!queue.isEmpty()) {
                CellRef ref = queue.removeFirst();
                if (!layer.isSingleton(ref.index())) {
                    continue;
                }
                int sign = layer.sign(ref.index());
                long label = layer.label(ref.index());
                if (!peeled.add(label)) {
                    continue;
                }
                entries.add(new PeeledEntry(label, sign));
                layer.update(label, -sign);
                layer.enqueuePositionsIfSingleton(label, queue, 0);
            }
            return new LocalPeelStats(layer.isEmpty(), entries);
        }
    }

    /**
     * Queue reference.
     */
    private record CellRef(int layerId, int index) {
        // empty
    }

    /**
     * Peeled entry with sign.
     */
    private record PeeledEntry(long label, int sign) {
        // empty
    }

    /**
     * Single-layer peel stats.
     */
    private record LocalPeelStats(boolean success, List<PeeledEntry> entries) {
        // empty
    }

    /**
     * Peel stats.
     */
    private record PeelStats(
        boolean success,
        int peeledItems,
        int mainResidualEdges,
        int auxiliaryResidualEdges,
        boolean auxiliaryUsed,
        long peelNanos
    ) {
        // empty
    }

    /**
     * Primitive SOGS layer without payload.
     */
    private static class Layer {
        /**
         * Edge degree.
         */
        private final int degree;
        /**
         * Cell count.
         */
        private final int vertexCount;
        /**
         * Subtable length.
         */
        private final int subTableLength;
        /**
         * Position seed.
         */
        private final long seed;
        /**
         * Check seed.
         */
        private final long checkSeed;
        /**
         * Signed degree per cell.
         */
        private final int[] degrees;
        /**
         * XOR of labels.
         */
        private final long[] labelXors;
        /**
         * XOR of checks.
         */
        private final long[] checkXors;

        Layer(int degree, int vertexCount, long seed) {
            if (degree < 2) {
                throw new IllegalArgumentException("degree must be at least 2");
            }
            if (vertexCount % degree != 0) {
                throw new IllegalArgumentException("vertexCount must be divisible by degree");
            }
            this.degree = degree;
            this.vertexCount = vertexCount;
            this.subTableLength = vertexCount / degree;
            this.seed = seed;
            this.checkSeed = SogsHashUtils.mix64(seed ^ CHECK_SEED_MASK);
            degrees = new int[vertexCount];
            labelXors = new long[vertexCount];
            checkXors = new long[vertexCount];
        }

        int vertexCount() {
            return vertexCount;
        }

        void update(long label, int signDelta) {
            long check = SogsHashUtils.check(label, checkSeed);
            for (int i = 0; i < degree; i++) {
                int position = position(label, i);
                degrees[position] += signDelta;
                labelXors[position] ^= label;
                checkXors[position] ^= check;
            }
        }

        void enqueueSingletons(ArrayDeque<CellRef> queue, int layerId) {
            for (int i = 0; i < vertexCount; i++) {
                if (isSingleton(i)) {
                    queue.add(new CellRef(layerId, i));
                }
            }
        }

        void enqueuePositionsIfSingleton(long label, ArrayDeque<CellRef> queue, int layerId) {
            for (int i = 0; i < degree; i++) {
                int position = position(label, i);
                if (isSingleton(position)) {
                    queue.add(new CellRef(layerId, position));
                }
            }
        }

        boolean isSingleton(int index) {
            return (degrees[index] == 1 || degrees[index] == -1)
                && checkXors[index] == SogsHashUtils.check(labelXors[index], checkSeed);
        }

        int sign(int index) {
            if (degrees[index] != 1 && degrees[index] != -1) {
                throw new IllegalStateException("not singleton");
            }
            return degrees[index] > 0 ? 1 : -1;
        }

        long label(int index) {
            return labelXors[index];
        }

        boolean isEmpty() {
            for (int i = 0; i < vertexCount; i++) {
                if (degrees[i] != 0 || labelXors[i] != 0L || checkXors[i] != 0L) {
                    return false;
                }
            }
            return true;
        }

        int residualEdgeCount() {
            long absDegreeSum = 0L;
            for (int degreeValue : degrees) {
                absDegreeSum += Math.abs(degreeValue);
            }
            return (int) (absDegreeSum / degree);
        }

        private int position(long label, int index) {
            long hash = SogsHashUtils.mix64(label ^ seed ^ (PHI * (index + 1)));
            return index * subTableLength + Math.floorMod(hash, subTableLength);
        }
    }
}
