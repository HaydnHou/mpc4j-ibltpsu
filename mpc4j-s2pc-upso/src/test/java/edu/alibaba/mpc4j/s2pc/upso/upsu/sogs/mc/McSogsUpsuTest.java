package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.PsoUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.*;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end test for MC-SOGS UPSU.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Sender element size.
     */
    private static final int SENDER_ELEMENT_SIZE = 1 << 4;
    /**
     * Receiver element size.
     */
    private static final int RECEIVER_ELEMENT_SIZE = 1 << 8;
    /**
     * Element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = 8;

    public McSogsUpsuTest() {
        super(McSogsUpsuTest.class.getSimpleName());
    }

    @Test
    public void testSmallDefault() throws InterruptedException {
        testPto(false);
    }

    @Test
    public void testSmallParallel() throws InterruptedException {
        testPto(true);
    }

    private void testPto(boolean parallel) throws InterruptedException {
        List<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(
            SENDER_ELEMENT_SIZE, RECEIVER_ELEMENT_SIZE, ELEMENT_BYTE_LENGTH
        );
        Set<ByteBuffer> senderElementSet = sets.get(0);
        Set<ByteBuffer> receiverElementSet = sets.get(1);
        McSogsUpsuConfig config = new McSogsUpsuConfig.Builder()
            .setCellNum(512)
            .build();
        UpsuSender sender = UpsuFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        UpsuReceiver receiver = UpsuFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(new SecureRandom().nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        sender.setParallel(parallel);
        receiver.setParallel(parallel);

        SenderThread senderThread = new SenderThread(sender, receiverElementSet.size(), senderElementSet);
        ReceiverThread receiverThread = new ReceiverThread(receiver, senderElementSet.size(), receiverElementSet);
        firstRpc.reset();
        secondRpc.reset();
        STOP_WATCH.start();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        STOP_WATCH.stop();
        long time = STOP_WATCH.getTime(TimeUnit.MILLISECONDS);
        STOP_WATCH.reset();

        Assert.assertNull(senderThread.throwable);
        Assert.assertNull(receiverThread.throwable);
        assertOutput(senderElementSet, receiverElementSet, receiverThread.output);
        printAndResetRpc(time);
        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
    }

    private static void assertOutput(Set<ByteBuffer> senderElementSet, Set<ByteBuffer> receiverElementSet,
                                     UpsuReceiverOutput output) {
        Assert.assertNotNull(output);
        Set<ByteBuffer> expectUnion = new HashSet<>(receiverElementSet);
        expectUnion.addAll(senderElementSet);
        Set<ByteBuffer> expectIntersection = new HashSet<>(receiverElementSet);
        expectIntersection.retainAll(senderElementSet);
        Assert.assertEquals(expectUnion, output.getUnion());
        Assert.assertEquals(expectIntersection.size(), output.getPsica());
    }

    private static class SenderThread extends Thread {
        private final UpsuSender sender;
        private final int receiverElementSize;
        private final Set<ByteBuffer> senderElementSet;
        private Throwable throwable;

        private SenderThread(UpsuSender sender, int receiverElementSize, Set<ByteBuffer> senderElementSet) {
            this.sender = sender;
            this.receiverElementSize = receiverElementSize;
            this.senderElementSet = senderElementSet;
        }

        @Override
        public void run() {
            try {
                sender.init(senderElementSet.size(), receiverElementSize);
                sender.psu(senderElementSet, ELEMENT_BYTE_LENGTH);
            } catch (MpcAbortException | RuntimeException e) {
                throwable = e;
            }
        }
    }

    private static class ReceiverThread extends Thread {
        private final UpsuReceiver receiver;
        private final int senderElementSize;
        private final Set<ByteBuffer> receiverElementSet;
        private UpsuReceiverOutput output;
        private Throwable throwable;

        private ReceiverThread(UpsuReceiver receiver, int senderElementSize, Set<ByteBuffer> receiverElementSet) {
            this.receiver = receiver;
            this.senderElementSize = senderElementSize;
            this.receiverElementSet = receiverElementSet;
        }

        @Override
        public void run() {
            try {
                receiver.init(receiverElementSet, senderElementSize, ELEMENT_BYTE_LENGTH);
                output = receiver.psu(senderElementSize);
            } catch (MpcAbortException | RuntimeException e) {
                throwable = e;
            }
        }
    }
}
