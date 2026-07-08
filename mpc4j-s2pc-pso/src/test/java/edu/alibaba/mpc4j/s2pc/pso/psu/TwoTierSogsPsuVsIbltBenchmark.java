package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.PsoUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.iblt.IbltPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Balanced 2-party benchmark for IBLT-H5 and two-tier SOGS-PSU.
 *
 * <p>This class intentionally does not end with "Test", so it only runs when explicitly selected by Maven.</p>
 *
 * @author donghai hou
 * @date 2026/07/06
 */
public class TwoTierSogsPsuVsIbltBenchmark extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = 16;
    /**
     * Warmup set size.
     */
    private static final int WARMUP_SET_SIZE = 1 << 10;
    /**
     * Two-tier auxiliary vertex count.
     */
    private static final int AUXILIARY_VERTEX_COUNT = 4098;
    /**
     * Default output file.
     */
    private static final Path DEFAULT_OUTPUT = Path.of(
        "/Users/haydnhou/Desktop/psux_ibltcodex/BPW/two_tier_sogs_2p_psu_vs_iblt.csv"
    );

    public TwoTierSogsPsuVsIbltBenchmark() {
        super("TWO_TIER_SOGS_PSU_VS_IBLT");
    }

    @Test
    public void benchmarkSizes() throws Exception {
        int[] logSizes = parseLogSizes();
        Path outputPath = Path.of(System.getProperty("output", DEFAULT_OUTPUT.toString()));
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write(String.join(",",
                "log_n",
                "n",
                "protocol",
                "offline_ms",
                "online_ms",
                "total_ms",
                "offline_send_bytes",
                "online_send_bytes",
                "total_send_bytes",
                "server_init_ms",
                "client_init_ms",
                "server_pto_ms",
                "client_pto_ms",
                "server_init_send_bytes",
                "client_init_send_bytes",
                "server_pto_send_bytes",
                "client_pto_send_bytes",
                "status",
                "error"
            ));
            writer.newLine();
            for (int logSize : logSizes) {
                int setSize = 1 << logSize;
                ArrayList<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(
                    setSize, setSize, ELEMENT_BYTE_LENGTH
                );
                List<BenchmarkCase> benchmarkCases = List.of(
                    new BenchmarkCase(
                        "IBLT_H5",
                        new IbltPsuConfig.Builder()
                            .setIbltMultiplier(H5LongIblt.DEFAULT_MULTIPLIER)
                            .build()
                    ),
                    new BenchmarkCase(
                        "TWO_TIER_SOGS_D3_AUX4096",
                        new SogsPsuConfig.Builder()
                            .setSogsAlpha(1.25)
                            .setSogsDegree(3)
                            .setTwoTier(true)
                            .setAuxiliaryDegree(3)
                            .setAuxiliaryVertexCount(AUXILIARY_VERTEX_COUNT)
                            .build()
                    )
                );
                for (BenchmarkCase benchmarkCase : benchmarkCases) {
                    BenchmarkResult result;
                    try {
                        warmup(benchmarkCase.config());
                        result = runOnce(
                            benchmarkCase.protocol(), benchmarkCase.config(), sets.get(0), sets.get(1), true
                        );
                        writeResult(writer, logSize, setSize, result, "OK", "");
                        printResult(logSize, setSize, result);
                    } catch (Throwable throwable) {
                        result = BenchmarkResult.failed(benchmarkCase.protocol());
                        String error = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
                        writeResult(writer, logSize, setSize, result, "FAIL", sanitizeCsv(error));
                        printFailure(logSize, setSize, benchmarkCase.protocol(), error);
                    }
                    writer.flush();
                    firstRpc.reset();
                    secondRpc.reset();
                    System.gc();
                }
            }
        }
        System.out.println("CSV_OUTPUT\t" + outputPath);
    }

    private int[] parseLogSizes() {
        String raw = System.getProperty("sizes", "14,16,18,20");
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .mapToInt(Integer::parseInt)
            .toArray();
    }

    private void warmup(PsuConfig config) throws Exception {
        ArrayList<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(
            WARMUP_SET_SIZE, WARMUP_SET_SIZE, ELEMENT_BYTE_LENGTH
        );
        runOnce("WARMUP", config, sets.get(0), sets.get(1), false);
        firstRpc.reset();
        secondRpc.reset();
        System.gc();
    }

    private BenchmarkResult runOnce(String protocol, PsuConfig config, Set<ByteBuffer> serverSet,
                                    Set<ByteBuffer> clientSet, boolean verifyOutput)
        throws Exception {
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(true);
        client.setParallel(true);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);
        try {
            PartyTimes initTimes = runInit(server, client, serverSet.size(), clientSet.size());
            long serverInitSendBytes = firstRpc.getSendByteLength();
            long clientInitSendBytes = secondRpc.getSendByteLength();
            firstRpc.reset();
            secondRpc.reset();

            PtoResult ptoResult = runPto(server, client, serverSet, clientSet);
            long serverPtoSendBytes = firstRpc.getSendByteLength();
            long clientPtoSendBytes = secondRpc.getSendByteLength();
            firstRpc.reset();
            secondRpc.reset();

            if (verifyOutput) {
                assertOutput(serverSet, clientSet, ptoResult.clientOutput());
            }
            return new BenchmarkResult(
                protocol,
                Math.max(initTimes.serverTimeMs(), initTimes.clientTimeMs()),
                Math.max(ptoResult.times().serverTimeMs(), ptoResult.times().clientTimeMs()),
                serverInitSendBytes + clientInitSendBytes,
                serverPtoSendBytes + clientPtoSendBytes,
                initTimes.serverTimeMs(),
                initTimes.clientTimeMs(),
                ptoResult.times().serverTimeMs(),
                ptoResult.times().clientTimeMs(),
                serverInitSendBytes,
                clientInitSendBytes,
                serverPtoSendBytes,
                clientPtoSendBytes
            );
        } finally {
            server.destroy();
            client.destroy();
        }
    }

    private PartyTimes runInit(PsuServer server, PsuClient client, int serverSetSize, int clientSetSize)
        throws InterruptedException, MpcAbortException {
        AtomicReference<Throwable> serverThrowable = new AtomicReference<>();
        AtomicReference<Throwable> clientThrowable = new AtomicReference<>();
        AtomicReference<Long> serverTimeMs = new AtomicReference<>(0L);
        AtomicReference<Long> clientTimeMs = new AtomicReference<>(0L);
        Thread serverThread = new Thread(() -> {
            try {
                serverTimeMs.set(timeMs(() -> server.init(serverSetSize, clientSetSize)));
            } catch (Throwable throwable) {
                serverThrowable.set(throwable);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                clientTimeMs.set(timeMs(() -> client.init(clientSetSize, serverSetSize)));
            } catch (Throwable throwable) {
                clientThrowable.set(throwable);
            }
        });
        serverThread.start();
        clientThread.start();
        waitForThreads("init", serverThread, clientThread, serverThrowable, clientThrowable);
        return new PartyTimes(serverTimeMs.get(), clientTimeMs.get());
    }

    private PtoResult runPto(PsuServer server, PsuClient client, Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet)
        throws InterruptedException, MpcAbortException {
        AtomicReference<Throwable> serverThrowable = new AtomicReference<>();
        AtomicReference<Throwable> clientThrowable = new AtomicReference<>();
        AtomicReference<Long> serverTimeMs = new AtomicReference<>(0L);
        AtomicReference<Long> clientTimeMs = new AtomicReference<>(0L);
        AtomicReference<PsuClientOutput> clientOutput = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                serverTimeMs.set(timeMs(() -> server.psu(serverSet, clientSet.size(), ELEMENT_BYTE_LENGTH)));
            } catch (Throwable throwable) {
                serverThrowable.set(throwable);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                clientTimeMs.set(timeMs(() -> {
                    PsuClientOutput output = client.psu(clientSet, serverSet.size(), ELEMENT_BYTE_LENGTH);
                    clientOutput.set(output);
                }));
            } catch (Throwable throwable) {
                clientThrowable.set(throwable);
            }
        });
        serverThread.start();
        clientThread.start();
        waitForThreads("pto", serverThread, clientThread, serverThrowable, clientThrowable);
        return new PtoResult(new PartyTimes(serverTimeMs.get(), clientTimeMs.get()), clientOutput.get());
    }

    private void waitForThreads(String phase, Thread serverThread, Thread clientThread,
                                AtomicReference<Throwable> serverThrowable, AtomicReference<Throwable> clientThrowable)
        throws InterruptedException, MpcAbortException {
        long timeoutMs = Long.getLong("timeoutMs", TimeUnit.MINUTES.toMillis(10));
        long deadline = System.currentTimeMillis() + timeoutMs;
        while ((serverThread.isAlive() || clientThread.isAlive()) && System.currentTimeMillis() < deadline) {
            if (serverThrowable.get() != null || clientThrowable.get() != null) {
                break;
            }
            serverThread.join(100);
            clientThread.join(100);
        }
        if (serverThrowable.get() != null || clientThrowable.get() != null) {
            serverThread.interrupt();
            clientThread.interrupt();
            serverThread.join(1000);
            clientThread.join(1000);
            throwIfNeeded(serverThrowable.get() != null ? serverThrowable.get() : clientThrowable.get());
        }
        if (serverThread.isAlive() || clientThread.isAlive()) {
            serverThread.interrupt();
            clientThread.interrupt();
            serverThread.join(1000);
            clientThread.join(1000);
            throw new IllegalStateException("benchmark " + phase + " timeout after " + timeoutMs + " ms");
        }
    }

    private long timeMs(ThrowingRunnable runnable) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        runnable.run();
        stopWatch.stop();
        return stopWatch.getTime(TimeUnit.MILLISECONDS);
    }

    private void throwIfNeeded(Throwable throwable) throws MpcAbortException {
        if (throwable == null) {
            return;
        }
        if (throwable instanceof MpcAbortException mpcAbortException) {
            throw mpcAbortException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(throwable);
    }

    private void assertOutput(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, PsuClientOutput clientOutput) {
        Set<ByteBuffer> expectUnionSet = new HashSet<>(serverSet);
        expectUnionSet.addAll(clientSet);
        Assert.assertEquals(expectUnionSet.size(), clientOutput.getUnion().size());
        for (ByteBuffer expected : expectUnionSet) {
            Assert.assertTrue(clientOutput.getUnion().contains(ByteBuffer.wrap(BytesUtils.clone(expected.array()))));
        }
    }

    private void writeResult(BufferedWriter writer, int logSize, int setSize, BenchmarkResult result,
                             String status, String error)
        throws IOException {
        writer.write(String.join(",",
            Integer.toString(logSize),
            Integer.toString(setSize),
            result.protocol(),
            Long.toString(result.offlineTimeMs()),
            Long.toString(result.onlineTimeMs()),
            Long.toString(result.totalTimeMs()),
            Long.toString(result.offlineSendBytes()),
            Long.toString(result.onlineSendBytes()),
            Long.toString(result.totalSendBytes()),
            Long.toString(result.serverInitMs()),
            Long.toString(result.clientInitMs()),
            Long.toString(result.serverPtoMs()),
            Long.toString(result.clientPtoMs()),
            Long.toString(result.serverInitSendBytes()),
            Long.toString(result.clientInitSendBytes()),
            Long.toString(result.serverPtoSendBytes()),
            Long.toString(result.clientPtoSendBytes()),
            status,
            error
        ));
        writer.newLine();
    }

    private void printResult(int logSize, int setSize, BenchmarkResult result) {
        System.out.println(String.join("\t", Arrays.asList(
            "BENCHMARK",
            "logN=" + logSize,
            "n=" + setSize,
            "protocol=" + result.protocol(),
            "offlineMs=" + result.offlineTimeMs(),
            "onlineMs=" + result.onlineTimeMs(),
            "totalMs=" + result.totalTimeMs(),
            "offlineSendBytes=" + result.offlineSendBytes(),
            "onlineSendBytes=" + result.onlineSendBytes(),
            "totalSendBytes=" + result.totalSendBytes()
        )));
    }

    private void printFailure(int logSize, int setSize, String protocol, String error) {
        System.out.println(String.join("\t", Arrays.asList(
            "BENCHMARK",
            "logN=" + logSize,
            "n=" + setSize,
            "protocol=" + protocol,
            "status=FAIL",
            "error=" + error
        )));
    }

    private String sanitizeCsv(String value) {
        return value == null ? "" : value.replace(',', ';').replace('\n', ' ');
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record BenchmarkCase(String protocol, PsuConfig config) {
        // empty
    }

    private record PartyTimes(long serverTimeMs, long clientTimeMs) {
        // empty
    }

    private record PtoResult(PartyTimes times, PsuClientOutput clientOutput) {
        // empty
    }

    private record BenchmarkResult(
        String protocol,
        long offlineTimeMs,
        long onlineTimeMs,
        long offlineSendBytes,
        long onlineSendBytes,
        long serverInitMs,
        long clientInitMs,
        long serverPtoMs,
        long clientPtoMs,
        long serverInitSendBytes,
        long clientInitSendBytes,
        long serverPtoSendBytes,
        long clientPtoSendBytes
    ) {
        static BenchmarkResult failed(String protocol) {
            return new BenchmarkResult(protocol, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1);
        }

        long totalTimeMs() {
            return offlineTimeMs + onlineTimeMs;
        }

        long totalSendBytes() {
            return offlineSendBytes + onlineSendBytes;
        }
    }
}
