package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * BA-SSU-IBLT measured production benchmark.
 *
 * <p>This runner constructs the package-internal final-readiness certificate, checks the production gate, and then
 * measures the SECURE_SEMI_HONEST sender / receiver endpoint through {@code runProductionEndpoint}. Estimate and
 * candidate benchmark code must not call this runner unless productionReady is true.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public final class BaSsuIbltMeasuredProductionBenchmark {
    /**
     * benchmark kind reserved for true production measurements.
     */
    public static final String BENCHMARK_KIND_MEASURED_PRODUCTION = "MEASURED_PRODUCTION";
    /**
     * fail-closed notice id.
     */
    public static final String UNAVAILABLE_NOTICE_ID = "MEASURED_PRODUCTION_UNAVAILABLE";
    /**
     * fail-closed notice.
     */
    public static final String UNAVAILABLE_NOTICE =
        "MEASURED_PRODUCTION is unavailable until the BA-SSU-IBLT secure endpoint passes the production gate";
    /**
     * production security notice.
     */
    public static final String SECURITY_NOTICE = "NONE";
    /**
     * unknown metadata placeholder.
     */
    private static final String UNKNOWN = "UNKNOWN";
    /**
     * default benchmark thread timeout.
     */
    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private BaSsuIbltMeasuredProductionBenchmark() {
        // empty
    }

    /**
     * Command line entry point.
     *
     * @param args key=value benchmark arguments.
     * @throws Exception if the run aborts.
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.fromArgs(args);
        System.out.println(run(config).toDisplayString());
    }

    /**
     * Runs a measured production benchmark.
     *
     * @param config benchmark config.
     * @return result.
     * @throws InterruptedException interrupted.
     * @throws MpcAbortException protocol abort.
     */
    public static Result run(Config config) throws InterruptedException, MpcAbortException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        config.validate();
        Set<ByteBuffer> senderSet = elementSet(config.elementByteLength, config.senderSize, config.overlap, true,
            config.seed);
        Set<ByteBuffer> receiverSet = elementSet(config.elementByteLength, config.receiverSize, config.overlap,
            false, config.seed);
        BaSsuIbltBiUpsuConfig protocolConfig = protocolConfig(config);
        checkProtocolConfig(protocolConfig);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        EndpointThread senderThread = null;
        EndpointThread receiverThread = null;
        try {
            int taskId = taskId(config.seed);
            senderThread = new EndpointThread(
                senderRpc, receiverRpc.ownParty(), true, senderSet, config, protocolConfig, taskId
            );
            receiverThread = new EndpointThread(
                receiverRpc, senderRpc.ownParty(), false, receiverSet, config, protocolConfig, taskId
            );
            long wallClockStart = System.nanoTime();
            senderThread.start();
            receiverThread.start();
            joinOrAbort(senderThread, receiverThread, config.timeoutMillis, "BA-SSU-IBLT measured production");
            long wallClockNanos = System.nanoTime() - wallClockStart;
            senderThread.throwIfFailed();
            receiverThread.throwIfFailed();
            Set<ByteBuffer> expectedUnion = new HashSet<>(senderSet);
            expectedUnion.addAll(receiverSet);
            if (!expectedUnion.equals(senderThread.output.getUnion())
                || !expectedUnion.equals(receiverThread.output.getUnion())) {
                throw new IllegalStateException("measured production benchmark produced an incorrect union");
            }
            return new Result(config, protocolConfig, senderThread.recorder, receiverThread.recorder,
                expectedUnion.size(), wallClockNanos);
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
            joinQuietly(senderThread, receiverThread);
        }
    }

    /**
     * Legacy readiness-only entry point retained for tests that pass an explicit protocol config.
     *
     * @param protocolConfig protocol config.
     */
    public static void run(BaSsuIbltBiUpsuConfig protocolConfig) {
        checkProtocolConfig(protocolConfig);
        throw new IllegalStateException(
            "MEASURED_PRODUCTION requires benchmark input sets; call run(Config) for an end-to-end measurement"
        );
    }

    /**
     * Checks whether a protocol config may produce a measured production benchmark.
     *
     * @param protocolConfig protocol config.
     * @throws IllegalStateException if production readiness is false.
     */
    public static void checkProtocolConfig(BaSsuIbltBiUpsuConfig protocolConfig) {
        if (protocolConfig == null) {
            throw new IllegalArgumentException("protocolConfig must be non-null");
        }
        if (!protocolConfig.isProductionReady()) {
            throw new IllegalStateException(notReadyMessage(protocolConfig));
        }
    }

    /**
     * Returns a stable fail-closed message for not-ready protocol configs.
     *
     * @param protocolConfig protocol config.
     * @return fail-closed message.
     */
    public static String notReadyMessage(BaSsuIbltBiUpsuConfig protocolConfig) {
        if (protocolConfig == null) {
            throw new IllegalArgumentException("protocolConfig must be non-null");
        }
        return UNAVAILABLE_NOTICE_ID + ": productionReady=false; reason="
            + protocolConfig.getProductionReadinessReason();
    }

    private static int taskId(long seed) {
        long rawTaskId = Math.floorMod(seed, Integer.MAX_VALUE - 1L);
        return (int) rawTaskId + 1;
    }

    private static void joinOrAbort(Thread firstThread, Thread secondThread, long timeoutMillis, String phase)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        joinUntil(firstThread, deadline);
        joinUntil(secondThread, deadline);
        if (!firstThread.isAlive() && !secondThread.isAlive()) {
            return;
        }
        firstThread.interrupt();
        secondThread.interrupt();
        throw new IllegalStateException(phase + " did not finish within " + timeoutMillis + " ms");
    }

    private static void joinUntil(Thread thread, long deadline) throws InterruptedException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return;
        }
        thread.join(TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1L);
    }

    private static void joinQuietly(Thread... threads) {
        boolean interrupted = false;
        for (Thread thread : threads) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static BaSsuIbltBiUpsuConfig protocolConfig(Config config) {
        int largeSize = Math.max(config.senderSize, config.receiverSize);
        int shadowSize = Math.min(config.senderSize, config.receiverSize);
        byte[] publicSeed = publicSeed(config);
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(largeSize, shadowSize)
            .setAlphaAnchor(config.alpha)
            .setDegree(config.degree)
            .setRetryCount(config.retryCount)
            .setLambda(config.lambda)
            .setMarginBits(config.marginBits)
            .setChecksPerBucket(config.checksPerBucket)
            .setPublicPlaceSeed(publicSeed)
            .build();
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setElementByteLength(config.elementByteLength)
                .setTagByteLength(BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()))
                .setCheckByteLength(BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits()))
                .setOnlineBatchSize(config.onlineBatchSize)
                .setAcceptAdaptiveQueueTranscriptLeakage(true)
                .setProductionReadinessCertificate(BaSsuIbltProductionReadinessCertificate.finalAuditPassed())
                .build();
        return new BaSsuIbltBiUpsuConfig.Builder()
            .setMaxElementByteLength(config.elementByteLength)
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(backend)
            .setAlphaAnchor(config.alpha)
            .setDegree(config.degree)
            .setRetryCount(config.retryCount)
            .setLambda(config.lambda)
            .setMarginBits(config.marginBits)
            .setChecksPerBucket(config.checksPerBucket)
            .setPublicPlaceSeed(publicSeed)
            .build();
    }

    private static Set<ByteBuffer> elementSet(int elementByteLength, int size, int overlap, boolean sender,
                                              long seed) {
        Set<ByteBuffer> set = new HashSet<>(size);
        for (int index = 0; index < overlap; index++) {
            set.add(ByteBuffer.wrap(element(elementByteLength, seed, 0, index)));
        }
        int uniqueSize = size - overlap;
        int domain = sender ? 1 : 2;
        for (int index = 0; index < uniqueSize; index++) {
            set.add(ByteBuffer.wrap(element(elementByteLength, seed, domain, index)));
        }
        if (set.size() != size) {
            throw new IllegalStateException("generated duplicate benchmark elements");
        }
        return set;
    }

    private static byte[] element(int byteLength, long seed, int domain, int index) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] output = new byte[byteLength];
            int offset = 0;
            int blockIndex = 0;
            while (offset < byteLength) {
                digest.update((byte) domain);
                digest.update(longBytes(seed));
                digest.update(intBytes(index));
                digest.update(intBytes(blockIndex));
                byte[] block = digest.digest();
                int copyLength = Math.min(block.length, byteLength - offset);
                System.arraycopy(block, 0, output, offset, copyLength);
                offset += copyLength;
                blockIndex++;
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    private static byte[] publicSeed(Config config) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 4 + Long.BYTES);
        buffer.putInt(config.senderSize);
        buffer.putInt(config.receiverSize);
        buffer.putInt(config.overlap);
        buffer.putInt(config.degree);
        buffer.putLong(config.seed);
        return buffer.array();
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    private static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static long currentUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static String gitCommit() {
        ProcessBuilder processBuilder = new ProcessBuilder("git", "rev-parse", "HEAD");
        processBuilder.directory(new File("."));
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                process.destroyForcibly();
                return UNKNOWN;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null || line.isBlank() ? UNKNOWN : line.trim();
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return UNKNOWN;
        }
    }

    /**
     * Benchmark config.
     */
    public static class Config {
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
         * element bytes.
         */
        private int elementByteLength;
        /**
         * IBLT degree.
         */
        private int degree;
        /**
         * table multiplier.
         */
        private double alpha;
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
         * online probe batch size.
         */
        private int onlineBatchSize;
        /**
         * deterministic seed.
         */
        private long seed;
        /**
         * endpoint thread timeout.
         */
        private long timeoutMillis;

        public Config() {
            senderSize = 1 << 10;
            receiverSize = 1 << 18;
            overlap = 256;
            elementByteLength = Long.BYTES;
            degree = 3;
            alpha = 1.55;
            retryCount = 1;
            lambda = BaSsuIbltBiUpsuParams.DEFAULT_LAMBDA;
            marginBits = BaSsuIbltBiUpsuParams.DEFAULT_MARGIN_BITS;
            checksPerBucket = BaSsuIbltBiUpsuParams.DEFAULT_CHECKS_PER_BUCKET;
            onlineBatchSize = 1 << 14;
            seed = 20260606L;
            timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
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
                case "degree":
                    degree = parseInt(value);
                    break;
                case "alpha":
                    alpha = Double.parseDouble(value);
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
                case "batch":
                case "batchsize":
                case "onlinebatch":
                case "onlinebatchsize":
                    onlineBatchSize = parseInt(value);
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
            if (degree != 3 && degree != 4) {
                throw new IllegalArgumentException("degree must be 3 or 4");
            }
            if (!Double.isFinite(alpha) || alpha <= 1.0) {
                throw new IllegalArgumentException("alpha must be finite and greater than 1");
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
            if (onlineBatchSize <= 0) {
                throw new IllegalArgumentException("onlineBatchSize must be positive");
            }
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis must be positive");
            }
        }

        private String toArgString() {
            return String.format(
                Locale.ROOT,
                "m=%d n=%d overlap=%d elementBytes=%d degree=%d alpha=%.3f retry=%d lambda=%d marginBits=%d "
                    + "checksPerBucket=%d onlineBatchSize=%d seed=%d timeoutMillis=%d",
                senderSize, receiverSize, overlap, elementByteLength, degree, alpha, retryCount, lambda, marginBits,
                checksPerBucket, onlineBatchSize, seed, timeoutMillis
            );
        }

        public Config setSenderSize(int senderSize) {
            this.senderSize = senderSize;
            return this;
        }

        public Config setReceiverSize(int receiverSize) {
            this.receiverSize = receiverSize;
            return this;
        }

        public Config setOverlap(int overlap) {
            this.overlap = overlap;
            return this;
        }

        public Config setElementByteLength(int elementByteLength) {
            this.elementByteLength = elementByteLength;
            return this;
        }

        public Config setAlpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Config setSeed(long seed) {
            this.seed = seed;
            return this;
        }

        public Config setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Config setOnlineBatchSize(int onlineBatchSize) {
            this.onlineBatchSize = onlineBatchSize;
            return this;
        }
    }

    /**
     * Benchmark result.
     */
    public static final class Result {
        /**
         * config.
         */
        private final Config config;
        /**
         * protocol config.
         */
        private final BaSsuIbltBiUpsuConfig protocolConfig;
        /**
         * sender recorder.
         */
        private final BaSsuIbltQueuePeelEndpoint.PhaseRecorder senderRecorder;
        /**
         * receiver recorder.
         */
        private final BaSsuIbltQueuePeelEndpoint.PhaseRecorder receiverRecorder;
        /**
         * union size.
         */
        private final int unionSize;
        /**
         * wall-clock time.
         */
        private final long wallClockNanos;
        /**
         * peak memory snapshot.
         */
        private final long peakMemoryBytes;
        /**
         * git commit.
         */
        private final String gitCommit;
        /**
         * java version.
         */
        private final String javaVersion;

        Result(Config config, BaSsuIbltBiUpsuConfig protocolConfig,
               BaSsuIbltQueuePeelEndpoint.PhaseRecorder senderRecorder,
               BaSsuIbltQueuePeelEndpoint.PhaseRecorder receiverRecorder, int unionSize, long wallClockNanos) {
            this.config = config;
            this.protocolConfig = protocolConfig;
            this.senderRecorder = senderRecorder;
            this.receiverRecorder = receiverRecorder;
            this.unionSize = unionSize;
            this.wallClockNanos = wallClockNanos;
            peakMemoryBytes = currentUsedMemory();
            gitCommit = gitCommit();
            javaVersion = System.getProperty("java.version", UNKNOWN);
        }

        public String getBenchmarkKind() {
            return BENCHMARK_KIND_MEASURED_PRODUCTION;
        }

        public boolean isMeasuredProduction() {
            return true;
        }

        public boolean isProductionReady() {
            return protocolConfig.isProductionReady();
        }

        public String getProductionReadinessReason() {
            return protocolConfig.getProductionReadinessReason();
        }

        public long getOfflineTimeNanos() {
            return Math.max(senderRecorder.getOfflineTimeNanos(), receiverRecorder.getOfflineTimeNanos());
        }

        public long getOnlineTimeNanos() {
            return Math.max(senderRecorder.getOnlineTimeNanos(), receiverRecorder.getOnlineTimeNanos());
        }

        public long getTotalTimeNanos() {
            return Math.addExact(getOfflineTimeNanos(), getOnlineTimeNanos());
        }

        public long getWallClockNanos() {
            return wallClockNanos;
        }

        public long getOfflineTotalBytes() {
            return Math.addExact(senderRecorder.getOfflineSendBytes(), receiverRecorder.getOfflineSendBytes());
        }

        public long getOnlineTotalBytes() {
            return Math.addExact(senderRecorder.getOnlineSendBytes(), receiverRecorder.getOnlineSendBytes());
        }

        public long getTotalBytes() {
            return Math.addExact(getOfflineTotalBytes(), getOnlineTotalBytes());
        }

        public long getOfflinePayloadBytes() {
            return Math.addExact(senderRecorder.getOfflinePayloadBytes(), receiverRecorder.getOfflinePayloadBytes());
        }

        public long getOnlinePayloadBytes() {
            return Math.addExact(senderRecorder.getOnlinePayloadBytes(), receiverRecorder.getOnlinePayloadBytes());
        }

        public long getOfflinePacketNum() {
            return Math.addExact(senderRecorder.getOfflinePacketNum(), receiverRecorder.getOfflinePacketNum());
        }

        public long getOnlinePacketNum() {
            return Math.addExact(senderRecorder.getOnlinePacketNum(), receiverRecorder.getOnlinePacketNum());
        }

        public long getPhaseBarrierTimeNanos() {
            return Math.max(senderRecorder.getPhaseBarrierTimeNanos(), receiverRecorder.getPhaseBarrierTimeNanos());
        }

        public long getPhaseBarrierBytes() {
            return Math.addExact(senderRecorder.getPhaseBarrierSendBytes(), receiverRecorder.getPhaseBarrierSendBytes());
        }

        public long getPhaseBarrierPayloadBytes() {
            return Math.addExact(
                senderRecorder.getPhaseBarrierPayloadBytes(), receiverRecorder.getPhaseBarrierPayloadBytes()
            );
        }

        public long getPhaseBarrierPacketNum() {
            return Math.addExact(
                senderRecorder.getPhaseBarrierPacketNum(), receiverRecorder.getPhaseBarrierPacketNum()
            );
        }

        public int getProbeCount() {
            return senderRecorder.getProbeCount();
        }

        public long getLogicalProbeCount() {
            return Math.max(senderRecorder.getLogicalProbeCount(), receiverRecorder.getLogicalProbeCount());
        }

        public long getOnlineBatchCount() {
            return Math.max(senderRecorder.getOnlineBatchCount(), receiverRecorder.getOnlineBatchCount());
        }

        public int getMaxProbeBatchSize() {
            return Math.max(senderRecorder.getMaxProbeBatchSize(), receiverRecorder.getMaxProbeBatchSize());
        }

        public double getAverageProbeBatchSize() {
            long onlineBatchCount = getOnlineBatchCount();
            if (onlineBatchCount == 0L) {
                return 0.0;
            }
            return (double) getLogicalProbeCount() / (double) onlineBatchCount;
        }

        public long getPublicDuplicateSkipCount() {
            return Math.max(
                senderRecorder.getPublicDuplicateSkipCount(), receiverRecorder.getPublicDuplicateSkipCount()
            );
        }

        public long getPublicUnchangedSkipCount() {
            return Math.max(
                senderRecorder.getPublicUnchangedSkipCount(), receiverRecorder.getPublicUnchangedSkipCount()
            );
        }

        public long getPublicSkipCount() {
            return Math.max(senderRecorder.getPublicSkipCount(), receiverRecorder.getPublicSkipCount());
        }

        public String getRetryStatus() {
            if (!senderRecorder.hasRetryStatus() || !receiverRecorder.hasRetryStatus()) {
                return UNKNOWN;
            }
            if (senderRecorder.getRetryIndex() != receiverRecorder.getRetryIndex()
                || senderRecorder.getProbeCount() != receiverRecorder.getProbeCount()
                || senderRecorder.isRetrySuccess() != receiverRecorder.isRetrySuccess()) {
                return "mismatch";
            }
            return String.format(Locale.ROOT, "%s@retry=%d,probes=%d",
                senderRecorder.isRetrySuccess() ? "success" : "failed",
                senderRecorder.getRetryIndex(), senderRecorder.getProbeCount());
        }

        public int getUnionSize() {
            return unionSize;
        }

        public String getSecurityNotice() {
            return SECURITY_NOTICE;
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT measured production benchmark%n"
                    + "benchmarkKind=%s%n"
                    + "measuredProduction=%s%n"
                    + "productionReady=%s%n"
                    + "securityNotice=%s%n"
                    + "senderSize=%d%n"
                    + "receiverSize=%d%n"
                    + "largeSize=%d%n"
                    + "shadowSize=%d%n"
                    + "overlap=%d%n"
                    + "unionSize=%d%n"
                    + "offlineTimeMs=%.3f%n"
                    + "onlineTimeMs=%.3f%n"
                    + "totalTimeMs=%.3f%n"
                    + "wallClockMs=%.3f%n"
                    + "offlineTotalBytes=%d%n"
                    + "onlineTotalBytes=%d%n"
                    + "totalBytes=%d%n"
                    + "rpcOfflineBytes=%d%n"
                    + "rpcOnlineBytes=%d%n"
                    + "offlinePayloadBytes=%d%n"
                    + "onlinePayloadBytes=%d%n"
                    + "offlinePacketNum=%d%n"
                    + "onlinePacketNum=%d%n"
                    + "phaseBarrierExcluded=true%n"
                    + "phaseBarrierTimeMs=%.3f%n"
                    + "phaseBarrierBytes=%d%n"
                    + "phaseBarrierPayloadBytes=%d%n"
                    + "phaseBarrierPacketNum=%d%n"
                    + "probeCount=%d%n"
                    + "logicalProbeCount=%d%n"
                    + "onlineBatchCount=%d%n"
                    + "avgLogicalProbesPerBatch=%.3f%n"
                    + "maxProbeBatchSize=%d%n"
                    + "publicDuplicateSkipCount=%d%n"
                    + "publicUnchangedSkipCount=%d%n"
                    + "publicSkipCount=%d%n"
                    + "retryStatus=%s%n"
                    + "rawCommand=%s%n"
                    + "rawOutputPath=N/A%n"
                    + "gitCommit=%s%n"
                    + "javaVersion=%s%n"
                    + "peakMemoryBytes=%d%n"
                    + "oomStatus=false",
                getBenchmarkKind(),
                isMeasuredProduction(),
                isProductionReady(),
                getSecurityNotice(),
                config.senderSize,
                config.receiverSize,
                Math.max(config.senderSize, config.receiverSize),
                Math.min(config.senderSize, config.receiverSize),
                config.overlap,
                unionSize,
                getOfflineTimeNanos() / 1_000_000.0,
                getOnlineTimeNanos() / 1_000_000.0,
                getTotalTimeNanos() / 1_000_000.0,
                wallClockNanos / 1_000_000.0,
                getOfflineTotalBytes(),
                getOnlineTotalBytes(),
                getTotalBytes(),
                getOfflineTotalBytes(),
                getOnlineTotalBytes(),
                getOfflinePayloadBytes(),
                getOnlinePayloadBytes(),
                getOfflinePacketNum(),
                getOnlinePacketNum(),
                getPhaseBarrierTimeNanos() / 1_000_000.0,
                getPhaseBarrierBytes(),
                getPhaseBarrierPayloadBytes(),
                getPhaseBarrierPacketNum(),
                getProbeCount(),
                getLogicalProbeCount(),
                getOnlineBatchCount(),
                getAverageProbeBatchSize(),
                getMaxProbeBatchSize(),
                getPublicDuplicateSkipCount(),
                getPublicUnchangedSkipCount(),
                getPublicSkipCount(),
                getRetryStatus(),
                "BaSsuIbltMeasuredProductionBenchmark " + config.toArgString(),
                gitCommit,
                javaVersion,
                peakMemoryBytes
            );
        }
    }

    /**
     * Two-party endpoint benchmark thread.
     */
    private static class EndpointThread extends Thread {
        /**
         * local RPC.
         */
        private final Rpc rpc;
        /**
         * other party.
         */
        private final Party otherParty;
        /**
         * whether the local API role is protocol sender.
         */
        private final boolean localIsProtocolSender;
        /**
         * local set.
         */
        private final Set<ByteBuffer> localSet;
        /**
         * benchmark config.
         */
        private final Config benchmarkConfig;
        /**
         * protocol config.
         */
        private final BaSsuIbltBiUpsuConfig protocolConfig;
        /**
         * task ID.
         */
        private final int taskId;
        /**
         * phase recorder.
         */
        private final BaSsuIbltQueuePeelEndpoint.PhaseRecorder recorder;
        /**
         * output.
         */
        private BiUpsuPartyOutput output;
        /**
         * exception.
         */
        private Throwable exception;

        EndpointThread(Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localSet,
                       Config benchmarkConfig, BaSsuIbltBiUpsuConfig protocolConfig, int taskId) {
            setDaemon(true);
            this.rpc = rpc;
            this.otherParty = otherParty;
            this.localIsProtocolSender = localIsProtocolSender;
            this.localSet = localSet;
            this.benchmarkConfig = benchmarkConfig;
            this.protocolConfig = protocolConfig;
            this.taskId = taskId;
            recorder = new BaSsuIbltQueuePeelEndpoint.PhaseRecorder();
        }

        @Override
        public void run() {
            try {
                output = BaSsuIbltQueuePeelEndpoint.runProductionEndpoint(
                    rpc, otherParty, localIsProtocolSender, localSet, benchmarkConfig.senderSize,
                    benchmarkConfig.receiverSize, benchmarkConfig.elementByteLength, protocolConfig, taskId, false,
                    recorder
                );
            } catch (Throwable t) {
                exception = t;
            }
        }

        private void throwIfFailed() throws MpcAbortException {
            if (exception == null) {
                return;
            }
            if (exception instanceof MpcAbortException) {
                throw (MpcAbortException) exception;
            }
            throw new IllegalStateException(exception);
        }
    }
}
