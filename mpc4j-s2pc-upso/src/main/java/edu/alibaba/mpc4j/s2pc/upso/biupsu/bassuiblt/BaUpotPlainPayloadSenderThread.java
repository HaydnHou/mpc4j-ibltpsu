package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.List;

/**
 * Plain payload-bound BA-UPOT sender thread.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadSenderThread extends Thread {
    /**
     * sender.
     */
    private final BaUpotPlainPayloadSender sender;
    /**
     * outputs.
     */
    private final Iterable<BaUpotBucketOutput> outputs;
    /**
     * bucket count.
     */
    private final int bucketNum;
    /**
     * result.
     */
    private BaUpotPartyBenchmarkResult result;
    /**
     * exception.
     */
    private Exception exception;

    public BaUpotPlainPayloadSenderThread(BaUpotPlainPayloadSender sender, List<BaUpotBucketOutput> outputs) {
        this(sender, outputs == null ? -1 : outputs.size(), outputs == null ? null : List.copyOf(outputs));
    }

    public BaUpotPlainPayloadSenderThread(BaUpotPlainPayloadSender sender, int bucketNum,
                                          Iterable<BaUpotBucketOutput> outputs) {
        if (sender == null) {
            throw new IllegalArgumentException("sender must be non-null");
        }
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        this.sender = sender;
        this.bucketNum = bucketNum;
        this.outputs = outputs;
    }

    public BaUpotPartyBenchmarkResult getResult() {
        return result;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public void run() {
        try {
            long offlineStart = System.nanoTime();
            sender.init(bucketNum);
            long offlineTimeNanos = System.nanoTime() - offlineStart;
            long offlineSendBytes = sender.getRpc().getSendByteLength();
            sender.getRpc().synchronize();
            sender.getRpc().reset();

            long onlineStart = System.nanoTime();
            long checksum = sender.execute(outputs.iterator());
            long onlineTimeNanos = System.nanoTime() - onlineStart;
            long onlineSendBytes = sender.getRpc().getSendByteLength();
            result = new BaUpotPartyBenchmarkResult(
                offlineTimeNanos, onlineTimeNanos, offlineSendBytes, onlineSendBytes, checksum
            );
            sender.getRpc().reset();
        } catch (Exception e) {
            exception = e;
        }
    }
}
