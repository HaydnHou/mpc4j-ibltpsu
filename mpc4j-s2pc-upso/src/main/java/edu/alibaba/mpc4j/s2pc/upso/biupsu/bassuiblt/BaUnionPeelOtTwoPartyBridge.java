package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;

import java.security.SecureRandom;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Protocol-facing BA-UnionPeel-OT fixed-bucket bridge.
 *
 * <p>The bridge runs the fixed-bucket payload boundary with real RPC accounting. The bucket outputs are still
 * precomputed by the plain BA-SSU-IBLT trace, so this class must stay separated from the final case-hiding BA-UPOT
 * implementation.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtTwoPartyBridge {
    /**
     * bridge thread poll interval.
     */
    private static final long BRIDGE_THREAD_POLL_MILLIS = 10L;
    /**
     * bridge thread abort join interval.
     */
    private static final long BRIDGE_THREAD_ABORT_JOIN_MILLIS = 5_000L;
    /**
     * private constructor.
     */
    private BaUnionPeelOtTwoPartyBridge() {
        // empty
    }

    /**
     * Runs the bridge over a fixed BA-SSU-IBLT bucket transcript.
     *
     * @param config config.
     * @param transcriptResult fixed transcript result.
     * @return bridge result.
     * @throws InterruptedException interrupted.
     */
    public static BaUnionPeelOtResult runMemoryBridge(BaUnionPeelOtConfig config,
                                                      BaSsuIbltTranscriptResult transcriptResult)
        throws InterruptedException {
        if (transcriptResult == null) {
            throw new IllegalArgumentException("transcriptResult must be non-null");
        }
        List<BaSsuIbltBucketTranscript> bucketTranscripts = transcriptResult.getBucketTranscripts();
        List<BaUpotBucketOutput> outputs = new ArrayList<>(bucketTranscripts.size());
        List<Integer> expectedBucketIndices = new ArrayList<>(bucketTranscripts.size());
        for (BaSsuIbltBucketTranscript transcript : bucketTranscripts) {
            BaUpotBucketOutput output = transcript.getDebugOutput();
            if (transcript.getBucketIndex() != output.getBucketIndex()) {
                throw new IllegalArgumentException("transcript bucket index must match output bucket index");
            }
            outputs.add(output);
            expectedBucketIndices.add(transcript.getBucketIndex());
        }
        if (transcriptResult.getTotalBucketTranscriptCount() != outputs.size()) {
            throw new IllegalArgumentException("fixed transcript count mismatch");
        }
        return runMemoryBridgeWithOutputs(config, outputs, expectedBucketIndices);
    }

    /**
     * Runs the bridge over streaming fixed BA-SSU-IBLT bucket outputs.
     *
     * @param config config.
     * @param leftSet left input set.
     * @param rightSet right input set.
     * @param elementByteLength element byte length.
     * @param params parameters.
     * @return bridge result.
     * @throws InterruptedException interrupted.
     */
    public static BaUnionPeelOtResult runMemoryStreamingBridge(
        BaUnionPeelOtConfig config, Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength,
        BaSsuIbltBiUpsuParams params) throws InterruptedException {
        return runMemoryStreamingBridge(config, leftSet, rightSet, elementByteLength, params, false);
    }

    /**
     * Runs the bridge over streaming fixed BA-SSU-IBLT bucket outputs.
     *
     * @param config config.
     * @param leftSet left input set.
     * @param rightSet right input set.
     * @param elementByteLength element byte length.
     * @param params parameters.
     * @param retainDecodedOutputs true if decoded outputs should be retained.
     * @return bridge result.
     * @throws InterruptedException interrupted.
     */
    public static BaUnionPeelOtResult runMemoryStreamingBridge(
        BaUnionPeelOtConfig config, Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength,
        BaSsuIbltBiUpsuParams params, boolean retainDecodedOutputs) throws InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        BaSsuIbltFixedBucketOutputIterable outputs = new BaSsuIbltFixedBucketOutputIterable(
            leftSet, rightSet, elementByteLength, params, BaUpotIdealEvaluator.getInstance()
        );
        BaSsuIbltFixedBucketIndexIterable expectedBucketIndices = new BaSsuIbltFixedBucketIndexIterable(
            outputs.getSchedule()
        );
        if (outputs.size() != expectedBucketIndices.size()) {
            throw new IllegalStateException("streaming output/index schedule mismatch");
        }
        return runMemoryBridgeWithSources(config, outputs.size(), outputs, expectedBucketIndices, retainDecodedOutputs);
    }

    /**
     * Runs the bridge over precomputed fixed bucket outputs. Package-private for test/debug use only because the
     * expected bucket schedule is derived from the outputs themselves.
     *
     * @param config config.
     * @param outputs fixed bucket outputs.
     * @return bridge result.
     * @throws InterruptedException interrupted.
     */
    static BaUnionPeelOtResult runMemoryBridgeWithOutputs(BaUnionPeelOtConfig config,
                                                          List<BaUpotBucketOutput> outputs)
        throws InterruptedException {
        validateConfigAndOutputs(config, outputs);
        return runMemoryBridgeWithOutputs(config, outputs, bucketIndices(outputs));
    }

    static BaUnionPeelOtResult runMemoryBridgeWithSources(BaUnionPeelOtConfig config, int bucketNum,
                                                          Iterable<BaUpotBucketOutput> outputs,
                                                          Iterable<Integer> expectedBucketIndices,
                                                          boolean retainDecodedOutputs)
        throws InterruptedException {
        validateConfigAndSources(config, bucketNum, outputs, expectedBucketIndices);
        switch (config.getMode()) {
            case PLAIN_PAYLOAD:
                return runPlainPayloadBridge(config, bucketNum, outputs, expectedBucketIndices, retainDecodedOutputs);
            case WIRE_MASKED_PAYLOAD:
                return runWireMaskedPayloadBridge(config, bucketNum, outputs, expectedBucketIndices,
                    retainDecodedOutputs);
            default:
                throw new IllegalStateException("Unhandled BA-UnionPeel-OT mode: " + config.getMode());
        }
    }

    private static BaUnionPeelOtResult runMemoryBridgeWithOutputs(BaUnionPeelOtConfig config,
                                                                  List<BaUpotBucketOutput> outputs,
                                                                  List<Integer> expectedBucketIndices)
        throws InterruptedException {
        validateConfigAndOutputs(config, outputs);
        validateExpectedBucketIndices(outputs, expectedBucketIndices);
        switch (config.getMode()) {
            case PLAIN_PAYLOAD:
                return runPlainPayloadBridge(config, outputs, expectedBucketIndices);
            case WIRE_MASKED_PAYLOAD:
                return runWireMaskedPayloadBridge(config, outputs, expectedBucketIndices);
            default:
                throw new IllegalStateException("Unhandled BA-UnionPeel-OT mode: " + config.getMode());
        }
    }

    private static BaUnionPeelOtResult runPlainPayloadBridge(BaUnionPeelOtConfig bridgeConfig,
                                                             List<BaUpotBucketOutput> outputs,
                                                             List<Integer> expectedBucketIndices)
        throws InterruptedException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUpotConfig config = bridgeConfig.toBenchmarkConfig(outputs.size()).createBaUpotConfig();
            BaUpotPlainPayloadSender sender = new BaUpotPlainPayloadSender(senderRpc, receiverRpc.ownParty(), config);
            BaUpotPlainPayloadReceiver receiver = new BaUpotPlainPayloadReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            BaUpotPlainPayloadSenderThread senderThread = new BaUpotPlainPayloadSenderThread(sender, outputs);
            BaUpotPlainPayloadReceiverThread receiverThread = new BaUpotPlainPayloadReceiverThread(
                receiver, expectedBucketIndices
            );
            senderThread.start();
            receiverThread.start();
            waitForBridgeThreads(senderThread, receiverThread, senderRpc, receiverRpc, senderThread::getException,
                receiverThread::getException);
            if (senderThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT plain sender failed", senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT plain receiver failed",
                    receiverThread.getException());
            }
            return new BaUnionPeelOtResult(bridgeConfig, outputs.size(), senderThread.getResult(),
                receiverThread.getResult(), receiverThread.getOutputs());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static BaUnionPeelOtResult runPlainPayloadBridge(BaUnionPeelOtConfig bridgeConfig, int bucketNum,
                                                             Iterable<BaUpotBucketOutput> outputs,
                                                             Iterable<Integer> expectedBucketIndices,
                                                             boolean retainDecodedOutputs)
        throws InterruptedException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUpotConfig config = bridgeConfig.toBenchmarkConfig(bucketNum).createBaUpotConfig();
            BaUpotPlainPayloadSender sender = new BaUpotPlainPayloadSender(senderRpc, receiverRpc.ownParty(), config);
            BaUpotPlainPayloadReceiver receiver = new BaUpotPlainPayloadReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            BaUpotPlainPayloadSenderThread senderThread = new BaUpotPlainPayloadSenderThread(
                sender, bucketNum, outputs
            );
            BaUpotPlainPayloadReceiverThread receiverThread = new BaUpotPlainPayloadReceiverThread(
                receiver, bucketNum, expectedBucketIndices, retainDecodedOutputs
            );
            senderThread.start();
            receiverThread.start();
            waitForBridgeThreads(senderThread, receiverThread, senderRpc, receiverRpc, senderThread::getException,
                receiverThread::getException);
            if (senderThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT plain sender failed", senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT plain receiver failed",
                    receiverThread.getException());
            }
            return new BaUnionPeelOtResult(bridgeConfig, bucketNum, senderThread.getResult(),
                receiverThread.getResult(), receiverThread.getOutputs(), receiverThread.getDecodedOutputCount(),
                retainDecodedOutputs);
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static BaUnionPeelOtResult runWireMaskedPayloadBridge(BaUnionPeelOtConfig bridgeConfig,
                                                                  List<BaUpotBucketOutput> outputs,
                                                                  List<Integer> expectedBucketIndices)
        throws InterruptedException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUpotConfig config = bridgeConfig.toBenchmarkConfig(outputs.size()).createBaUpotConfig();
            byte[] maskSeed = bridgeConfig.getMaskSeed();
            BaUpotWireMaskedPayloadSender sender = new BaUpotWireMaskedPayloadSender(
                senderRpc, receiverRpc.ownParty(), config, maskSeed
            );
            BaUpotWireMaskedPayloadReceiver receiver = new BaUpotWireMaskedPayloadReceiver(
                receiverRpc, senderRpc.ownParty(), config, maskSeed
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            BaUpotWireMaskedPayloadSenderThread senderThread = new BaUpotWireMaskedPayloadSenderThread(
                sender, outputs
            );
            BaUpotWireMaskedPayloadReceiverThread receiverThread = new BaUpotWireMaskedPayloadReceiverThread(
                receiver, expectedBucketIndices
            );
            senderThread.start();
            receiverThread.start();
            waitForBridgeThreads(senderThread, receiverThread, senderRpc, receiverRpc, senderThread::getException,
                receiverThread::getException);
            if (senderThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT masked sender failed", senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT masked receiver failed",
                    receiverThread.getException());
            }
            return new BaUnionPeelOtResult(bridgeConfig, outputs.size(), senderThread.getResult(),
                receiverThread.getResult(), receiverThread.getOutputs());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static BaUnionPeelOtResult runWireMaskedPayloadBridge(BaUnionPeelOtConfig bridgeConfig, int bucketNum,
                                                                  Iterable<BaUpotBucketOutput> outputs,
                                                                  Iterable<Integer> expectedBucketIndices,
                                                                  boolean retainDecodedOutputs)
        throws InterruptedException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUpotConfig config = bridgeConfig.toBenchmarkConfig(bucketNum).createBaUpotConfig();
            byte[] maskSeed = bridgeConfig.getMaskSeed();
            BaUpotWireMaskedPayloadSender sender = new BaUpotWireMaskedPayloadSender(
                senderRpc, receiverRpc.ownParty(), config, maskSeed
            );
            BaUpotWireMaskedPayloadReceiver receiver = new BaUpotWireMaskedPayloadReceiver(
                receiverRpc, senderRpc.ownParty(), config, maskSeed
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            BaUpotWireMaskedPayloadSenderThread senderThread = new BaUpotWireMaskedPayloadSenderThread(
                sender, bucketNum, outputs
            );
            BaUpotWireMaskedPayloadReceiverThread receiverThread = new BaUpotWireMaskedPayloadReceiverThread(
                receiver, bucketNum, expectedBucketIndices, retainDecodedOutputs
            );
            senderThread.start();
            receiverThread.start();
            waitForBridgeThreads(senderThread, receiverThread, senderRpc, receiverRpc, senderThread::getException,
                receiverThread::getException);
            if (senderThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT masked sender failed", senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new IllegalStateException("BA-UnionPeel-OT masked receiver failed",
                    receiverThread.getException());
            }
            return new BaUnionPeelOtResult(bridgeConfig, bucketNum, senderThread.getResult(),
                receiverThread.getResult(), receiverThread.getOutputs(), receiverThread.getDecodedOutputCount(),
                retainDecodedOutputs);
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static void validateConfigAndOutputs(BaUnionPeelOtConfig config, List<BaUpotBucketOutput> outputs) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("outputs must be non-empty");
        }
        for (BaUpotBucketOutput output : outputs) {
            if (output == null) {
                throw new IllegalArgumentException("outputs must not contain null entries");
            }
        }
    }

    private static void validateConfigAndSources(BaUnionPeelOtConfig config, int bucketNum,
                                                 Iterable<BaUpotBucketOutput> outputs,
                                                 Iterable<Integer> expectedBucketIndices) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        if (expectedBucketIndices == null) {
            throw new IllegalArgumentException("expectedBucketIndices must be non-null");
        }
    }

    private static void validateExpectedBucketIndices(List<BaUpotBucketOutput> outputs,
                                                      List<Integer> expectedBucketIndices) {
        if (expectedBucketIndices == null) {
            throw new IllegalArgumentException("expectedBucketIndices must be non-null");
        }
        if (expectedBucketIndices.size() != outputs.size()) {
            throw new IllegalArgumentException("expected bucket index count must equal output count");
        }
        for (int index = 0; index < outputs.size(); index++) {
            Integer expectedBucketIndex = expectedBucketIndices.get(index);
            if (expectedBucketIndex == null) {
                throw new IllegalArgumentException("expectedBucketIndices must not contain null entries");
            }
            if (expectedBucketIndex != outputs.get(index).getBucketIndex()) {
                throw new IllegalArgumentException("output bucket index does not match expected schedule");
            }
        }
    }

    private static List<Integer> bucketIndices(List<BaUpotBucketOutput> outputs) {
        List<Integer> bucketIndices = new ArrayList<>(outputs.size());
        for (BaUpotBucketOutput output : outputs) {
            bucketIndices.add(output.getBucketIndex());
        }
        return bucketIndices;
    }

    private static void waitForBridgeThreads(Thread senderThread, Thread receiverThread, Rpc senderRpc, Rpc receiverRpc,
                                             Supplier<Exception> senderExceptionSupplier,
                                             Supplier<Exception> receiverExceptionSupplier)
        throws InterruptedException {
        while (senderThread.isAlive() || receiverThread.isAlive()) {
            senderThread.join(BRIDGE_THREAD_POLL_MILLIS);
            receiverThread.join(BRIDGE_THREAD_POLL_MILLIS);
            if ((senderExceptionSupplier.get() != null && receiverThread.isAlive())
                || (receiverExceptionSupplier.get() != null && senderThread.isAlive())) {
                senderRpc.disconnect();
                receiverRpc.disconnect();
                senderThread.join(BRIDGE_THREAD_ABORT_JOIN_MILLIS);
                receiverThread.join(BRIDGE_THREAD_ABORT_JOIN_MILLIS);
                if (senderThread.isAlive() || receiverThread.isAlive()) {
                    senderThread.interrupt();
                    receiverThread.interrupt();
                    throw new IllegalStateException("BA-UnionPeel-OT bridge peer did not stop after exception");
                }
                return;
            }
        }
    }
}
