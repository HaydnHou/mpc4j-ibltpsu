package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * BA-SSU-IBLT costed two-party end-to-end benchmark.
 *
 * <p>This harness combines the plain BA-SSU-IBLT peel correctness path with a real two-party BA-UPOT run over a fixed
 * public direct-all-buckets schedule. It is the bridge benchmark before binding case-hiding BA-UPOT payloads into the
 * live peel loop.</p>
 *
 * <p>This benchmark is reference/costed only; it is not production-secure.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltTwoPartyBenchmark {
    /**
     * Direct benchmark set-size cap for local in-memory runs.
     */
    private static final int MAX_BENCHMARK_SET_SIZE = 1 << 24;
    /**
     * TSV-safe security notice id.
     */
    public static final String SECURITY_NOTICE_ID = "REFERENCE_COSTED_ONLY";
    /**
     * Security notice for human-readable benchmark output.
     */
    public static final String SECURITY_NOTICE = "reference/costed benchmark only; not production-secure; "
        + "payload bridge uses precomputed bucket outputs before final case-hiding BA-UPOT";
    /**
     * private constructor.
     */
    private BaSsuIbltTwoPartyBenchmark() {
        // empty
    }

    /**
     * Runs benchmark.
     *
     * @param args key=value args.
     * @throws InterruptedException interrupted.
     * @throws IOException failed to read config.
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        Config config = Config.fromArgs(args);
        if (config.isTsv()) {
            suppressInfoLogsForTsv();
        }
        String output;
        if (config.getRepeatCount() > 1 || config.getWarmupCount() > 0) {
            RepeatedResult result = runRepeated(config);
            output = config.isTsv() ? result.toTsvString() : result.toDisplayString();
        } else {
            Result result = run(config);
            output = config.isTsv() ? Result.tsvHeader() + System.lineSeparator() + result.toTsvLine(0)
                : result.toDisplayString();
        }
        emitOutput(config, output);
    }

    /**
     * Runs benchmark with warmup and repeated measured runs.
     *
     * @param config config.
     * @return repeated result.
     * @throws InterruptedException interrupted.
     */
    public static RepeatedResult runRepeated(Config config) throws InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        validatePublicShape(config);
        for (int index = 0; index < config.warmupCount; index++) {
            run(config);
        }
        List<Result> results = new ArrayList<>(config.repeatCount);
        for (int index = 0; index < config.repeatCount; index++) {
            results.add(run(config));
        }
        return new RepeatedResult(config, results);
    }

    private static void emitOutput(Config config, String output) throws IOException {
        if (config.tsvFilePath == null) {
            System.out.println(output);
        } else {
            Files.writeString(Path.of(config.tsvFilePath), output + System.lineSeparator());
        }
    }

    private static void suppressInfoLogsForTsv() {
        try {
            Class<?> loggerClass = Class.forName("org.apache.log4j.Logger");
            Class<?> levelClass = Class.forName("org.apache.log4j.Level");
            Object rootLogger = loggerClass.getMethod("getRootLogger").invoke(null);
            Object warnLevel = levelClass.getField("WARN").get(null);
            loggerClass.getMethod("setLevel", levelClass).invoke(rootLogger, warnLevel);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // keep benchmark output best-effort when log4j is not on the runtime classpath.
        }
    }

    private static void validatePublicShape(Config config) {
        if (config.largeSize > MAX_BENCHMARK_SET_SIZE || config.shadowSize > MAX_BENCHMARK_SET_SIZE) {
            throw new IllegalArgumentException(
                "largeSize and shadowSize must be at most " + MAX_BENCHMARK_SET_SIZE + " for local benchmark runs"
            );
        }
        if (!Double.isFinite(config.alpha)) {
            throw new IllegalArgumentException("alpha must be finite");
        }
        double tableLengthDouble = Math.ceil(config.alpha * config.largeSize);
        if (!Double.isFinite(tableLengthDouble) || tableLengthDouble <= 0 || tableLengthDouble > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("fixed table length must be in int range");
        }
        long fixedDeliveryBuckets = (long) tableLengthDouble * config.retryCount;
        if (fixedDeliveryBuckets <= 0 || fixedDeliveryBuckets > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("fixed delivery bucket count must be in int range");
        }
    }

    /**
     * Runs benchmark.
     *
     * @param config config.
     * @return result.
     * @throws InterruptedException interrupted.
     */
    public static Result run(Config config) throws InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        validatePublicShape(config);
        BaSsuIbltPlainBenchmark.Config plainConfig = new BaSsuIbltPlainBenchmark.Config();
        plainConfig.largeSize = config.largeSize;
        plainConfig.shadowSize = config.shadowSize;
        plainConfig.overlap = config.overlap;
        plainConfig.elementByteLength = config.elementByteLength;
        plainConfig.degree = config.degree;
        plainConfig.alpha = config.alpha;
        plainConfig.retryCount = config.retryCount;
        plainConfig.seed = config.seed;
        plainConfig.collectTrace = false;
        plainConfig.evaluatorMode = config.evaluatorMode;
        BaSsuIbltPlainBenchmark.Result plainBenchmarkResult = BaSsuIbltPlainBenchmark.run(plainConfig);

        if (!plainBenchmarkResult.getPlainResult().isSuccess()) {
            return new Result(config, plainBenchmarkResult, null, null, null, null);
        }

        BaSsuIbltBiUpsuParams params = params(config);
        BaSsuIbltFixedBucketSchedule fixedSchedule = BaSsuIbltFixedBucketSchedule.directAllBuckets(params);
        long fixedDeliveryBuckets = fixedSchedule.getTotalBucketCount();
        if (fixedDeliveryBuckets > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("fixedDeliveryBuckets exceeds int range: " + fixedDeliveryBuckets);
        }
        int checkBits = Math.max(config.checkBits, plainBenchmarkResult.getPlainResult().getRecommendedCheckBits());
        BaUpotTwoPartyBenchmark.BenchmarkConfig upotConfig = new BaUpotTwoPartyBenchmark.BenchmarkConfig()
            .setBucketNum((int) fixedDeliveryBuckets)
            .setElementByteLength(config.elementByteLength)
            .setCheckBits(checkBits)
            .setTagBits(Math.max(config.tagBits, checkBits))
            .setCotNumPerBucket(config.cotNumPerBucket)
            .setOnlineBatchSize(config.onlineBatchSize);
        BaUpotTwoPartyBenchmark.Result upotResult = BaUpotTwoPartyBenchmark.runMemoryBenchmark(upotConfig);
        BaUpotTwoPartyBenchmark.Result reverseUpotResult = null;
        if (config.deliveryMode == BaSsuIbltBiOutputDeliveryMode.TWO_PASS) {
            reverseUpotResult = BaUpotTwoPartyBenchmark.runMemoryBenchmark(upotConfig);
        }
        SetPair bridgePair = null;
        if (config.plainPayload || config.maskedPayload) {
            bridgePair = createSets(config.largeSize, config.shadowSize, config.overlap, config.elementByteLength);
        }
        BaUnionPeelOtResult plainPayloadResult = null;
        if (config.plainPayload) {
            BaUnionPeelOtConfig bridgeConfig = BaUnionPeelOtConfig.fromBenchmarkConfig(
                upotConfig, BaUnionPeelOtMode.PLAIN_PAYLOAD
            );
            plainPayloadResult = BaUnionPeelOtTwoPartyBridge.runMemoryStreamingBridge(
                bridgeConfig, bridgePair.largeSet, bridgePair.shadowSet, config.elementByteLength, params
            );
        }
        BaUnionPeelOtResult maskedPayloadResult = null;
        if (config.maskedPayload) {
            BaUnionPeelOtConfig bridgeConfig = BaUnionPeelOtConfig.fromBenchmarkConfig(
                upotConfig, BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD
            );
            maskedPayloadResult = BaUnionPeelOtTwoPartyBridge.runMemoryStreamingBridge(
                bridgeConfig, bridgePair.largeSet, bridgePair.shadowSet, config.elementByteLength, params
            );
        }
        return new Result(config, plainBenchmarkResult, upotResult, reverseUpotResult, plainPayloadResult,
            maskedPayloadResult);
    }

    private static BaSsuIbltBiUpsuParams params(Config config) {
        return new BaSsuIbltBiUpsuParams.Builder(config.largeSize, config.shadowSize)
            .setDegree(config.degree)
            .setAlphaAnchor(config.alpha)
            .setRetryCount(config.retryCount)
            .setPublicPlaceSeed(config.seed)
            .build();
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
         * check bits.
         */
        private int checkBits;
        /**
         * tag bits.
         */
        private int tagBits;
        /**
         * COT count per bucket.
         */
        private int cotNumPerBucket;
        /**
         * online batch size.
         */
        private int onlineBatchSize;
        /**
         * run plain payload-bound transport over bucket trace.
         */
        private boolean plainPayload;
        /**
         * run wire-masked payload-bound transport over bucket trace.
         */
        private boolean maskedPayload;
        /**
         * live bucket evaluator mode.
         */
        private BaUpotBucketEvaluatorMode evaluatorMode;
        /**
         * bi-output delivery mode.
         */
        private BaSsuIbltBiOutputDeliveryMode deliveryMode;
        /**
         * measured repeat count.
         */
        private int repeatCount;
        /**
         * warmup count.
         */
        private int warmupCount;
        /**
         * true if benchmark should print TSV output.
         */
        private boolean tsv;
        /**
         * optional TSV output file path.
         */
        private String tsvFilePath;

        public Config() {
            largeSize = 1 << 18;
            shadowSize = 1 << 10;
            overlap = shadowSize / 4;
            elementByteLength = Long.BYTES;
            degree = 3;
            alpha = 1.55;
            retryCount = 1;
            seed = 20260603L;
            checkBits = 182;
            tagBits = 182;
            cotNumPerBucket = BaUpotConfig.DEFAULT_COT_NUM_PER_BUCKET;
            onlineBatchSize = BaUpotConfig.DEFAULT_ONLINE_BATCH_SIZE;
            plainPayload = false;
            maskedPayload = false;
            evaluatorMode = BaUpotBucketEvaluatorMode.IDEAL;
            deliveryMode = BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT;
            repeatCount = 1;
            warmupCount = 0;
            tsv = false;
            tsvFilePath = null;
        }

        public static Config fromArgs(String[] args) throws IOException {
            Config config = new Config();
            for (String arg : args) {
                String normalized = arg.startsWith("--") ? arg.substring(2) : arg;
                int index = normalized.indexOf('=');
                if (index <= 0) {
                    throw new IllegalArgumentException("Argument must be key=value: " + arg);
                }
                String key = normalized.substring(0, index).trim();
                String value = normalized.substring(index + 1).trim();
                if (isConfigPathKey(key)) {
                    config.applyProperties(loadProperties(value));
                } else {
                    config.applyOption(key, value);
                }
            }
            config.validate();
            return config;
        }

        public static Config fromProperties(Properties properties) {
            if (properties == null) {
                throw new IllegalArgumentException("properties must be non-null");
            }
            Config config = new Config();
            config.applyProperties(properties);
            config.validate();
            return config;
        }

        public int getLargeSize() {
            return largeSize;
        }

        public int getShadowSize() {
            return shadowSize;
        }

        public int getOverlap() {
            return overlap;
        }

        public int getElementByteLength() {
            return elementByteLength;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public boolean isPlainPayload() {
            return plainPayload;
        }

        public boolean isMaskedPayload() {
            return maskedPayload;
        }

        public BaUpotBucketEvaluatorMode getEvaluatorMode() {
            return evaluatorMode;
        }

        public BaSsuIbltBiOutputDeliveryMode getDeliveryMode() {
            return deliveryMode;
        }

        public int getRepeatCount() {
            return repeatCount;
        }

        public int getWarmupCount() {
            return warmupCount;
        }

        public boolean isTsv() {
            return tsv;
        }

        public String getTsvFilePath() {
            return tsvFilePath;
        }

        private void applyProperties(Properties properties) {
            checkDuplicatePropertyGroups(properties);
            for (String key : properties.stringPropertyNames()) {
                if (isSizeKey(key.trim())) {
                    applyOption(key.trim(), properties.getProperty(key).trim());
                }
            }
            for (String key : properties.stringPropertyNames()) {
                if (!isSizeKey(key.trim())) {
                    applyOption(key.trim(), properties.getProperty(key).trim());
                }
            }
        }

        private void applyOption(String key, String value) {
            switch (key) {
                case "large":
                case "largeSize":
                case "n":
                case "nSize":
                    largeSize = Integer.parseInt(value);
                    break;
                case "largeLog":
                case "largeLogSetSize":
                case "nLog":
                case "nLogSetSize":
                    largeSize = logSize(value, "largeSize");
                    break;
                case "shadow":
                case "shadowSize":
                case "m":
                case "mSize":
                    shadowSize = Integer.parseInt(value);
                    break;
                case "shadowLog":
                case "shadowLogSetSize":
                case "mLog":
                case "mLogSetSize":
                    shadowSize = logSize(value, "shadowSize");
                    break;
                case "overlap":
                    overlap = Integer.parseInt(value);
                    break;
                case "overlapRate":
                    overlap = (int) Math.round(Double.parseDouble(value) * shadowSize);
                    break;
                case "elementBytes":
                case "elementByteLength":
                    elementByteLength = Integer.parseInt(value);
                    break;
                case "degree":
                    degree = Integer.parseInt(value);
                    break;
                case "alpha":
                    alpha = Double.parseDouble(value);
                    break;
                case "retry":
                case "retryCount":
                    retryCount = Integer.parseInt(value);
                    break;
                case "seed":
                    seed = Long.parseLong(value);
                    break;
                case "checkBits":
                    checkBits = Integer.parseInt(value);
                    break;
                case "tagBits":
                    tagBits = Integer.parseInt(value);
                    break;
                case "cot":
                case "cotNumPerBucket":
                    cotNumPerBucket = Integer.parseInt(value);
                    break;
                case "batch":
                case "onlineBatchSize":
                    onlineBatchSize = Integer.parseInt(value);
                    break;
                case "plainPayload":
                case "payload":
                    plainPayload = parseBoolean(value);
                    break;
                case "maskedPayload":
                case "wireMaskedPayload":
                case "masked":
                    maskedPayload = parseBoolean(value);
                    break;
                case "caseGate":
                case "liveCaseGate":
                    evaluatorMode = parseBoolean(value)
                        ? BaUpotBucketEvaluatorMode.CASE_GATE
                        : BaUpotBucketEvaluatorMode.IDEAL;
                    break;
                case "caseGateWireMasked":
                case "liveCaseGateWireMasked":
                    evaluatorMode = parseBoolean(value)
                        ? BaUpotBucketEvaluatorMode.CASE_GATE_WIRE_MASKED
                        : BaUpotBucketEvaluatorMode.IDEAL;
                    break;
                case "evaluator":
                case "evaluatorMode":
                    evaluatorMode = BaUpotBucketEvaluatorMode.valueOf(normalizeEnum(value));
                    break;
                case "biOutput":
                case "biOutputDelivery":
                case "twoWayOutput":
                    deliveryMode = parseBoolean(value)
                        ? BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT
                        : BaSsuIbltBiOutputDeliveryMode.SINGLE_OUTPUT;
                    break;
                case "singleOutput":
                    deliveryMode = parseBoolean(value)
                        ? BaSsuIbltBiOutputDeliveryMode.SINGLE_OUTPUT
                        : BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT;
                    break;
                case "twoPass":
                case "twoPassDelivery":
                    deliveryMode = parseBoolean(value)
                        ? BaSsuIbltBiOutputDeliveryMode.TWO_PASS
                        : BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT;
                    break;
                case "delivery":
                case "deliveryMode":
                case "biOutputDeliveryMode":
                    deliveryMode = BaSsuIbltBiOutputDeliveryMode.valueOf(normalizeEnum(value));
                    break;
                case "repeat":
                case "repeatCount":
                case "runs":
                    repeatCount = Integer.parseInt(value);
                    break;
                case "warmup":
                case "warmups":
                case "warmupCount":
                    warmupCount = Integer.parseInt(value);
                    break;
                case "tsv":
                    tsv = parseBoolean(value);
                    break;
                case "tsvFile":
                case "tsvOutput":
                    tsvFilePath = value;
                    tsv = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + key);
            }
        }

        private void validate() {
            if (largeSize <= 0) {
                throw new IllegalArgumentException("largeSize must be positive");
            }
            if (shadowSize <= 0) {
                throw new IllegalArgumentException("shadowSize must be positive");
            }
            if (overlap < 0 || overlap > Math.min(largeSize, shadowSize)) {
                throw new IllegalArgumentException("overlap must be in [0, min(largeSize, shadowSize)]");
            }
            if (elementByteLength <= 0) {
                throw new IllegalArgumentException("elementByteLength must be positive");
            }
            if (degree <= 0) {
                throw new IllegalArgumentException("degree must be positive");
            }
            if (!Double.isFinite(alpha) || !(alpha > 0.0)) {
                throw new IllegalArgumentException("alpha must be finite and positive");
            }
            if (retryCount <= 0) {
                throw new IllegalArgumentException("retryCount must be positive");
            }
            if (checkBits <= 0 || tagBits <= 0) {
                throw new IllegalArgumentException("checkBits and tagBits must be positive");
            }
            if (cotNumPerBucket <= 0 || onlineBatchSize <= 0) {
                throw new IllegalArgumentException("cotNumPerBucket and onlineBatchSize must be positive");
            }
            if (evaluatorMode == null) {
                throw new IllegalArgumentException("evaluatorMode must be non-null");
            }
            if (deliveryMode == null) {
                throw new IllegalArgumentException("deliveryMode must be non-null");
            }
            if (repeatCount <= 0) {
                throw new IllegalArgumentException("repeatCount must be positive");
            }
            if (warmupCount < 0) {
                throw new IllegalArgumentException("warmupCount must be non-negative");
            }
            if (tsvFilePath != null && tsvFilePath.isBlank()) {
                throw new IllegalArgumentException("tsvFilePath must be non-blank");
            }
        }

        private static void checkDuplicatePropertyGroups(Properties properties) {
            Set<String> seenGroups = new LinkedHashSet<>();
            for (String key : properties.stringPropertyNames()) {
                String group = canonicalPropertyGroup(key.trim());
                if (group != null && !seenGroups.add(group)) {
                    throw new IllegalArgumentException("Duplicate BA-SSU-IBLT config group: " + group);
                }
            }
        }

        private static String canonicalPropertyGroup(String key) {
            switch (key) {
                case "large":
                case "largeSize":
                case "n":
                case "nSize":
                case "largeLog":
                case "largeLogSetSize":
                case "nLog":
                case "nLogSetSize":
                    return "largeSize";
                case "shadow":
                case "shadowSize":
                case "m":
                case "mSize":
                case "shadowLog":
                case "shadowLogSetSize":
                case "mLog":
                case "mLogSetSize":
                    return "shadowSize";
                case "overlap":
                case "overlapRate":
                    return "overlap";
                case "elementBytes":
                case "elementByteLength":
                    return "elementByteLength";
                case "retry":
                case "retryCount":
                    return "retryCount";
                case "cot":
                case "cotNumPerBucket":
                    return "cotNumPerBucket";
                case "batch":
                case "onlineBatchSize":
                    return "onlineBatchSize";
                case "plainPayload":
                case "payload":
                    return "plainPayload";
                case "maskedPayload":
                case "wireMaskedPayload":
                case "masked":
                    return "maskedPayload";
                case "caseGate":
                case "liveCaseGate":
                case "caseGateWireMasked":
                case "liveCaseGateWireMasked":
                case "evaluator":
                case "evaluatorMode":
                    return "evaluatorMode";
                case "biOutput":
                case "biOutputDelivery":
                case "twoWayOutput":
                case "singleOutput":
                case "twoPass":
                case "twoPassDelivery":
                case "delivery":
                case "deliveryMode":
                case "biOutputDeliveryMode":
                    return "deliveryMode";
                case "repeat":
                case "repeatCount":
                case "runs":
                    return "repeatCount";
                case "warmup":
                case "warmups":
                case "warmupCount":
                    return "warmupCount";
                case "tsvFile":
                case "tsvOutput":
                    return "tsvFilePath";
                default:
                    return key;
            }
        }

        private static Properties loadProperties(String path) throws IOException {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(Path.of(path))) {
                properties.load(inputStream);
            }
            return properties;
        }

        private static boolean isConfigPathKey(String key) {
            return "conf".equals(key) || "config".equals(key) || "properties".equals(key);
        }

        private static boolean isSizeKey(String key) {
            switch (key) {
                case "large":
                case "largeSize":
                case "n":
                case "nSize":
                case "largeLog":
                case "largeLogSetSize":
                case "nLog":
                case "nLogSetSize":
                case "shadow":
                case "shadowSize":
                case "m":
                case "mSize":
                case "shadowLog":
                case "shadowLogSetSize":
                case "mLog":
                case "mLogSetSize":
                    return true;
                default:
                    return false;
            }
        }

        private static int logSize(String value, String name) {
            int logValue = Integer.parseInt(value);
            if (logValue < 0 || logValue >= Integer.SIZE - 1) {
                throw new IllegalArgumentException(name + " log must be in [0, 30]");
            }
            return 1 << logValue;
        }

        private static boolean parseBoolean(String value) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw new IllegalArgumentException("Boolean value must be true or false: " + value);
        }

        private static String normalizeEnum(String value) {
            return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
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
         * plain benchmark.
         */
        private final BaSsuIbltPlainBenchmark.Result plainBenchmarkResult;
        /**
         * BA-UPOT two-party benchmark.
         */
        private final BaUpotTwoPartyBenchmark.Result upotResult;
        /**
         * reverse BA-UPOT delivery benchmark for bi-output accounting.
         */
        private final BaUpotTwoPartyBenchmark.Result reverseUpotResult;
        /**
         * plain payload-bound transport benchmark.
         */
        private final BaUnionPeelOtResult plainPayloadResult;
        /**
         * wire-masked BA-UnionPeel-OT bridge benchmark.
         */
        private final BaUnionPeelOtResult maskedPayloadResult;

        Result(Config config, BaSsuIbltPlainBenchmark.Result plainBenchmarkResult,
               BaUpotTwoPartyBenchmark.Result upotResult,
               BaUpotTwoPartyBenchmark.Result reverseUpotResult,
               BaUnionPeelOtResult plainPayloadResult,
               BaUnionPeelOtResult maskedPayloadResult) {
            this.config = config;
            this.plainBenchmarkResult = plainBenchmarkResult;
            this.upotResult = upotResult;
            this.reverseUpotResult = reverseUpotResult;
            this.plainPayloadResult = plainPayloadResult;
            this.maskedPayloadResult = maskedPayloadResult;
        }

        public BaSsuIbltPlainBenchmark.Result getPlainBenchmarkResult() {
            return plainBenchmarkResult;
        }

        public BaUpotTwoPartyBenchmark.Result getUpotResult() {
            return upotResult;
        }

        public BaUpotTwoPartyBenchmark.Result getReverseUpotResult() {
            return reverseUpotResult;
        }

        BaUnionPeelOtResult getPlainPayloadResult() {
            return plainPayloadResult;
        }

        BaUnionPeelOtResult getMaskedPayloadResult() {
            return maskedPayloadResult;
        }

        public boolean isBiOutputDelivery() {
            return config.deliveryMode != BaSsuIbltBiOutputDeliveryMode.SINGLE_OUTPUT;
        }

        public BaSsuIbltBiOutputDeliveryMode getDeliveryMode() {
            return config.deliveryMode;
        }

        public boolean isSuccess() {
            return plainBenchmarkResult.getPlainResult().isSuccess() && upotResult != null
                && (config.deliveryMode != BaSsuIbltBiOutputDeliveryMode.TWO_PASS || reverseUpotResult != null)
                && (plainPayloadResult == null || plainPayloadResult.isChecksumEqual())
                && (maskedPayloadResult == null || maskedPayloadResult.isChecksumEqual());
        }

        public long getTotalTimeNanos() {
            if (upotResult == null) {
                return plainBenchmarkResult.getPlainTimeNanos();
            }
            return getOfflineTimeNanos() + getOnlineTimeNanos();
        }

        public long getOfflineTimeNanos() {
            if (upotResult == null) {
                return 0L;
            }
            long timeNanos = upotResult.getOfflineTimeNanos();
            if (reverseUpotResult != null) {
                timeNanos += reverseUpotResult.getOfflineTimeNanos();
            }
            return timeNanos;
        }

        public long getOnlineTimeNanos() {
            if (upotResult == null) {
                return plainBenchmarkResult.getPlainTimeNanos();
            }
            long timeNanos = plainBenchmarkResult.getPlainTimeNanos() + upotResult.getOnlineTimeNanos();
            if (reverseUpotResult != null) {
                timeNanos += reverseUpotResult.getOnlineTimeNanos();
            }
            return timeNanos;
        }

        public long getTotalSendBytes() {
            if (upotResult == null) {
                return 0L;
            }
            return getOfflineSendBytes() + getOnlineSendBytes();
        }

        public long getOfflineSendBytes() {
            if (upotResult == null) {
                return 0L;
            }
            long sendBytes = upotResult.getOfflineSendBytes();
            if (reverseUpotResult != null) {
                sendBytes += reverseUpotResult.getOfflineSendBytes();
            }
            return sendBytes;
        }

        public long getOnlineSendBytes() {
            if (upotResult == null) {
                return 0L;
            }
            long sendBytes = upotResult.getOnlineSendBytes();
            if (reverseUpotResult != null) {
                sendBytes += reverseUpotResult.getOnlineSendBytes();
            }
            return sendBytes;
        }

        public long getCostedProtocolTotalTimeNanos() {
            return getCostedProtocolOfflineTimeNanos() + getCostedProtocolOnlineTimeNanos();
        }

        public long getCostedProtocolOfflineTimeNanos() {
            return getOfflineTimeNanos();
        }

        public long getCostedProtocolOnlineTimeNanos() {
            return getOnlineTimeNanos();
        }

        public long getCostedProtocolOfflineSendBytes() {
            return getOfflineSendBytes();
        }

        public long getCostedProtocolOnlineSendBytes() {
            return getOnlineSendBytes();
        }

        public long getForwardSendBytes() {
            return upotResult == null ? 0L : upotResult.getOfflineSendBytes() + upotResult.getOnlineSendBytes();
        }

        public long getReverseSendBytes() {
            return reverseUpotResult == null
                ? 0L
                : reverseUpotResult.getOfflineSendBytes() + reverseUpotResult.getOnlineSendBytes();
        }

        public long getSignedSourceSplitSendBytes() {
            return config.deliveryMode == BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT
                ? getForwardSendBytes()
                : 0L;
        }

        public long getPlainPayloadSendBytes() {
            return plainPayloadResult == null ? 0L : plainPayloadResult.getOnlineSendBytes();
        }

        public long getMaskedPayloadSendBytes() {
            return maskedPayloadResult == null ? 0L : maskedPayloadResult.getOnlineSendBytes();
        }

        public long getBridgeTotalTimeNanos() {
            long timeNanos = 0L;
            if (plainPayloadResult != null) {
                timeNanos += plainPayloadResult.getOfflineTimeNanos() + plainPayloadResult.getOnlineTimeNanos();
            }
            if (maskedPayloadResult != null) {
                timeNanos += maskedPayloadResult.getOfflineTimeNanos() + maskedPayloadResult.getOnlineTimeNanos();
            }
            return timeNanos;
        }

        public long getBridgeTotalSendBytes() {
            long sendBytes = 0L;
            if (plainPayloadResult != null) {
                sendBytes += plainPayloadResult.getOfflineSendBytes() + plainPayloadResult.getOnlineSendBytes();
            }
            if (maskedPayloadResult != null) {
                sendBytes += maskedPayloadResult.getOfflineSendBytes() + maskedPayloadResult.getOnlineSendBytes();
            }
            return sendBytes;
        }

        public BaUpotCaseGateCostEstimator.Estimate getCaseGateEstimate() {
            return BaUpotCaseGateCostEstimator.estimate(
                upotResult == null
                    ? plainBenchmarkResult.getPlainResult().getScheduledBucketCount()
                    : upotResult.getConfig().getBucketNum()
            );
        }

        public static String tsvHeader() {
            return String.join("\t",
                "runIndex",
                "large",
                "shadow",
                "overlap",
                "degree",
                "alpha",
                "retry",
                "evaluator",
                "delivery",
                "success",
                "unionSize",
                "anchorOnly",
                "shadowOnly",
                "sharedSingleton",
                "dynamicPeelBuckets",
                "fixedDeliveryBuckets",
                "recommendedCheckBits",
                "plainPeelMs",
                "offlineMs",
                "onlineMs",
                "totalMs",
                "costedProtocolOfflineBytes",
                "costedProtocolOnlineBytes",
                "costedProtocolTotalBytes",
                "plainPayloadBytes",
                "maskedPayloadBytes",
                "payloadBridgeBytes",
                "grandTotalBytes",
                "securityNotice"
            );
        }

        public String toTsvLine(int runIndex) {
            BaSsuIbltPlainResult plainResult = plainBenchmarkResult.getPlainResult();
            long fixedDeliveryBuckets = upotResult == null ? 0L : upotResult.getConfig().getBucketNum();
            long grandTotalBytes = getTotalSendBytes() + getBridgeTotalSendBytes();
            return String.format(
                Locale.ROOT,
                "%d\t%d\t%d\t%d\t%d\t%.4f\t%d\t%s\t%s\t%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f"
                    + "\t%.3f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%s",
                runIndex,
                config.largeSize,
                config.shadowSize,
                config.overlap,
                config.degree,
                config.alpha,
                config.retryCount,
                config.evaluatorMode,
                config.deliveryMode,
                isSuccess(),
                plainResult.getLeftUnion().size(),
                plainResult.getSignedPeelOutput().getAnchorOnlyCount(),
                plainResult.getSignedPeelOutput().getShadowOnlyCount(),
                plainResult.getSharedSingletonCount(),
                plainResult.getScheduledBucketCount(),
                fixedDeliveryBuckets,
                plainResult.getRecommendedCheckBits(),
                plainBenchmarkResult.getPlainTimeNanos() / 1_000_000.0,
                getCostedProtocolOfflineTimeNanos() / 1_000_000.0,
                getCostedProtocolOnlineTimeNanos() / 1_000_000.0,
                getCostedProtocolTotalTimeNanos() / 1_000_000.0,
                getCostedProtocolOfflineSendBytes(),
                getCostedProtocolOnlineSendBytes(),
                getTotalSendBytes(),
                getPlainPayloadSendBytes(),
                getMaskedPayloadSendBytes(),
                getBridgeTotalSendBytes(),
                grandTotalBytes,
                SECURITY_NOTICE_ID
            );
        }

        public String toDisplayString() {
            if (upotResult == null) {
                return String.format(
                    Locale.ROOT,
                    "BA-SSU-IBLT costed two-party benchmark%n"
                        + "securityNotice=%s%n"
                        + "large=%d, shadow=%d, overlap=%d, degree=%d, alpha=%.2f, retry=%d, evaluator=%s, biOutput=%s%n"
                        + "success=false, plainPeelTime=%.3f ms",
                    SECURITY_NOTICE,
                    config.largeSize,
                    config.shadowSize,
                    config.overlap,
                    config.degree,
                    config.alpha,
                    config.retryCount,
                    config.evaluatorMode,
                    isBiOutputDelivery(),
                    plainBenchmarkResult.getPlainTimeNanos() / 1_000_000.0
                );
            }
            BaSsuIbltPlainResult plainResult = plainBenchmarkResult.getPlainResult();
            BaUpotCaseGateCostEstimator.Estimate gateEstimate = getCaseGateEstimate();
            String base = String.format(
                Locale.ROOT,
                "BA-SSU-IBLT costed two-party benchmark%n"
                    + "securityNotice=%s%n"
                    + "large=%d, shadow=%d, overlap=%d, degree=%d, alpha=%.2f, retry=%d, evaluator=%s, biOutput=%s%n"
                    + "delivery=%s%n"
                    + "success=%s, unionSize=%d, debugSelectedRetry=%d, debugSelectedRounds=%d%n"
                    + "anchorOnly=%d, shadowOnly=%d, sharedSingleton=%d%n"
                    + "dynamicPeelBuckets=%d, fixedDeliveryBuckets=%d, recommendedCheckBits=%d%n"
                    + "caseGateAnd=%d, caseGateXor=%d, caseGateNot=%d%n"
                    + "plainPeelTime=%.3f ms%n"
                    + "realUpotOfflineTime=%.3f ms, realUpotOnlineTime=%.3f ms%n"
                    + "standaloneOfflineTime=%.3f ms, standaloneOnlineTime=%.3f ms, standaloneTotalTime=%.3f ms%n"
                    + "standaloneOfflineBytes=%d, standaloneOnlineBytes=%d, standaloneTotalBytes=%d%n"
                    + "costedProtocolOfflineTime=%.3f ms, costedProtocolOnlineTime=%.3f ms, "
                    + "costedProtocolTotalTime=%.3f ms%n"
                    + "costedProtocolOfflineBytes=%d, costedProtocolOnlineBytes=%d",
                SECURITY_NOTICE,
                config.largeSize,
                config.shadowSize,
                config.overlap,
                config.degree,
                config.alpha,
                config.retryCount,
                config.evaluatorMode,
                isBiOutputDelivery(),
                config.deliveryMode,
                isSuccess(),
                plainResult.getLeftUnion().size(),
                plainResult.getSelectedRetryIndex(),
                plainResult.getSelectedRoundCount(),
                plainResult.getSignedPeelOutput().getAnchorOnlyCount(),
                plainResult.getSignedPeelOutput().getShadowOnlyCount(),
                plainResult.getSharedSingletonCount(),
                plainResult.getScheduledBucketCount(),
                upotResult.getConfig().getBucketNum(),
                plainResult.getRecommendedCheckBits(),
                gateEstimate.getAndGateCount(),
                gateEstimate.getXorGateCount(),
                gateEstimate.getNotGateCount(),
                plainBenchmarkResult.getPlainTimeNanos() / 1_000_000.0,
                upotResult.getOfflineTimeNanos() / 1_000_000.0,
                upotResult.getOnlineTimeNanos() / 1_000_000.0,
                getOfflineTimeNanos() / 1_000_000.0,
                getOnlineTimeNanos() / 1_000_000.0,
                getTotalTimeNanos() / 1_000_000.0,
                getOfflineSendBytes(),
                getOnlineSendBytes(),
                getTotalSendBytes(),
                getCostedProtocolOfflineTimeNanos() / 1_000_000.0,
                getCostedProtocolOnlineTimeNanos() / 1_000_000.0,
                getCostedProtocolTotalTimeNanos() / 1_000_000.0,
                getCostedProtocolOfflineSendBytes(),
                getCostedProtocolOnlineSendBytes()
            );
            String detail = base;
            if (plainPayloadResult != null) {
                detail += String.format(
                    Locale.ROOT,
                    "%nplainPayloadBridgeOnlineTime=%.3f ms, plainPayloadBridgeOnlineBytes=%d, "
                        + "plainPayloadChecksumEqual=%s",
                    plainPayloadResult.getOnlineTimeNanos() / 1_000_000.0,
                    plainPayloadResult.getOnlineSendBytes(),
                    plainPayloadResult.isChecksumEqual()
                );
            }
            if (maskedPayloadResult != null) {
                detail += String.format(
                    Locale.ROOT,
                    "%nmaskedPayloadBridgeOnlineTime=%.3f ms, maskedPayloadBridgeOnlineBytes=%d, "
                        + "maskedPayloadChecksumEqual=%s",
                    maskedPayloadResult.getOnlineTimeNanos() / 1_000_000.0,
                    maskedPayloadResult.getOnlineSendBytes(),
                    maskedPayloadResult.isChecksumEqual()
                );
            }
            if (reverseUpotResult != null) {
                detail += String.format(
                    Locale.ROOT,
                    "%nreverseUpotOfflineTime=%.3f ms, reverseUpotOnlineTime=%.3f ms"
                        + "%nforwardUpotBytes=%d, reverseUpotBytes=%d, biOutputTotalBytes=%d",
                    reverseUpotResult.getOfflineTimeNanos() / 1_000_000.0,
                    reverseUpotResult.getOnlineTimeNanos() / 1_000_000.0,
                    getForwardSendBytes(),
                    getReverseSendBytes(),
                    getTotalSendBytes()
                );
            } else if (config.deliveryMode == BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT) {
                detail += String.format(
                    Locale.ROOT,
                    "%nsignedSourceSplitBytes=%d, signedSourceSplitOnlineTime=%.3f ms",
                    getSignedSourceSplitSendBytes(),
                    upotResult.getOnlineTimeNanos() / 1_000_000.0
                );
            }
            if (plainBenchmarkResult.hasCaseGateStats()) {
                BaUpotCaseGateCircuit.GateStats stats = plainBenchmarkResult.getCaseGateStats();
                detail += String.format(
                    Locale.ROOT,
                    "%nliveCaseGateBuckets=%d, liveCaseGateAnd=%d, liveCaseGateXor=%d, liveCaseGateNot=%d"
                        + "%nlivePayloadBytes=%d, livePayloadCapsuleBytes=%d",
                    plainBenchmarkResult.getCaseGateBucketCount(),
                    stats.getAndGateCount(),
                    stats.getXorGateCount(),
                    stats.getNotGateCount(),
                    plainBenchmarkResult.getLivePayloadBytes(),
                    plainBenchmarkResult.getLivePayloadCapsuleByteLength()
                );
            }
            if (plainPayloadResult != null || maskedPayloadResult != null) {
                detail += String.format(
                    Locale.ROOT,
                    "%npayloadBridgeTotalTime=%.3f ms, payloadBridgeTotalBytes=%d",
                    getBridgeTotalTimeNanos() / 1_000_000.0,
                    getBridgeTotalSendBytes()
                );
            }
            return detail;
        }
    }

    /**
     * Repeated benchmark result.
     */
    public static class RepeatedResult {
        /**
         * config.
         */
        private final Config config;
        /**
         * measured results.
         */
        private final List<Result> results;

        RepeatedResult(Config config, List<Result> results) {
            if (config == null) {
                throw new IllegalArgumentException("config must be non-null");
            }
            if (results == null || results.isEmpty()) {
                throw new IllegalArgumentException("results must be non-empty");
            }
            this.config = config;
            this.results = List.copyOf(results);
        }

        public List<Result> getResults() {
            return results;
        }

        public boolean isSuccess() {
            for (Result result : results) {
                if (!result.isSuccess()) {
                    return false;
                }
            }
            return true;
        }

        public String toTsvString() {
            StringBuilder stringBuilder = new StringBuilder(Result.tsvHeader());
            for (int index = 0; index < results.size(); index++) {
                stringBuilder.append(System.lineSeparator()).append(results.get(index).toTsvLine(index));
            }
            return stringBuilder.toString();
        }

        public String toDisplayString() {
            MetricStats offlineTime = stats(Result::getCostedProtocolOfflineTimeNanos);
            MetricStats onlineTime = stats(Result::getCostedProtocolOnlineTimeNanos);
            MetricStats totalTime = stats(Result::getCostedProtocolTotalTimeNanos);
            MetricStats offlineBytes = stats(Result::getCostedProtocolOfflineSendBytes);
            MetricStats onlineBytes = stats(Result::getCostedProtocolOnlineSendBytes);
            MetricStats totalBytes = stats(Result::getTotalSendBytes);
            MetricStats payloadBridgeBytes = stats(Result::getBridgeTotalSendBytes);
            MetricStats grandTotalBytes = stats(result -> result.getTotalSendBytes() + result.getBridgeTotalSendBytes());
            Result firstResult = results.get(0);
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT repeated costed two-party benchmark%n"
                    + "securityNotice=%s%n"
                    + "large=%d, shadow=%d, overlap=%d, degree=%d, alpha=%.2f, retry=%d, evaluator=%s, delivery=%s%n"
                    + "repeat=%d, warmup=%d, success=%s%n"
                    + "costedProtocolOfflineTimeMs=%s%n"
                    + "costedProtocolOnlineTimeMs=%s%n"
                    + "costedProtocolTotalTimeMs=%s%n"
                    + "costedProtocolOfflineBytes=%s%n"
                    + "costedProtocolOnlineBytes=%s%n"
                    + "costedProtocolTotalBytes=%s%n"
                    + "payloadBridgeBytes=%s%n"
                    + "grandTotalBytes=%s%n"
                    + "fixedDeliveryBuckets=%d, dynamicPeelBuckets=%d, recommendedCheckBits=%d",
                SECURITY_NOTICE,
                config.largeSize,
                config.shadowSize,
                config.overlap,
                config.degree,
                config.alpha,
                config.retryCount,
                config.evaluatorMode,
                config.deliveryMode,
                config.repeatCount,
                config.warmupCount,
                isSuccess(),
                formatTimeStats(offlineTime),
                formatTimeStats(onlineTime),
                formatTimeStats(totalTime),
                formatByteStats(offlineBytes),
                formatByteStats(onlineBytes),
                formatByteStats(totalBytes),
                formatByteStats(payloadBridgeBytes),
                formatByteStats(grandTotalBytes),
                firstResult.getUpotResult() == null ? 0 : firstResult.getUpotResult().getConfig().getBucketNum(),
                firstResult.getPlainBenchmarkResult().getPlainResult().getScheduledBucketCount(),
                firstResult.getPlainBenchmarkResult().getPlainResult().getRecommendedCheckBits()
            );
        }

        private MetricStats stats(ToLongFunction<Result> metric) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            double mean = 0.0;
            int count = 0;
            for (Result result : results) {
                long value = metric.applyAsLong(result);
                min = Math.min(min, value);
                max = Math.max(max, value);
                count++;
                mean += (value - mean) / count;
            }
            return new MetricStats(min, mean, max);
        }

        private static String formatTimeStats(MetricStats stats) {
            return String.format(
                Locale.ROOT, "mean=%.3f,min=%.3f,max=%.3f",
                stats.mean / 1_000_000.0, stats.min / 1_000_000.0, stats.max / 1_000_000.0
            );
        }

        private static String formatByteStats(MetricStats stats) {
            return String.format(Locale.ROOT, "mean=%.1f,min=%d,max=%d", stats.mean, stats.min, stats.max);
        }
    }

    /**
     * Long metric stats.
     */
    private static class MetricStats {
        /**
         * min.
         */
        private final long min;
        /**
         * mean.
         */
        private final double mean;
        /**
         * max.
         */
        private final long max;

        MetricStats(long min, double mean, long max) {
            this.min = min;
            this.mean = mean;
            this.max = max;
        }
    }

    /**
     * benchmark set pair.
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
