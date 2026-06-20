package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcPropertiesUtils;
import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3.Abb3MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.replicate.Aby3Z2cConfig;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.replicate.Aby3Z2cFactory;
import edu.alibaba.mpc4j.s3pc.abb3.context.TripletProvider;
import edu.alibaba.mpc4j.s3pc.abb3.context.TripletProviderConfig;
import edu.alibaba.mpc4j.s3pc.abb3.context.cr.S3pcCrProviderConfig;
import edu.alibaba.mpc4j.s3pc.abb3.context.tuple.RpMtProviderFactory;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Netty entry for three-party all-output MP-SOGS MPSU.
 *
 * <p>Run one process per party with the same config file and own name {@code first}, {@code second}, or
 * {@code third}. This class intentionally stays outside {@code PsoMain}, whose current shape is two-party.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuNettyMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(MpSogsMpsuNettyMain.class);
    /**
     * Protocol type name used in config files.
     */
    public static final String PTO_TYPE_NAME = "MP_SOGS_MPSU";
    /**
     * Number of parties supported by the current ABB3 backend.
     */
    private static final int PARTY_NUM = 3;
    /**
     * Key: use malicious ABB3 backend.
     */
    private static final String IS_MALICIOUS = "malicious";
    /**
     * Key: verify malicious multiplication with MAC.
     */
    private static final String VERIFY_WITH_MAC = "use_mac";
    /**
     * Key: use simulated MT generation for malicious runs.
     */
    private static final String USE_MT_SIM_MODE = "mt_sim_mode";
    /**
     * Key: run ABB3 operations in parallel.
     */
    private static final String PARALLEL = "parallel";
    /**
     * Key: log local set sizes.
     */
    private static final String LOG_SET_SIZE = "log_set_size";
    /**
     * Key: direct local set sizes.
     */
    private static final String SET_SIZE = "set_size";
    /**
     * Key: common overlap fraction.
     */
    private static final String COMMON_OVERLAP = "overlap";
    /**
     * Key: SOGS alpha.
     */
    private static final String ALPHA = "alpha";
    /**
     * Key: SOGS hash number.
     */
    private static final String HASH_NUM = "k";
    /**
     * Key: public base hash seed.
     */
    private static final String HASH_SEED = "hash_seed";
    /**
     * Key: maximum peel rounds.
     */
    private static final String MAX_PEEL_ROUNDS = "max_peel_rounds";
    /**
     * Key: maximum public hash-seed attempts.
     */
    private static final String MAX_HASH_SEED_RETRIES = "max_hash_seed_retries";
    /**
     * Key: maximum cells per secure-uPeel batch.
     */
    private static final String MAX_BATCH_CELLS = "max_batch_cells";
    /**
     * Key: ABB3 correlated-randomness byte buffer size.
     */
    private static final String CR_BUFFER_BYTE_SIZE = "cr_buffer_byte_size";
    /**
     * Key: task id base.
     */
    private static final String TASK_ID = "task_id";
    /**
     * Key: trial count.
     */
    private static final String TRIALS = "trials";
    /**
     * Key: override public union-size upper bound.
     */
    private static final String TAU_MAX = "tau_max";
    /**
     * Shared key: every process reads this same input file. Mainly useful for smoke tests.
     */
    private static final String INPUT_FILE = "input_file";
    /**
     * Per-party input file suffix.
     */
    private static final String INPUT_FILE_SUFFIX = "_input_file";
    /**
     * Key: whether to write the opened union elements to a file.
     */
    private static final String WRITE_UNION = "write_union";

    private final Properties properties;
    private final String ownName;
    private final Rpc ownRpc;
    private final boolean parallel;
    private final boolean malicious;
    private final boolean useMac;
    private final boolean useSimMt;
    private final String inputFile;
    private final boolean writeUnion;
    private final int[] setSizes;
    private final double overlap;
    private final double alpha;
    private final int hashNum;
    private final long hashSeed;
    private final int maxPeelRounds;
    private final int maxHashSeedRetries;
    private final int maxBatchCells;
    private final int crBufferByteSize;
    private final int taskIdBase;
    private final int trials;
    private final Integer tauMax;
    private final String appendString;
    private final String filePathString;
    private final StopWatch stopWatch;

    public MpSogsMpsuNettyMain(Properties properties, String ownName) {
        this.properties = properties;
        this.ownName = ownName;
        String ptoType = MainPtoConfigUtils.readPtoType(properties);
        if (!PTO_TYPE_NAME.equals(ptoType)) {
            throw new IllegalArgumentException("Invalid " + MainPtoConfigUtils.PTO_TYPE_KEY + ": " + ptoType);
        }
        appendString = MainPtoConfigUtils.readAppendString(properties);
        filePathString = MainPtoConfigUtils.readFileFolderName(properties);
        File outputFolder = new File(filePathString);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            throw new IllegalStateException("failed to create output folder: " + outputFolder.getAbsolutePath());
        }
        ownRpc = RpcPropertiesUtils.readNettyRpcWithOwnName(properties, ownName, "first", "second", "third");
        parallel = PropertiesUtils.readBoolean(properties, PARALLEL, true);
        malicious = PropertiesUtils.readBoolean(properties, IS_MALICIOUS, false);
        useMac = PropertiesUtils.readBoolean(properties, VERIFY_WITH_MAC, false);
        useSimMt = PropertiesUtils.readBoolean(properties, USE_MT_SIM_MODE, false);
        inputFile = readLocalInputFile(properties, ownName);
        writeUnion = PropertiesUtils.readBoolean(properties, WRITE_UNION, false);
        setSizes = inputFile == null ? readSetSizes(properties) : new int[0];
        overlap = PropertiesUtils.readDouble(properties, COMMON_OVERLAP, 0.5);
        alpha = PropertiesUtils.readDouble(properties, ALPHA, MpSogsMpsuParams.DEFAULT_ALPHA);
        hashNum = PropertiesUtils.readInt(properties, HASH_NUM, MpSogsMpsuParams.DEFAULT_HASH_NUM);
        hashSeed = Long.parseLong(PropertiesUtils.readString(
            properties, HASH_SEED, Long.toString(MpSogsMpsuParams.DEFAULT_HASH_SEED)
        ));
        maxPeelRounds = PropertiesUtils.readInt(properties, MAX_PEEL_ROUNDS, 10_000);
        maxHashSeedRetries = PropertiesUtils.readInt(properties, MAX_HASH_SEED_RETRIES, 4);
        maxBatchCells = PropertiesUtils.readInt(properties, MAX_BATCH_CELLS,
            MpSogsMpsuConfig.DEFAULT_MAX_BATCH_CELLS);
        crBufferByteSize = PropertiesUtils.readInt(properties, CR_BUFFER_BYTE_SIZE, 1 << 24);
        if (crBufferByteSize <= 0 || (crBufferByteSize & 15) != 0) {
            throw new IllegalArgumentException(CR_BUFFER_BYTE_SIZE + " must be positive and 16-byte aligned: "
                + crBufferByteSize);
        }
        taskIdBase = PropertiesUtils.readInt(properties, TASK_ID, 1_000_000);
        trials = PropertiesUtils.readInt(properties, TRIALS, 1);
        tauMax = PropertiesUtils.containsKeyword(properties, TAU_MAX)
            ? PropertiesUtils.readInt(properties, TAU_MAX)
            : null;
        stopWatch = new StopWatch();
    }

    public static void main(String[] args) throws Exception {
        PropertiesUtils.loadLog4jProperties();
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: MpSogsMpsuNettyMain <config-file-or-dir> <first|second|third>");
        }
        File inputFile = new File(args[0]);
        String ownName = args[1];
        List<String> configFiles = collectConfigFiles(inputFile);
        for (String configFile : configFiles) {
            Properties properties = PropertiesUtils.loadProperties(configFile);
            new MpSogsMpsuNettyMain(properties, ownName).runNetty();
        }
        System.exit(0);
    }

    public void runNetty() throws IOException {
        LOGGER.info("{} create MP-SOGS result file", ownRpc.ownParty().getPartyName());
        String filePath = filePathString + PTO_TYPE_NAME
            + "_" + appendString
            + "_" + ownRpc.ownParty().getPartyId()
            + "_" + ForkJoinPool.getCommonPoolParallelism()
            + ".output";
        try (FileWriter fileWriter = new FileWriter(filePath);
             PrintWriter printWriter = new PrintWriter(fileWriter, true)) {
            printWriter.println(header());
            ownRpc.connect();
            int taskId = taskIdBase;
            if (inputFile != null) {
                Set<Long> localInput = readInputFile(inputFile);
                if (tauMax == null) {
                    throw new IllegalArgumentException("tau_max must be set when using input-file mode");
                }
                runOneTest(taskId, 0, localInput, tauMax, printWriter);
            } else {
                for (int trialIndex = 0; trialIndex < trials; trialIndex++) {
                    for (int setSize : setSizes) {
                        Set<Long> localInput = generateInputForParty(
                            PARTY_NUM, ownRpc.ownParty().getPartyId(), setSize, overlap, trialIndex
                        );
                        runOneTest(taskId, trialIndex, localInput, expectedUnionSize(PARTY_NUM, setSize, overlap),
                            printWriter);
                        taskId++;
                    }
                }
            }
            ownRpc.disconnect();
        }
    }

    private void runOneTest(int taskId, int trialIndex, Set<Long> localInput, int unionUpperBound,
                            PrintWriter printWriter) {
        LOGGER.info("{} run MP-SOGS: trial={}, localSetSize={}, tauMax={}, alpha={}, k={}, parallel={}",
            ownRpc.ownParty().getPartyName(), trialIndex, localInput.size(), unionUpperBound, alpha, hashNum, parallel);
        TripletZ2cParty z2cParty = createZ2cParty(ownRpc, malicious, useMac, useSimMt);
        z2cParty.setTaskId(taskId);
        z2cParty.setParallel(parallel);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(PARTY_NUM, unionUpperBound)
            .setAlpha(alpha)
            .setHashNum(hashNum)
            .setHashSeed(hashSeed + trialIndex)
            .setMaxPeelRounds(maxPeelRounds)
            .build();
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.ABB3)
            .setMaxHashSeedRetries(maxHashSeedRetries)
            .setMaxBatchCells(maxBatchCells)
            .build();
        ownRpc.synchronize();
        ownRpc.reset();
        stopWatch.start();
        try {
            z2cParty.init();
        } catch (Exception e) {
            throw new IllegalStateException("failed to init ABB3 Z2 party", e);
        }
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long initPacketNum = ownRpc.getSendDataPacketNum();
        long initPayloadBytes = ownRpc.getPayloadByteLength();
        long initSendBytes = ownRpc.getSendByteLength();
        ownRpc.synchronize();
        ownRpc.reset();
        stopWatch.start();
        MpSogsTranscript transcript = new Abb3MpSogsMpsuPartyRunner(z2cParty, config).runAfterInit(localInput);
        stopWatch.stop();
        long ptoTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long ptoPacketNum = ownRpc.getSendDataPacketNum();
        long ptoPayloadBytes = ownRpc.getPayloadByteLength();
        long ptoSendBytes = ownRpc.getSendByteLength();
        printWriter.println(toLine(
            trialIndex, localInput.size(), unionUpperBound, transcript, initTime, initPacketNum,
            initPayloadBytes, initSendBytes, ptoTime, ptoPacketNum, ptoPayloadBytes, ptoSendBytes
        ));
        if (writeUnion) {
            writeUnionOutput(taskId, trialIndex, transcript);
        }
        ownRpc.synchronize();
        ownRpc.reset();
        z2cParty.destroy();
    }

    private TripletZ2cParty createZ2cParty(Rpc rpc, boolean malicious, boolean useMac, boolean useSimMt) {
        Aby3Z2cConfig z2cConfig = new Aby3Z2cConfig.Builder(malicious).build();
        S3pcCrProviderConfig.Builder crProviderConfigBuilder = new S3pcCrProviderConfig.Builder();
        crProviderConfigBuilder.setBufferByteSize(crBufferByteSize);
        TripletProviderConfig.Builder providerConfigBuilder = new TripletProviderConfig.Builder(malicious)
            .setCrProviderConfig(crProviderConfigBuilder.build());
        TripletProviderConfig providerConfig = (malicious && useSimMt)
            ? providerConfigBuilder
            .setRpZ2MtpConfig(RpMtProviderFactory.createZ2MtpConfigTestMode())
            .setRpZl64MtpConfig(RpMtProviderFactory.createZl64MtpConfigTestMode())
            .build()
            : providerConfigBuilder.build();
        if (malicious && useMac) {
            LOGGER.warn("use_mac is read for compatibility; direct Z2 party creation uses the ABB3 Z2 config.");
        }
        return Aby3Z2cFactory.createParty(rpc, z2cConfig, new TripletProvider(rpc, providerConfig));
    }

    private static List<String> collectConfigFiles(File inputFile) {
        if (!inputFile.isDirectory()) {
            return List.of(inputFile.getPath());
        }
        File[] files = inputFile.listFiles();
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
            .filter(file -> !file.isDirectory())
            .map(File::getPath)
            .filter(path -> path.endsWith(".conf"))
            .sorted()
            .toList();
    }

    private static int[] readSetSizes(Properties properties) {
        if (PropertiesUtils.containsKeyword(properties, SET_SIZE)) {
            return PropertiesUtils.readIntArray(properties, SET_SIZE);
        }
        int[] logSetSizes = PropertiesUtils.readLogIntArray(properties, LOG_SET_SIZE);
        return Arrays.stream(logSetSizes)
            .map(logSetSize -> 1 << logSetSize)
            .toArray();
    }

    private static String readLocalInputFile(Properties properties, String ownName) {
        String ownInputFileKey = ownName + INPUT_FILE_SUFFIX;
        if (PropertiesUtils.containsKeyword(properties, ownInputFileKey)) {
            return PropertiesUtils.readString(properties, ownInputFileKey);
        }
        if (PropertiesUtils.containsKeyword(properties, INPUT_FILE)) {
            return PropertiesUtils.readString(properties, INPUT_FILE);
        }
        return null;
    }

    private static Set<Long> readInputFile(String inputFile) throws IOException {
        Set<Long> input = new HashSet<>();
        for (String line : Files.readAllLines(Path.of(inputFile))) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            input.add(parseLong(value));
        }
        return input;
    }

    private static long parseLong(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return Long.parseUnsignedLong(value.substring(2), 16);
        }
        return Long.parseLong(value);
    }

    static Set<Long> generateInputForParty(int partyNum, int partyIndex, int setSize, double commonOverlap,
                                           int trialIndex) {
        if (partyNum != PARTY_NUM) {
            throw new IllegalArgumentException("current ABB3 backend supports exactly 3 parties");
        }
        if (partyIndex < 0 || partyIndex >= partyNum) {
            throw new IllegalArgumentException("invalid partyIndex: " + partyIndex);
        }
        int commonCount = (int) Math.round(setSize * commonOverlap);
        int uniqueCount = setSize - commonCount;
        long trialOffset = ((long) trialIndex) << 48;
        Set<Long> input = new HashSet<>();
        for (long value = 1; value <= commonCount; value++) {
            input.add(trialOffset + value);
        }
        long start = trialOffset + commonCount + (long) partyIndex * uniqueCount + 1;
        for (long value = start; value < start + uniqueCount; value++) {
            input.add(value);
        }
        return input;
    }

    private static int expectedUnionSize(int partyNum, int setSize, double commonOverlap) {
        int commonCount = (int) Math.round(setSize * commonOverlap);
        int uniqueCount = setSize - commonCount;
        return commonCount + partyNum * uniqueCount;
    }

    private void writeUnionOutput(int taskId, int trialIndex, MpSogsTranscript transcript) {
        String unionFilePath = filePathString + PTO_TYPE_NAME
            + "_" + appendString
            + "_" + ownRpc.ownParty().getPartyId()
            + "_trial_" + trialIndex
            + "_task_" + taskId
            + ".union";
        try (FileWriter fileWriter = new FileWriter(unionFilePath);
             PrintWriter printWriter = new PrintWriter(fileWriter, true)) {
            for (long value : new TreeSet<>(transcript.getUnionOutput())) {
                printWriter.println(Long.toUnsignedString(value));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to write MP-SOGS union output: " + unionFilePath, e);
        }
    }

    private static String header() {
        return "Trial\tParty ID\tSet Size\tUnion Upper Bound\tOverlap\tAlpha\tK\tMax Hash Seed Retries"
            + "\tMax Batch Cells\tCR Buffer Bytes\tHash Seed Attempts\tSuccess\tRounds\tUpeel Calls\tDuplicate Openings"
            + "\tInit Time(ms)\tInit DataPacket Num\tInit Payload Bytes(B)\tInit Send Bytes(B)"
            + "\tPto Time(ms)\tPto DataPacket Num\tPto Payload Bytes(B)\tPto Send Bytes(B)\tFailure Reason";
    }

    private String toLine(int trialIndex, int setSize, int expectedUnionSize, MpSogsTranscript transcript,
                          long initTime, long initPacketNum, long initPayloadBytes, long initSendBytes,
                          long ptoTime, long ptoPacketNum, long ptoPayloadBytes, long ptoSendBytes) {
        List<String> columns = new ArrayList<>();
        columns.add(Integer.toString(trialIndex));
        columns.add(Integer.toString(ownRpc.ownParty().getPartyId()));
        columns.add(Integer.toString(setSize));
        columns.add(Integer.toString(expectedUnionSize));
        columns.add(Double.toString(overlap));
        columns.add(Double.toString(alpha));
        columns.add(Integer.toString(hashNum));
        columns.add(Integer.toString(maxHashSeedRetries));
        columns.add(Integer.toString(maxBatchCells));
        columns.add(Integer.toString(crBufferByteSize));
        columns.add(Integer.toString(transcript.getHashSeedAttempts()));
        columns.add(Boolean.toString(transcript.isSuccess()));
        columns.add(Integer.toString(transcript.getRoundNum()));
        columns.add(Long.toString(transcript.getUpeelCalls()));
        columns.add(Integer.toString(transcript.getDuplicateOpenings()));
        columns.add(Long.toString(initTime));
        columns.add(Long.toString(initPacketNum));
        columns.add(Long.toString(initPayloadBytes));
        columns.add(Long.toString(initSendBytes));
        columns.add(Long.toString(ptoTime));
        columns.add(Long.toString(ptoPacketNum));
        columns.add(Long.toString(ptoPayloadBytes));
        columns.add(Long.toString(ptoSendBytes));
        columns.add(transcript.getFailureReason() == null ? "" : transcript.getFailureReason());
        return String.join("\t", columns);
    }
}
