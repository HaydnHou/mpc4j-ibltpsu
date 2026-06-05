package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.List;

/**
 * Plain payload-bound BA-UPOT receiver thread.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadReceiverThread extends Thread {
    /**
     * receiver.
     */
    private final BaUpotPlainPayloadReceiver receiver;
    /**
     * bucket indices.
     */
    private final Iterable<Integer> bucketIndices;
    /**
     * bucket count.
     */
    private final int bucketNum;
    /**
     * true if decoded outputs should be retained.
     */
    private final boolean retainOutputs;
    /**
     * result.
     */
    private BaUpotPartyBenchmarkResult result;
    /**
     * decoded outputs.
     */
    private List<BaUpotBucketOutput> outputs;
    /**
     * decoded output count.
     */
    private int decodedOutputCount;
    /**
     * exception.
     */
    private Exception exception;

    public BaUpotPlainPayloadReceiverThread(BaUpotPlainPayloadReceiver receiver, List<Integer> bucketIndices) {
        this(receiver, bucketIndices == null ? -1 : bucketIndices.size(),
            bucketIndices == null ? null : List.copyOf(bucketIndices), true);
    }

    public BaUpotPlainPayloadReceiverThread(BaUpotPlainPayloadReceiver receiver, int bucketNum,
                                            Iterable<Integer> bucketIndices, boolean retainOutputs) {
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must be non-null");
        }
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        if (bucketIndices == null) {
            throw new IllegalArgumentException("bucketIndices must be non-null");
        }
        this.receiver = receiver;
        this.bucketNum = bucketNum;
        this.bucketIndices = bucketIndices;
        this.retainOutputs = retainOutputs;
    }

    public BaUpotPartyBenchmarkResult getResult() {
        return result;
    }

    public List<BaUpotBucketOutput> getOutputs() {
        return outputs == null ? List.of() : outputs;
    }

    public int getDecodedOutputCount() {
        return decodedOutputCount;
    }

    public Exception getException() {
        return exception;
    }

    @Override
    public void run() {
        try {
            long offlineStart = System.nanoTime();
            receiver.init(bucketNum);
            long offlineTimeNanos = System.nanoTime() - offlineStart;
            long offlineSendBytes = receiver.getRpc().getSendByteLength();
            receiver.getRpc().synchronize();
            receiver.getRpc().reset();

            long onlineStart = System.nanoTime();
            long checksum = receiver.execute(bucketIndices.iterator(), retainOutputs);
            long onlineTimeNanos = System.nanoTime() - onlineStart;
            long onlineSendBytes = receiver.getRpc().getSendByteLength();
            outputs = receiver.getOutputs();
            decodedOutputCount = receiver.getDecodedOutputCount();
            result = new BaUpotPartyBenchmarkResult(
                offlineTimeNanos, onlineTimeNanos, offlineSendBytes, onlineSendBytes, checksum
            );
            receiver.getRpc().reset();
        } catch (Exception e) {
            exception = e;
        }
    }
}
