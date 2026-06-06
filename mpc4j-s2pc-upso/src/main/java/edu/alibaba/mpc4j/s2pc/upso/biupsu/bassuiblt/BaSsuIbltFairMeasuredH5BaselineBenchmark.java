package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.iblt.IbltPsuConfig;

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
 * Fair measured H5 / IBLT-PSU baseline benchmark for BA-SSU-IBLT comparisons.
 *
 * <p>MPC4J's IBLT-PSU endpoint is client-output. A fair bi-output baseline runs the same IBLT-PSU protocol twice with
 * swapped roles so both parties obtain {@code X union Y}. This runner records the two measured executions as a baseline
 * row only; it never prints a speedup claim.</p>
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public final class BaSsuIbltFairMeasuredH5BaselineBenchmark {
    /**
     * benchmark kind.
     */
    public static final String BENCHMARK_KIND = "MEASURED_H5_IBLT_PSU_BASELINE";
    /**
     * fair baseline name.
     */
    public static final String BASELINE_NAME = "H5_IBLT_PSU_TWO_OUTPUT_MEASURED";
    /**
     * security notice.
     */
    public static final String SECURITY_NOTICE = "IBLT_PSU_BASELINE_MEASURED_TWO_OUTPUT";
    /**
     * output semantics label.
     */
    public static final String OUTPUT_SEMANTICS = "TWO_OUTPUT_BY_ROLE_SWAP";
    /**
     * unknown metadata placeholder.
     */
    private static final String UNKNOWN = "UNKNOWN";
    /**
     * default join timeout.
     */
    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private BaSsuIbltFairMeasuredH5BaselineBenchmark() {
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
     * Runs the fair measured baseline.
     *
     * @param config config.
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
        DirectionResult senderToReceiver = runOneDirection(senderSet, receiverSet, config, 0);
        DirectionResult receiverToSender = runOneDirection(receiverSet, senderSet, config, 1);
        Set<ByteBuffer> expectedUnion = expectedUnion(senderSet, receiverSet);
        if (!expectedUnion.equals(senderToReceiver.output.getUnion())) {
            throw new IllegalStateException("sender-to-receiver IBLT-PSU baseline produced an incorrect union");
        }
        if (!expectedUnion.equals(receiverToSender.output.getUnion())) {
            throw new IllegalStateException("receiver-to-sender IBLT-PSU baseline produced an incorrect union");
        }
        int expectedPsica = senderSet.size() + receiverSet.size() - expectedUnion.size();
        if (senderToReceiver.output.getPsiCa() != expectedPsica || receiverToSender.output.getPsiCa() != expectedPsica) {
            throw new IllegalStateException("IBLT-PSU baseline produced an incorrect PSI-CA");
        }
        return new Result(config, senderToReceiver, receiverToSender, expectedUnion.size(), expectedPsica);
    }

    private static DirectionResult runOneDirection(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, Config config,
                                                   int directionIndex)
        throws InterruptedException, MpcAbortException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc serverRpc = rpcManager.getRpc(0);
        Rpc clientRpc = rpcManager.getRpc(1);
        serverRpc.connect();
        clientRpc.connect();
        PsuServer server = null;
        PsuClient client = null;
        InitThread serverInitThread = null;
        InitThread clientInitThread = null;
        PtoThread serverPtoThread = null;
        PtoThread clientPtoThread = null;
        try {
            IbltPsuConfig ibltPsuConfig = new IbltPsuConfig.Builder().build();
            server = PsuFactory.createServer(serverRpc, clientRpc.ownParty(), ibltPsuConfig);
            client = PsuFactory.createClient(clientRpc, serverRpc.ownParty(), ibltPsuConfig);
            server.setParallel(config.parallel);
            client.setParallel(config.parallel);
            int taskId = taskId(config.seed, directionIndex);
            server.setTaskId(taskId);
            client.setTaskId(taskId);
            serverRpc.reset();
            clientRpc.reset();
            serverInitThread = new InitThread(server, serverSet.size(), clientSet.size(), true);
            clientInitThread = new InitThread(client, clientSet.size(), serverSet.size(), false);
            long offlineStart = System.nanoTime();
            serverInitThread.start();
            clientInitThread.start();
            joinOrAbort(serverInitThread, clientInitThread, config.timeoutMillis, "IBLT-PSU baseline init");
            long offlineTimeNanos = System.nanoTime() - offlineStart;
            serverInitThread.throwIfFailed();
            clientInitThread.throwIfFailed();
            long offlineSendBytes = sendBytes(serverRpc, clientRpc);
            long offlinePayloadBytes = payloadBytes(serverRpc, clientRpc);
            long offlinePacketNum = packetNum(serverRpc, clientRpc);
            serverRpc.reset();
            clientRpc.reset();
            serverPtoThread = new PtoThread(
                server, serverSet, clientSet.size(), config.elementByteLength, true
            );
            clientPtoThread = new PtoThread(
                client, clientSet, serverSet.size(), config.elementByteLength, false
            );
            long onlineStart = System.nanoTime();
            serverPtoThread.start();
            clientPtoThread.start();
            joinOrAbort(serverPtoThread, clientPtoThread, config.timeoutMillis, "IBLT-PSU baseline online");
            long onlineTimeNanos = System.nanoTime() - onlineStart;
            serverPtoThread.throwIfFailed();
            clientPtoThread.throwIfFailed();
            long onlineSendBytes = sendBytes(serverRpc, clientRpc);
            long onlinePayloadBytes = payloadBytes(serverRpc, clientRpc);
            long onlinePacketNum = packetNum(serverRpc, clientRpc);
            return new DirectionResult(
                offlineTimeNanos, onlineTimeNanos, offlineSendBytes, onlineSendBytes, offlinePayloadBytes,
                onlinePayloadBytes, offlinePacketNum, onlinePacketNum, clientPtoThread.output
            );
        } finally {
            if (server != null) {
                server.destroy();
            }
            if (client != null) {
                client.destroy();
            }
            serverRpc.disconnect();
            clientRpc.disconnect();
            joinQuietly(serverInitThread, clientInitThread, serverPtoThread, clientPtoThread);
        }
    }

    private static int taskId(long seed, int directionIndex) {
        long rawTaskId = Math.floorMod(seed + 7919L * directionIndex, Integer.MAX_VALUE - 1L);
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

    private static void joinUntil(Thread thread, long deadline) throws InterruptedException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            return;
        }
        thread.join(TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1L);
    }

    private static long sendBytes(Rpc firstRpc, Rpc secondRpc) {
        return Math.addExact(firstRpc.getSendByteLength(), secondRpc.getSendByteLength());
    }

    private static long payloadBytes(Rpc firstRpc, Rpc secondRpc) {
        return Math.addExact(firstRpc.getPayloadByteLength(), secondRpc.getPayloadByteLength());
    }

    private static long packetNum(Rpc firstRpc, Rpc secondRpc) {
        return Math.addExact(firstRpc.getSendDataPacketNum(), secondRpc.getSendDataPacketNum());
    }

    private static Set<ByteBuffer> expectedUnion(Set<ByteBuffer> firstSet, Set<ByteBuffer> secondSet) {
        Set<ByteBuffer> union = new HashSet<>(firstSet);
        union.addAll(secondSet);
        return union;
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
         * sender set size.
         */
        private int senderSize;
        /**
         * receiver set size.
         */
        private int receiverSize;
        /**
         * overlap.
         */
        private int overlap;
        /**
         * element bytes.
         */
        private int elementByteLength;
        /**
         * parallel execution.
         */
        private boolean parallel;
        /**
         * deterministic seed.
         */
        private long seed;
        /**
         * protocol thread timeout.
         */
        private long timeoutMillis;

        public Config() {
            senderSize = 1 << 10;
            receiverSize = 1 << 18;
            overlap = 256;
            elementByteLength = Long.BYTES;
            parallel = false;
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
                case "parallel":
                    parallel = Boolean.parseBoolean(value);
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
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis must be positive");
            }
        }

        private String toArgString() {
            return String.format(
                Locale.ROOT,
                "m=%d n=%d overlap=%d elementBytes=%d ibltMultiplier=H5_DEFAULT parallel=%s seed=%d timeoutMillis=%d",
                senderSize, receiverSize, overlap, elementByteLength, parallel, seed, timeoutMillis
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

        public Config setParallel(boolean parallel) {
            this.parallel = parallel;
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
         * sender-to-receiver direction.
         */
        private final DirectionResult senderToReceiver;
        /**
         * receiver-to-sender direction.
         */
        private final DirectionResult receiverToSender;
        /**
         * union size.
         */
        private final int unionSize;
        /**
         * PSI-CA.
         */
        private final int psica;
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

        private Result(Config config, DirectionResult senderToReceiver, DirectionResult receiverToSender,
                       int unionSize, int psica) {
            this.config = config;
            this.senderToReceiver = senderToReceiver;
            this.receiverToSender = receiverToSender;
            this.unionSize = unionSize;
            this.psica = psica;
            peakMemoryBytes = currentUsedMemory();
            gitCommit = gitCommit();
            javaVersion = System.getProperty("java.version", UNKNOWN);
        }

        public String getBenchmarkKind() {
            return BENCHMARK_KIND;
        }

        public String getBaselineName() {
            return BASELINE_NAME;
        }

        public String getSecurityNotice() {
            return SECURITY_NOTICE;
        }

        public String getOutputSemantics() {
            return OUTPUT_SEMANTICS;
        }

        public boolean isMeasuredBaseline() {
            return true;
        }

        public boolean isTwoOutputBaseline() {
            return true;
        }

        public boolean isMeasuredProduction() {
            return false;
        }

        public boolean isProductionReady() {
            return false;
        }

        public boolean isSpeedupClaimReady() {
            return false;
        }

        public int getUnionSize() {
            return unionSize;
        }

        public int getPsiCa() {
            return psica;
        }

        public long getOfflineTimeNanos() {
            return Math.addExact(senderToReceiver.offlineTimeNanos, receiverToSender.offlineTimeNanos);
        }

        public long getOnlineTimeNanos() {
            return Math.addExact(senderToReceiver.onlineTimeNanos, receiverToSender.onlineTimeNanos);
        }

        public long getTotalTimeNanos() {
            return Math.addExact(getOfflineTimeNanos(), getOnlineTimeNanos());
        }

        public long getOfflineTotalBytes() {
            return Math.addExact(senderToReceiver.offlineSendBytes, receiverToSender.offlineSendBytes);
        }

        public long getOnlineTotalBytes() {
            return Math.addExact(senderToReceiver.onlineSendBytes, receiverToSender.onlineSendBytes);
        }

        public long getTotalBytes() {
            return Math.addExact(getOfflineTotalBytes(), getOnlineTotalBytes());
        }

        public long getOfflinePayloadBytes() {
            return Math.addExact(senderToReceiver.offlinePayloadBytes, receiverToSender.offlinePayloadBytes);
        }

        public long getOnlinePayloadBytes() {
            return Math.addExact(senderToReceiver.onlinePayloadBytes, receiverToSender.onlinePayloadBytes);
        }

        public long getOfflinePacketNum() {
            return Math.addExact(senderToReceiver.offlinePacketNum, receiverToSender.offlinePacketNum);
        }

        public long getOnlinePacketNum() {
            return Math.addExact(senderToReceiver.onlinePacketNum, receiverToSender.onlinePacketNum);
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT fair measured H5/IBLT-PSU baseline%n"
                    + "benchmarkKind=%s%n"
                    + "baselineName=%s%n"
                    + "measuredBaseline=%s%n"
                    + "twoOutputBaseline=%s%n"
                    + "measuredProduction=%s%n"
                    + "productionReady=%s%n"
                    + "outputSemantics=%s%n"
                    + "securityNotice=%s%n"
                    + "senderSize=%d%n"
                    + "receiverSize=%d%n"
                    + "overlap=%d%n"
                    + "unionSize=%d%n"
                    + "psica=%d%n"
                    + "offlineTimeMs=%.3f%n"
                    + "onlineTimeMs=%.3f%n"
                    + "totalTimeMs=%.3f%n"
                    + "offlineTotalBytes=%d%n"
                    + "onlineTotalBytes=%d%n"
                    + "totalBytes=%d%n"
                    + "offlinePayloadBytes=%d%n"
                    + "onlinePayloadBytes=%d%n"
                    + "offlinePacketNum=%d%n"
                    + "onlinePacketNum=%d%n"
                    + "directionCount=2%n"
                    + "speedupClaimReady=%s%n"
                    + "rawCommand=%s%n"
                    + "rawOutputPath=N/A%n"
                    + "gitCommit=%s%n"
                    + "javaVersion=%s%n"
                    + "peakMemoryBytes=%d%n"
                    + "oomStatus=false",
                getBenchmarkKind(),
                getBaselineName(),
                isMeasuredBaseline(),
                isTwoOutputBaseline(),
                isMeasuredProduction(),
                isProductionReady(),
                getOutputSemantics(),
                getSecurityNotice(),
                config.senderSize,
                config.receiverSize,
                config.overlap,
                unionSize,
                psica,
                getOfflineTimeNanos() / 1_000_000.0,
                getOnlineTimeNanos() / 1_000_000.0,
                getTotalTimeNanos() / 1_000_000.0,
                getOfflineTotalBytes(),
                getOnlineTotalBytes(),
                getTotalBytes(),
                getOfflinePayloadBytes(),
                getOnlinePayloadBytes(),
                getOfflinePacketNum(),
                getOnlinePacketNum(),
                isSpeedupClaimReady(),
                "BaSsuIbltFairMeasuredH5BaselineBenchmark " + config.toArgString(),
                gitCommit,
                javaVersion,
                peakMemoryBytes
            );
        }
    }

    private static final class DirectionResult {
        /**
         * offline time.
         */
        private final long offlineTimeNanos;
        /**
         * online time.
         */
        private final long onlineTimeNanos;
        /**
         * offline bytes.
         */
        private final long offlineSendBytes;
        /**
         * online bytes.
         */
        private final long onlineSendBytes;
        /**
         * offline payload bytes.
         */
        private final long offlinePayloadBytes;
        /**
         * online payload bytes.
         */
        private final long onlinePayloadBytes;
        /**
         * offline packets.
         */
        private final long offlinePacketNum;
        /**
         * online packets.
         */
        private final long onlinePacketNum;
        /**
         * client output.
         */
        private final PsuClientOutput output;

        private DirectionResult(long offlineTimeNanos, long onlineTimeNanos, long offlineSendBytes,
                                long onlineSendBytes, long offlinePayloadBytes, long onlinePayloadBytes,
                                long offlinePacketNum, long onlinePacketNum, PsuClientOutput output) {
            this.offlineTimeNanos = offlineTimeNanos;
            this.onlineTimeNanos = onlineTimeNanos;
            this.offlineSendBytes = offlineSendBytes;
            this.onlineSendBytes = onlineSendBytes;
            this.offlinePayloadBytes = offlinePayloadBytes;
            this.onlinePayloadBytes = onlinePayloadBytes;
            this.offlinePacketNum = offlinePacketNum;
            this.onlinePacketNum = onlinePacketNum;
            this.output = output;
        }
    }

    private static final class InitThread extends CheckedThread {
        /**
         * server.
         */
        private final PsuServer server;
        /**
         * client.
         */
        private final PsuClient client;
        /**
         * own size.
         */
        private final int ownSize;
        /**
         * other size.
         */
        private final int otherSize;
        /**
         * server role.
         */
        private final boolean serverRole;

        private InitThread(PsuServer server, int ownSize, int otherSize, boolean serverRole) {
            this.server = server;
            client = null;
            this.ownSize = ownSize;
            this.otherSize = otherSize;
            this.serverRole = serverRole;
        }

        private InitThread(PsuClient client, int ownSize, int otherSize, boolean serverRole) {
            server = null;
            this.client = client;
            this.ownSize = ownSize;
            this.otherSize = otherSize;
            this.serverRole = serverRole;
        }

        @Override
        void runChecked() throws MpcAbortException {
            if (serverRole) {
                server.init(ownSize, otherSize);
            } else {
                client.init(ownSize, otherSize);
            }
        }
    }

    private static final class PtoThread extends CheckedThread {
        /**
         * server.
         */
        private final PsuServer server;
        /**
         * client.
         */
        private final PsuClient client;
        /**
         * local set.
         */
        private final Set<ByteBuffer> localSet;
        /**
         * other size.
         */
        private final int otherSize;
        /**
         * element bytes.
         */
        private final int elementByteLength;
        /**
         * server role.
         */
        private final boolean serverRole;
        /**
         * client output.
         */
        private PsuClientOutput output;

        private PtoThread(PsuServer server, Set<ByteBuffer> localSet, int otherSize, int elementByteLength,
                          boolean serverRole) {
            this.server = server;
            client = null;
            this.localSet = localSet;
            this.otherSize = otherSize;
            this.elementByteLength = elementByteLength;
            this.serverRole = serverRole;
        }

        private PtoThread(PsuClient client, Set<ByteBuffer> localSet, int otherSize, int elementByteLength,
                          boolean serverRole) {
            server = null;
            this.client = client;
            this.localSet = localSet;
            this.otherSize = otherSize;
            this.elementByteLength = elementByteLength;
            this.serverRole = serverRole;
        }

        @Override
        void runChecked() throws MpcAbortException {
            if (serverRole) {
                server.psu(localSet, otherSize, elementByteLength);
            } else {
                output = client.psu(localSet, otherSize, elementByteLength);
            }
        }
    }

    private abstract static class CheckedThread extends Thread {
        /**
         * thrown exception.
         */
        private Throwable exception;

        private CheckedThread() {
            setDaemon(true);
        }

        @Override
        public final void run() {
            try {
                runChecked();
            } catch (Throwable t) {
                exception = t;
            }
        }

        abstract void runChecked() throws MpcAbortException;

        void throwIfFailed() throws MpcAbortException {
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
