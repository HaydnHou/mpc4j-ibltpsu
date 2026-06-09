package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuMain;
import edu.alibaba.mpc4j.s2pc.pso.PsoUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.iblt.IbltPsuConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IBLT-PSU test.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class IbltPsuTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(IbltPsuTest.class);
    /**
     * default element byte length.
     */
    private static final int DEFAULT_ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * small element byte length.
     */
    private static final int SMALL_ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * large element byte length.
     */
    private static final int LARGE_ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH * 2;

    public IbltPsuTest() {
        super(PsuFactory.PsuType.IBLT.name());
    }

    @Test
    public void test2() {
        testPto(2, 2, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void test10() {
        testPto(10, 10, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testParallel10() {
        testPto(10, 10, DEFAULT_ELEMENT_BYTE_LENGTH, true);
    }

    @Test
    public void testSmallElementByteLength() {
        testPto(10, 10, SMALL_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testLargeElementByteLength() {
        testPto(10, 10, LARGE_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testUnbalanced() {
        testPto(20, 6, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testSameSet() {
        Set<ByteBuffer> serverSet = createElementSet(1, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> clientSet = cloneSet(serverSet);
        testPto(serverSet, clientSet, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testDisjointSet() {
        Set<ByteBuffer> serverSet = createElementSet(1, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> clientSet = createElementSet(100, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        testPto(serverSet, clientSet, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testZeroElement() {
        int elementByteLength = DEFAULT_ELEMENT_BYTE_LENGTH;
        Set<ByteBuffer> serverSet = new HashSet<>();
        Set<ByteBuffer> clientSet = new HashSet<>();
        byte[] zero = new byte[elementByteLength];
        byte[] common = new byte[elementByteLength];
        common[0] = 1;
        byte[] serverOnly = new byte[elementByteLength];
        serverOnly[0] = 2;
        byte[] clientOnly = new byte[elementByteLength];
        clientOnly[0] = 3;
        serverSet.add(ByteBuffer.wrap(zero));
        serverSet.add(ByteBuffer.wrap(common));
        serverSet.add(ByteBuffer.wrap(serverOnly));
        clientSet.add(ByteBuffer.wrap(zero.clone()));
        clientSet.add(ByteBuffer.wrap(common.clone()));
        clientSet.add(ByteBuffer.wrap(clientOnly));
        testPto(serverSet, clientSet, elementByteLength, false);
    }

    @Test
    public void testCreateConfig() {
        Properties properties = new Properties();
        properties.setProperty(PsuMain.PTO_NAME_KEY, PsuFactory.PsuType.IBLT.name());
        IbltPsuConfig defaultConfig = (IbltPsuConfig) PsuConfigUtils.createConfig(properties);
        Assert.assertEquals(PsuFactory.PsuType.IBLT, defaultConfig.getPtoType());
    }

    @Test
    public void testRepeatSameInstance() throws MpcAbortException, InterruptedException {
        IbltPsuConfig config = new IbltPsuConfig.Builder().build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);
        Set<ByteBuffer> serverSet0 = createElementSet(1, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> clientSet0 = createElementSet(5, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> serverSet1 = createElementSet(100, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> clientSet1 = createElementSet(105, 10, DEFAULT_ELEMENT_BYTE_LENGTH);
        try {
            runInitializedPto(server, client, serverSet0, clientSet0, DEFAULT_ELEMENT_BYTE_LENGTH, true);
            runInitializedPto(server, client, serverSet1, clientSet1, DEFAULT_ELEMENT_BYTE_LENGTH, false);
        } finally {
            new Thread(server::destroy).start();
            new Thread(client::destroy).start();
        }
    }

    private void testPto(int serverSize, int clientSize, int elementByteLength, boolean parallel) {
        ArrayList<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
        testPto(sets.get(0), sets.get(1), elementByteLength, parallel);
    }

    private void testPto(int serverSize, int clientSize, int elementByteLength, boolean parallel,
                         IbltPsuConfig config) {
        ArrayList<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
        testPto(sets.get(0), sets.get(1), elementByteLength, parallel, config);
    }

    private void testPto(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet,
                         int elementByteLength, boolean parallel) {
        testPto(serverSet, clientSet, elementByteLength, parallel, new IbltPsuConfig.Builder().build());
    }

    private void testPto(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet,
                         int elementByteLength, boolean parallel, IbltPsuConfig config) {
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);
        try {
            LOGGER.info("-----test {}, server_size = {}, client_size = {}-----",
                server.getPtoDesc().getPtoName(), serverSet.size(), clientSet.size()
            );
            PsuServerThread serverThread = new PsuServerThread(
                server, serverSet, clientSet.size(), elementByteLength
            );
            PsuClientThread clientThread = new PsuClientThread(
                client, clientSet, serverSet.size(), elementByteLength
            );
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            serverThread.start();
            clientThread.start();
            serverThread.join();
            clientThread.join();
            stopWatch.stop();
            long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
            stopWatch.reset();
            assertOutput(serverSet, clientSet, clientThread.getClientOutput());
            printAndResetRpc(time);
            new Thread(server::destroy).start();
            new Thread(client::destroy).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void assertOutput(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, PsuClientOutput clientOutput) {
        Set<ByteBuffer> expectIntersectionSet = new HashSet<>(serverSet);
        expectIntersectionSet.retainAll(clientSet);
        Set<ByteBuffer> expectUnionSet = new HashSet<>(serverSet);
        expectUnionSet.addAll(clientSet);
        Assert.assertEquals(expectIntersectionSet.size(), clientOutput.getPsiCa());
        Set<ByteBuffer> actualUnionSet = clientOutput.getUnion();
        Assert.assertTrue(actualUnionSet.containsAll(expectUnionSet));
        Assert.assertTrue(expectUnionSet.containsAll(actualUnionSet));
    }

    private void runInitializedPto(PsuServer server, PsuClient client, Set<ByteBuffer> serverSet,
                                   Set<ByteBuffer> clientSet, int elementByteLength, boolean init)
        throws MpcAbortException, InterruptedException {
        AtomicReference<Throwable> serverThrowable = new AtomicReference<>();
        AtomicReference<Throwable> clientThrowable = new AtomicReference<>();
        AtomicReference<PsuClientOutput> clientOutput = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                if (init) {
                    server.init(serverSet.size(), clientSet.size());
                }
                server.psu(serverSet, clientSet.size(), elementByteLength);
            } catch (Throwable throwable) {
                serverThrowable.set(throwable);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                if (init) {
                    client.init(clientSet.size(), serverSet.size());
                }
                clientOutput.set(client.psu(clientSet, serverSet.size(), elementByteLength));
            } catch (Throwable throwable) {
                clientThrowable.set(throwable);
            }
        });
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();
        if (serverThrowable.get() instanceof MpcAbortException) {
            throw (MpcAbortException) serverThrowable.get();
        }
        if (clientThrowable.get() instanceof MpcAbortException) {
            throw (MpcAbortException) clientThrowable.get();
        }
        Assert.assertNull(serverThrowable.get());
        Assert.assertNull(clientThrowable.get());
        assertOutput(serverSet, clientSet, clientOutput.get());
    }

    private Set<ByteBuffer> createElementSet(int start, int size, int elementByteLength) {
        Set<ByteBuffer> set = new HashSet<>();
        for (int i = 0; i < size; i++) {
            ByteBuffer element = ByteBuffer.allocate(elementByteLength);
            element.putInt(start + i);
            set.add(ByteBuffer.wrap(element.array()));
        }
        return set;
    }

    private Set<ByteBuffer> cloneSet(Set<ByteBuffer> set) {
        Set<ByteBuffer> cloneSet = new HashSet<>();
        for (ByteBuffer element : set) {
            cloneSet.add(ByteBuffer.wrap(element.array().clone()));
        }
        return cloneSet;
    }
}
