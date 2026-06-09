package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.s2pc.pso.PsoUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuMain;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * SOGS-PSU test.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * default element byte length.
     */
    private static final int DEFAULT_ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;

    public SogsPsuTest() {
        super(PsuFactory.PsuType.SOGS.name());
    }

    @Test
    public void test10() {
        testPto(10, 10, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testUnbalanced() {
        testPto(32, 8, DEFAULT_ELEMENT_BYTE_LENGTH, false);
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
        properties.setProperty(PsuMain.PTO_NAME_KEY, PsuFactory.PsuType.SOGS.name());
        properties.setProperty(PsuConfigUtils.SOGS_ALPHA, "1.25");
        properties.setProperty(PsuConfigUtils.SOGS_DEGREE, "3");
        PsuConfig config = PsuConfigUtils.createConfig(properties);
        Assert.assertEquals(PsuFactory.PsuType.SOGS, config.getPtoType());
        Assert.assertTrue(config instanceof SogsPsuConfig);
        SogsPsuConfig sogsConfig = (SogsPsuConfig) config;
        Assert.assertEquals(1.25, sogsConfig.getSogsAlpha(), 0.0);
        Assert.assertEquals(3, sogsConfig.getSogsDegree());
    }

    private void testPto(int serverSize, int clientSize, int elementByteLength, boolean parallel) {
        ArrayList<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
        testPto(sets.get(0), sets.get(1), elementByteLength, parallel);
    }

    private void testPto(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet,
                         int elementByteLength, boolean parallel) {
        SogsPsuConfig config = new SogsPsuConfig.Builder()
            .setSogsDegree(3)
            .setSogsAlpha(1.45)
            .build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);
        try {
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
}
