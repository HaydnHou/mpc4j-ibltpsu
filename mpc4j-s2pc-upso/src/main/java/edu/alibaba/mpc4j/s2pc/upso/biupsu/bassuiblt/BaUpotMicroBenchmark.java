package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Specialized BA-UPOT fixed-bucket micro-benchmark.
 *
 * <p>This benchmark isolates the fixed-shape per-bucket work of the specialized FUnionPeel-style BA-UPOT route. It is
 * not a generic 2PC benchmark. The portable implementation models the real hot path with SHA-256 based OPRF tag,
 * check-token, LCOT/case-mask, and payload-mask derivations while keeping every scheduled bucket the same external
 * shape.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotMicroBenchmark {
    /**
     * block byte length.
     */
    public static final int BLOCK_BYTE_LENGTH = 16;
    /**
     * fixed case count.
     */
    public static final int DEFAULT_CASE_NUM = 4;
    /**
     * default LCOT choices per bucket.
     */
    public static final int DEFAULT_LCOT_NUM_PER_BUCKET = 2;

    /**
     * hash domain: tag.
     */
    private static final byte DOMAIN_TAG = 0x01;
    /**
     * hash domain: check.
     */
    private static final byte DOMAIN_CHECK = 0x02;
    /**
     * hash domain: case.
     */
    private static final byte DOMAIN_CASE = 0x03;
    /**
     * hash domain: payload.
     */
    private static final byte DOMAIN_PAYLOAD = 0x04;
    /**
     * hash domain: capsule authentication.
     */
    private static final byte DOMAIN_AUTH = 0x05;

    /**
     * private constructor.
     */
    private BaUpotMicroBenchmark() {
        // empty
    }

    /**
     * Runs the benchmark with command line arguments.
     *
     * <p>Example:
     * {@code buckets=1089925 warmup=1 measure=3 elementBytes=8 checkBits=182 tagBits=182}</p>
     *
     * @param args key=value arguments.
     */
    public static void main(String[] args) {
        Config config = Config.fromArgs(args);
        Result result = run(config);
        System.out.println(result.toDisplayString());
    }

    /**
     * Runs the micro-benchmark.
     *
     * @param config benchmark config.
     * @return benchmark result.
     */
    public static Result run(Config config) {
        config.validate();
        Worker worker = new Worker(config);
        long checksum = 0L;
        for (int round = 0; round < config.warmupRounds; round++) {
            checksum ^= worker.runRound(round);
        }
        long start = System.nanoTime();
        for (int round = 0; round < config.measureRounds; round++) {
            checksum ^= worker.runRound(config.warmupRounds + round);
        }
        long elapsedNanos = System.nanoTime() - start;
        long measuredBuckets = (long) config.bucketNum * config.measureRounds;
        long hashCalls = measuredBuckets * config.hashCallsPerBucket();
        return new Result(config, measuredBuckets, elapsedNanos, hashCalls, checksum);
    }

    /**
     * Benchmark config.
     */
    public static class Config {
        /**
         * bucket count.
         */
        private int bucketNum;
        /**
         * warmup rounds.
         */
        private int warmupRounds;
        /**
         * measured rounds.
         */
        private int measureRounds;
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * check bits.
         */
        private int checkBits;
        /**
         * tag bits.
         */
        private int tagBits;
        /**
         * case number.
         */
        private int caseNum;
        /**
         * LCOT number per bucket.
         */
        private int lcotNumPerBucket;
        /**
         * seed.
         */
        private long seed;

        /**
         * Creates default config.
         */
        public Config() {
            bucketNum = 1_089_925;
            warmupRounds = 1;
            measureRounds = 3;
            elementByteLength = Long.BYTES;
            checkBits = 182;
            tagBits = 182;
            caseNum = DEFAULT_CASE_NUM;
            lcotNumPerBucket = DEFAULT_LCOT_NUM_PER_BUCKET;
            seed = 0xBA551B17A5A5A5A5L;
        }

        /**
         * Parses key=value arguments.
         *
         * @param args key=value arguments.
         * @return config.
         */
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
                    case "buckets":
                    case "bucketNum":
                        config.setBucketNum(Integer.parseInt(value));
                        break;
                    case "warmup":
                    case "warmupRounds":
                        config.setWarmupRounds(Integer.parseInt(value));
                        break;
                    case "measure":
                    case "measureRounds":
                        config.setMeasureRounds(Integer.parseInt(value));
                        break;
                    case "elementBytes":
                    case "elementByteLength":
                        config.setElementByteLength(Integer.parseInt(value));
                        break;
                    case "checkBits":
                        config.setCheckBits(Integer.parseInt(value));
                        break;
                    case "tagBits":
                        config.setTagBits(Integer.parseInt(value));
                        break;
                    case "caseNum":
                        config.setCaseNum(Integer.parseInt(value));
                        break;
                    case "lcot":
                    case "lcotNumPerBucket":
                        config.setLcotNumPerBucket(Integer.parseInt(value));
                        break;
                    case "seed":
                        config.setSeed(Long.decode(value));
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }
            return config;
        }

        public int getBucketNum() {
            return bucketNum;
        }

        public Config setBucketNum(int bucketNum) {
            this.bucketNum = bucketNum;
            return this;
        }

        public int getWarmupRounds() {
            return warmupRounds;
        }

        public Config setWarmupRounds(int warmupRounds) {
            this.warmupRounds = warmupRounds;
            return this;
        }

        public int getMeasureRounds() {
            return measureRounds;
        }

        public Config setMeasureRounds(int measureRounds) {
            this.measureRounds = measureRounds;
            return this;
        }

        public int getElementByteLength() {
            return elementByteLength;
        }

        public Config setElementByteLength(int elementByteLength) {
            this.elementByteLength = elementByteLength;
            return this;
        }

        public int getCheckBits() {
            return checkBits;
        }

        public Config setCheckBits(int checkBits) {
            this.checkBits = checkBits;
            return this;
        }

        public int getTagBits() {
            return tagBits;
        }

        public Config setTagBits(int tagBits) {
            this.tagBits = tagBits;
            return this;
        }

        public int getCaseNum() {
            return caseNum;
        }

        public Config setCaseNum(int caseNum) {
            this.caseNum = caseNum;
            return this;
        }

        public int getLcotNumPerBucket() {
            return lcotNumPerBucket;
        }

        public Config setLcotNumPerBucket(int lcotNumPerBucket) {
            this.lcotNumPerBucket = lcotNumPerBucket;
            return this;
        }

        public long getSeed() {
            return seed;
        }

        public Config setSeed(long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Returns check byte length.
         *
         * @return check byte length.
         */
        public int checkByteLength() {
            return (checkBits + Byte.SIZE - 1) / Byte.SIZE;
        }

        /**
         * Returns tag byte length.
         *
         * @return tag byte length.
         */
        public int tagByteLength() {
            return (tagBits + Byte.SIZE - 1) / Byte.SIZE;
        }

        /**
         * Returns hash calls per bucket in this portable fixed-shape implementation.
         *
         * @return hash calls per bucket.
         */
        public int hashCallsPerBucket() {
            return 2 + 2 + caseNum + 2 + 1;
        }

        /**
         * Returns fixed online bytes per bucket.
         *
         * @return online bytes per bucket.
         */
        public long onlineBytesPerBucket() {
            return 2L * elementByteLength + 2L * checkByteLength() + 2L * BLOCK_BYTE_LENGTH;
        }

        /**
         * Returns silent-COT correlation bytes consumed per bucket.
         *
         * @return offline bytes per bucket.
         */
        public long offlineCotBytesPerBucket() {
            return (long) lcotNumPerBucket * BLOCK_BYTE_LENGTH;
        }

        private void validate() {
            if (bucketNum <= 0) {
                throw new IllegalArgumentException("bucketNum must be positive");
            }
            if (warmupRounds < 0) {
                throw new IllegalArgumentException("warmupRounds must be non-negative");
            }
            if (measureRounds <= 0) {
                throw new IllegalArgumentException("measureRounds must be positive");
            }
            if (elementByteLength <= 0 || elementByteLength > 32) {
                throw new IllegalArgumentException("elementByteLength must be in range [1, 32]");
            }
            if (checkBits <= 0 || checkByteLength() > 32) {
                throw new IllegalArgumentException("checkBits must fit one SHA-256 digest");
            }
            if (tagBits <= 0 || tagByteLength() > 32) {
                throw new IllegalArgumentException("tagBits must fit one SHA-256 digest");
            }
            if (caseNum <= 0) {
                throw new IllegalArgumentException("caseNum must be positive");
            }
            if (lcotNumPerBucket <= 0) {
                throw new IllegalArgumentException("lcotNumPerBucket must be positive");
            }
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
         * measured bucket count.
         */
        private final long measuredBuckets;
        /**
         * elapsed nanos.
         */
        private final long elapsedNanos;
        /**
         * hash calls.
         */
        private final long hashCalls;
        /**
         * checksum.
         */
        private final long checksum;

        private Result(Config config, long measuredBuckets, long elapsedNanos, long hashCalls, long checksum) {
            this.config = config;
            this.measuredBuckets = measuredBuckets;
            this.elapsedNanos = elapsedNanos;
            this.hashCalls = hashCalls;
            this.checksum = checksum;
        }

        public Config getConfig() {
            return config;
        }

        public long getMeasuredBuckets() {
            return measuredBuckets;
        }

        public long getElapsedNanos() {
            return elapsedNanos;
        }

        public long getHashCalls() {
            return hashCalls;
        }

        public long getChecksum() {
            return checksum;
        }

        public double getNanosPerBucket() {
            return ((double) elapsedNanos) / measuredBuckets;
        }

        public double getNanosPerHashCall() {
            return ((double) elapsedNanos) / hashCalls;
        }

        public double getBucketsPerSecond() {
            return 1_000_000_000.0 / getNanosPerBucket();
        }

        public long getOnlineBytesPerBucket() {
            return config.onlineBytesPerBucket();
        }

        public long getOfflineCotBytesPerBucket() {
            return config.offlineCotBytesPerBucket();
        }

        public long getTotalOnlineBytes() {
            return measuredBuckets * getOnlineBytesPerBucket();
        }

        public long getTotalOfflineCotBytes() {
            return measuredBuckets * getOfflineCotBytesPerBucket();
        }

        /**
         * Returns a display string.
         *
         * @return display string.
         */
        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-UPOT specialized fixed-bucket micro-benchmark%n"
                    + "bucketNum=%d, measuredBuckets=%d, warmupRounds=%d, measureRounds=%d%n"
                    + "elementBytes=%d, checkBits=%d, tagBits=%d, caseNum=%d, lcotPerBucket=%d%n"
                    + "hashCallsPerBucket=%d, measuredHashCalls=%d%n"
                    + "elapsed=%.3f ms, nanosPerBucket=%.2f, nanosPerHash=%.2f, bucketsPerSecond=%.2f%n"
                    + "onlineBytesPerBucket=%d, offlineCotBytesPerBucket=%d%n"
                    + "totalOnlineBytes=%d, totalOfflineCotBytes=%d%n"
                    + "checksum=%016x",
                config.getBucketNum(),
                measuredBuckets,
                config.getWarmupRounds(),
                config.getMeasureRounds(),
                config.getElementByteLength(),
                config.getCheckBits(),
                config.getTagBits(),
                config.getCaseNum(),
                config.getLcotNumPerBucket(),
                config.hashCallsPerBucket(),
                hashCalls,
                elapsedNanos / 1_000_000.0,
                getNanosPerBucket(),
                getNanosPerHashCall(),
                getBucketsPerSecond(),
                getOnlineBytesPerBucket(),
                getOfflineCotBytesPerBucket(),
                getTotalOnlineBytes(),
                getTotalOfflineCotBytes(),
                checksum
            );
        }
    }

    /**
     * Benchmark worker.
     */
    private static class Worker {
        /**
         * config.
         */
        private final Config config;
        /**
         * digest.
         */
        private final MessageDigest digest;
        /**
         * index buffer.
         */
        private final byte[] indexBuffer;
        /**
         * element A.
         */
        private final byte[] elementA;
        /**
         * element B.
         */
        private final byte[] elementB;
        /**
         * tag A.
         */
        private final byte[] tagA;
        /**
         * tag B.
         */
        private final byte[] tagB;
        /**
         * check A.
         */
        private final byte[] checkA;
        /**
         * check B.
         */
        private final byte[] checkB;
        /**
         * full digest.
         */
        private final byte[] fullDigest;
        /**
         * payload mask.
         */
        private final byte[] payloadMask;
        /**
         * payload output.
         */
        private final byte[] payloadOutput;

        private Worker(Config config) {
            this.config = config;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is unavailable", e);
            }
            indexBuffer = new byte[Long.BYTES];
            elementA = new byte[config.getElementByteLength()];
            elementB = new byte[config.getElementByteLength()];
            tagA = new byte[config.tagByteLength()];
            tagB = new byte[config.tagByteLength()];
            checkA = new byte[config.checkByteLength()];
            checkB = new byte[config.checkByteLength()];
            fullDigest = new byte[32];
            payloadMask = new byte[config.getElementByteLength()];
            payloadOutput = new byte[config.getElementByteLength()];
        }

        private long runRound(int round) {
            long checksum = mix64(config.getSeed() ^ round);
            for (int bucketIndex = 0; bucketIndex < config.getBucketNum(); bucketIndex++) {
                checksum ^= processBucket(bucketIndex, round, checksum);
            }
            return checksum;
        }

        private long processBucket(int bucketIndex, int round, long runningChecksum) {
            fillElement(elementA, mix64(config.getSeed() + bucketIndex * 0x9E3779B97F4A7C15L + round));
            fillElement(elementB, mix64(config.getSeed() ^ bucketIndex * 0xD1B54A32D192ED03L ^ round));

            digest(DOMAIN_TAG, bucketIndex, 0, elementA, tagA);
            digest(DOMAIN_TAG, bucketIndex, 1, elementB, tagB);
            digest(DOMAIN_CHECK, bucketIndex, 0, tagA, checkA);
            digest(DOMAIN_CHECK, bucketIndex, 1, tagB, checkB);
            int equalFlag = constantTimeEqual(checkA, checkB);

            long checksum = runningChecksum ^ equalFlag;
            for (int caseIndex = 0; caseIndex < config.getCaseNum(); caseIndex++) {
                digest(DOMAIN_CASE, bucketIndex, caseIndex, tagA, fullDigest);
                checksum ^= firstLong(fullDigest);
            }

            digest(DOMAIN_PAYLOAD, bucketIndex, 0, tagA, payloadMask);
            xor(elementA, payloadMask, payloadOutput);
            checksum ^= firstLong(payloadOutput);
            digest(DOMAIN_PAYLOAD, bucketIndex, 1, tagB, payloadMask);
            xor(elementB, payloadMask, payloadOutput);
            checksum ^= firstLong(payloadOutput);

            digest(DOMAIN_AUTH, bucketIndex, equalFlag, payloadOutput, fullDigest);
            return checksum ^ firstLong(fullDigest);
        }

        private void digest(byte domain, int bucketIndex, int branch, byte[] input, byte[] output) {
            digest.reset();
            digest.update(domain);
            longToBytes((((long) bucketIndex) << 32) ^ (branch & 0xFFFFFFFFL), indexBuffer);
            digest.update(indexBuffer);
            digest.update(input);
            try {
                digest.digest(fullDigest, 0, fullDigest.length);
            } catch (DigestException e) {
                throw new IllegalStateException("SHA-256 digest failed", e);
            }
            System.arraycopy(fullDigest, 0, output, 0, output.length);
        }
    }

    private static int constantTimeEqual(byte[] left, byte[] right) {
        int diff = left.length ^ right.length;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            diff |= left[i] ^ right[i];
        }
        return ((diff - 1) >>> 31) & 1;
    }

    private static void xor(byte[] left, byte[] right, byte[] output) {
        for (int i = 0; i < output.length; i++) {
            output[i] = (byte) (left[i] ^ right[i]);
        }
    }

    private static void fillElement(byte[] element, long state) {
        long value = state;
        int offset = 0;
        while (offset < element.length) {
            value = mix64(value);
            for (int i = 0; i < Long.BYTES && offset < element.length; i++) {
                element[offset++] = (byte) (value >>> (i * Byte.SIZE));
            }
        }
    }

    private static long firstLong(byte[] bytes) {
        long value = 0L;
        int length = Math.min(Long.BYTES, bytes.length);
        for (int i = 0; i < length; i++) {
            value |= (bytes[i] & 0xFFL) << (i * Byte.SIZE);
        }
        return value;
    }

    private static void longToBytes(long value, byte[] output) {
        for (int i = 0; i < Long.BYTES; i++) {
            output[i] = (byte) (value >>> ((Long.BYTES - 1 - i) * Byte.SIZE));
        }
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
