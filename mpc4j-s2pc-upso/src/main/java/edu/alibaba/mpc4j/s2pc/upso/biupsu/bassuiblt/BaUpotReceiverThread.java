package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UPOT receiver benchmark thread.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotReceiverThread extends Thread {
    /**
     * receiver.
     */
    private final BaUpotReceiver receiver;
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

    public BaUpotReceiverThread(BaUpotReceiver receiver, int bucketNum) {
        this.receiver = receiver;
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
            receiver.init(bucketNum);
            long offlineTimeNanos = System.nanoTime() - offlineStart;
            long offlineSendBytes = receiver.getRpc().getSendByteLength();
            receiver.getRpc().synchronize();
            receiver.getRpc().reset();

            long onlineStart = System.nanoTime();
            long checksum = receiver.execute();
            long onlineTimeNanos = System.nanoTime() - onlineStart;
            long onlineSendBytes = receiver.getRpc().getSendByteLength();
            result = new BaUpotPartyBenchmarkResult(
                offlineTimeNanos, onlineTimeNanos, offlineSendBytes, onlineSendBytes, checksum
            );
            receiver.getRpc().reset();
        } catch (Exception e) {
            exception = e;
        }
    }
}
