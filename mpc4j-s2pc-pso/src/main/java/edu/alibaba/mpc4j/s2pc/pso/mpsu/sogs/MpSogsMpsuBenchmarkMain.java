package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3.Abb3MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity.ShamirMultiplicityMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity.PersistentShamirMultiplicityMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity.SsmOpeningMode;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4.Rep4MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss.Rep4PrssMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss.Rep5PrssMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.shamir.ShamirMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.replicate.Aby3Z2cConfig;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.replicate.Aby3Z2cFactory;
import edu.alibaba.mpc4j.s3pc.abb3.context.TripletProvider;
import edu.alibaba.mpc4j.s3pc.abb3.context.TripletProviderConfig;
import edu.alibaba.mpc4j.s3pc.abb3.context.cr.S3pcCrProviderConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Local MemoryRpc benchmark entry for ABB3 MP-SOGS MPSU.
 *
 * <p>This benchmark is intended for protocol bring-up and regression. It runs all three participants in one JVM over
 * MemoryRpc and reports per-party time/communication. Network deployment can reuse the same
 * {@link Abb3MpSogsMpsuPartyRunner} runner.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuBenchmarkMain {
    /**
     * Supported party count in the current ABB3 implementation.
     */
    private static final int PARTY_NUM = 3;

    private MpSogsMpsuBenchmarkMain() {
        // empty
    }

    public static void main(String[] args) throws InterruptedException {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        System.out.println(BenchmarkResult.csvHeader());
        for (int trialIndex = 0; trialIndex < config.trials; trialIndex++) {
            BenchmarkResult result = run(config, trialIndex);
            for (PartyBenchmarkResult partyResult : result.partyResults) {
                System.out.println(partyResult.toCsvLine(result));
            }
        }
    }

    public static BenchmarkResult run(BenchmarkConfig config, int trialIndex) throws InterruptedException {
        List<Set<Long>> inputs = generateInputs(config.partyNum, config.n, config.commonOverlap, trialIndex);
        Set<Long> expectedUnion = ClearMpSogsMpsu.unionOf(inputs);
        MpSogsMpsuConfig.SecurePeelType securePeelType = config.effectiveSecurePeelType();
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(config.partyNum, config.tauMax(expectedUnion.size()))
            .setAlpha(config.alpha)
            .setHashNum(config.hashNum)
            .setTwoTier(config.twoTier)
            .setAuxiliaryHashNum(config.auxiliaryHashNum)
            .setAuxiliaryCellNum(config.auxiliaryCellNum)
            .setHashSeed(config.hashSeed + trialIndex)
            .setMaxPeelRounds(config.maxPeelRounds)
            .build();
        MpSogsMpsuConfig ptoConfig = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(securePeelType)
            .setLabelEncoding(config.labelEncoding)
            .setSsmOpeningMode(config.ssmOpeningMode)
            .setNetworkRttMillis(config.networkRttMillis)
            .setNetworkBandwidthMbps(config.networkBandwidthMbps)
            .setMaxHashSeedRetries(config.maxHashSeedRetries)
            .setMaxBatchCells(config.maxBatchCells)
            .build();
        MemoryRpcManager rpcManager = new MemoryRpcManager(config.partyNum);
        Rpc[] rpcs = IntStream.range(0, config.partyNum).mapToObj(rpcManager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        Arrays.stream(rpcs).forEach(Rpc::reset);
        BenchmarkThread[] threads = createBenchmarkThreads(
            config, trialIndex, rpcs, inputs, expectedUnion, ptoConfig, securePeelType
        );
        try {
            Arrays.stream(threads).forEach(Thread::start);
            waitForThreads(threads, config.joinTimeoutSeconds);
            List<PartyBenchmarkResult> partyResults = new ArrayList<>(config.partyNum);
            for (int partyIndex = 0; partyIndex < config.partyNum; partyIndex++) {
                BenchmarkThread thread = threads[partyIndex];
                if (thread.throwable != null) {
                    throw new IllegalStateException("party " + partyIndex + " benchmark failed", thread.throwable);
                }
                partyResults.add(new PartyBenchmarkResult(
                    partyIndex, thread.timeMs, rpcs[partyIndex].getSendByteLength(), thread.transcript
                ));
            }
            return new BenchmarkResult(config, trialIndex, expectedUnion.size(), partyResults);
        } finally {
            for (BenchmarkThread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            }
            for (BenchmarkThread thread : threads) {
                thread.destroy();
            }
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static BenchmarkThread[] createBenchmarkThreads(BenchmarkConfig config, int trialIndex, Rpc[] rpcs,
                                                            List<Set<Long>> inputs, Set<Long> expectedUnion,
                                                            MpSogsMpsuConfig ptoConfig,
                                                            MpSogsMpsuConfig.SecurePeelType securePeelType) {
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.ABB3) {
            TripletZ2cParty[] parties = createParties(
                rpcs, config.parallel, config.taskId + trialIndex, config.crBufferByteSize
            );
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new Abb3BenchmarkThread(
                    parties[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig
                ))
                .toArray(BenchmarkThread[]::new);
        }
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.SHAMIR) {
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new ShamirBenchmarkThread(
                    rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig, config.taskId + trialIndex
                ))
                .toArray(BenchmarkThread[]::new);
        }
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY) {
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new ShamirMultiplicityBenchmarkThread(
                    rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig, config.taskId + trialIndex
                ))
                .toArray(BenchmarkThread[]::new);
        }
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT
            || securePeelType
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT
            || securePeelType
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS
            || securePeelType
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
            || securePeelType
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE
            || securePeelType
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED) {
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new PersistentShamirMultiplicityBenchmarkThread(
                    rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig, config.taskId + trialIndex
                ))
                .toArray(BenchmarkThread[]::new);
        }
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.REP4_PACKED) {
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new Rep4BenchmarkThread(
                    rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig, config.taskId + trialIndex
                ))
                .toArray(BenchmarkThread[]::new);
        }
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_PACKED
            || securePeelType == MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_OPENED_FIRST) {
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new Rep4PrssBenchmarkThread(
                    rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig, config.taskId + trialIndex
                ))
                .toArray(BenchmarkThread[]::new);
        }
        if (securePeelType == MpSogsMpsuConfig.SecurePeelType.REP5_PRSS_OPENED_FIRST) {
            return IntStream.range(0, config.partyNum)
                .mapToObj(partyIndex -> new Rep5PrssBenchmarkThread(
                    rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, ptoConfig, config.taskId + trialIndex
                ))
                .toArray(BenchmarkThread[]::new);
        }
        throw new UnsupportedOperationException("unsupported benchmark secure peel type: " + securePeelType);
    }

    private static void waitForThreads(BenchmarkThread[] threads, int joinTimeoutSeconds) throws InterruptedException {
        long deadline = joinTimeoutSeconds <= 0
            ? Long.MAX_VALUE
            : System.nanoTime() + joinTimeoutSeconds * 1_000_000_000L;
        while (true) {
            List<BenchmarkThread> failedThreads = Arrays.stream(threads)
                .filter(thread -> thread.throwable != null)
                .toList();
            if (!failedThreads.isEmpty()) {
                interruptAndJoinAlive(threads);
                BenchmarkThread failedThread = failedThreads.get(0);
                throw new IllegalStateException("MP-SOGS benchmark party failed: " + failedThread.getName(),
                    failedThread.throwable);
            }
            boolean allDone = Arrays.stream(threads).noneMatch(Thread::isAlive);
            if (allDone) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                List<String> aliveThreadNames = Arrays.stream(threads)
                    .filter(Thread::isAlive)
                    .map(Thread::getName)
                    .toList();
                interruptAndJoinAlive(threads);
                throw new IllegalStateException("MP-SOGS benchmark timed out after " + joinTimeoutSeconds
                    + "s; alive parties: " + aliveThreadNames);
            }
            for (BenchmarkThread thread : threads) {
                if (thread.isAlive()) {
                    thread.join(1_000L);
                }
            }
        }
    }

    private static void interruptAndJoinAlive(BenchmarkThread[] threads) throws InterruptedException {
        Arrays.stream(threads).filter(Thread::isAlive).forEach(Thread::interrupt);
        for (BenchmarkThread thread : threads) {
            if (thread.isAlive()) {
                thread.join(1_000L);
            }
        }
    }

    private static TripletZ2cParty[] createParties(Rpc[] rpcs, boolean parallel, int taskId,
                                                   int crBufferByteSize) {
        Aby3Z2cConfig z2cConfig = new Aby3Z2cConfig.Builder(false).build();
        S3pcCrProviderConfig.Builder crProviderConfigBuilder = new S3pcCrProviderConfig.Builder();
        crProviderConfigBuilder.setBufferByteSize(crBufferByteSize);
        TripletProviderConfig providerConfig = new TripletProviderConfig.Builder(false)
            .setCrProviderConfig(crProviderConfigBuilder.build())
            .build();
        TripletProvider[] tripletProviders = IntStream.range(0, rpcs.length)
            .mapToObj(index -> new TripletProvider(rpcs[index], providerConfig))
            .toArray(TripletProvider[]::new);
        TripletZ2cParty[] parties = IntStream.range(0, rpcs.length)
            .mapToObj(index -> Aby3Z2cFactory.createParty(rpcs[index], z2cConfig, tripletProviders[index]))
            .toArray(TripletZ2cParty[]::new);
        Arrays.stream(parties).forEach(party -> {
            party.setTaskId(taskId);
            party.setParallel(parallel);
        });
        return parties;
    }

    private static List<Set<Long>> generateInputs(int partyNum, int n, double commonOverlap, int trialIndex) {
        int commonCount = (int) Math.round(n * commonOverlap);
        int uniqueCount = n - commonCount;
        long trialOffset = ((long) trialIndex) << 48;
        List<Set<Long>> inputs = new ArrayList<>(partyNum);
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            Set<Long> input = new HashSet<>();
            for (long value = 1; value <= commonCount; value++) {
                input.add(trialOffset + value);
            }
            long start = trialOffset + commonCount + (long) partyIndex * uniqueCount + 1;
            for (long value = start; value < start + uniqueCount; value++) {
                input.add(value);
            }
            inputs.add(input);
        }
        return inputs;
    }

    /**
     * Benchmark command-line configuration.
     */
    public static class BenchmarkConfig {
        private int partyNum = PARTY_NUM;
        private int n = 1024;
        private double commonOverlap = 0.5;
        private double alpha = MpSogsMpsuParams.DEFAULT_ALPHA;
        private int hashNum = MpSogsMpsuParams.DEFAULT_HASH_NUM;
        private boolean twoTier = true;
        private int auxiliaryHashNum = MpSogsMpsuParams.DEFAULT_HASH_NUM;
        private int auxiliaryCellNum = MpSogsMpsuParams.DEFAULT_AUXILIARY_CELL_NUM;
        private long hashSeed = MpSogsMpsuParams.DEFAULT_HASH_SEED;
        private int maxPeelRounds = 10_000;
        private int trials = 1;
        private boolean parallel = true;
        private int taskId = 1_000_000;
        private Integer tauMax;
        private int maxHashSeedRetries = 4;
        private int joinTimeoutSeconds = 0;
        private int maxBatchCells = MpSogsMpsuConfig.DEFAULT_MAX_BATCH_CELLS;
        private int crBufferByteSize = 1 << 24;
        private MpSogsMpsuConfig.SecurePeelType securePeelType;
        private MpSogsLabelEncoding labelEncoding = MpSogsLabelEncoding.EXACT_QUOTIENT;
        private SsmOpeningMode ssmOpeningMode = SsmOpeningMode.BALANCED_TWO_PHASE;
        private double networkRttMillis;
        private double networkBandwidthMbps;

        public static BenchmarkConfig fromArgs(String[] args) {
            BenchmarkConfig config = new BenchmarkConfig();
            for (String arg : args) {
                String[] tokens = arg.split("=", 2);
                if (tokens.length != 2 || !tokens[0].startsWith("--")) {
                    throw new IllegalArgumentException("invalid argument: " + arg);
                }
                String key = tokens[0].substring(2).toLowerCase(Locale.ROOT);
                String value = tokens[1];
                switch (key) {
                    case "parties":
                        config.partyNum = Integer.parseInt(value);
                        break;
                    case "n":
                        config.n = Integer.parseInt(value);
                        break;
                    case "overlap":
                        config.commonOverlap = Double.parseDouble(value);
                        break;
                    case "alpha":
                        config.alpha = Double.parseDouble(value);
                        break;
                    case "k":
                    case "hashnum":
                        config.hashNum = Integer.parseInt(value);
                        break;
                    case "twotier":
                    case "two_tier":
                        config.twoTier = Boolean.parseBoolean(value);
                        break;
                    case "auxk":
                    case "auxiliaryhashnum":
                    case "auxiliary_hash_num":
                        config.auxiliaryHashNum = Integer.parseInt(value);
                        break;
                    case "auxiliarycellnum":
                    case "auxiliary_cell_num":
                    case "auxiliarycells":
                        config.auxiliaryCellNum = Integer.parseInt(value);
                        break;
                    case "hashseed":
                        config.hashSeed = Long.parseLong(value);
                        break;
                    case "maxpeelrounds":
                        config.maxPeelRounds = Integer.parseInt(value);
                        break;
                    case "trials":
                        config.trials = Integer.parseInt(value);
                        break;
                    case "parallel":
                        config.parallel = Boolean.parseBoolean(value);
                        break;
                    case "taskid":
                        config.taskId = Integer.parseInt(value);
                        break;
                    case "taumax":
                        config.tauMax = Integer.parseInt(value);
                        break;
                    case "retries":
                    case "maxhashseedretries":
                        config.maxHashSeedRetries = Integer.parseInt(value);
                        break;
                    case "jointimeoutseconds":
                        config.joinTimeoutSeconds = Integer.parseInt(value);
                        break;
                    case "maxbatchcells":
                        config.maxBatchCells = Integer.parseInt(value);
                        break;
                    case "crbufferbytes":
                    case "crbufferbytesize":
                    case "cr_buffer_byte_size":
                        config.crBufferByteSize = Integer.parseInt(value);
                        break;
                    case "securepeeltype":
                    case "secure_peel_type":
                        config.securePeelType = MpSogsMpsuConfig.SecurePeelType.valueOf(value.toUpperCase(Locale.ROOT));
                        break;
                    case "labelencoding":
                    case "label_encoding":
                        config.labelEncoding = MpSogsLabelEncoding.valueOf(value.toUpperCase(Locale.ROOT));
                        break;
                    case "ssmopeningmode":
                    case "ssm_opening_mode":
                        config.ssmOpeningMode = SsmOpeningMode.valueOf(value.toUpperCase(Locale.ROOT));
                        break;
                    case "networkrttms":
                    case "network_rtt_ms":
                        config.networkRttMillis = Double.parseDouble(value);
                        break;
                    case "networkbandwidthmbps":
                    case "network_bandwidth_mbps":
                        config.networkBandwidthMbps = Double.parseDouble(value);
                        break;
                    default:
                        throw new IllegalArgumentException("unknown argument: " + arg);
                }
            }
            if (config.crBufferByteSize <= 0 || (config.crBufferByteSize & 15) != 0) {
                throw new IllegalArgumentException("crBufferByteSize must be positive and 16-byte aligned: "
                    + config.crBufferByteSize);
            }
            return config;
        }

        private int tauMax(int unionSize) {
            return tauMax == null ? unionSize : tauMax;
        }

        private MpSogsMpsuConfig.SecurePeelType effectiveSecurePeelType() {
            if (securePeelType != null) {
                return securePeelType;
            }
            return partyNum == PARTY_NUM
                ? MpSogsMpsuConfig.SecurePeelType.ABB3
                : MpSogsMpsuConfig.SecurePeelType.SHAMIR;
        }
    }

    /**
     * Whole benchmark result.
     */
    public static class BenchmarkResult {
        private final BenchmarkConfig config;
        private final int trialIndex;
        private final int unionSize;
        private final List<PartyBenchmarkResult> partyResults;

        private BenchmarkResult(BenchmarkConfig config, int trialIndex, int unionSize,
                                List<PartyBenchmarkResult> partyResults) {
            this.config = config;
            this.trialIndex = trialIndex;
            this.unionSize = unionSize;
            this.partyResults = partyResults;
        }

        public List<PartyBenchmarkResult> getPartyResults() {
            return partyResults;
        }

        public boolean allSuccess() {
            return partyResults.stream().allMatch(result -> result.transcript.isSuccess());
        }

        private static String csvHeader() {
            return "trial,party,secure_peel_type,ssm_opening_mode,network_rtt_ms,network_bandwidth_mbps,"
                + "n,union_size,overlap,alpha,k,two_tier,label_encoding,aux_k,aux_cells,"
                + "max_hash_seed_retries,max_batch_cells,"
                + "cr_buffer_byte_size,hash_seed_attempts,success,"
                + "rounds,upeel_calls,send_bytes,offline_ms,online_ms,time_ms,failure_reason";
        }
    }

    /**
     * Per-party benchmark result.
     */
    public static class PartyBenchmarkResult {
        private final int partyIndex;
        private final long timeMs;
        private final long sendBytes;
        private final MpSogsTranscript transcript;

        private PartyBenchmarkResult(int partyIndex, long timeMs, long sendBytes, MpSogsTranscript transcript) {
            this.partyIndex = partyIndex;
            this.timeMs = timeMs;
            this.sendBytes = sendBytes;
            this.transcript = transcript;
        }

        public int getPartyIndex() {
            return partyIndex;
        }

        public long getTimeMs() {
            return timeMs;
        }

        public long getSendBytes() {
            return sendBytes;
        }

        public MpSogsTranscript getTranscript() {
            return transcript;
        }

        private String toCsvLine(BenchmarkResult result) {
            return result.trialIndex + ","
                + partyIndex + ","
                + result.config.effectiveSecurePeelType() + ","
                + result.config.ssmOpeningMode + ","
                + result.config.networkRttMillis + ","
                + result.config.networkBandwidthMbps + ","
                + result.config.n + ","
                + result.unionSize + ","
                + result.config.commonOverlap + ","
                + result.config.alpha + ","
                + result.config.hashNum + ","
                + result.config.twoTier + ","
                + result.config.labelEncoding + ","
                + result.config.auxiliaryHashNum + ","
                + result.config.auxiliaryCellNum + ","
                + result.config.maxHashSeedRetries + ","
                + result.config.maxBatchCells + ","
                + result.config.crBufferByteSize + ","
                + transcript.getHashSeedAttempts() + ","
                + transcript.isSuccess() + ","
                + transcript.getRoundNum() + ","
                + transcript.getUpeelCalls() + ","
                + sendBytes + ","
                + transcript.getOfflineMs() + ","
                + Math.max(0L, timeMs - transcript.getOfflineMs()) + ","
                + timeMs + ","
                + csvEscape(transcript.getFailureReason());
        }

        private String csvEscape(String value) {
            String escaped = value == null ? "" : value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
    }

    /**
     * One benchmark participant thread.
     */
    private abstract static class BenchmarkThread extends Thread {
        private MpSogsTranscript transcript;
        private long timeMs;
        private Throwable throwable;

        private BenchmarkThread(String name) {
            setName(name);
        }

        abstract MpSogsTranscript runProtocol();

        void destroy() {
            // empty
        }

        @Override
        public void run() {
            try {
                long start = System.nanoTime();
                transcript = runProtocol();
                timeMs = (System.nanoTime() - start) / 1_000_000L;
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }

    /**
     * ABB3 benchmark participant thread.
     */
    private static class Abb3BenchmarkThread extends BenchmarkThread {
        private final TripletZ2cParty z2cParty;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;

        private Abb3BenchmarkThread(TripletZ2cParty z2cParty, Set<Long> localInput, Set<Long> expectedUnion,
                                    MpSogsMpsuConfig config) {
            super("mp-sogs-abb3-party-" + z2cParty.ownParty().getPartyId());
            this.z2cParty = z2cParty;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new Abb3MpSogsMpsuPartyRunner(z2cParty, config).run(localInput, expectedUnion);
        }

        @Override
        void destroy() {
            z2cParty.destroy();
        }
    }

    /**
     * Shamir benchmark participant thread.
     */
    private static class ShamirBenchmarkThread extends BenchmarkThread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;

        private ShamirBenchmarkThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                                      MpSogsMpsuConfig config, long taskId) {
            super("mp-sogs-shamir-party-" + rpc.ownParty().getPartyId());
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new ShamirMpSogsMpsuPartyRunner(rpc, config, taskId).run(localInput, expectedUnion);
        }
    }

    /**
     * Secret-shared multiplicity Shamir benchmark participant thread.
     */
    private static class ShamirMultiplicityBenchmarkThread extends BenchmarkThread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;

        private ShamirMultiplicityBenchmarkThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                                                  MpSogsMpsuConfig config, long taskId) {
            super("mp-sogs-shamir-multiplicity-party-" + rpc.ownParty().getPartyId());
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new ShamirMultiplicityMpSogsMpsuPartyRunner(rpc, config, taskId)
                .run(localInput, expectedUnion);
        }
    }

    /**
     * Persistent secret-shared multiplicity Shamir benchmark participant thread.
     */
    private static class PersistentShamirMultiplicityBenchmarkThread extends BenchmarkThread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;

        private PersistentShamirMultiplicityBenchmarkThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                                                            MpSogsMpsuConfig config, long taskId) {
            super("mp-sogs-shamir-multiplicity-persistent-party-" + rpc.ownParty().getPartyId());
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new PersistentShamirMultiplicityMpSogsMpsuPartyRunner(rpc, config, taskId)
                .run(localInput, expectedUnion);
        }
    }

    /**
     * REP4 benchmark participant thread.
     */
    private static class Rep4BenchmarkThread extends BenchmarkThread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;

        private Rep4BenchmarkThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                                    MpSogsMpsuConfig config, long taskId) {
            super("mp-sogs-rep4-party-" + rpc.ownParty().getPartyId());
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new Rep4MpSogsMpsuPartyRunner(rpc, config, taskId).run(localInput, expectedUnion);
        }
    }

    /**
     * REP4 PRSS benchmark participant thread.
     */
    private static class Rep4PrssBenchmarkThread extends BenchmarkThread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;

        private Rep4PrssBenchmarkThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                                        MpSogsMpsuConfig config, long taskId) {
            super("mp-sogs-rep4-prss-party-" + rpc.ownParty().getPartyId());
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new Rep4PrssMpSogsMpsuPartyRunner(rpc, config, taskId).run(localInput, expectedUnion);
        }
    }

    /**
     * REP5 PRSS benchmark participant thread.
     */
    private static class Rep5PrssBenchmarkThread extends BenchmarkThread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;

        private Rep5PrssBenchmarkThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                                        MpSogsMpsuConfig config, long taskId) {
            super("mp-sogs-rep5-prss-party-" + rpc.ownParty().getPartyId());
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        MpSogsTranscript runProtocol() {
            return new Rep5PrssMpSogsMpsuPartyRunner(rpc, config, taskId).run(localInput, expectedUnion);
        }
    }
}
