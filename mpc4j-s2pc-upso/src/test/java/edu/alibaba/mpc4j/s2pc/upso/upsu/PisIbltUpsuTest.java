package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltConservativePeelSimulator;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuMode;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuParams;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltSourceSplitTable;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PISF-IBLT enhanced UPSU test.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltUpsuTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    public PisIbltUpsuTest() {
        super(UpsuFactory.UpsuType.PIS_IBLT.name());
    }

    @Test
    public void testCapacityPadding() throws InterruptedException {
        Set<ByteBuffer> senderSet = createElementSet(1, 4);
        Set<ByteBuffer> receiverSet = createElementSet(3, 10);
        testPto(senderSet, receiverSet, 8, false);
    }

    @Test
    public void testParallelCapacityPadding() throws InterruptedException {
        Set<ByteBuffer> senderSet = createElementSet(1, 4);
        Set<ByteBuffer> receiverSet = createElementSet(3, 10);
        testPto(senderSet, receiverSet, 8, true);
    }

    @Test
    public void testFixedReceiverCapacityPadding() throws InterruptedException {
        Set<ByteBuffer> senderSet = createElementSet(1, 6);
        Set<ByteBuffer> receiverSet = createElementSet(4, 5);
        PisIbltUpsuParams params = new PisIbltUpsuParams.Builder(1 << 5, 1 << 4).build();
        testPto(senderSet, receiverSet, params, false);
    }

    @Test
    public void testZeroSenderDelta() throws InterruptedException {
        Set<ByteBuffer> senderSet = new HashSet<>();
        senderSet.add(ByteBuffer.wrap(new byte[ELEMENT_BYTE_LENGTH]));
        senderSet.add(element(1));
        senderSet.add(element(2));
        Set<ByteBuffer> receiverSet = createElementSet(1, 6);
        testPto(senderSet, receiverSet, 6, false);
    }

    @Test
    public void testAllIntersection() throws InterruptedException {
        Set<ByteBuffer> receiverSet = createElementSet(1, 8);
        Set<ByteBuffer> senderSet = createElementSet(3, 4);
        testPto(senderSet, receiverSet, 6, false);
    }

    @Test
    public void testConservativeSourceSplitPeelAudit() {
        PisIbltUpsuParams params = new PisIbltUpsuParams.Builder(1 << 5, 1 << 5)
            .setTableLength(1 << 8)
            .setPeelRound(1 << 8)
            .build();
        PisIbltSourceSplitTable table = new PisIbltSourceSplitTable(params, ELEMENT_BYTE_LENGTH, new byte[16]);
        boolean[] senderReal = new boolean[params.getSenderCapacity()];
        ByteBuffer[] sender = new ByteBuffer[] {
            element(1), element(2), element(3), element(4),
        };
        ByteBuffer[] receiver = new ByteBuffer[] {
            element(3), element(4), element(5), element(6),
        };
        for (int i = 0; i < sender.length; i++) {
            senderReal[i] = true;
            table.insertSender(EnvType.STANDARD, sender[i].array(), i + 1, i + 1, 0);
        }
        for (int i = 0; i < receiver.length; i++) {
            table.insertReceiver(EnvType.STANDARD, receiver[i].array(), i + 1, 0);
        }
        PisIbltConservativePeelSimulator.Result result = PisIbltConservativePeelSimulator.simulate(table, senderReal);
        Assert.assertTrue(result.isSuccess());
        boolean[] emitted = result.getEmitted();
        boolean[] absorbed = result.getAbsorbed();
        Assert.assertTrue(emitted[0]);
        Assert.assertTrue(emitted[1]);
        Assert.assertTrue(absorbed[2]);
        Assert.assertTrue(absorbed[3]);
    }

    @Test
    public void testConservativePeelZeroKeySenderOnly() {
        PisIbltUpsuParams params = new PisIbltUpsuParams.Builder(1 << 5, 1 << 5)
            .setTableLength(1 << 8)
            .setPeelRound(1 << 8)
            .build();
        PisIbltSourceSplitTable table = new PisIbltSourceSplitTable(params, ELEMENT_BYTE_LENGTH, new byte[16]);
        boolean[] senderReal = new boolean[params.getSenderCapacity()];
        senderReal[0] = true;
        table.insertSender(EnvType.STANDARD, new byte[ELEMENT_BYTE_LENGTH], 1, 1, 0);
        PisIbltConservativePeelSimulator.Result result = PisIbltConservativePeelSimulator.simulate(table, senderReal);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(result.getEmitted()[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHighThroughputRequiresOsdaBackend() {
        PisIbltUpsuConfig config = new PisIbltUpsuConfig.Builder()
            .setMode(PisIbltUpsuMode.HIGH_THROUGHPUT)
            .setMaxElementByteLength(ELEMENT_BYTE_LENGTH)
            .build();
        UpsuFactory.createSender(firstRpc, secondRpc.ownParty(), config);
    }

    private void testPto(Set<ByteBuffer> senderSet, Set<ByteBuffer> receiverSet, int senderCapacity, boolean parallel)
        throws InterruptedException {
        PisIbltUpsuConfig config = new PisIbltUpsuConfig.Builder()
            .setMode(PisIbltUpsuMode.STRICT)
            .setMaxElementByteLength(ELEMENT_BYTE_LENGTH)
            .build();
        testPto(senderSet, receiverSet, config, senderCapacity, parallel);
    }

    private void testPto(Set<ByteBuffer> senderSet, Set<ByteBuffer> receiverSet, PisIbltUpsuParams params,
                         boolean parallel) throws InterruptedException {
        PisIbltUpsuConfig config = new PisIbltUpsuConfig.Builder()
            .setMode(PisIbltUpsuMode.STRICT)
            .setMaxElementByteLength(ELEMENT_BYTE_LENGTH)
            .setParams(params)
            .build();
        testPto(senderSet, receiverSet, config, params.getSenderCapacity(), parallel);
    }

    private void testPto(Set<ByteBuffer> senderSet, Set<ByteBuffer> receiverSet, PisIbltUpsuConfig config,
                         int senderCapacity, boolean parallel) throws InterruptedException {
        UpsuSender sender = UpsuFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        UpsuReceiver receiver = UpsuFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        sender.setParallel(parallel);
        receiver.setParallel(parallel);
        AtomicReference<Throwable> senderThrowable = new AtomicReference<>();
        AtomicReference<Throwable> receiverThrowable = new AtomicReference<>();
        AtomicReference<UpsuReceiverOutput> receiverOutput = new AtomicReference<>();
        Thread senderThread = new Thread(() -> {
            try {
                sender.init(senderCapacity, receiverSet.size());
                sender.psu(senderSet, ELEMENT_BYTE_LENGTH);
            } catch (Throwable throwable) {
                senderThrowable.set(throwable);
            }
        });
        Thread receiverThread = new Thread(() -> {
            try {
                receiver.init(receiverSet, senderCapacity, ELEMENT_BYTE_LENGTH);
                receiverOutput.set(receiver.psu(senderCapacity));
            } catch (Throwable throwable) {
                receiverThrowable.set(throwable);
            }
        });
        senderThread.setDaemon(true);
        receiverThread.setDaemon(true);
        senderThread.start();
        receiverThread.start();
        waitForProtocol(senderThread, receiverThread, senderThrowable, receiverThrowable);
        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
        Assert.assertNull(senderThrowable.get());
        Assert.assertNull(receiverThrowable.get());
        assertOutput(senderSet, receiverSet, receiverOutput.get());
    }

    private void waitForProtocol(Thread senderThread, Thread receiverThread, AtomicReference<Throwable> senderThrowable,
                                 AtomicReference<Throwable> receiverThrowable) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
        while ((senderThread.isAlive() || receiverThread.isAlive()) && System.currentTimeMillis() < deadline) {
            if (senderThrowable.get() != null || receiverThrowable.get() != null) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        if (senderThrowable.get() != null || receiverThrowable.get() != null) {
            senderThread.interrupt();
            receiverThread.interrupt();
        }
        senderThread.join(TimeUnit.SECONDS.toMillis(1));
        receiverThread.join(TimeUnit.SECONDS.toMillis(1));
        Assert.assertFalse("sender thread timed out", senderThread.isAlive());
        Assert.assertFalse("receiver thread timed out", receiverThread.isAlive());
    }

    private void assertOutput(Set<ByteBuffer> senderSet, Set<ByteBuffer> receiverSet, UpsuReceiverOutput output) {
        Set<ByteBuffer> expectUnion = new HashSet<>(receiverSet);
        expectUnion.addAll(senderSet);
        Assert.assertEquals(UpsuReceiverOutput.UNKNOWN_PSICA, output.getPsica());
        Assert.assertTrue(output.getUnion().containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(output.getUnion()));
    }

    private Set<ByteBuffer> createElementSet(int start, int size) {
        Set<ByteBuffer> set = new HashSet<>();
        for (int i = 0; i < size; i++) {
            set.add(element(start + i));
        }
        return set;
    }

    private ByteBuffer element(int value) {
        ByteBuffer element = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        element.putInt(value);
        return ByteBuffer.wrap(element.array());
    }
}
