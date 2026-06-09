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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Target-size benchmark for IBLT-PSU H5 and standalone SOGS-PSU.
 *
 * <p>This class intentionally does not end with "Test" so normal Maven discovery does not run it unless selected with
 * {@code -Dtest=IbltPsuBackendTargetBenchmark}.</p>
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class IbltPsuBackendTargetBenchmark extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Large server set size.
     */
    private static final int DEFAULT_SERVER_SET_SIZE = 1 << 18;
    /**
     * Small client set size.
     */
    private static final int DEFAULT_CLIENT_SET_SIZE = 1 << 10;
    /**
     * Warmup set size.
     */
    private static final int DEFAULT_WARMUP_SET_SIZE = 1 << 10;
    /**
     * Element byte length.
     */
    private static final int DEFAULT_ELEMENT_BYTE_LENGTH = 16;
    /**
     * SOGS degree.
     */
    private static final int SOGS_DEGREE = 3;
    /**
     * SOGS alpha.
     */
    private static final double SOGS_ALPHA = 1.25;
    /**
     * Server set size.
     */
    private final int serverSetSize;
    /**
     * Client set size.
     */
    private final int clientSetSize;
    /**
     * Warmup set size.
     */
    private final int warmupSetSize;
    /**
     * Element byte length.
     */
    private final int elementByteLength;

    public IbltPsuBackendTargetBenchmark() {
        super(PsuFactory.PsuType.IBLT.name());
        serverSetSize = Integer.getInteger("serverSetSize", DEFAULT_SERVER_SET_SIZE);
        clientSetSize = Integer.getInteger("clientSetSize", DEFAULT_CLIENT_SET_SIZE);
        warmupSetSize = Integer.getInteger("warmupSetSize", DEFAULT_WARMUP_SET_SIZE);
        elementByteLength = Integer.getInteger("elementByteLength", DEFAULT_ELEMENT_BYTE_LENGTH);
    }

    @Test
    public void benchmarkTargetSize() throws Exception {
        BenchmarkResult h5Result = benchmarkIbltH5();
        BenchmarkResult sogsResult = benchmarkSogsPsu();
        printResult(h5Result);
        printResult(sogsResult);
    }

    private BenchmarkResult benchmarkIbltH5() throws Exception {
        IbltPsuConfig config = new IbltPsuConfig.Builder()
            .setIbltMultiplier(H5LongIblt.DEFAULT_MULTIPLIER)
            .build();
        return benchmarkConfig("IBLT_H5", config);
    }

    private BenchmarkResult benchmarkSogsPsu() throws Exception {
        SogsPsuConfig config = new SogsPsuConfig.Builder()
            .setSogsAlpha(SOGS_ALPHA)
            .setSogsDegree(SOGS_DEGREE)
            .build();
        return benchmarkConfig("SOGS_PSU", config);
    }

    private BenchmarkResult benchmarkConfig(String protocol, PsuConfig config) throws Exception {
        warmup(config);
        firstRpc.reset();
        secondRpc.reset();
        var sets = PsoUtils.generateBytesSets(serverSetSize, clientSetSize, elementByteLength);
        return runOnce(protocol, config, sets.get(0), sets.get(1), true);
    }

    private void warmup(PsuConfig config) throws Exception {
        var sets = PsoUtils.generateBytesSets(warmupSetSize, warmupSetSize, elementByteLength);
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
        serverThread.join();
        clientThread.join();
        throwIfNeeded(serverThrowable.get());
        throwIfNeeded(clientThrowable.get());
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
                serverTimeMs.set(timeMs(() -> server.psu(serverSet, clientSet.size(), elementByteLength)));
            } catch (Throwable throwable) {
                serverThrowable.set(throwable);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                clientTimeMs.set(timeMs(() -> {
                    PsuClientOutput output = client.psu(clientSet, serverSet.size(), elementByteLength);
                    clientOutput.set(output);
                }));
            } catch (Throwable throwable) {
                clientThrowable.set(throwable);
            }
        });
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();
        throwIfNeeded(serverThrowable.get());
        throwIfNeeded(clientThrowable.get());
        return new PtoResult(new PartyTimes(serverTimeMs.get(), clientTimeMs.get()), clientOutput.get());
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

    private void printResult(BenchmarkResult result) {
        System.out.println(String.join("\t", Arrays.asList(
            "BENCHMARK",
            "protocol=" + result.protocol(),
            "server=" + serverSetSize,
            "client=" + clientSetSize,
            "elementByteLength=" + elementByteLength,
            "offlineMs=" + result.offlineTimeMs(),
            "onlineMs=" + result.onlineTimeMs(),
            "totalMs=" + result.totalTimeMs(),
            "offlineSendBytes=" + result.offlineSendBytes(),
            "onlineSendBytes=" + result.onlineSendBytes(),
            "totalSendBytes=" + result.totalSendBytes(),
            "serverInitMs=" + result.serverInitMs(),
            "clientInitMs=" + result.clientInitMs(),
            "serverPtoMs=" + result.serverPtoMs(),
            "clientPtoMs=" + result.clientPtoMs(),
            "serverInitSendBytes=" + result.serverInitSendBytes(),
            "clientInitSendBytes=" + result.clientInitSendBytes(),
            "serverPtoSendBytes=" + result.serverPtoSendBytes(),
            "clientPtoSendBytes=" + result.clientPtoSendBytes()
        )));
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
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
        long totalTimeMs() {
            return offlineTimeMs + onlineTimeMs;
        }

        long totalSendBytes() {
            return offlineSendBytes + onlineSendBytes;
        }
    }
}
