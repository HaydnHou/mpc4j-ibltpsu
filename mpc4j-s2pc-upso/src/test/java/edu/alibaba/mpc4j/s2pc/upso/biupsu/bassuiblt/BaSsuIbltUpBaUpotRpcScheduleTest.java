package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * P51 RPC-backed UP-BA-UPOT schedule tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotRpcScheduleTest {

    @Test
    public void testInitRejectsMismatchedScheduleShape() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltUpBaUpotOfflineSchedule badSchedule = new BaSsuIbltUpBaUpotOfflineSchedule(
            "M10_N18_D3", 1, 1, 2,
            config.getElementByteLength(), config.getTagByteLength() + 1,
            config.getCheckByteLength(), config.getAuthTagByteLength()
        );
        Assert.assertThrows(IllegalArgumentException.class, () -> sender.init(badSchedule));
    }

    @Test
    public void testProbeBeforeInitRejects() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltUpBaUpotPublicInput publicInput =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
        Assert.assertThrows(IllegalStateException.class, () ->
            sender.probe(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput)));
    }

    @Test
    public void testProbeOrdinalMonotonicityIsEnforced() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 8);
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        sender.init(schedule);
        BaSsuIbltUpBaUpotPublicInput skippedOrdinal =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 1);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.probe(skippedOrdinal, BaSsuIbltUpBaUpotLocalInput.empty(skippedOrdinal)));
    }

    @Test
    public void testBothSidesRejectSkippedMaterialOrdinal() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 8);
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
            receiverRpc, senderRpc.ownParty(), config
        );
        sender.init(schedule);
        receiver.init(schedule);
        BaSsuIbltUpBaUpotPublicInput skippedOrdinal =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 1);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.probe(skippedOrdinal, BaSsuIbltUpBaUpotLocalInput.empty(skippedOrdinal)));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            receiver.probe(skippedOrdinal, BaSsuIbltUpBaUpotLocalInput.empty(skippedOrdinal)));
    }

    @Test
    public void testBothSidesRejectWrongPublicInputShape() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
            receiverRpc, senderRpc.ownParty(), config
        );
        sender.init(schedule);
        receiver.init(schedule);
        BaSsuIbltUpBaUpotPublicInput wrongProfile = new BaSsuIbltUpBaUpotPublicInput(
            "OTHER_PROFILE", 0, 0, 0, config.getElementByteLength(),
            config.getTagByteLength() * Byte.SIZE, config.getCheckByteLength() * Byte.SIZE,
            config.getAuthTagByteLength() * Byte.SIZE
        );
        BaSsuIbltUpBaUpotPublicInput wrongBucket =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 8, 0);
        BaSsuIbltUpBaUpotPublicInput wrongShape = new BaSsuIbltUpBaUpotPublicInput(
            "M10_N18_D3", 0, 0, 0, config.getElementByteLength(),
            (config.getTagByteLength() + 1) * Byte.SIZE, config.getCheckByteLength() * Byte.SIZE,
            config.getAuthTagByteLength() * Byte.SIZE
        );
        assertBothSidesRejectPublicInput(sender, receiver, wrongProfile);
        assertBothSidesRejectPublicInput(sender, receiver, wrongBucket);
        assertBothSidesRejectPublicInput(sender, receiver, wrongShape);
    }

    @Test
    public void testRetryLocalProbeOrdinalAllowsNextRetryZeroOrdinal() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 1, 8);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
                senderRpc, receiverRpc.ownParty(), config
            );
            BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            sender.init(schedule);
            receiver.init(schedule);
            prepareOffline(sender, receiver);
            BaSsuIbltUpBaUpotPublicInput retry0 =
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
            runOneProbe(sender, receiver, retry0, BaSsuIbltUpBaUpotLocalInput.empty(retry0),
                BaSsuIbltUpBaUpotLocalInput.empty(retry0));
            BaSsuIbltUpBaUpotPublicInput retry1 =
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 1, 0, 0);
            runOneProbe(sender, receiver, retry1, BaSsuIbltUpBaUpotLocalInput.empty(retry1),
                BaSsuIbltUpBaUpotLocalInput.empty(retry1));
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testStalePublicInputReplayRejectedAfterSuccessfulProbe() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 8);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
                senderRpc, receiverRpc.ownParty(), config
            );
            BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            sender.init(schedule);
            receiver.init(schedule);
            prepareOffline(sender, receiver);
            BaSsuIbltUpBaUpotPublicInput first =
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
            runOneProbe(sender, receiver, first, BaSsuIbltUpBaUpotLocalInput.empty(first),
                BaSsuIbltUpBaUpotLocalInput.empty(first));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                sender.probe(first, BaSsuIbltUpBaUpotLocalInput.empty(first)));
            Assert.assertThrows(IllegalArgumentException.class, () ->
                receiver.probe(first, BaSsuIbltUpBaUpotLocalInput.empty(first)));
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testLocalInputMustMatchPublicSlot() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        sender.init(schedule);
        BaSsuIbltUpBaUpotPublicInput currentSlot =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
        BaSsuIbltUpBaUpotPublicInput staleSlot =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 1, 0);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.probe(currentSlot, BaSsuIbltUpBaUpotLocalInput.empty(staleSlot)));
    }

    @Test
    public void testRpcShellExchangesFixedMessagesAndReturnsFixedResult() throws Exception {
        assertSingleProbeRpcShape(publicInput -> BaSsuIbltUpBaUpotLocalInput.empty(publicInput));
        assertSingleProbeRpcShape(publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 7L));
        assertSingleProbeRpcShape(publicInput -> BaSsuIbltUpBaUpotFixedOnlineShapeTest.blockedLocalInput(
            publicInput, 9L
        ));
    }

    @Test
    public void testRpcProbeImplementsSourceAgnosticTruthTable() throws Exception {
        BaSsuIbltProductionUnionProbeOutput senderOnly = runSingleRpcProbe(
            publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L),
            publicInput -> BaSsuIbltUpBaUpotLocalInput.empty(publicInput)
        );
        Assert.assertTrue(senderOnly.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L), senderOnly.getElement());

        BaSsuIbltProductionUnionProbeOutput receiverOnly = runSingleRpcProbe(
            publicInput -> BaSsuIbltUpBaUpotLocalInput.empty(publicInput),
            publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 11L)
        );
        Assert.assertTrue(receiverOnly.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(11L), receiverOnly.getElement());

        BaSsuIbltProductionUnionProbeOutput sameSingleton = runSingleRpcProbe(
            publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 12L),
            publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 12L)
        );
        Assert.assertTrue(sameSingleton.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(12L), sameSingleton.getElement());

        BaSsuIbltProductionUnionProbeOutput differentSingleton = runSingleRpcProbe(
            publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 13L),
            publicInput -> BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 14L)
        );
        Assert.assertTrue(differentSingleton.isBottom());
    }

    private static void assertSingleProbeRpcShape(LocalInputFactory localInputFactory) throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
                senderRpc, receiverRpc.ownParty(), config
            );
            BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            sender.init(schedule);
            receiver.init(schedule);
            prepareOffline(sender, receiver);
            senderRpc.reset();
            receiverRpc.reset();
            BaSsuIbltUpBaUpotPublicInput publicInput =
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
            runOneProbe(
                sender, receiver, publicInput, localInputFactory.create(publicInput),
                BaSsuIbltUpBaUpotLocalInput.empty(publicInput)
            );
            Assert.assertTrue(senderRpc.getSendDataPacketNum() >= 1);
            Assert.assertTrue(receiverRpc.getSendDataPacketNum() >= 1);
            Assert.assertTrue(senderRpc.getPayloadByteLength() >= sender.getFixedProbeCapsuleByteLength());
            Assert.assertTrue(receiverRpc.getPayloadByteLength() >= receiver.getFixedResultByteLength());
            Assert.assertEquals(BaSsuIbltUpBaUpotBucketProbeGadget.maskedRowsByteLength(schedule),
                sender.getFixedProbeCapsuleByteLength());
            Assert.assertEquals(1 + config.getElementByteLength(), receiver.getFixedResultByteLength());
            Assert.assertEquals((long) schedule.getMaterialCount() * config.getCotNumPerProbe(),
                sender.getPrecomputedCotNum());
            Assert.assertEquals(sender.getPrecomputedCotNum(), receiver.getPrecomputedCotNum());
            Assert.assertFalse(config.isQueuePeelProductionReady());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static BaSsuIbltProductionUnionProbeOutput runOneProbe(BaSsuIbltRpcUpBaUpotSender sender,
                                    BaSsuIbltRpcUpBaUpotReceiver receiver,
                                    BaSsuIbltUpBaUpotPublicInput publicInput,
                                    BaSsuIbltUpBaUpotLocalInput senderLocalInput,
                                    BaSsuIbltUpBaUpotLocalInput receiverLocalInput)
        throws InterruptedException {
        RpcProbeThread senderThread = RpcProbeThread.sender(sender, publicInput, senderLocalInput);
        RpcProbeThread receiverThread = RpcProbeThread.receiver(receiver, publicInput, receiverLocalInput);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        Assert.assertNull(senderThread.getException());
        Assert.assertNull(receiverThread.getException());
        assertSameOutput(senderThread.getOutput(), receiverThread.getOutput());
        return senderThread.getOutput();
    }

    private static void prepareOffline(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltRpcUpBaUpotReceiver receiver)
        throws InterruptedException {
        PrepareThread senderThread = PrepareThread.sender(sender);
        PrepareThread receiverThread = PrepareThread.receiver(receiver);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        Assert.assertNull(senderThread.getException());
        Assert.assertNull(receiverThread.getException());
        Assert.assertTrue(sender.isOfflineMaterialPrepared());
        Assert.assertTrue(receiver.isOfflineMaterialPrepared());
    }

    private static BaSsuIbltProductionUnionProbeOutput runSingleRpcProbe(LocalInputFactory senderInputFactory,
                                                                         LocalInputFactory receiverInputFactory)
        throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
                senderRpc, receiverRpc.ownParty(), config
            );
            BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            sender.init(schedule);
            receiver.init(schedule);
            prepareOffline(sender, receiver);
            BaSsuIbltUpBaUpotPublicInput publicInput =
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
            return runOneProbe(
                sender, receiver, publicInput, senderInputFactory.create(publicInput),
                receiverInputFactory.create(publicInput)
            );
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static void assertSameOutput(BaSsuIbltProductionUnionProbeOutput left,
                                         BaSsuIbltProductionUnionProbeOutput right) {
        Assert.assertNotNull(left);
        Assert.assertNotNull(right);
        Assert.assertEquals(left.getType(), right.getType());
        Assert.assertEquals(left.getBucketIndex(), right.getBucketIndex());
        Assert.assertEquals(left.getElementByteLength(), right.getElementByteLength());
        if (left.isSingleton()) {
            Assert.assertArrayEquals(left.getElement(), right.getElement());
        }
    }

    private static void assertBothSidesRejectPublicInput(BaSsuIbltRpcUpBaUpotSender sender,
                                                         BaSsuIbltRpcUpBaUpotReceiver receiver,
                                                         BaSsuIbltUpBaUpotPublicInput publicInput) {
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.probe(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput)));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            receiver.probe(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput)));
    }

    /**
     * local input factory.
     */
    private interface LocalInputFactory {
        /**
         * Creates a local input bound to the public slot.
         *
         * @param publicInput public input.
         * @return local input.
         */
        BaSsuIbltUpBaUpotLocalInput create(BaSsuIbltUpBaUpotPublicInput publicInput);
    }

    /**
     * one offline preparation thread.
     */
    private static class PrepareThread extends Thread {
        /**
         * sender.
         */
        private final BaSsuIbltRpcUpBaUpotSender sender;
        /**
         * receiver.
         */
        private final BaSsuIbltRpcUpBaUpotReceiver receiver;
        /**
         * exception.
         */
        private Throwable exception;

        private PrepareThread(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltRpcUpBaUpotReceiver receiver) {
            this.sender = sender;
            this.receiver = receiver;
        }

        static PrepareThread sender(BaSsuIbltRpcUpBaUpotSender sender) {
            return new PrepareThread(sender, null);
        }

        static PrepareThread receiver(BaSsuIbltRpcUpBaUpotReceiver receiver) {
            return new PrepareThread(null, receiver);
        }

        @Override
        public void run() {
            try {
                if (sender != null) {
                    sender.prepareOfflineMaterial();
                } else {
                    receiver.prepareOfflineMaterial();
                }
            } catch (Throwable t) {
                exception = t;
            }
        }

        Throwable getException() {
            return exception;
        }
    }

    /**
     * one RPC probe thread.
     */
    private static class RpcProbeThread extends Thread {
        /**
         * sender.
         */
        private final BaSsuIbltRpcUpBaUpotSender sender;
        /**
         * receiver.
         */
        private final BaSsuIbltRpcUpBaUpotReceiver receiver;
        /**
         * public input.
         */
        private final BaSsuIbltUpBaUpotPublicInput publicInput;
        /**
         * local input.
         */
        private final BaSsuIbltUpBaUpotLocalInput localInput;
        /**
         * exception.
         */
        private Throwable exception;
        /**
         * output.
         */
        private BaSsuIbltProductionUnionProbeOutput output;

        private RpcProbeThread(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltRpcUpBaUpotReceiver receiver,
                               BaSsuIbltUpBaUpotPublicInput publicInput,
                               BaSsuIbltUpBaUpotLocalInput localInput) {
            this.sender = sender;
            this.receiver = receiver;
            this.publicInput = publicInput;
            this.localInput = localInput;
        }

        static RpcProbeThread sender(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltUpBaUpotPublicInput publicInput,
                                     BaSsuIbltUpBaUpotLocalInput localInput) {
            return new RpcProbeThread(sender, null, publicInput, localInput);
        }

        static RpcProbeThread receiver(BaSsuIbltRpcUpBaUpotReceiver receiver, BaSsuIbltUpBaUpotPublicInput publicInput,
                                       BaSsuIbltUpBaUpotLocalInput localInput) {
            return new RpcProbeThread(null, receiver, publicInput, localInput);
        }

        @Override
        public void run() {
            try {
                if (sender != null) {
                    output = sender.probe(publicInput, localInput);
                } else {
                    output = receiver.probe(publicInput, localInput);
                }
            } catch (Throwable t) {
                exception = t;
            }
        }

        Throwable getException() {
            return exception;
        }

        BaSsuIbltProductionUnionProbeOutput getOutput() {
            return output;
        }
    }
}
