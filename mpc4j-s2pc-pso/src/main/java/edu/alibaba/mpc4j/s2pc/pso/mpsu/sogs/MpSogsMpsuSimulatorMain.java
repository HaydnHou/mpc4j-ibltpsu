package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clear MP-SOGS MPSU simulator main.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuSimulatorMain {
    /**
     * Public hash-seed retry stride, matching the ABB3 runner.
     */
    private static final long HASH_SEED_RETRY_STRIDE = 0x9E3779B97F4A7C15L;

    private MpSogsMpsuSimulatorMain() {
        // empty
    }

    public static void main(String[] args) {
        Config config = Config.parse(args);
        System.out.println("parties,n,overlap,alpha,k,max_hash_seed_retries,hash_seed_attempts,trial,success,"
            + "unionSize,rounds,upeelCalls,duplicateOpenings,comm16B,comm32B,comm48B,comm96B,seqCallEstimate,"
            + "seqToMpCallRatio,failureReason");
        for (double overlap : config.overlaps) {
            for (int trial = 0; trial < config.trials; trial++) {
                List<Set<Long>> inputs = generateInputs(config.parties, config.n, overlap);
                int unionSize = union(inputs).size();
                MpSogsTranscript transcript = runWithRetry(config, inputs, unionSize, overlap, trial);
                long calls = transcript.getUpeelCalls();
                long seqEstimate = sequentialCallEstimate(config.parties, config.n, overlap, config.alpha, config.k);
                double seqToMp = calls == 0 ? 0.0 : (double) seqEstimate / calls;
                System.out.printf(
                    "%d,%d,%.4f,%.4f,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.6f,\"%s\"%n",
                    config.parties, config.n, overlap, config.alpha, config.k, config.maxHashSeedRetries,
                    transcript.getHashSeedAttempts(), trial, transcript.isSuccess(), unionSize,
                    transcript.getRoundNum(), calls,
                    transcript.getDuplicateOpenings(), calls * 16, calls * 32, calls * 48, calls * 96,
                    seqEstimate, seqToMp, transcript.getFailureReason().replace("\"", "\"\"")
                );
            }
        }
    }

    private static MpSogsTranscript runWithRetry(Config config, List<Set<Long>> inputs, int unionSize, double overlap,
                                                 int trial) {
        List<MpSogsRoundStats> aggregateStats = new ArrayList<>();
        MpSogsTranscript lastTranscript = null;
        String lastFailureReason = "";
        for (int retryIndex = 0; retryIndex < config.maxHashSeedRetries; retryIndex++) {
            MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(config.parties, unionSize)
                .setAlpha(config.alpha)
                .setHashNum(config.k)
                .setHashSeed(config.seed + 1_000_003L * trial + Math.round(overlap * 1_000_000)
                    + HASH_SEED_RETRY_STRIDE * retryIndex)
                .build();
            MpSogsEquivalenceChecker.Result check = MpSogsEquivalenceChecker.check(inputs, params);
            MpSogsTranscript transcript = check.isSuccess()
                ? check.getTranscript()
                : ClearMpSogsMpsu.run(inputs, params);
            aggregateStats.addAll(transcript.getRoundStats());
            if (check.isSuccess() && transcript.isSuccess()) {
                return new MpSogsTranscript(transcript.getUnionOutput(), aggregateStats, true, "", retryIndex + 1);
            }
            lastTranscript = transcript;
            lastFailureReason = check.isSuccess() ? transcript.getFailureReason() : check.getMessage();
        }
        if (lastTranscript == null) {
            throw new IllegalStateException("no clear MP-SOGS attempt was executed");
        }
        return new MpSogsTranscript(
            lastTranscript.getUnionOutput(), aggregateStats, false,
            lastFailureReason + " after " + config.maxHashSeedRetries + " public hash-seed attempt(s)",
            config.maxHashSeedRetries
        );
    }

    private static List<Set<Long>> generateInputs(int parties, int n, double commonOverlap) {
        int commonCount = (int) Math.round(n * commonOverlap);
        int uniqueCount = n - commonCount;
        List<Set<Long>> inputs = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < parties; partyIndex++) {
            Set<Long> input = new HashSet<>();
            for (long value = 1; value <= commonCount; value++) {
                input.add(value);
            }
            long start = commonCount + (long) partyIndex * uniqueCount + 1;
            for (long value = start; value < start + uniqueCount; value++) {
                input.add(value);
            }
            inputs.add(input);
        }
        return inputs;
    }

    private static Set<Long> union(List<Set<Long>> inputs) {
        Set<Long> union = new HashSet<>();
        inputs.forEach(union::addAll);
        return union;
    }

    private static long sequentialCallEstimate(int parties, int n, double commonOverlap, double alpha, int k) {
        int commonCount = (int) Math.round(n * commonOverlap);
        int uniqueCount = n - commonCount;
        long calls = 0L;
        for (int partyCount = 2; partyCount <= parties; partyCount++) {
            long stepUnion = commonCount + (long) partyCount * uniqueCount;
            calls += (long) Math.ceil((alpha + k) * stepUnion);
        }
        return calls;
    }

    private static class Config {
        private int parties = 3;
        private int n = 1024;
        private double alpha = MpSogsMpsuParams.DEFAULT_ALPHA;
        private int k = MpSogsMpsuParams.DEFAULT_HASH_NUM;
        private int trials = 5;
        private long seed = MpSogsMpsuParams.DEFAULT_HASH_SEED;
        private double[] overlaps = new double[]{0.0, 0.5, 0.9};
        private int maxHashSeedRetries = 1;

        private static Config parse(String[] args) {
            Config config = new Config();
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                String key = arg;
                String value;
                int equalIndex = arg.indexOf('=');
                if (equalIndex >= 0) {
                    key = arg.substring(0, equalIndex);
                    value = arg.substring(equalIndex + 1);
                } else {
                    value = args[++index];
                }
                switch (key) {
                    case "--parties" -> config.parties = Integer.parseInt(value);
                    case "--n" -> config.n = parseInt(value);
                    case "--alpha" -> config.alpha = Double.parseDouble(value);
                    case "--k" -> config.k = Integer.parseInt(value);
                    case "--trials" -> config.trials = Integer.parseInt(value);
                    case "--seed" -> config.seed = Long.parseLong(value);
                    case "--overlaps" -> config.overlaps = parseOverlaps(value);
                    case "--retries", "--maxHashSeedRetries" -> config.maxHashSeedRetries = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("unknown argument: " + arg);
                }
            }
            return config;
        }

        private static int parseInt(String text) {
            if (text.contains("^")) {
                String[] parts = text.split("\\^");
                return (int) Math.round(Math.pow(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
            return Integer.parseInt(text);
        }

        private static double[] parseOverlaps(String text) {
            String[] parts = text.split(",");
            double[] values = new double[parts.length];
            for (int index = 0; index < parts.length; index++) {
                values[index] = Double.parseDouble(parts[index].trim());
            }
            return values;
        }
    }
}
