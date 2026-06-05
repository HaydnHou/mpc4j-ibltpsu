package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;

import java.util.Locale;

/**
 * BA-SSU-IBLT secure-component fair benchmark estimator.
 *
 * <p>This benchmark reports the implemented secure components without over-claiming final security. The current M14a
 * transport is fixed-shape and COT-backed, but it is not full oblivious branch selection. Therefore the report separates
 * the current fixed-loop M14a path, historical one-pass lower-bound accounting, and the queue-peel fast target.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltSecureFairBenchmark {
    /**
     * security notice id.
     */
    public static final String SECURITY_NOTICE_ID = "QUEUE_PEEL_ESTIMATE_NOT_PRODUCTION";
    /**
     * security notice.
     */
    public static final String SECURITY_NOTICE = "QUEUE_PEEL_ALIGNED estimate uses MP-OPRF and specialized "
        + "union-probe accounting; production union-probe BA-UPOT is not implemented, so do not claim measured "
        + "secure speedup";
    /**
     * benchmark kind for the current queue-peel report.
     */
    public static final String BENCHMARK_KIND_ESTIMATE = "ESTIMATE";
    /**
     * baseline accounting name.
     */
    public static final String H5_BASELINE_NAME = "H5_IBLT_PSU_BUCKET_PROBE_ESTIMATE";
    /**
     * retry status label for queue-peel estimate reports.
     */
    public static final String QUEUE_PEEL_ESTIMATE_RETRY_STATUS = "estimate-public-retry-schedule";
    /**
     * default COT offline ns per bucket from the existing local two-party BA-UPOT profile.
     */
    private static final double DEFAULT_COT_OFFLINE_NS_PER_BUCKET = 1596.19;
    /**
     * M14a capsule auth tag bytes.
     */
    private static final int M14A_AUTH_TAG_BYTE_LENGTH = 16;
    /**
     * M14a mask seed for byte-length calculation only.
     */
    private static final byte[] M14A_LENGTH_SEED = new byte[]{0x42, 0x41, 0x2D, 0x53, 0x53, 0x55};

    /**
     * private constructor.
     */
    private BaSsuIbltSecureFairBenchmark() {
        // empty
    }

    /**
     * Runs benchmark from command line.
     *
     * @param args key=value arguments.
     * @throws InterruptedException interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        Config config = Config.fromArgs(args);
        Result result = run(config);
        System.out.println(config.tsv ? Result.tsvHeader() + System.lineSeparator() + result.toTsvLine()
            : result.toDisplayString());
    }

    /**
     * Runs benchmark / estimator.
     *
     * @param config config.
     * @return result.
     * @throws InterruptedException interrupted.
     */
    public static Result run(Config config) throws InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        config.validate();
        BaSsuIbltBiUpsuParams params = params(config);
        BaSsuIbltOprfTagConfig tagConfig = BaSsuIbltOprfTagConfig.fromParams(
            OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST), params
        );
        BaSsuIbltOprfTagBenchmark.Result oprfResult = BaSsuIbltOprfTagBenchmark.run(
            tagConfig, params.getNLarge(), config.elementByteLength, config.seed
        );
        BaUpotMicroBenchmark.Config microConfig = new BaUpotMicroBenchmark.Config()
            .setBucketNum(config.calibrationBucketNum)
            .setWarmupRounds(config.warmupRounds)
            .setMeasureRounds(config.measureRounds)
            .setElementByteLength(config.elementByteLength)
            .setCheckBits(params.getCheckBits())
            .setTagBits(params.getTagBits())
            .setLcotNumPerBucket(config.cotNumPerBucket)
            .setSeed(config.seed);
        BaUpotMicroBenchmark.Result microResult = BaUpotMicroBenchmark.run(microConfig);
        return new Result(config, params, oprfResult, microResult);
    }

    private static BaSsuIbltBiUpsuParams params(Config config) {
        int tableLength = (int) Math.ceil(config.alpha * config.largeSize);
        long queuePeelProbeBound = Math.multiplyExact(
            config.retryCount,
            Math.addExact(
                tableLength,
                Math.multiplyExact((long) config.degree, Math.addExact(config.largeSize, config.shadowSize))
            )
        );
        long nTests = Math.multiplyExact(queuePeelProbeBound, BaSsuIbltBiUpsuParams.DEFAULT_CHECKS_PER_BUCKET);
        BaSsuIbltBiUpsuParams.Builder builder = new BaSsuIbltBiUpsuParams.Builder(
            config.largeSize, config.shadowSize
        )
            .setDegree(config.degree)
            .setAlphaAnchor(config.alpha)
            .setRetryCount(config.retryCount)
            .setNTests(nTests)
            .setPublicPlaceSeed(config.seed);
        if (config.checkBits > 0) {
            builder.setCheckBits(config.checkBits);
        }
        if (config.tagBits > 0) {
            builder.setTagBits(config.tagBits);
        }
        BaSsuIbltBiUpsuParams params = builder.build();
        int minCheckBits = BaSsuIbltProtocolSchedule.queuePeelMinCheckBits(params);
        if (params.getCheckBits() < minCheckBits) {
            throw new IllegalArgumentException(
                "checkBits must be at least " + minCheckBits + " for QUEUE_PEEL_ALIGNED accounting"
            );
        }
        return params;
    }

    private static long multiplyBucketDouble(long bucketCount, double valuePerBucket) {
        return (long) Math.ceil(bucketCount * valuePerBucket);
    }

    private static long multiplyBucketLong(long bucketCount, long valuePerBucket) {
        return Math.multiplyExact(bucketCount, valuePerBucket);
    }

    private static int m14aCapsuleByteLength(int elementByteLength) {
        return new BaUnionPeelOtSecureOutputCapsuleCodec(
            elementByteLength, M14A_AUTH_TAG_BYTE_LENGTH, M14A_LENGTH_SEED
        ).capsuleByteLength();
    }

    /**
     * Config.
     */
    public static class Config {
        /**
         * large size.
         */
        private int largeSize;
        /**
         * shadow size.
         */
        private int shadowSize;
        /**
         * overlap.
         */
        private int overlap;
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * degree.
         */
        private int degree;
        /**
         * alpha.
         */
        private double alpha;
        /**
         * retry count.
         */
        private int retryCount;
        /**
         * seed.
         */
        private long seed;
        /**
         * check bits, 0 means parameter default.
         */
        private int checkBits;
        /**
         * tag bits, 0 means parameter default.
         */
        private int tagBits;
        /**
         * COT number per bucket.
         */
        private int cotNumPerBucket;
        /**
         * calibration bucket count.
         */
        private int calibrationBucketNum;
        /**
         * warmup rounds.
         */
        private int warmupRounds;
        /**
         * measured rounds.
         */
        private int measureRounds;
        /**
         * TSV output.
         */
        private boolean tsv;

        public Config() {
            largeSize = 1 << 18;
            shadowSize = 1 << 10;
            overlap = shadowSize / 4;
            elementByteLength = Long.BYTES;
            degree = 3;
            alpha = 1.55;
            retryCount = 1;
            seed = 20260604L;
            checkBits = 0;
            tagBits = 0;
            cotNumPerBucket = BaUpotMicroBenchmark.DEFAULT_LCOT_NUM_PER_BUCKET;
            calibrationBucketNum = 4096;
            warmupRounds = 1;
            measureRounds = 1;
            tsv = false;
        }

        public static Config fromArgs(String[] args) {
            Config config = new Config();
            for (String arg : args) {
                String normalized = arg.startsWith("--") ? arg.substring(2) : arg;
                int index = normalized.indexOf('=');
                if (index <= 0) {
                    throw new IllegalArgumentException("Argument must be key=value: " + arg);
                }
                String key = normalized.substring(0, index).trim();
                String value = normalized.substring(index + 1).trim();
                switch (key) {
                    case "large":
                    case "largeSize":
                    case "n":
                        config.largeSize = Integer.parseInt(value);
                        break;
                    case "largeLog":
                    case "nLog":
                        config.largeSize = logSize(value, "largeSize");
                        break;
                    case "shadow":
                    case "shadowSize":
                    case "m":
                        config.shadowSize = Integer.parseInt(value);
                        break;
                    case "shadowLog":
                    case "mLog":
                        config.shadowSize = logSize(value, "shadowSize");
                        break;
                    case "overlap":
                        config.overlap = Integer.parseInt(value);
                        break;
                    case "overlapRate":
                        config.overlap = (int) Math.round(Double.parseDouble(value) * config.shadowSize);
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
                        config.seed = Long.decode(value);
                        break;
                    case "checkBits":
                        config.checkBits = Integer.parseInt(value);
                        break;
                    case "tagBits":
                        config.tagBits = Integer.parseInt(value);
                        break;
                    case "cot":
                    case "cotNumPerBucket":
                        config.cotNumPerBucket = Integer.parseInt(value);
                        break;
                    case "calibrationBuckets":
                    case "calibrationBucketNum":
                        config.calibrationBucketNum = Integer.parseInt(value);
                        break;
                    case "warmup":
                    case "warmupRounds":
                        config.warmupRounds = Integer.parseInt(value);
                        break;
                    case "measure":
                    case "measureRounds":
                        config.measureRounds = Integer.parseInt(value);
                        break;
                    case "tsv":
                        config.tsv = Boolean.parseBoolean(value);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }
            config.validate();
            return config;
        }

        public int getLargeSize() {
            return largeSize;
        }

        public int getShadowSize() {
            return shadowSize;
        }

        public int getCalibrationBucketNum() {
            return calibrationBucketNum;
        }

        private void validate() {
            if (largeSize <= 0 || shadowSize <= 0) {
                throw new IllegalArgumentException("largeSize and shadowSize must be positive");
            }
            if ((long) largeSize + shadowSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("largeSize + shadowSize must fit int range for fair benchmark");
            }
            if (overlap < 0 || overlap > Math.min(largeSize, shadowSize)) {
                throw new IllegalArgumentException("overlap must be in [0, min(largeSize, shadowSize)]");
            }
            if (elementByteLength <= 0) {
                throw new IllegalArgumentException("elementByteLength must be positive");
            }
            if (degree != 3 && degree != 4) {
                throw new IllegalArgumentException("degree must be 3 or 4");
            }
            if (!Double.isFinite(alpha) || alpha <= 0.0) {
                throw new IllegalArgumentException("alpha must be finite and positive");
            }
            if (retryCount <= 0 || cotNumPerBucket <= 0 || calibrationBucketNum <= 0 || measureRounds <= 0) {
                throw new IllegalArgumentException("retry/cot/calibration/measure values must be positive");
            }
            if (warmupRounds < 0) {
                throw new IllegalArgumentException("warmupRounds must be non-negative");
            }
            if (checkBits < 0 || tagBits < 0) {
                throw new IllegalArgumentException("checkBits/tagBits must be non-negative");
            }
        }

        private static int logSize(String value, String name) {
            int logValue = Integer.parseInt(value);
            if (logValue < 0 || logValue >= Integer.SIZE - 1) {
                throw new IllegalArgumentException(name + " log must be in [0, 30]");
            }
            return 1 << logValue;
        }
    }

    /**
     * Result.
     */
    public static class Result {
        /**
         * config.
         */
        private final Config config;
        /**
         * params.
         */
        private final BaSsuIbltBiUpsuParams params;
        /**
         * OPRF result.
         */
        private final BaSsuIbltOprfTagBenchmark.Result oprfResult;
        /**
         * BA-UPOT micro result.
         */
        private final BaUpotMicroBenchmark.Result microResult;
        /**
         * current M14a fixed-loop buckets.
         */
        private final long currentFixedLoopBuckets;
        /**
         * target one-pass buckets.
         */
        private final long targetOnePassBuckets;
        /**
         * queue-peel max probes.
         */
        private final long queuePeelBuckets;
        /**
         * H5 cells.
         */
        private final long h5Cells;
        /**
         * H5 probes.
         */
        private final long h5BucketProbes;
        /**
         * M14a capsule bytes.
         */
        private final int m14aCapsuleBytesPerBucket;

        Result(Config config, BaSsuIbltBiUpsuParams params, BaSsuIbltOprfTagBenchmark.Result oprfResult,
               BaUpotMicroBenchmark.Result microResult) {
            this.config = config;
            this.params = params;
            this.oprfResult = oprfResult;
            this.microResult = microResult;
            BaSsuIbltProtocolSchedule currentSchedule = BaSsuIbltProtocolSchedule.currentFixedLoopM14a(params);
            BaSsuIbltProtocolSchedule targetSchedule = BaSsuIbltProtocolSchedule.targetOnePass(params);
            BaSsuIbltProtocolSchedule queueSchedule = BaSsuIbltProtocolSchedule.queuePeelAligned(params);
            currentFixedLoopBuckets = currentSchedule.getScheduledBucketCount();
            targetOnePassBuckets = targetSchedule.getScheduledBucketCount();
            queuePeelBuckets = queueSchedule.getScheduledBucketCount();
            h5Cells = BaUpotCostEstimator.estimateH5Cells(params.getNLarge(), params.getNShadow());
            h5BucketProbes = BaUpotCostEstimator.estimateH5BucketProbes(params.getNLarge(), params.getNShadow());
            m14aCapsuleBytesPerBucket = m14aCapsuleByteLength(config.elementByteLength);
        }

        public BaSsuIbltOprfTagBenchmark.Result getOprfResult() {
            return oprfResult;
        }

        public BaUpotMicroBenchmark.Result getMicroResult() {
            return microResult;
        }

        public long getCurrentFixedLoopBuckets() {
            return currentFixedLoopBuckets;
        }

        public long getTargetOnePassBuckets() {
            return targetOnePassBuckets;
        }

        public long getQueuePeelBuckets() {
            return queuePeelBuckets;
        }

        public long getH5Cells() {
            return h5Cells;
        }

        public long getH5BucketProbes() {
            return h5BucketProbes;
        }

        public int getM14aCapsuleBytesPerBucket() {
            return m14aCapsuleBytesPerBucket;
        }

        public long getCurrentM14aOfflineBytes() {
            return multiplyBucketLong(currentFixedLoopBuckets, microResult.getOfflineCotBytesPerBucket());
        }

        public long getCurrentM14aOnlineBytes() {
            return multiplyBucketLong(currentFixedLoopBuckets, m14aCapsuleBytesPerBucket);
        }

        public long getTargetOnePassOfflineBytes() {
            return multiplyBucketLong(targetOnePassBuckets, microResult.getOfflineCotBytesPerBucket());
        }

        public long getTargetOnePassOnlineBytes() {
            return multiplyBucketLong(targetOnePassBuckets, microResult.getOnlineBytesPerBucket());
        }

        public long getQueuePeelOfflineBytes() {
            return multiplyBucketLong(queuePeelBuckets, microResult.getOfflineCotBytesPerBucket());
        }

        public long getQueuePeelOnlineBytes() {
            return multiplyBucketLong(queuePeelBuckets, microResult.getOnlineBytesPerBucket());
        }

        public long getCurrentM14aOfflineTimeNanos() {
            return oprfResult.getOfflineTimeNanos()
                + multiplyBucketDouble(currentFixedLoopBuckets, DEFAULT_COT_OFFLINE_NS_PER_BUCKET);
        }

        public long getCurrentM14aOnlineTimeNanos() {
            return oprfResult.getOnlineTimeNanos()
                + multiplyBucketDouble(currentFixedLoopBuckets, microResult.getNanosPerBucket());
        }

        public long getTargetOnePassOfflineTimeNanos() {
            return oprfResult.getOfflineTimeNanos()
                + multiplyBucketDouble(targetOnePassBuckets, DEFAULT_COT_OFFLINE_NS_PER_BUCKET);
        }

        public long getTargetOnePassOnlineTimeNanos() {
            return oprfResult.getOnlineTimeNanos()
                + multiplyBucketDouble(targetOnePassBuckets, microResult.getNanosPerBucket());
        }

        public long getQueuePeelOfflineTimeNanos() {
            return oprfResult.getOfflineTimeNanos()
                + multiplyBucketDouble(queuePeelBuckets, DEFAULT_COT_OFFLINE_NS_PER_BUCKET);
        }

        public long getQueuePeelOnlineTimeNanos() {
            return oprfResult.getOnlineTimeNanos()
                + multiplyBucketDouble(queuePeelBuckets, microResult.getNanosPerBucket());
        }

        public long getCurrentM14aOfflineTotalBytes() {
            return oprfResult.getOfflineSendBytes() + getCurrentM14aOfflineBytes();
        }

        public long getCurrentM14aOnlineTotalBytes() {
            return oprfResult.getOnlineSendBytes() + getCurrentM14aOnlineBytes();
        }

        public long getTargetOnePassOfflineTotalBytes() {
            return oprfResult.getOfflineSendBytes() + getTargetOnePassOfflineBytes();
        }

        public long getTargetOnePassOnlineTotalBytes() {
            return oprfResult.getOnlineSendBytes() + getTargetOnePassOnlineBytes();
        }

        public long getQueuePeelOfflineTotalBytes() {
            return oprfResult.getOfflineSendBytes() + getQueuePeelOfflineBytes();
        }

        public long getQueuePeelOnlineTotalBytes() {
            return oprfResult.getOnlineSendBytes() + getQueuePeelOnlineBytes();
        }

        public double getTargetOnePassVsH5ProbeRatio() {
            return ((double) targetOnePassBuckets) / h5BucketProbes;
        }

        public double getQueuePeelVsH5ProbeRatio() {
            return ((double) queuePeelBuckets) / h5BucketProbes;
        }

        public double getQueuePeelVsH5EstimatedProbeRatio() {
            return getQueuePeelVsH5ProbeRatio();
        }

        public boolean isProductionReady() {
            return false;
        }

        public boolean isMeasuredProduction() {
            return false;
        }

        public String getBenchmarkKind() {
            return BENCHMARK_KIND_ESTIMATE;
        }

        public String getBaselineName() {
            return H5_BASELINE_NAME;
        }

        public String getQueuePeelRetryStatus() {
            return QUEUE_PEEL_ESTIMATE_RETRY_STATUS;
        }

        public static String tsvHeader() {
            return String.join("\t",
                "large",
                "shadow",
                "overlap",
                "tableLength",
                "fixedRounds",
                "currentFixedLoopBuckets",
                "historicalTargetOnePassLowerBoundBuckets",
                "queuePeelBuckets",
                "h5Cells",
                "h5BucketProbes",
                "historicalTargetOnePassVsH5ProbeRatio",
                "queuePeelVsH5EstimatedProbeRatio",
                "oprfOfflineMs",
                "oprfOnlineMs",
                "currentM14aOfflineMs",
                "currentM14aOnlineMs",
                "historicalTargetOnePassOfflineMs",
                "historicalTargetOnePassOnlineMs",
                "queuePeelOfflineMs",
                "queuePeelOnlineMs",
                "currentM14aOfflineTotalBytes",
                "currentM14aOnlineTotalBytes",
                "historicalTargetOnePassOfflineTotalBytes",
                "historicalTargetOnePassOnlineTotalBytes",
                "queuePeelOfflineTotalBytes",
                "queuePeelOnlineTotalBytes",
                "benchmarkKind",
                "measuredProduction",
                "baselineName",
                "productionReady",
                "retryStatus",
                "securityNotice"
            );
        }

        public String toTsvLine() {
            return String.format(
                Locale.ROOT,
                "%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.6f\t%.6f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f"
                    + "\t%.3f\t%.3f\t%d\t%d\t%d\t%d\t%d\t%d\t%s\t%s\t%s\t%s\t%s\t%s",
                config.largeSize,
                config.shadowSize,
                config.overlap,
                params.getTableLength(),
                BaSsuIbltSecureProtocol.fixedRoundCount(params),
                currentFixedLoopBuckets,
                targetOnePassBuckets,
                queuePeelBuckets,
                h5Cells,
                h5BucketProbes,
                getTargetOnePassVsH5ProbeRatio(),
                getQueuePeelVsH5EstimatedProbeRatio(),
                oprfResult.getOfflineTimeNanos() / 1_000_000.0,
                oprfResult.getOnlineTimeNanos() / 1_000_000.0,
                getCurrentM14aOfflineTimeNanos() / 1_000_000.0,
                getCurrentM14aOnlineTimeNanos() / 1_000_000.0,
                getTargetOnePassOfflineTimeNanos() / 1_000_000.0,
                getTargetOnePassOnlineTimeNanos() / 1_000_000.0,
                getQueuePeelOfflineTimeNanos() / 1_000_000.0,
                getQueuePeelOnlineTimeNanos() / 1_000_000.0,
                getCurrentM14aOfflineTotalBytes(),
                getCurrentM14aOnlineTotalBytes(),
                getTargetOnePassOfflineTotalBytes(),
                getTargetOnePassOnlineTotalBytes(),
                getQueuePeelOfflineTotalBytes(),
                getQueuePeelOnlineTotalBytes(),
                getBenchmarkKind(),
                isMeasuredProduction(),
                getBaselineName(),
                isProductionReady(),
                getQueuePeelRetryStatus(),
                SECURITY_NOTICE_ID
            );
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT secure-component fair benchmark%n"
                    + "securityNotice=%s%n"
                    + "benchmarkKind=%s%n"
                    + "measuredProduction=%s%n"
                    + "productionReady=%s%n"
                    + "retryStatus=%s%n"
                    + "baselineName=%s%n"
                    + "large=%d, shadow=%d, overlap=%d, degree=%d, alpha=%.4f, retry=%d%n"
                    + "tableLength=%d, fixedRounds=%d%n"
                    + "h5Cells=%d, h5BucketProbes=%d%n"
                    + "historicalTargetOnePassLowerBoundBuckets=%d, "
                    + "historicalTargetOnePassVsH5ProbeRatio=%.6f%n"
                    + "queuePeelBuckets=%d, queuePeelVsH5EstimatedProbeRatio=%.6f%n"
                    + "currentM14aFixedLoopBuckets=%d%n"
                    + "oprfOfflineTime=%.3f ms, oprfOnlineTime=%.3f ms%n"
                    + "oprfOfflineBytes=%d, oprfOnlineBytes=%d%n"
                    + "m14aCapsuleBytesPerBucket=%d, targetBaUpotOnlineBytesPerBucket=%d, "
                    + "offlineCotBytesPerBucket=%d%n"
                    + "currentM14aOfflineTime=%.3f ms, currentM14aOnlineTime=%.3f ms%n"
                    + "currentM14aOfflineTotalBytes=%d, currentM14aOnlineTotalBytes=%d%n"
                    + "historicalTargetOnePassOfflineTime=%.3f ms, "
                    + "historicalTargetOnePassOnlineTime=%.3f ms%n"
                    + "historicalTargetOnePassOfflineTotalBytes=%d, historicalTargetOnePassOnlineTotalBytes=%d%n"
                    + "queuePeelOfflineTime=%.3f ms, queuePeelOnlineTime=%.3f ms%n"
                    + "queuePeelOfflineTotalBytes=%d, queuePeelOnlineTotalBytes=%d",
                SECURITY_NOTICE,
                getBenchmarkKind(),
                isMeasuredProduction(),
                isProductionReady(),
                getQueuePeelRetryStatus(),
                getBaselineName(),
                config.largeSize,
                config.shadowSize,
                config.overlap,
                params.getDegree(),
                params.getAlphaAnchor(),
                params.getRetryCount(),
                params.getTableLength(),
                BaSsuIbltSecureProtocol.fixedRoundCount(params),
                h5Cells,
                h5BucketProbes,
                targetOnePassBuckets,
                getTargetOnePassVsH5ProbeRatio(),
                queuePeelBuckets,
                getQueuePeelVsH5EstimatedProbeRatio(),
                currentFixedLoopBuckets,
                oprfResult.getOfflineTimeNanos() / 1_000_000.0,
                oprfResult.getOnlineTimeNanos() / 1_000_000.0,
                oprfResult.getOfflineSendBytes(),
                oprfResult.getOnlineSendBytes(),
                m14aCapsuleBytesPerBucket,
                microResult.getOnlineBytesPerBucket(),
                microResult.getOfflineCotBytesPerBucket(),
                getCurrentM14aOfflineTimeNanos() / 1_000_000.0,
                getCurrentM14aOnlineTimeNanos() / 1_000_000.0,
                getCurrentM14aOfflineTotalBytes(),
                getCurrentM14aOnlineTotalBytes(),
                getTargetOnePassOfflineTimeNanos() / 1_000_000.0,
                getTargetOnePassOnlineTimeNanos() / 1_000_000.0,
                getTargetOnePassOfflineTotalBytes(),
                getTargetOnePassOnlineTotalBytes(),
                getQueuePeelOfflineTimeNanos() / 1_000_000.0,
                getQueuePeelOnlineTimeNanos() / 1_000_000.0,
                getQueuePeelOfflineTotalBytes(),
                getQueuePeelOnlineTotalBytes()
            );
        }
    }
}
