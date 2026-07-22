package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Public MP-SOGS transcript summary.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsTranscript {
    private final Set<Long> unionOutput;
    private final List<MpSogsRoundStats> roundStats;
    private final boolean success;
    private final String failureReason;
    private final int hashSeedAttempts;
    /**
     * Local no-communication SOGS sketch construction time.
     */
    private final long offlineMs;
    /**
     * Protocol time measured inside the runner after local sketch construction.
     */
    private final long onlineMs;

    public MpSogsTranscript(Set<Long> unionOutput, List<MpSogsRoundStats> roundStats, boolean success,
                            String failureReason) {
        this(unionOutput, roundStats, success, failureReason, 1, 0L, 0L);
    }

    public MpSogsTranscript(Set<Long> unionOutput, List<MpSogsRoundStats> roundStats, boolean success,
                            String failureReason, int hashSeedAttempts) {
        this(unionOutput, roundStats, success, failureReason, hashSeedAttempts, 0L, 0L);
    }

    public MpSogsTranscript(Set<Long> unionOutput, List<MpSogsRoundStats> roundStats, boolean success,
                            String failureReason, int hashSeedAttempts, long offlineMs, long onlineMs) {
        if (hashSeedAttempts <= 0) {
            throw new IllegalArgumentException("hashSeedAttempts must be positive: " + hashSeedAttempts);
        }
        if (offlineMs < 0 || onlineMs < 0) {
            throw new IllegalArgumentException("timings must be non-negative: offline=" + offlineMs
                + ", online=" + onlineMs);
        }
        this.unionOutput = Collections.unmodifiableSet(new LinkedHashSet<>(unionOutput));
        this.roundStats = Collections.unmodifiableList(new ArrayList<>(roundStats));
        this.success = success;
        this.failureReason = failureReason;
        this.hashSeedAttempts = hashSeedAttempts;
        this.offlineMs = offlineMs;
        this.onlineMs = onlineMs;
    }

    public Set<Long> getUnionOutput() {
        return unionOutput;
    }

    public List<MpSogsRoundStats> getRoundStats() {
        return roundStats;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getHashSeedAttempts() {
        return hashSeedAttempts;
    }

    public long getOfflineMs() {
        return offlineMs;
    }

    public long getOnlineMs() {
        return onlineMs;
    }

    public int getRoundNum() {
        return roundStats.size();
    }

    public long getUpeelCalls() {
        return roundStats.stream().mapToLong(MpSogsRoundStats::getUpeelCalls).sum();
    }

    public int getDuplicateOpenings() {
        return roundStats.stream().mapToInt(MpSogsRoundStats::getDuplicateOpenings).sum();
    }

    public long getSendBytes() {
        return roundStats.stream().mapToLong(MpSogsRoundStats::getSendBytes).sum();
    }

    public long getReceiveBytes() {
        return roundStats.stream().mapToLong(MpSogsRoundStats::getReceiveBytes).sum();
    }

    public int getNetworkRoundCount() {
        return roundStats.stream().mapToInt(MpSogsRoundStats::getNetworkRoundCount).sum();
    }
}
