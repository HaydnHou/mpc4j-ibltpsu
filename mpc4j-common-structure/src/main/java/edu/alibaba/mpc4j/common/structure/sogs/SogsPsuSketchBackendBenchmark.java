package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fair local benchmark for H5-IBLT and SOGS PSU sketch backends.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchBackendBenchmark {
    /**
     * Default seed.
     */
    private static final long DEFAULT_SEED = 20260609L;

    private SogsPsuSketchBackendBenchmark() {
        // empty
    }

    /**
     * Benchmark config.
     *
     * @param largeSize         large set size.
     * @param smallSize         small set size.
     * @param overlapSize       overlap size.
     * @param payloadByteLength payload byte length.
     * @param alpha             SOGS multiplier.
     * @param degree            SOGS degree.
     * @param seed              seed.
     * @param ibltMultiplier    H5 multiplier.
     * @param trials            trials.
     */
    public record Config(
        int largeSize, int smallSize, int overlapSize, int payloadByteLength,
        double alpha, int degree, long seed, double ibltMultiplier, int trials
    ) {
        /**
         * Default target profile.
         *
         * @return config.
         */
        public static Config defaultTargetProfile() {
            return new Config(
                1 << 18, 1 << 10, 1 << 9, 16,
                1.45, 3, DEFAULT_SEED, H5LongIblt.DEFAULT_MULTIPLIER, 3
            );
        }
    }

    /**
     * Single run result.
     *
     * @param unionSize          union size.
     * @param payloadByteLength  payload byte length.
     * @param sogsSuccess        SOGS success.
     * @param sogsPayloadMatch   SOGS payload match.
     * @param sogsTableSize      SOGS table size.
     * @param sogsHashNum        SOGS hash number.
     * @param sogsBuildNanos     SOGS build time.
     * @param sogsPeelNanos      SOGS peel time.
     * @param sogsResidualEdges  SOGS residual edges.
     * @param h5Success          H5 success.
     * @param h5PayloadMatch     H5 payload match.
     * @param h5TableSize        H5 table size.
     * @param h5HashNum          H5 hash number.
     * @param h5BuildNanos       H5 build time.
     * @param h5PeelNanos        H5 peel time.
     */
    public record Result(
        int unionSize, int payloadByteLength,
        boolean sogsSuccess, boolean sogsPayloadMatch, int sogsTableSize, int sogsHashNum,
        long sogsBuildNanos, long sogsPeelNanos, int sogsResidualEdges,
        boolean h5Success, boolean h5PayloadMatch, int h5TableSize, int h5HashNum,
        long h5BuildNanos, long h5PeelNanos
    ) {
        /**
         * Gets SOGS/H5 table ratio.
         *
         * @return ratio.
         */
        public double tableRatio() {
            return sogsTableSize / (double) h5TableSize;
        }

        /**
         * Gets SOGS/H5 hash-probe ratio.
         *
         * @return ratio.
         */
        public double hashProbeRatio() {
            return sogsHashNum / (double) h5HashNum;
        }

        /**
         * Returns display string.
         *
         * @return string.
         */
        public String toDisplayString() {
            int cellBytes = Integer.BYTES + Long.BYTES + Long.BYTES + payloadByteLength;
            long sogsBytes = (long) sogsTableSize * cellBytes;
            long h5Bytes = (long) h5TableSize * cellBytes;
            return String.format(
                Locale.ROOT,
                "union=%d, payload=%dB%n"
                    + "SOGS: ok=%s, payload=%s, table=%d, d=%d, build=%.3f ms, peel=%.3f ms, "
                    + "residual=%d, estBytes=%.3f MB%n"
                    + "H5: ok=%s, payload=%s, table=%d, d=%d, build=%.3f ms, peel=%.3f ms, estBytes=%.3f MB%n"
                    + "tableRatio=%.3f, hashProbeRatio=%.3f",
                unionSize, payloadByteLength,
                sogsSuccess, sogsPayloadMatch, sogsTableSize, sogsHashNum,
                sogsBuildNanos / 1_000_000.0, sogsPeelNanos / 1_000_000.0, sogsResidualEdges,
                sogsBytes / 1024.0 / 1024.0,
                h5Success, h5PayloadMatch, h5TableSize, h5HashNum,
                h5BuildNanos / 1_000_000.0, h5PeelNanos / 1_000_000.0,
                h5Bytes / 1024.0 / 1024.0, tableRatio(), hashProbeRatio()
            );
        }
    }

    /**
     * Multi-trial summary.
     *
     * @param config                config.
     * @param unionSize             union size.
     * @param sogsSuccessCount      SOGS success count.
     * @param sogsPayloadMatchCount SOGS payload match count.
     * @param h5SuccessCount        H5 success count.
     * @param h5PayloadMatchCount   H5 payload match count.
     * @param sogsTableSize         SOGS table size.
     * @param h5TableSize           H5 table size.
     * @param avgSogsBuildNanos     average SOGS build time.
     * @param avgSogsPeelNanos      average SOGS peel time.
     * @param avgH5BuildNanos       average H5 build time.
     * @param avgH5PeelNanos        average H5 peel time.
     */
    public record Summary(
        Config config, int unionSize,
        int sogsSuccessCount, int sogsPayloadMatchCount, int h5SuccessCount, int h5PayloadMatchCount,
        int sogsTableSize, int h5TableSize,
        long avgSogsBuildNanos, long avgSogsPeelNanos, long avgH5BuildNanos, long avgH5PeelNanos
    ) {
        /**
         * Returns whether all trials succeeded and matched payloads.
         *
         * @return true if all succeeded.
         */
        public boolean allMatched() {
            return sogsPayloadMatchCount == config.trials() && h5PayloadMatchCount == config.trials();
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
         * Gets hash-probe ratio.
         *
         * @return ratio.
         */
        public double hashProbeRatio() {
            return config.degree() / (double) H5LongIblt.HASH_NUM;
        }

        /**
         * Returns display string.
         *
         * @return string.
         */
        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "trials=%d, large=%d, small=%d, overlap=%d, union=%d, payload=%dB%n"
                    + "SOGS: ok=%d/%d, payload=%d/%d, table=%d, d=%d, avgBuild=%.3f ms, avgPeel=%.3f ms%n"
                    + "H5: ok=%d/%d, payload=%d/%d, table=%d, d=%d, avgBuild=%.3f ms, avgPeel=%.3f ms%n"
                    + "tableRatio=%.3f, hashProbeRatio=%.3f",
                config.trials(), config.largeSize(), config.smallSize(), config.overlapSize(), unionSize,
                config.payloadByteLength(),
                sogsSuccessCount, config.trials(), sogsPayloadMatchCount, config.trials(),
                sogsTableSize, config.degree(), avgSogsBuildNanos / 1_000_000.0, avgSogsPeelNanos / 1_000_000.0,
                h5SuccessCount, config.trials(), h5PayloadMatchCount, config.trials(),
                h5TableSize, H5LongIblt.HASH_NUM, avgH5BuildNanos / 1_000_000.0,
                avgH5PeelNanos / 1_000_000.0, tableRatio(), hashProbeRatio()
            );
        }
    }

    /**
     * Runs one trial.
     *
     * @param config config.
     * @return result.
     */
    public static Result runOnce(Config config) {
        validateConfig(config);
        Map<Long, byte[]> expected = unionPayloadMap(config);
        long sogsBuildStart = System.nanoTime();
        SogsPsuSketchBackend sogs = SogsPsuSketchBackendFactory.createSogsBackend(
            expected.size(), config.alpha(), config.degree(), config.payloadByteLength(), config.seed()
        );
        for (Map.Entry<Long, byte[]> entry : expected.entrySet()) {
            sogs.add(entry.getKey(), entry.getValue());
        }
        long sogsBuildNanos = System.nanoTime() - sogsBuildStart;
        SogsPsuSketchPeelResult sogsPeelResult = sogs.peel();
        Map<Long, byte[]> sogsRecovered = positiveMap(sogsPeelResult);

        long h5BuildStart = System.nanoTime();
        SogsPsuSketchBackend h5 = SogsPsuSketchBackendFactory.createH5Backend(
            EnvType.STANDARD, expected.size(), config.ibltMultiplier(), config.payloadByteLength(), hashKey(config.seed())
        );
        for (Map.Entry<Long, byte[]> entry : expected.entrySet()) {
            h5.add(entry.getKey(), entry.getValue());
        }
        long h5BuildNanos = System.nanoTime() - h5BuildStart;
        SogsPsuSketchPeelResult h5PeelResult = h5.peel();
        Map<Long, byte[]> h5Recovered = positiveMap(h5PeelResult);

        return new Result(
            expected.size(), config.payloadByteLength(),
            sogsPeelResult.success(), payloadMapEquals(expected, sogsRecovered), sogs.tableSize(), sogs.hashNum(),
            sogsBuildNanos, sogsPeelResult.peelNanos(), sogsPeelResult.residualEdgeCount(),
            h5PeelResult.success(), payloadMapEquals(expected, h5Recovered), h5.tableSize(), h5.hashNum(),
            h5BuildNanos, h5PeelResult.peelNanos()
        );
    }

    /**
     * Runs all trials.
     *
     * @param config config.
     * @return summary.
     */
    public static Summary run(Config config) {
        validateConfig(config);
        int sogsSuccessCount = 0;
        int sogsPayloadMatchCount = 0;
        int h5SuccessCount = 0;
        int h5PayloadMatchCount = 0;
        int unionSize = 0;
        int sogsTableSize = 0;
        int h5TableSize = 0;
        long sogsBuildNanos = 0L;
        long sogsPeelNanos = 0L;
        long h5BuildNanos = 0L;
        long h5PeelNanos = 0L;
        for (int trial = 0; trial < config.trials(); trial++) {
            Result result = runOnce(withTrialSeed(config, trial));
            if (result.sogsSuccess()) {
                sogsSuccessCount++;
            }
            if (result.sogsPayloadMatch()) {
                sogsPayloadMatchCount++;
            }
            if (result.h5Success()) {
                h5SuccessCount++;
            }
            if (result.h5PayloadMatch()) {
                h5PayloadMatchCount++;
            }
            unionSize = result.unionSize();
            sogsTableSize = result.sogsTableSize();
            h5TableSize = result.h5TableSize();
            sogsBuildNanos += result.sogsBuildNanos();
            sogsPeelNanos += result.sogsPeelNanos();
            h5BuildNanos += result.h5BuildNanos();
            h5PeelNanos += result.h5PeelNanos();
        }
        return new Summary(
            config, unionSize, sogsSuccessCount, sogsPayloadMatchCount, h5SuccessCount, h5PayloadMatchCount,
            sogsTableSize, h5TableSize, sogsBuildNanos / config.trials(), sogsPeelNanos / config.trials(),
            h5BuildNanos / config.trials(), h5PeelNanos / config.trials()
        );
    }

    private static Config withTrialSeed(Config config, int trial) {
        return new Config(
            config.largeSize(), config.smallSize(), config.overlapSize(), config.payloadByteLength(),
            config.alpha(), config.degree(), config.seed() + 0x9E3779B97F4A7C15L * (trial + 1),
            config.ibltMultiplier(), 1
        );
    }

    private static Map<Long, byte[]> unionPayloadMap(Config config) {
        int unionSize = config.largeSize() + config.smallSize() - config.overlapSize();
        Map<Long, byte[]> unionMap = new LinkedHashMap<>(unionSize);
        for (int i = 0; i < config.largeSize(); i++) {
            unionMap.put(label(i, config.seed()), payload(i, config.payloadByteLength(), config.seed()));
        }
        long smallOnlyBase = 1L << 40;
        for (int i = 0; i < config.smallSize() - config.overlapSize(); i++) {
            long item = smallOnlyBase + i;
            unionMap.put(label(item, config.seed()), payload(item, config.payloadByteLength(), config.seed()));
        }
        return unionMap;
    }

    private static Map<Long, byte[]> positiveMap(SogsPsuSketchPeelResult result) {
        Map<Long, byte[]> map = new LinkedHashMap<>(result.positiveEntries().size());
        for (SogsPsuSketchEntry entry : result.positiveEntries()) {
            map.put(entry.label(), entry.payload());
        }
        return map;
    }

    private static boolean payloadMapEquals(Map<Long, byte[]> expected, Map<Long, byte[]> actual) {
        if (!expected.keySet().equals(actual.keySet())) {
            return false;
        }
        for (Map.Entry<Long, byte[]> entry : expected.entrySet()) {
            if (!Arrays.equals(entry.getValue(), actual.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static long label(long item, long seed) {
        return SogsHashUtils.label(item, seed);
    }

    private static byte[] payload(long item, int byteLength, long seed) {
        byte[] payload = new byte[byteLength];
        long state = SogsHashUtils.mix64(item ^ seed ^ 0xC6BC279692B5C323L);
        for (int i = 0; i < payload.length; i++) {
            state = SogsHashUtils.mix64(state + i);
            payload[i] = (byte) state;
        }
        return payload;
    }

    private static byte[] hashKey(long seed) {
        byte[] key = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        ByteBuffer.wrap(key)
            .putLong(SogsHashUtils.mix64(seed ^ 0x243F6A8885A308D3L))
            .putLong(SogsHashUtils.mix64(seed ^ 0x13198A2E03707344L));
        return key;
    }

    private static void validateConfig(Config config) {
        if (config == null) {
            throw new NullPointerException("config");
        }
        if (config.largeSize() <= 0 || config.smallSize() <= 0) {
            throw new IllegalArgumentException("set sizes must be positive");
        }
        if (config.overlapSize() < 0 || config.overlapSize() > Math.min(config.largeSize(), config.smallSize())) {
            throw new IllegalArgumentException("invalid overlap size: " + config.overlapSize());
        }
        if (config.payloadByteLength() < 0) {
            throw new IllegalArgumentException("payloadByteLength must be non-negative");
        }
        if (config.trials() <= 0) {
            throw new IllegalArgumentException("trials must be positive");
        }
    }

    /**
     * Main entry.
     *
     * @param args key=value arguments.
     */
    public static void main(String[] args) {
        System.out.println(run(parseArgs(args)).toDisplayString());
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
        Config defaults = Config.defaultTargetProfile();
        return new Config(
            getInt(options, "largeSize", defaults.largeSize()),
            getInt(options, "smallSize", defaults.smallSize()),
            getInt(options, "overlap", defaults.overlapSize()),
            getInt(options, "payloadByteLength", defaults.payloadByteLength()),
            getDouble(options, "alpha", defaults.alpha()),
            getInt(options, "degree", defaults.degree()),
            getLong(options, "seed", defaults.seed()),
            getDouble(options, "ibltMultiplier", defaults.ibltMultiplier()),
            getInt(options, "trials", defaults.trials())
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
}
