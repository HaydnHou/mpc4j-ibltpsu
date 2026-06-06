package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.List;

/**
 * P57 RPC-backed UP-BA-UPOT batch probe tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltUpBaUpotRpcBatchProbeTest {

    @Test
    public void testBatchProbeReturnsSequentialLogicalOutputsAndReducesPackets() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(8, 8);
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

            List<BaSsuIbltUpBaUpotPublicInput> publicInputs = List.of(
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0),
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 1, 1),
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 2, 2)
            );
            List<BaSsuIbltUpBaUpotLocalInput> senderInputs = List.of(
                BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInputs.get(0), 10L),
                BaSsuIbltUpBaUpotLocalInput.empty(publicInputs.get(1)),
                BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInputs.get(2), 12L)
            );
            List<BaSsuIbltUpBaUpotLocalInput> receiverInputs = List.of(
                BaSsuIbltUpBaUpotLocalInput.empty(publicInputs.get(0)),
                BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInputs.get(1), 11L),
                BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInputs.get(2), 12L)
            );

            BatchProbeThread senderThread = BatchProbeThread.sender(sender, publicInputs, senderInputs);
            BatchProbeThread receiverThread = BatchProbeThread.receiver(receiver, publicInputs, receiverInputs);
            senderThread.start();
            receiverThread.start();
            senderThread.join();
            receiverThread.join();
            Assert.assertNull(senderThread.getException());
            Assert.assertNull(receiverThread.getException());
            assertSameOutputs(senderThread.getOutputs(), receiverThread.getOutputs());
            Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L),
                senderThread.getOutputs().get(0).getElement());
            Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(11L),
                senderThread.getOutputs().get(1).getElement());
            Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(12L),
                senderThread.getOutputs().get(2).getElement());
            Assert.assertEquals(1L, senderRpc.getSendDataPacketNum());
            Assert.assertEquals(2L, receiverRpc.getSendDataPacketNum());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testBatchProbeRejectsSkippedMaterialOrdinal() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(8, 8);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
            receiverRpc, senderRpc.ownParty(), config
        );
        sender.init(schedule);
        receiver.init(schedule);
        List<BaSsuIbltUpBaUpotPublicInput> publicInputs = List.of(
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0),
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 1, 2)
        );
        List<BaSsuIbltUpBaUpotLocalInput> localInputs = List.of(
            BaSsuIbltUpBaUpotLocalInput.empty(publicInputs.get(0)),
            BaSsuIbltUpBaUpotLocalInput.empty(publicInputs.get(1))
        );
        Assert.assertThrows(IllegalArgumentException.class, () -> sender.probeBatch(publicInputs, localInputs));
        Assert.assertThrows(IllegalArgumentException.class, () -> receiver.probeBatch(publicInputs, localInputs));
    }

    @Test
    public void testBatchProbeAllowsRetryBoundaryMaterialGap() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 4, 8);
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

            BaSsuIbltUpBaUpotPublicInput retry0 = schedule.publicInput(0, 0, 0);
            runOneBatch(
                sender, receiver, List.of(retry0),
                List.of(BaSsuIbltUpBaUpotApiTest.singletonLocalInput(retry0, 21L)),
                List.of(BaSsuIbltUpBaUpotLocalInput.empty(retry0))
            );

            BaSsuIbltUpBaUpotPublicInput retry1 = schedule.publicInput(1, 1, 0);
            List<BaSsuIbltProductionUnionProbeOutput> outputs = runOneBatch(
                sender, receiver, List.of(retry1),
                List.of(BaSsuIbltUpBaUpotLocalInput.empty(retry1)),
                List.of(BaSsuIbltUpBaUpotApiTest.singletonLocalInput(retry1, 22L))
            );
            Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(22L),
                outputs.get(0).getElement());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testBatchProbeRejectsCrossRetryBatch() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 2, 8);
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

            BaSsuIbltUpBaUpotPublicInput retry0Probe0 = schedule.publicInput(0, 0, 0);
            runOneBatch(
                sender, receiver, List.of(retry0Probe0),
                List.of(BaSsuIbltUpBaUpotLocalInput.empty(retry0Probe0)),
                List.of(BaSsuIbltUpBaUpotLocalInput.empty(retry0Probe0))
            );

            List<BaSsuIbltUpBaUpotPublicInput> crossRetryInputs = List.of(
                schedule.publicInput(0, 1, 1),
                schedule.publicInput(1, 2, 0)
            );
            List<BaSsuIbltUpBaUpotLocalInput> localInputs = List.of(
                BaSsuIbltUpBaUpotLocalInput.empty(crossRetryInputs.get(0)),
                BaSsuIbltUpBaUpotLocalInput.empty(crossRetryInputs.get(1))
            );
            Assert.assertThrows(IllegalArgumentException.class,
                () -> sender.probeBatch(crossRetryInputs, localInputs));
            Assert.assertThrows(IllegalArgumentException.class,
                () -> receiver.probeBatch(crossRetryInputs, localInputs));
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testBatchProbeRejectsSkippedWholeRetryAtBoundary() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(3, 2, 8);
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

            List<BaSsuIbltUpBaUpotPublicInput> retry0Inputs = List.of(
                schedule.publicInput(0, 0, 0),
                schedule.publicInput(0, 1, 1)
            );
            runOneBatch(
                sender, receiver, retry0Inputs,
                List.of(
                    BaSsuIbltUpBaUpotLocalInput.empty(retry0Inputs.get(0)),
                    BaSsuIbltUpBaUpotLocalInput.empty(retry0Inputs.get(1))
                ),
                List.of(
                    BaSsuIbltUpBaUpotLocalInput.empty(retry0Inputs.get(0)),
                    BaSsuIbltUpBaUpotLocalInput.empty(retry0Inputs.get(1))
                )
            );

            BaSsuIbltUpBaUpotPublicInput retry2 = schedule.publicInput(2, 4, 0);
            List<BaSsuIbltUpBaUpotPublicInput> skippedRetryInputs = List.of(retry2);
            List<BaSsuIbltUpBaUpotLocalInput> localInputs = List.of(
                BaSsuIbltUpBaUpotLocalInput.empty(retry2)
            );
            Assert.assertThrows(IllegalArgumentException.class,
                () -> sender.probeBatch(skippedRetryInputs, localInputs));
            Assert.assertThrows(IllegalArgumentException.class,
                () -> receiver.probeBatch(skippedRetryInputs, localInputs));
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
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
    }

    private static void assertSameOutputs(List<BaSsuIbltProductionUnionProbeOutput> left,
                                          List<BaSsuIbltProductionUnionProbeOutput> right) {
        Assert.assertEquals(left.size(), right.size());
        for (int i = 0; i < left.size(); i++) {
            Assert.assertEquals(left.get(i).getType(), right.get(i).getType());
            Assert.assertEquals(left.get(i).getBucketIndex(), right.get(i).getBucketIndex());
            Assert.assertEquals(left.get(i).getElementByteLength(), right.get(i).getElementByteLength());
            if (left.get(i).isSingleton()) {
                Assert.assertArrayEquals(left.get(i).getElement(), right.get(i).getElement());
            }
        }
    }

    private static List<BaSsuIbltProductionUnionProbeOutput> runOneBatch(
        BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltRpcUpBaUpotReceiver receiver,
        List<BaSsuIbltUpBaUpotPublicInput> publicInputs, List<BaSsuIbltUpBaUpotLocalInput> senderInputs,
        List<BaSsuIbltUpBaUpotLocalInput> receiverInputs
    ) throws InterruptedException {
        BatchProbeThread senderThread = BatchProbeThread.sender(sender, publicInputs, senderInputs);
        BatchProbeThread receiverThread = BatchProbeThread.receiver(receiver, publicInputs, receiverInputs);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        Assert.assertNull(senderThread.getException());
        Assert.assertNull(receiverThread.getException());
        assertSameOutputs(senderThread.getOutputs(), receiverThread.getOutputs());
        return senderThread.getOutputs();
    }

    private static class PrepareThread extends Thread {
        private final BaSsuIbltRpcUpBaUpotSender sender;
        private final BaSsuIbltRpcUpBaUpotReceiver receiver;
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

    private static class BatchProbeThread extends Thread {
        private final BaSsuIbltRpcUpBaUpotSender sender;
        private final BaSsuIbltRpcUpBaUpotReceiver receiver;
        private final List<BaSsuIbltUpBaUpotPublicInput> publicInputs;
        private final List<BaSsuIbltUpBaUpotLocalInput> localInputs;
        private Throwable exception;
        private List<BaSsuIbltProductionUnionProbeOutput> outputs;

        private BatchProbeThread(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltRpcUpBaUpotReceiver receiver,
                                 List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
                                 List<BaSsuIbltUpBaUpotLocalInput> localInputs) {
            this.sender = sender;
            this.receiver = receiver;
            this.publicInputs = publicInputs;
            this.localInputs = localInputs;
        }

        static BatchProbeThread sender(BaSsuIbltRpcUpBaUpotSender sender,
                                       List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
                                       List<BaSsuIbltUpBaUpotLocalInput> localInputs) {
            return new BatchProbeThread(sender, null, publicInputs, localInputs);
        }

        static BatchProbeThread receiver(BaSsuIbltRpcUpBaUpotReceiver receiver,
                                         List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
                                         List<BaSsuIbltUpBaUpotLocalInput> localInputs) {
            return new BatchProbeThread(null, receiver, publicInputs, localInputs);
        }

        @Override
        public void run() {
            try {
                if (sender != null) {
                    outputs = sender.probeBatch(publicInputs, localInputs);
                } else {
                    outputs = receiver.probeBatch(publicInputs, localInputs);
                }
            } catch (Throwable t) {
                exception = t;
            }
        }

        Throwable getException() {
            return exception;
        }

        List<BaSsuIbltProductionUnionProbeOutput> getOutputs() {
            return outputs;
        }
    }
}
