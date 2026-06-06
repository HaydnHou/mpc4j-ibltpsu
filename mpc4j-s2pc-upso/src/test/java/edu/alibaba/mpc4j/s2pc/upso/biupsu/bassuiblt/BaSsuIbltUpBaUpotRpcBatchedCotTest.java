package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

/**
 * P51 batched-COT regression tests for RPC-backed UP-BA-UPOT.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltUpBaUpotRpcBatchedCotTest {
    /**
     * package path.
     */
    private static final String PACKAGE_PATH = "edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt/";

    @Test
    public void testProbeBodyDoesNotRequestPerProbeCoreCot() throws IOException {
        String senderProbe = methodBody(source("BaSsuIbltRpcUpBaUpotSender.java"),
            "public BaSsuIbltProductionUnionProbeOutput probe");
        Assert.assertTrue(senderProbe.contains("probeBatch("));
        String senderProbeBatch = methodBody(source("BaSsuIbltRpcUpBaUpotSender.java"),
            "public List<BaSsuIbltProductionUnionProbeOutput> probeBatch");
        Assert.assertFalse(senderProbeBatch.contains("coreCotSender.init("));
        Assert.assertFalse(senderProbeBatch.contains("coreCotSender.send("));
        Assert.assertFalse(senderProbeBatch.contains("coreCotReceiver.init("));
        Assert.assertFalse(senderProbeBatch.contains("coreCotReceiver.receive("));
        Assert.assertTrue(senderProbeBatch.contains("ensureOfflineMaterialPrepared();"));
        Assert.assertTrue(senderProbeBatch.contains("ONLINE_CHOICE_CORRECTION_BATCH"));

        String receiverProbe = methodBody(source("BaSsuIbltRpcUpBaUpotReceiver.java"),
            "public BaSsuIbltProductionUnionProbeOutput probe");
        Assert.assertTrue(receiverProbe.contains("probeBatch("));
        String receiverProbeBatch = methodBody(source("BaSsuIbltRpcUpBaUpotReceiver.java"),
            "public List<BaSsuIbltProductionUnionProbeOutput> probeBatch");
        Assert.assertFalse(receiverProbeBatch.contains("coreCotSender.init("));
        Assert.assertFalse(receiverProbeBatch.contains("coreCotSender.send("));
        Assert.assertFalse(receiverProbeBatch.contains("coreCotReceiver.init("));
        Assert.assertFalse(receiverProbeBatch.contains("coreCotReceiver.receive("));
        Assert.assertTrue(receiverProbeBatch.contains("ensureOfflineMaterialPrepared();"));
        Assert.assertTrue(receiverProbeBatch.contains("ONLINE_CHOICE_CORRECTION_BATCH"));
    }

    @Test
    public void testProbeWithoutPreparedOfflineMaterialRejects() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
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
        BaSsuIbltUpBaUpotPublicInput publicInput =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
        Assert.assertFalse(sender.isOfflineMaterialPrepared());
        Assert.assertFalse(receiver.isOfflineMaterialPrepared());
        Assert.assertThrows(IllegalStateException.class, () ->
            sender.probe(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput)));
        Assert.assertThrows(IllegalStateException.class, () ->
            receiver.probe(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput)));
    }

    @Test
    public void testPreparedProbeOnlinePayloadExcludesCoreCot() throws Exception {
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
            runPrepare(sender::prepareOfflineMaterial, receiver::prepareOfflineMaterial);
            Assert.assertTrue(sender.isOfflineMaterialPrepared());
            Assert.assertTrue(receiver.isOfflineMaterialPrepared());

            senderRpc.reset();
            receiverRpc.reset();
            BaSsuIbltUpBaUpotPublicInput publicInput =
                BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
            runProbe(
                sender, receiver, publicInput,
                BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L),
                BaSsuIbltUpBaUpotLocalInput.empty(publicInput)
            );

            Assert.assertEquals(sender.getFixedProbeCapsuleByteLength(), senderRpc.getPayloadByteLength());
            Assert.assertEquals(
                receiver.getFixedChoiceCorrectionByteLength() + receiver.getFixedResultByteLength(),
                receiverRpc.getPayloadByteLength()
            );
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static void runPrepare(ThrowingRunnable senderRunnable, ThrowingRunnable receiverRunnable)
        throws InterruptedException {
        WorkerThread senderThread = new WorkerThread(senderRunnable);
        WorkerThread receiverThread = new WorkerThread(receiverRunnable);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        Assert.assertNull(senderThread.getException());
        Assert.assertNull(receiverThread.getException());
    }

    private static void runProbe(BaSsuIbltRpcUpBaUpotSender sender,
                                 BaSsuIbltRpcUpBaUpotReceiver receiver,
                                 BaSsuIbltUpBaUpotPublicInput publicInput,
                                 BaSsuIbltUpBaUpotLocalInput senderLocalInput,
                                 BaSsuIbltUpBaUpotLocalInput receiverLocalInput)
        throws InterruptedException {
        ProbeThread senderThread = ProbeThread.sender(sender, publicInput, senderLocalInput);
        ProbeThread receiverThread = ProbeThread.receiver(receiver, publicInput, receiverLocalInput);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        Assert.assertNull(senderThread.getException());
        Assert.assertNull(receiverThread.getException());
        Assert.assertEquals(senderThread.getOutput().getType(), receiverThread.getOutput().getType());
    }

    private static String source(String fileName) throws IOException {
        Path modulePath = Paths.get("src/main/java").resolve(PACKAGE_PATH).resolve(fileName);
        if (Files.exists(modulePath)) {
            return Files.readString(modulePath);
        }
        Path rootPath = Paths.get("mpc4j-s2pc-upso/src/main/java").resolve(PACKAGE_PATH).resolve(fileName);
        if (Files.exists(rootPath)) {
            return Files.readString(rootPath);
        }
        throw new IOException("cannot locate source file: " + fileName);
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureStart = source.indexOf(signaturePrefix);
        Assert.assertTrue("method not found: " + signaturePrefix, signatureStart >= 0);
        int braceStart = source.indexOf('{', signatureStart);
        Assert.assertTrue("method brace not found: " + signaturePrefix, braceStart > signatureStart);
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, i + 1);
                }
            }
        }
        Assert.fail("method end not found: " + signaturePrefix);
        return "";
    }

    /**
     * throwing runnable.
     */
    private interface ThrowingRunnable {
        /**
         * Runs the task.
         *
         * @throws Exception on failure.
         */
        void run() throws Exception;
    }

    /**
     * worker thread.
     */
    private static class WorkerThread extends Thread {
        /**
         * runnable.
         */
        private final ThrowingRunnable runnable;
        /**
         * exception.
         */
        private Throwable exception;

        WorkerThread(ThrowingRunnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                runnable.run();
            } catch (Throwable t) {
                exception = t;
            }
        }

        Throwable getException() {
            return exception;
        }
    }

    /**
     * probe thread.
     */
    private static class ProbeThread extends Thread {
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

        private ProbeThread(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltRpcUpBaUpotReceiver receiver,
                            BaSsuIbltUpBaUpotPublicInput publicInput, BaSsuIbltUpBaUpotLocalInput localInput) {
            this.sender = sender;
            this.receiver = receiver;
            this.publicInput = publicInput;
            this.localInput = localInput;
        }

        static ProbeThread sender(BaSsuIbltRpcUpBaUpotSender sender, BaSsuIbltUpBaUpotPublicInput publicInput,
                                  BaSsuIbltUpBaUpotLocalInput localInput) {
            return new ProbeThread(sender, null, publicInput, localInput);
        }

        static ProbeThread receiver(BaSsuIbltRpcUpBaUpotReceiver receiver, BaSsuIbltUpBaUpotPublicInput publicInput,
                                    BaSsuIbltUpBaUpotLocalInput localInput) {
            return new ProbeThread(null, receiver, publicInput, localInput);
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
