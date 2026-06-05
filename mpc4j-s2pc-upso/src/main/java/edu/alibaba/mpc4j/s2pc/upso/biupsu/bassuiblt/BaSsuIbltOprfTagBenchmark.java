package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * BA-SSU-IBLT MP-OPRF tag benchmark.
 *
 * <p>The MP-OPRF public capacity is the receiver query capacity. The sender can derive PRF tags for a larger local
 * input array after the same fixed receiver-capacity OPRF interaction.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltOprfTagBenchmark {
    /**
     * private constructor.
     */
    private BaSsuIbltOprfTagBenchmark() {
        // empty
    }

    /**
     * Runs a two-party in-memory MP-OPRF tag benchmark.
     *
     * @param config config.
     * @param senderInputSize sender local input size.
     * @param elementByteLength element byte length.
     * @param seed deterministic input seed.
     * @return benchmark result.
     * @throws InterruptedException interrupted.
     */
    public static Result run(BaSsuIbltOprfTagConfig config, int senderInputSize, int elementByteLength, long seed)
        throws InterruptedException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (senderInputSize <= 0) {
            throw new IllegalArgumentException("senderInputSize must be positive");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        byte[][] senderInputs = fixedInputs(senderInputSize, elementByteLength, seed);
        byte[][] receiverInputs = fixedInputs(config.getPublicCapacity(), elementByteLength, seed ^ 0x5A5A5A5A5A5A5A5AL);
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltOprfTagSender sender = new BaSsuIbltOprfTagSender(senderRpc, receiverRpc.ownParty(), config);
            BaSsuIbltOprfTagReceiver receiver = new BaSsuIbltOprfTagReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            senderRpc.reset();
            receiverRpc.reset();

            InitThread senderInitThread = new InitThread(sender);
            InitThread receiverInitThread = new InitThread(receiver);
            senderInitThread.start();
            receiverInitThread.start();
            senderInitThread.join();
            receiverInitThread.join();
            checkThread(senderInitThread);
            checkThread(receiverInitThread);
            long offlineTimeNanos = Math.max(senderInitThread.getElapsedNanos(), receiverInitThread.getElapsedNanos());
            long offlineSendBytes = senderRpc.getSendByteLength() + receiverRpc.getSendByteLength();
            senderRpc.reset();
            receiverRpc.reset();

            GenerateThread senderGenerateThread = new GenerateThread(sender, senderInputs);
            GenerateThread receiverGenerateThread = new GenerateThread(receiver, receiverInputs);
            senderGenerateThread.start();
            receiverGenerateThread.start();
            senderGenerateThread.join();
            receiverGenerateThread.join();
            checkThread(senderGenerateThread);
            checkThread(receiverGenerateThread);
            long onlineTimeNanos = Math.max(
                senderGenerateThread.getElapsedNanos(), receiverGenerateThread.getElapsedNanos()
            );
            long onlineSendBytes = senderRpc.getSendByteLength() + receiverRpc.getSendByteLength();
            BaSsuIbltOprfTagOutput senderOutput = senderGenerateThread.getOutput();
            BaSsuIbltOprfTagOutput receiverOutput = receiverGenerateThread.getOutput();
            long checksum = checksum(senderOutput) ^ checksum(receiverOutput);
            sender.destroy();
            receiver.destroy();
            return new Result(
                config.getPublicCapacity(), senderInputSize, elementByteLength, offlineTimeNanos, onlineTimeNanos,
                offlineSendBytes, onlineSendBytes, senderOutput.getBatchSize(), receiverOutput.getBatchSize(), checksum
            );
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static byte[][] fixedInputs(int size, int elementByteLength, long seed) {
        byte[][] inputs = new byte[size][];
        long state = seed;
        for (int index = 0; index < size; index++) {
            state = state * 0x9E3779B97F4A7C15L + 0xD1B54A32D192ED03L;
            inputs[index] = element(state, elementByteLength);
        }
        return inputs;
    }

    private static byte[] element(long value, int elementByteLength) {
        byte[] element = new byte[elementByteLength];
        for (int index = elementByteLength - 1; index >= 0; index--) {
            element[index] = (byte) value;
            value >>>= Byte.SIZE;
        }
        return element;
    }

    private static void checkThread(AbstractBenchmarkThread thread) {
        if (thread.getException() != null) {
            throw new IllegalStateException("MP-OPRF tag benchmark failed", thread.getException());
        }
    }

    private static long checksum(BaSsuIbltOprfTagOutput output) {
        long checksum = 0L;
        for (int index = 0; index < output.getBatchSize(); index++) {
            checksum ^= firstLong(output.getTag(index));
            checksum ^= Long.rotateLeft(firstLong(output.getCheck(index)), 17);
        }
        return checksum;
    }

    private static long firstLong(byte[] bytes) {
        long value = 0L;
        int length = Math.min(Long.BYTES, bytes.length);
        for (int index = 0; index < length; index++) {
            value = (value << Byte.SIZE) | (bytes[index] & 0xFFL);
        }
        return value;
    }

    /**
     * benchmark result.
     */
    public static class Result {
        /**
         * receiver public capacity.
         */
        private final int receiverPublicCapacity;
        /**
         * sender local input size.
         */
        private final int senderInputSize;
        /**
         * element byte length.
         */
        private final int elementByteLength;
        /**
         * offline time.
         */
        private final long offlineTimeNanos;
        /**
         * online time.
         */
        private final long onlineTimeNanos;
        /**
         * offline bytes.
         */
        private final long offlineSendBytes;
        /**
         * online bytes.
         */
        private final long onlineSendBytes;
        /**
         * sender output batch size.
         */
        private final int senderOutputBatchSize;
        /**
         * receiver output batch size.
         */
        private final int receiverOutputBatchSize;
        /**
         * checksum.
         */
        private final long checksum;

        Result(int receiverPublicCapacity, int senderInputSize, int elementByteLength, long offlineTimeNanos,
               long onlineTimeNanos, long offlineSendBytes, long onlineSendBytes, int senderOutputBatchSize,
               int receiverOutputBatchSize, long checksum) {
            this.receiverPublicCapacity = receiverPublicCapacity;
            this.senderInputSize = senderInputSize;
            this.elementByteLength = elementByteLength;
            this.offlineTimeNanos = offlineTimeNanos;
            this.onlineTimeNanos = onlineTimeNanos;
            this.offlineSendBytes = offlineSendBytes;
            this.onlineSendBytes = onlineSendBytes;
            this.senderOutputBatchSize = senderOutputBatchSize;
            this.receiverOutputBatchSize = receiverOutputBatchSize;
            this.checksum = checksum;
        }

        public int getReceiverPublicCapacity() {
            return receiverPublicCapacity;
        }

        public int getSenderInputSize() {
            return senderInputSize;
        }

        public int getElementByteLength() {
            return elementByteLength;
        }

        public long getOfflineTimeNanos() {
            return offlineTimeNanos;
        }

        public long getOnlineTimeNanos() {
            return onlineTimeNanos;
        }

        public long getOfflineSendBytes() {
            return offlineSendBytes;
        }

        public long getOnlineSendBytes() {
            return onlineSendBytes;
        }

        public int getSenderOutputBatchSize() {
            return senderOutputBatchSize;
        }

        public int getReceiverOutputBatchSize() {
            return receiverOutputBatchSize;
        }

        public long getChecksum() {
            return checksum;
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-SSU-IBLT MP-OPRF tag benchmark%n"
                    + "receiverPublicCapacity=%d, senderInputSize=%d, elementBytes=%d%n"
                    + "offlineTime=%.3f ms, onlineTime=%.3f ms%n"
                    + "offlineSendBytes=%d, onlineSendBytes=%d%n"
                    + "senderOutputBatchSize=%d, receiverOutputBatchSize=%d%n"
                    + "checksum=%016x",
                receiverPublicCapacity,
                senderInputSize,
                elementByteLength,
                offlineTimeNanos / 1_000_000.0,
                onlineTimeNanos / 1_000_000.0,
                offlineSendBytes,
                onlineSendBytes,
                senderOutputBatchSize,
                receiverOutputBatchSize,
                checksum
            );
        }
    }

    /**
     * abstract benchmark thread.
     */
    private abstract static class AbstractBenchmarkThread extends Thread {
        /**
         * elapsed time.
         */
        private long elapsedNanos;
        /**
         * exception.
         */
        private Exception exception;

        @Override
        public final void run() {
            long start = System.nanoTime();
            try {
                execute();
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            } finally {
                elapsedNanos = System.nanoTime() - start;
            }
        }

        abstract void execute() throws MpcAbortException;

        long getElapsedNanos() {
            return elapsedNanos;
        }

        Exception getException() {
            return exception;
        }
    }

    /**
     * init thread.
     */
    private static class InitThread extends AbstractBenchmarkThread {
        /**
         * sender.
         */
        private final BaSsuIbltOprfTagSender sender;
        /**
         * receiver.
         */
        private final BaSsuIbltOprfTagReceiver receiver;

        InitThread(BaSsuIbltOprfTagSender sender) {
            this.sender = sender;
            receiver = null;
        }

        InitThread(BaSsuIbltOprfTagReceiver receiver) {
            sender = null;
            this.receiver = receiver;
        }

        @Override
        void execute() throws MpcAbortException {
            if (sender != null) {
                sender.init();
            } else {
                receiver.init();
            }
        }
    }

    /**
     * generate thread.
     */
    private static class GenerateThread extends AbstractBenchmarkThread {
        /**
         * sender.
         */
        private final BaSsuIbltOprfTagSender sender;
        /**
         * receiver.
         */
        private final BaSsuIbltOprfTagReceiver receiver;
        /**
         * inputs.
         */
        private final byte[][] inputs;
        /**
         * output.
         */
        private BaSsuIbltOprfTagOutput output;

        GenerateThread(BaSsuIbltOprfTagSender sender, byte[][] inputs) {
            this.sender = sender;
            receiver = null;
            this.inputs = inputs;
        }

        GenerateThread(BaSsuIbltOprfTagReceiver receiver, byte[][] inputs) {
            sender = null;
            this.receiver = receiver;
            this.inputs = inputs;
        }

        @Override
        void execute() throws MpcAbortException {
            output = sender != null ? sender.generate(inputs) : receiver.generate(inputs);
        }

        BaSsuIbltOprfTagOutput getOutput() {
            return output;
        }
    }
}
