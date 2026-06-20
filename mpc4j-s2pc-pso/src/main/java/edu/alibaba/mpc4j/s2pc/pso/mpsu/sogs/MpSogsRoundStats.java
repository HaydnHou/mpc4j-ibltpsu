package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Public MP-SOGS round statistics.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsRoundStats {
    private final int roundIndex;
    private final int queueSize;
    private final int openedBatchSize;
    private final int newDistinctSize;
    private final int duplicateOpenings;
    private final long uPeelCalls;
    private final long sendBytes;
    private final long receiveBytes;
    private final int networkRoundCount;

    public MpSogsRoundStats(
        int roundIndex, int queueSize, int openedBatchSize, int newDistinctSize, int duplicateOpenings,
        long uPeelCalls
    ) {
        this(roundIndex, queueSize, openedBatchSize, newDistinctSize, duplicateOpenings, uPeelCalls, 0L, 0L, 0);
    }

    public MpSogsRoundStats(
        int roundIndex, int queueSize, int openedBatchSize, int newDistinctSize, int duplicateOpenings,
        long uPeelCalls, long sendBytes, long receiveBytes, int networkRoundCount
    ) {
        if (sendBytes < 0 || receiveBytes < 0) {
            throw new IllegalArgumentException("byte counters must be non-negative");
        }
        if (networkRoundCount < 0) {
            throw new IllegalArgumentException("networkRoundCount must be non-negative");
        }
        this.roundIndex = roundIndex;
        this.queueSize = queueSize;
        this.openedBatchSize = openedBatchSize;
        this.newDistinctSize = newDistinctSize;
        this.duplicateOpenings = duplicateOpenings;
        this.uPeelCalls = uPeelCalls;
        this.sendBytes = sendBytes;
        this.receiveBytes = receiveBytes;
        this.networkRoundCount = networkRoundCount;
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getOpenedBatchSize() {
        return openedBatchSize;
    }

    public int getNewDistinctSize() {
        return newDistinctSize;
    }

    public int getDuplicateOpenings() {
        return duplicateOpenings;
    }

    public long getUpeelCalls() {
        return uPeelCalls;
    }

    public long getSendBytes() {
        return sendBytes;
    }

    public long getReceiveBytes() {
        return receiveBytes;
    }

    public int getNetworkRoundCount() {
        return networkRoundCount;
    }
}
