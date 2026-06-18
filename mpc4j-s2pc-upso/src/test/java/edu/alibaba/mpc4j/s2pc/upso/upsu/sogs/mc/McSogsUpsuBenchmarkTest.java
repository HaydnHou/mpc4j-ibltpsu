package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.PsoUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.*;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Targeted benchmark for TCL23 UPSU vs MC-SOGS UPSU under the server-output semantics used in this branch.
 *
 * <p>Run one protocol / one size per JVM via system properties so large receiver sets do not accumulate across
 * benchmark rows.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuBenchmarkTest {
    /**
     * Protocol system property.
     */
    private static final String PROTOCOL_PROPERTY = "upsu.bench.protocol";
    /**
     * Receiver/server log-size system property.
     */
    private static final String LOG_N_PROPERTY = "upsu.bench.logN";
    /**
     * Sender/client log-size system property.
     */
    private static final String LOG_M_PROPERTY = "upsu.bench.logM";
    /**
     * Element byte length system property.
     */
    private static final String ELEMENT_BYTE_LENGTH_PROPERTY = "upsu.bench.elementByteLength";
    /**
     * Output CSV system property.
     */
    private static final String OUT_PROPERTY = "upsu.bench.out";
    /**
     * Parallel execution system property.
     */
    private static final String PARALLEL_PROPERTY = "upsu.bench.parallel";
    /**
     * SOGS cell number system property.
     */
    private static final String CELL_NUM_PROPERTY = "upsu.bench.cellNum";
    /**
     * Default output path.
     */
    private static final Path DEFAULT_OUT = Path.of(
        "/Users/haydnhou/Desktop/psux_ibltcodex/BPW/upsu_tcl23_vs_mc_sogs_benchmark.csv"
    );
    /**
     * Secure random.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Test
    public void runOneBenchmark() throws Exception {
        BenchmarkConfig benchmarkConfig = BenchmarkConfig.fromProperties();
        BenchmarkResult result = run(benchmarkConfig);
        appendResult(benchmarkConfig.out(), result);
        Assert.assertTrue(result.success());
    }

    private static BenchmarkResult run(BenchmarkConfig benchmarkConfig) throws Exception {
        int serverYSize = 1 << benchmarkConfig.logN();
        int clientXSize = 1 << benchmarkConfig.logM();
        List<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(
            serverYSize, clientXSize, benchmarkConfig.elementByteLength()
        );
        Set<ByteBuffer> serverY = sets.get(0);
        Set<ByteBuffer> clientX = sets.get(1);
        int expectedPsica = expectedIntersectionSize(clientXSize);
        int expectedUnionSize = serverYSize + clientXSize - expectedPsica;

        MemoryRpcManager rpcManager = new MemoryRpcManager(2);
        Rpc clientRpc = rpcManager.getRpc(0);
        Rpc serverRpc = rpcManager.getRpc(1);
        clientRpc.connect();
        serverRpc.connect();
        try {
            UpsuConfig config = createConfig(benchmarkConfig);
            UpsuSender client = UpsuFactory.createSender(clientRpc, serverRpc.ownParty(), config);
            UpsuReceiver server = UpsuFactory.createReceiver(serverRpc, clientRpc.ownParty(), config);
            int taskId = Math.abs(SECURE_RANDOM.nextInt());
            client.setTaskId(taskId);
            server.setTaskId(taskId);
            client.setParallel(benchmarkConfig.parallel());
            server.setParallel(benchmarkConfig.parallel());

            clientRpc.reset();
            serverRpc.reset();
            AtomicReference<Throwable> initError = new AtomicReference<>();
            long initStart = System.nanoTime();
            Thread clientInitThread = new Thread(
                () -> runClientInit(client, clientXSize, serverYSize, initError), "upsu-bench-client-init"
            );
            Thread serverInitThread = new Thread(
                () -> runServerInit(server, serverY, clientXSize, benchmarkConfig.elementByteLength(), initError),
                "upsu-bench-server-init"
            );
            clientInitThread.start();
            serverInitThread.start();
            clientInitThread.join();
            serverInitThread.join();
            if (initError.get() != null) {
                throw new IllegalStateException(initError.get());
            }
            long initEnd = System.nanoTime();
            PhaseMetrics initMetrics = PhaseMetrics.capture(initStart, initEnd, clientRpc, serverRpc);

            clientRpc.reset();
            serverRpc.reset();
            AtomicReference<Throwable> ptoError = new AtomicReference<>();
            AtomicReference<UpsuReceiverOutput> outputReference = new AtomicReference<>();
            long ptoStart = System.nanoTime();
            Thread clientPtoThread = new Thread(
                () -> runClientPto(client, clientX, benchmarkConfig.elementByteLength(), ptoError),
                "upsu-bench-client-pto"
            );
            Thread serverPtoThread = new Thread(
                () -> runServerPto(server, clientXSize, outputReference, ptoError), "upsu-bench-server-pto"
            );
            clientPtoThread.start();
            serverPtoThread.start();
            clientPtoThread.join();
            serverPtoThread.join();
            if (ptoError.get() != null) {
                throw new IllegalStateException(ptoError.get());
            }
            long ptoEnd = System.nanoTime();
            PhaseMetrics ptoMetrics = PhaseMetrics.capture(ptoStart, ptoEnd, clientRpc, serverRpc);
            UpsuReceiverOutput output = outputReference.get();
            boolean success = output != null
                && output.getPsica() == expectedPsica
                && output.getUnion().size() == expectedUnionSize
                && output.getUnion().containsAll(clientX);

            client.destroy();
            server.destroy();
            return new BenchmarkResult(
                benchmarkConfig.protocol(), benchmarkConfig.logN(), serverYSize, benchmarkConfig.logM(), clientXSize,
                benchmarkConfig.elementByteLength(), benchmarkConfig.parallel(), benchmarkConfig.cellNum(),
                initMetrics, ptoMetrics, output == null ? -1 : output.getPsica(),
                output == null ? -1 : output.getUnion().size(), expectedPsica, expectedUnionSize, success
            );
        } finally {
            clientRpc.disconnect();
            serverRpc.disconnect();
        }
    }

    private static UpsuConfig createConfig(BenchmarkConfig benchmarkConfig) {
        switch (benchmarkConfig.protocol()) {
            case "TCL23":
                return new Tcl23UpsuConfig.Builder().build();
            case "MC_SOGS":
                McSogsUpsuConfig.Builder builder = new McSogsUpsuConfig.Builder();
                if (benchmarkConfig.cellNum() > 0) {
                    builder.setCellNum(benchmarkConfig.cellNum());
                }
                return builder.build();
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + benchmarkConfig.protocol());
        }
    }

    private static void runClientInit(UpsuSender client, int clientXSize, int serverYSize,
                                      AtomicReference<Throwable> error) {
        try {
            client.init(clientXSize, serverYSize);
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        }
    }

    private static void runServerInit(UpsuReceiver server, Set<ByteBuffer> serverY, int clientXSize,
                                      int elementByteLength, AtomicReference<Throwable> error) {
        try {
            server.init(serverY, clientXSize, elementByteLength);
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        }
    }

    private static void runClientPto(UpsuSender client, Set<ByteBuffer> clientX, int elementByteLength,
                                     AtomicReference<Throwable> error) {
        try {
            client.psu(clientX, elementByteLength);
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        }
    }

    private static void runServerPto(UpsuReceiver server, int clientXSize,
                                     AtomicReference<UpsuReceiverOutput> outputReference,
                                     AtomicReference<Throwable> error) {
        try {
            outputReference.set(server.psu(clientXSize));
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        }
    }

    private static int expectedIntersectionSize(int clientXSize) {
        return clientXSize / 4 + clientXSize / 4;
    }

    private static void appendResult(Path out, BenchmarkResult result) throws IOException {
        Files.createDirectories(out.getParent());
        boolean writeHeader = !Files.exists(out) || Files.size(out) == 0;
        StringBuilder builder = new StringBuilder();
        if (writeHeader) {
            builder.append(BenchmarkResult.header()).append(System.lineSeparator());
        }
        builder.append(result.toCsv()).append(System.lineSeparator());
        Files.write(
            out, builder.toString().getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        );
        System.out.println(result.toCsv());
    }

    private record BenchmarkConfig(
        String protocol,
        int logN,
        int logM,
        int elementByteLength,
        boolean parallel,
        int cellNum,
        Path out
    ) {
        static BenchmarkConfig fromProperties() {
            String protocol = System.getProperty(PROTOCOL_PROPERTY, "MC_SOGS").trim();
            int logN = Integer.getInteger(LOG_N_PROPERTY, 18);
            int logM = Integer.getInteger(LOG_M_PROPERTY, 10);
            int elementByteLength = Integer.getInteger(ELEMENT_BYTE_LENGTH_PROPERTY, 16);
            boolean parallel = Boolean.parseBoolean(System.getProperty(PARALLEL_PROPERTY, "true"));
            int cellNum = Integer.getInteger(CELL_NUM_PROPERTY, 0);
            Path out = Path.of(System.getProperty(OUT_PROPERTY, DEFAULT_OUT.toString()));
            return new BenchmarkConfig(protocol, logN, logM, elementByteLength, parallel, cellNum, out);
        }
    }

    private record PhaseMetrics(
        double ms,
        long clientPackets,
        long clientPayloadBytes,
        long clientSendBytes,
        long serverPackets,
        long serverPayloadBytes,
        long serverSendBytes
    ) {
        static PhaseMetrics capture(long startNanos, long endNanos, Rpc clientRpc, Rpc serverRpc) {
            return new PhaseMetrics(
                (endNanos - startNanos) / 1_000_000.0,
                clientRpc.getSendDataPacketNum(), clientRpc.getPayloadByteLength(), clientRpc.getSendByteLength(),
                serverRpc.getSendDataPacketNum(), serverRpc.getPayloadByteLength(), serverRpc.getSendByteLength()
            );
        }

        long totalPackets() {
            return clientPackets + serverPackets;
        }

        long totalPayloadBytes() {
            return clientPayloadBytes + serverPayloadBytes;
        }

        long totalSendBytes() {
            return clientSendBytes + serverSendBytes;
        }
    }

    private record BenchmarkResult(
        String protocol,
        int logN,
        int serverYSize,
        int logM,
        int clientXSize,
        int elementByteLength,
        boolean parallel,
        int cellNum,
        PhaseMetrics init,
        PhaseMetrics online,
        int psica,
        int unionSize,
        int expectedPsica,
        int expectedUnionSize,
        boolean success
    ) {
        static String header() {
            return "protocol,log_n,server_y_size,log_m,client_x_size,element_byte_length,parallel,cell_num,"
                + "init_ms,init_client_send_bytes,init_server_send_bytes,init_total_send_bytes,init_total_packets,"
                + "online_ms,online_client_send_bytes,online_server_send_bytes,online_total_send_bytes,"
                + "online_total_payload_bytes,online_total_packets,total_send_bytes,"
                + "online_mib,total_mib,online_wire_100mbps_ms,online_wire_1gbps_ms,total_wire_100mbps_ms,"
                + "psica,union_size,expected_psica,expected_union_size,success";
        }

        String toCsv() {
            long totalSendBytes = init.totalSendBytes() + online.totalSendBytes();
            return String.format(
                Locale.ROOT,
                "%s,%d,%d,%d,%d,%d,%s,%d,"
                    + "%.3f,%d,%d,%d,%d,"
                    + "%.3f,%d,%d,%d,%d,%d,%d,"
                    + "%.6f,%.6f,%.3f,%.3f,%.3f,"
                    + "%d,%d,%d,%d,%s",
                protocol, logN, serverYSize, logM, clientXSize, elementByteLength, parallel, cellNum,
                init.ms(), init.clientSendBytes(), init.serverSendBytes(), init.totalSendBytes(), init.totalPackets(),
                online.ms(), online.clientSendBytes(), online.serverSendBytes(), online.totalSendBytes(),
                online.totalPayloadBytes(), online.totalPackets(), totalSendBytes,
                bytesToMiB(online.totalSendBytes()), bytesToMiB(totalSendBytes),
                wireMs(online.totalSendBytes(), 100_000_000L), wireMs(online.totalSendBytes(), 1_000_000_000L),
                wireMs(totalSendBytes, 100_000_000L),
                psica, unionSize, expectedPsica, expectedUnionSize, success
            );
        }

        private static double bytesToMiB(long bytes) {
            return bytes / 1024.0 / 1024.0;
        }

        private static double wireMs(long bytes, long bitsPerSecond) {
            return bytes * 8.0 * 1000.0 / bitsPerSecond;
        }
    }
}
