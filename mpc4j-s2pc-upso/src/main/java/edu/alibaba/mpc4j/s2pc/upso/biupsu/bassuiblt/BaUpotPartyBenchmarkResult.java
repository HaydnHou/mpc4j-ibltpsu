package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * One-party BA-UPOT benchmark result.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotPartyBenchmarkResult {
    /**
     * offline time.
     */
    private final long offlineTimeNanos;
    /**
     * online time.
     */
    private final long onlineTimeNanos;
    /**
     * offline send bytes.
     */
    private final long offlineSendBytes;
    /**
     * online send bytes.
     */
    private final long onlineSendBytes;
    /**
     * checksum.
     */
    private final long checksum;

    BaUpotPartyBenchmarkResult(long offlineTimeNanos, long onlineTimeNanos, long offlineSendBytes,
                               long onlineSendBytes, long checksum) {
        this.offlineTimeNanos = offlineTimeNanos;
        this.onlineTimeNanos = onlineTimeNanos;
        this.offlineSendBytes = offlineSendBytes;
        this.onlineSendBytes = onlineSendBytes;
        this.checksum = checksum;
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

    public long getChecksum() {
        return checksum;
    }
}
