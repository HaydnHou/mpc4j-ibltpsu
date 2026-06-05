package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Specialized BA-UPOT standalone two-party benchmark.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotTwoPartyBenchmark {
    /**
     * private constructor.
     */
    private BaUpotTwoPartyBenchmark() {
        // empty
    }

    /**
     * Runs the benchmark from command line.
     *
     * @param args key=value args.
     * @throws InterruptedException interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        Result result = runMemoryBenchmark(config);
        System.out.println(result.toDisplayString());
    }

    /**
     * Runs an in-memory two-party benchmark.
     *
     * @param benchmarkConfig benchmark config.
     * @return result.
     * @throws InterruptedException interrupted.
     */
    public static Result runMemoryBenchmark(BenchmarkConfig benchmarkConfig) throws InterruptedException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUpotConfig config = benchmarkConfig.createBaUpotConfig();
            BaUpotSender sender = new BaUpotSender(senderRpc, receiverRpc.ownParty(), config);
            BaUpotReceiver receiver = new BaUpotReceiver(receiverRpc, senderRpc.ownParty(), config);
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            BaUpotSenderThread senderThread = new BaUpotSenderThread(sender, benchmarkConfig.getBucketNum());
            BaUpotReceiverThread receiverThread = new BaUpotReceiverThread(receiver, benchmarkConfig.getBucketNum());
            senderThread.start();
            receiverThread.start();
            senderThread.join();
            receiverThread.join();
            if (senderThread.getException() != null) {
                throw new IllegalStateException("sender benchmark failed", senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new IllegalStateException("receiver benchmark failed", receiverThread.getException());
            }
            return new Result(benchmarkConfig, senderThread.getResult(), receiverThread.getResult());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    /**
     * Benchmark config.
     */
    public static class BenchmarkConfig {
        /**
         * bucket count.
         */
        private int bucketNum;
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * check bits.
         */
        private int checkBits;
        /**
         * tag bits.
         */
        private int tagBits;
        /**
         * case count.
         */
        private int caseNum;
        /**
         * COT count per bucket.
         */
        private int cotNumPerBucket;
        /**
         * online batch size.
         */
        private int onlineBatchSize;

        public BenchmarkConfig() {
            bucketNum = 1_089_925;
            elementByteLength = Long.BYTES;
            checkBits = 182;
            tagBits = 182;
            caseNum = BaUpotConfig.DEFAULT_CASE_NUM;
            cotNumPerBucket = BaUpotConfig.DEFAULT_COT_NUM_PER_BUCKET;
            onlineBatchSize = BaUpotConfig.DEFAULT_ONLINE_BATCH_SIZE;
        }

        public static BenchmarkConfig fromArgs(String[] args) {
            BenchmarkConfig config = new BenchmarkConfig();
            for (String arg : args) {
                String normalized = arg.startsWith("--") ? arg.substring(2) : arg;
                int index = normalized.indexOf('=');
                if (index <= 0) {
                    throw new IllegalArgumentException("Argument must be key=value: " + arg);
                }
                String key = normalized.substring(0, index).trim();
                String value = normalized.substring(index + 1).trim();
                switch (key) {
                    case "buckets":
                    case "bucketNum":
                        config.setBucketNum(Integer.parseInt(value));
                        break;
                    case "elementBytes":
                    case "elementByteLength":
                        config.setElementByteLength(Integer.parseInt(value));
                        break;
                    case "checkBits":
                        config.setCheckBits(Integer.parseInt(value));
                        break;
                    case "tagBits":
                        config.setTagBits(Integer.parseInt(value));
                        break;
                    case "caseNum":
                        config.setCaseNum(Integer.parseInt(value));
                        break;
                    case "cot":
                    case "cotNumPerBucket":
                        config.setCotNumPerBucket(Integer.parseInt(value));
                        break;
                    case "batch":
                    case "onlineBatchSize":
                        config.setOnlineBatchSize(Integer.parseInt(value));
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown argument: " + key);
                }
            }
            return config;
        }

        public int getBucketNum() {
            return bucketNum;
        }

        public BenchmarkConfig setBucketNum(int bucketNum) {
            this.bucketNum = bucketNum;
            return this;
        }

        public int getElementByteLength() {
            return elementByteLength;
        }

        public BenchmarkConfig setElementByteLength(int elementByteLength) {
            this.elementByteLength = elementByteLength;
            return this;
        }

        public int getCheckBits() {
            return checkBits;
        }

        public BenchmarkConfig setCheckBits(int checkBits) {
            this.checkBits = checkBits;
            return this;
        }

        public int getTagBits() {
            return tagBits;
        }

        public BenchmarkConfig setTagBits(int tagBits) {
            this.tagBits = tagBits;
            return this;
        }

        public int getCaseNum() {
            return caseNum;
        }

        public BenchmarkConfig setCaseNum(int caseNum) {
            this.caseNum = caseNum;
            return this;
        }

        public int getCotNumPerBucket() {
            return cotNumPerBucket;
        }

        public BenchmarkConfig setCotNumPerBucket(int cotNumPerBucket) {
            this.cotNumPerBucket = cotNumPerBucket;
            return this;
        }

        public int getOnlineBatchSize() {
            return onlineBatchSize;
        }

        public BenchmarkConfig setOnlineBatchSize(int onlineBatchSize) {
            this.onlineBatchSize = onlineBatchSize;
            return this;
        }

        public BaUpotConfig createBaUpotConfig() {
            return new BaUpotConfig.Builder()
                .setElementByteLength(elementByteLength)
                .setCheckBits(checkBits)
                .setTagBits(tagBits)
                .setCaseNum(caseNum)
                .setCotNumPerBucket(cotNumPerBucket)
                .setOnlineBatchSize(onlineBatchSize)
                .build();
        }
    }

    /**
     * Combined benchmark result.
     */
    public static class Result {
        /**
         * config.
         */
        private final BenchmarkConfig config;
        /**
         * sender result.
         */
        private final BaUpotPartyBenchmarkResult senderResult;
        /**
         * receiver result.
         */
        private final BaUpotPartyBenchmarkResult receiverResult;

        Result(BenchmarkConfig config, BaUpotPartyBenchmarkResult senderResult,
               BaUpotPartyBenchmarkResult receiverResult) {
            this.config = config;
            this.senderResult = senderResult;
            this.receiverResult = receiverResult;
        }

        public BenchmarkConfig getConfig() {
            return config;
        }

        public BaUpotPartyBenchmarkResult getSenderResult() {
            return senderResult;
        }

        public BaUpotPartyBenchmarkResult getReceiverResult() {
            return receiverResult;
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

        public double getOfflineNanosPerBucket() {
            return ((double) getOfflineTimeNanos()) / config.getBucketNum();
        }

        public double getOnlineNanosPerBucket() {
            return ((double) getOnlineTimeNanos()) / config.getBucketNum();
        }

        public double getOfflineBytesPerBucket() {
            return ((double) getOfflineSendBytes()) / config.getBucketNum();
        }

        public double getOnlineBytesPerBucket() {
            return ((double) getOnlineSendBytes()) / config.getBucketNum();
        }

        public String toDisplayString() {
            return String.format(
                Locale.ROOT,
                "BA-UPOT specialized two-party benchmark%n"
                    + "bucketNum=%d, elementBytes=%d, checkBits=%d, tagBits=%d, cotPerBucket=%d, batch=%d%n"
                    + "offlineTime=%.3f ms, onlineTime=%.3f ms%n"
                    + "offlineNsPerBucket=%.2f, onlineNsPerBucket=%.2f%n"
                    + "offlineSendBytes=%d, onlineSendBytes=%d%n"
                    + "offlineBytesPerBucket=%.2f, onlineBytesPerBucket=%.2f%n"
                    + "senderOfflineBytes=%d, receiverOfflineBytes=%d%n"
                    + "senderOnlineBytes=%d, receiverOnlineBytes=%d%n"
                    + "senderChecksum=%016x, receiverChecksum=%016x",
                config.getBucketNum(),
                config.getElementByteLength(),
                config.getCheckBits(),
                config.getTagBits(),
                config.getCotNumPerBucket(),
                config.getOnlineBatchSize(),
                getOfflineTimeNanos() / 1_000_000.0,
                getOnlineTimeNanos() / 1_000_000.0,
                getOfflineNanosPerBucket(),
                getOnlineNanosPerBucket(),
                getOfflineSendBytes(),
                getOnlineSendBytes(),
                getOfflineBytesPerBucket(),
                getOnlineBytesPerBucket(),
                senderResult.getOfflineSendBytes(),
                receiverResult.getOfflineSendBytes(),
                senderResult.getOnlineSendBytes(),
                receiverResult.getOnlineSendBytes(),
                senderResult.getChecksum(),
                receiverResult.getChecksum()
            );
        }
    }
}
