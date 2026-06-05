package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain payload-bound BA-UPOT two-party benchmark.
 *
 * <p>This benchmark sends real ideal bucket outputs through fixed-shape capsules. It is not a secure BA-UPOT
 * benchmark; it measures and validates the payload-bound transport that will be replaced by case-hiding BA-UPOT.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadTwoPartyBenchmark {
    /**
     * private constructor.
     */
    private BaUpotPlainPayloadTwoPartyBenchmark() {
        // empty
    }

    /**
     * Runs a benchmark using outputs from a plain BA-SSU trace.
     *
     * @param benchmarkConfig benchmark config.
     * @param bucketTrace bucket trace.
     * @return result.
     * @throws InterruptedException interrupted.
     */
    static Result runMemoryBenchmark(BaUpotTwoPartyBenchmark.BenchmarkConfig benchmarkConfig,
                                     List<BaSsuIbltBucketTrace> bucketTrace) throws InterruptedException {
        List<BaUpotBucketOutput> outputs = new ArrayList<>(bucketTrace.size());
        for (BaSsuIbltBucketTrace trace : bucketTrace) {
            outputs.add(trace.getOutput());
        }
        return runMemoryBenchmarkWithOutputs(benchmarkConfig, outputs);
    }

    /**
     * Runs a benchmark using precomputed bucket outputs.
     *
     * @param benchmarkConfig benchmark config.
     * @param outputs bucket outputs.
     * @return result.
     * @throws InterruptedException interrupted.
     */
    static Result runMemoryBenchmarkWithOutputs(BaUpotTwoPartyBenchmark.BenchmarkConfig benchmarkConfig,
                                                List<BaUpotBucketOutput> outputs)
        throws InterruptedException {
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("outputs must be non-empty");
        }
        if (benchmarkConfig.getBucketNum() != outputs.size()) {
            throw new IllegalArgumentException("bucketNum must equal outputs size");
        }
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUpotConfig config = benchmarkConfig.createBaUpotConfig();
            BaUpotPlainPayloadSender sender = new BaUpotPlainPayloadSender(
                senderRpc, receiverRpc.ownParty(), config
            );
            BaUpotPlainPayloadReceiver receiver = new BaUpotPlainPayloadReceiver(
                receiverRpc, senderRpc.ownParty(), config
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            List<Integer> bucketIndices = new ArrayList<>(outputs.size());
            for (BaUpotBucketOutput output : outputs) {
                bucketIndices.add(output.getBucketIndex());
            }
            BaUpotPlainPayloadSenderThread senderThread = new BaUpotPlainPayloadSenderThread(sender, outputs);
            BaUpotPlainPayloadReceiverThread receiverThread = new BaUpotPlainPayloadReceiverThread(
                receiver, bucketIndices
            );
            senderThread.start();
            receiverThread.start();
            senderThread.join();
            receiverThread.join();
            if (senderThread.getException() != null) {
                throw new IllegalStateException("plain payload sender failed", senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new IllegalStateException("plain payload receiver failed", receiverThread.getException());
            }
            return new Result(benchmarkConfig, senderThread.getResult(), receiverThread.getResult(),
                receiverThread.getOutputs());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    /**
     * Benchmark result.
     */
    static class Result {
        /**
         * config.
         */
        private final BaUpotTwoPartyBenchmark.BenchmarkConfig config;
        /**
         * sender result.
         */
        private final BaUpotPartyBenchmarkResult senderResult;
        /**
         * receiver result.
         */
        private final BaUpotPartyBenchmarkResult receiverResult;
        /**
         * decoded outputs.
         */
        private final List<BaUpotBucketOutput> decodedOutputs;

        Result(BaUpotTwoPartyBenchmark.BenchmarkConfig config, BaUpotPartyBenchmarkResult senderResult,
               BaUpotPartyBenchmarkResult receiverResult, List<BaUpotBucketOutput> decodedOutputs) {
            this.config = config;
            this.senderResult = senderResult;
            this.receiverResult = receiverResult;
            this.decodedOutputs = List.copyOf(decodedOutputs);
        }

        public BaUpotTwoPartyBenchmark.BenchmarkConfig getConfig() {
            return config;
        }

        public BaUpotPartyBenchmarkResult getSenderResult() {
            return senderResult;
        }

        public BaUpotPartyBenchmarkResult getReceiverResult() {
            return receiverResult;
        }

        List<BaUpotBucketOutput> getDecodedOutputs() {
            return decodedOutputs;
        }

        public long getOfflineTimeNanos() {
            return Math.max(senderResult.getOfflineTimeNanos(), receiverResult.getOfflineTimeNanos());
        }

        public long getOnlineTimeNanos() {
            return Math.max(senderResult.getOnlineTimeNanos(), receiverResult.getOnlineTimeNanos());
        }

        public long getOfflineSendBytes() {
            return senderResult.getOfflineSendBytes() + receiverResult.getOfflineSendBytes();
        }

        public long getOnlineSendBytes() {
            return senderResult.getOnlineSendBytes() + receiverResult.getOnlineSendBytes();
        }

        public boolean isChecksumEqual() {
            return senderResult.getChecksum() == receiverResult.getChecksum();
        }

        public double getOnlineBytesPerBucket() {
            return ((double) getOnlineSendBytes()) / config.getBucketNum();
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-UPOT plain payload two-party benchmark%n"
                    + "bucketNum=%d, elementBytes=%d, checkBits=%d, tagBits=%d, batch=%d%n"
                    + "offlineTime=%.3f ms, onlineTime=%.3f ms%n"
                    + "offlineSendBytes=%d, onlineSendBytes=%d, onlineBytesPerBucket=%.2f%n"
                    + "senderChecksum=%016x, receiverChecksum=%016x, checksumEqual=%s",
                config.getBucketNum(),
                config.getElementByteLength(),
                config.getCheckBits(),
                config.getTagBits(),
                config.getOnlineBatchSize(),
                getOfflineTimeNanos() / 1_000_000.0,
                getOnlineTimeNanos() / 1_000_000.0,
                getOfflineSendBytes(),
                getOnlineSendBytes(),
                getOnlineBytesPerBucket(),
                senderResult.getChecksum(),
                receiverResult.getChecksum(),
                isChecksumEqual()
            );
        }
    }
}
