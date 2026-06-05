package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UPOT sender benchmark thread.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotSenderThread extends Thread {
    /**
     * sender.
     */
    private final BaUpotSender sender;
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

    public BaUpotSenderThread(BaUpotSender sender, int bucketNum) {
        this.sender = sender;
        this.bucketNum = bucketNum;
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
            long checksum = sender.execute();
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
