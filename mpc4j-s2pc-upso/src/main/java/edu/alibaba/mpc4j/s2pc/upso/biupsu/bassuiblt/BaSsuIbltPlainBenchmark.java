package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * BA-SSU-IBLT plain end-to-end benchmark.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltPlainBenchmark {
    /**
     * measured BA-UPOT offline ns per bucket.
     */
    private static final double BA_UPOT_OFFLINE_NS_PER_BUCKET = 1596.19;
    /**
     * measured BA-UPOT online ns per bucket.
     */
    private static final double BA_UPOT_ONLINE_NS_PER_BUCKET = 1033.97;
    /**
     * measured BA-UPOT offline bytes per bucket.
     */
    private static final double BA_UPOT_OFFLINE_BYTES_PER_BUCKET = 32.0;
    /**
     * measured BA-UPOT online bytes per bucket.
     */
    private static final double BA_UPOT_ONLINE_BYTES_PER_BUCKET = 94.0;
    /**
     * benchmark wire-mask seed.
     */
    private static final byte[] BENCHMARK_MASK_SEED = new byte[]{0x42, 0x41, 0x2D, 0x55, 0x50, 0x4F, 0x54};

    /**
     * private constructor.
     */
    private BaSsuIbltPlainBenchmark() {
        // empty
    }

    /**
     * Runs benchmark.
     *
     * @param args key=value args.
     */
    public static void main(String[] args) {
        Config config = Config.fromArgs(args);
        Result result = run(config);
        System.out.println(result.toDisplayString());
    }

    /**
     * Runs benchmark.
     *
     * @param config config.
     * @return result.
     */
    public static Result run(Config config) {
        SetPair pair = createSets(config.largeSize, config.shadowSize, config.overlap, config.elementByteLength);
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(config.largeSize, config.shadowSize)
            .setDegree(config.degree)
            .setAlphaAnchor(config.alpha)
            .setRetryCount(config.retryCount)
            .setPublicPlaceSeed(config.seed)
            .build();
        long start = System.nanoTime();
        BaUpotCaseGateEvaluator caseGateEvaluator = null;
        BaUpotCaseGateWireMaskedPayloadTransducer caseGateWireMaskedTransducer = null;
        BaSsuIbltPlainResult plainResult;
        if (config.evaluatorMode == BaUpotBucketEvaluatorMode.CASE_GATE) {
            caseGateEvaluator = new BaUpotCaseGateEvaluator();
            plainResult = BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
                pair.largeSet, pair.shadowSet, config.elementByteLength, params, caseGateEvaluator
            );
        } else if (config.evaluatorMode == BaUpotBucketEvaluatorMode.CASE_GATE_WIRE_MASKED) {
            BaUpotConfig upotConfig = new BaUpotConfig.Builder()
                .setElementByteLength(config.elementByteLength)
                .setCheckBits(params.getLambda())
                .setTagBits(params.getLambda())
                .build();
            caseGateWireMaskedTransducer = BaUpotCaseGateWireMaskedPayloadTransducer.fromConfig(
                upotConfig, BENCHMARK_MASK_SEED
            );
            plainResult = BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
                pair.largeSet, pair.shadowSet, config.elementByteLength, params, caseGateWireMaskedTransducer
            );
        } else if (config.collectTrace) {
            plainResult = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
                pair.largeSet, pair.shadowSet, config.elementByteLength, params
            );
        } else {
            plainResult = BaSsuIbltPlainProtocol.runBiOutput(
                pair.largeSet, pair.shadowSet, config.elementByteLength, params
            );
        }
        long plainTimeNanos = System.nanoTime() - start;
        BaSsuIbltPlainResult.CostEstimate costEstimate = plainResult.estimateCost(
            BA_UPOT_OFFLINE_NS_PER_BUCKET, BA_UPOT_ONLINE_NS_PER_BUCKET,
            BA_UPOT_OFFLINE_BYTES_PER_BUCKET, BA_UPOT_ONLINE_BYTES_PER_BUCKET
        );
        BaUpotCaseGateCircuit.GateStats liveCaseGateStats = null;
        long liveCaseGateBucketCount = 0L;
        long livePayloadBytes = 0L;
        int livePayloadCapsuleByteLength = 0;
        if (caseGateEvaluator != null) {
            liveCaseGateStats = caseGateEvaluator.getAggregateGateStats();
            liveCaseGateBucketCount = caseGateEvaluator.getBucketCount();
        } else if (caseGateWireMaskedTransducer != null) {
            liveCaseGateStats = caseGateWireMaskedTransducer.getAggregateGateStats();
            liveCaseGateBucketCount = caseGateWireMaskedTransducer.getBucketCount();
            livePayloadBytes = caseGateWireMaskedTransducer.getCapsuleBytes();
            livePayloadCapsuleByteLength = caseGateWireMaskedTransducer.getCapsuleByteLength();
        }
        return new Result(config, plainResult, plainTimeNanos, costEstimate,
            liveCaseGateStats, liveCaseGateBucketCount, livePayloadBytes, livePayloadCapsuleByteLength
        );
    }

    private static SetPair createSets(int largeSize, int shadowSize, int overlap, int elementByteLength) {
        Set<ByteBuffer> largeSet = new HashSet<>(largeSize);
        Set<ByteBuffer> shadowSet = new HashSet<>(shadowSize);
        for (int i = 0; i < largeSize; i++) {
            ByteBuffer element = element(i + 1L, elementByteLength);
            largeSet.add(element);
            if (i < overlap) {
                shadowSet.add(ByteBuffer.wrap(element.array().clone()));
            }
        }
        for (int i = overlap; i < shadowSize; i++) {
            shadowSet.add(element((long) largeSize + i - overlap + 1L, elementByteLength));
        }
        return new SetPair(largeSet, shadowSet);
    }

    private static ByteBuffer element(long value, int elementByteLength) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(elementByteLength);
        for (int i = elementByteLength - 1; i >= 0; i--) {
            byteBuffer.put(i, (byte) value);
            value >>>= Byte.SIZE;
        }
        return ByteBuffer.wrap(byteBuffer.array());
    }

    /**
     * Benchmark config.
     */
    public static class Config {
        /**
         * large size.
         */
        int largeSize;
        /**
         * shadow size.
         */
        int shadowSize;
        /**
         * overlap.
         */
        int overlap;
        /**
         * element byte length.
         */
        int elementByteLength;
        /**
         * degree.
         */
        int degree;
        /**
         * alpha.
         */
        double alpha;
        /**
         * retry count.
         */
        int retryCount;
        /**
         * seed.
         */
        long seed;
        /**
         * collect bucket trace.
         */
        boolean collectTrace;
        /**
         * bucket evaluator mode.
         */
        BaUpotBucketEvaluatorMode evaluatorMode;

        public Config() {
            largeSize = 1 << 18;
            shadowSize = 1 << 10;
            overlap = shadowSize / 4;
            elementByteLength = Long.BYTES;
            degree = 3;
            alpha = 1.55;
            retryCount = 1;
            seed = 20260603L;
            collectTrace = false;
            evaluatorMode = BaUpotBucketEvaluatorMode.IDEAL;
        }

        public static Config fromArgs(String[] args) {
            Config config = new Config();
            for (String arg : args) {
                String normalized = arg.startsWith("--") ? arg.substring(2) : arg;
                int index = normalized.indexOf('=');
                if (index <= 0) {
                    throw new IllegalArgumentException("Argument must be key=value: " + arg);
                }
                String key = normalized.substring(0, index);
                String value = normalized.substring(index + 1);
                switch (key) {
                    case "large":
                    case "largeSize":
                        config.largeSize = Integer.parseInt(value);
                        break;
                    case "shadow":
                    case "shadowSize":
                        config.shadowSize = Integer.parseInt(value);
                        break;
                    case "overlap":
                        config.overlap = Integer.parseInt(value);
                        break;
                    case "elementBytes":
                    case "elementByteLength":
                        config.elementByteLength = Integer.parseInt(value);
                        break;
                    case "degree":
                        config.degree = Integer.parseInt(value);
                        break;
                    case "alpha":
                        config.alpha = Double.parseDouble(value);
                        break;
                    case "retry":
                    case "retryCount":
                        config.retryCount = Integer.parseInt(value);
                        break;
                    case "seed":
                        config.seed = Long.parseLong(value);
                        break;
                    case "trace":
                    case "collectTrace":
                        config.collectTrace = Boolean.parseBoolean(value);
                        break;
                    case "caseGate":
                        config.evaluatorMode = Boolean.parseBoolean(value)
                            ? BaUpotBucketEvaluatorMode.CASE_GATE
                            : BaUpotBucketEvaluatorMode.IDEAL;
                        break;
                    case "caseGateWireMasked":
                        config.evaluatorMode = Boolean.parseBoolean(value)
                            ? BaUpotBucketEvaluatorMode.CASE_GATE_WIRE_MASKED
                            : BaUpotBucketEvaluatorMode.IDEAL;
                        break;
                    case "evaluator":
                    case "evaluatorMode":
                        config.evaluatorMode = BaUpotBucketEvaluatorMode.valueOf(value.toUpperCase(Locale.ROOT));
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }
            if (config.overlap < 0 || config.overlap > config.shadowSize) {
                throw new IllegalArgumentException("overlap must be in [0, shadowSize]");
            }
            return config;
        }
    }

    /**
     * Benchmark result.
     */
    public static class Result {
        /**
         * config.
         */
        private final Config config;
        /**
         * plain result.
         */
        private final BaSsuIbltPlainResult plainResult;
        /**
         * plain time.
         */
        private final long plainTimeNanos;
        /**
         * BA-UPOT estimate.
         */
        private final BaSsuIbltPlainResult.CostEstimate costEstimate;
        /**
         * live case-gate stats.
         */
        private final BaUpotCaseGateCircuit.GateStats caseGateStats;
        /**
         * live case-gate bucket count.
         */
        private final long caseGateBucketCount;
        /**
         * live payload bytes.
         */
        private final long livePayloadBytes;
        /**
         * live payload capsule byte length.
         */
        private final int livePayloadCapsuleByteLength;

        Result(Config config, BaSsuIbltPlainResult plainResult, long plainTimeNanos,
               BaSsuIbltPlainResult.CostEstimate costEstimate,
               BaUpotCaseGateCircuit.GateStats caseGateStats, long caseGateBucketCount, long livePayloadBytes,
               int livePayloadCapsuleByteLength) {
            this.config = config;
            this.plainResult = plainResult;
            this.plainTimeNanos = plainTimeNanos;
            this.costEstimate = costEstimate;
            this.caseGateStats = caseGateStats;
            this.caseGateBucketCount = caseGateBucketCount;
            this.livePayloadBytes = livePayloadBytes;
            this.livePayloadCapsuleByteLength = livePayloadCapsuleByteLength;
        }

        public BaSsuIbltPlainResult getPlainResult() {
            return plainResult;
        }

        public long getPlainTimeNanos() {
            return plainTimeNanos;
        }

        public BaSsuIbltPlainResult.CostEstimate getCostEstimate() {
            return costEstimate;
        }

        public boolean hasCaseGateStats() {
            return caseGateStats != null;
        }

        public BaUpotCaseGateCircuit.GateStats getCaseGateStats() {
            return caseGateStats;
        }

        public long getCaseGateBucketCount() {
            return caseGateBucketCount;
        }

        public long getLivePayloadBytes() {
            return livePayloadBytes;
        }

        public int getLivePayloadCapsuleByteLength() {
            return livePayloadCapsuleByteLength;
        }

        public String toDisplayString() {
            String display = String.format(
                Locale.ROOT,
                "BA-SSU-IBLT plain end-to-end benchmark%n"
                    + "large=%d, shadow=%d, overlap=%d, degree=%d, alpha=%.2f, retry=%d, evaluator=%s%n"
                    + "success=%s, unionSize=%d, selectedRetry=%d, selectedRounds=%d, maxRounds=%d%n"
                    + "anchorOnly=%d, shadowOnly=%d, sharedSingleton=%d%n"
                    + "scheduledBuckets=%d, crossLayerBlocking=%d, recommendedCheckBits=%d%n"
                    + "plainPeelTime=%.3f ms%n"
                    + "estimatedOfflineTime=%.3f ms, estimatedOnlineTime=%.3f ms, estimatedTotalTime=%.3f ms%n"
                    + "estimatedOfflineBytes=%d, estimatedOnlineBytes=%d, estimatedTotalBytes=%d",
                config.largeSize,
                config.shadowSize,
                config.overlap,
                config.degree,
                config.alpha,
                config.retryCount,
                config.evaluatorMode,
                plainResult.isSuccess(),
                plainResult.getLeftUnion().size(),
                plainResult.getSelectedRetryIndex(),
                plainResult.getSelectedRoundCount(),
                plainResult.getMaxRoundCount(),
                plainResult.getSignedPeelOutput().getAnchorOnlyCount(),
                plainResult.getSignedPeelOutput().getShadowOnlyCount(),
                plainResult.getSharedSingletonCount(),
                plainResult.getScheduledBucketCount(),
                plainResult.getCrossLayerBlockingCount(),
                plainResult.getRecommendedCheckBits(),
                plainTimeNanos / 1_000_000.0,
                costEstimate.getOfflineTimeNanos() / 1_000_000.0,
                costEstimate.getOnlineTimeNanos() / 1_000_000.0,
                costEstimate.getTotalTimeNanos() / 1_000_000.0,
                costEstimate.getOfflineBytes(),
                costEstimate.getOnlineBytes(),
                costEstimate.getTotalBytes()
            );
            if (caseGateStats == null) {
                return display;
            }
            return display + String.format(
                Locale.ROOT,
                "%nliveCaseGateBuckets=%d, liveCaseGateAnd=%d, liveCaseGateXor=%d, liveCaseGateNot=%d"
                    + "%nlivePayloadBytes=%d, livePayloadCapsuleBytes=%d",
                caseGateBucketCount,
                caseGateStats.getAndGateCount(),
                caseGateStats.getXorGateCount(),
                caseGateStats.getNotGateCount(),
                livePayloadBytes,
                livePayloadCapsuleByteLength
            );
        }
    }

    /**
     * generated pair.
     */
    private static class SetPair {
        /**
         * large set.
         */
        private final Set<ByteBuffer> largeSet;
        /**
         * shadow set.
         */
        private final Set<ByteBuffer> shadowSet;

        SetPair(Set<ByteBuffer> largeSet, Set<ByteBuffer> shadowSet) {
            this.largeSet = largeSet;
            this.shadowSet = shadowSet;
        }
    }
}
