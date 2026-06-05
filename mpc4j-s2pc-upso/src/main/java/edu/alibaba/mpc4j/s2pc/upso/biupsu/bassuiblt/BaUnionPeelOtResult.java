package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.List;

/**
 * Protocol-facing BA-UnionPeel-OT bridge result.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtResult {
    /**
     * bridge config.
     */
    private final BaUnionPeelOtConfig config;
    /**
     * bucket count.
     */
    private final int bucketNum;
    /**
     * sender benchmark result.
     */
    private final BaUpotPartyBenchmarkResult senderResult;
    /**
     * receiver benchmark result.
     */
    private final BaUpotPartyBenchmarkResult receiverResult;
    /**
     * decoded outputs.
     */
    private final List<BaUpotBucketOutput> decodedOutputs;
    /**
     * decoded output count.
     */
    private final int decodedOutputCount;
    /**
     * true if decoded outputs are retained.
     */
    private final boolean decodedOutputsRetained;

    BaUnionPeelOtResult(BaUnionPeelOtConfig config, int bucketNum, BaUpotPartyBenchmarkResult senderResult,
                        BaUpotPartyBenchmarkResult receiverResult, List<BaUpotBucketOutput> decodedOutputs) {
        this(config, bucketNum, senderResult, receiverResult, decodedOutputs,
            decodedOutputs == null ? 0 : decodedOutputs.size(), true);
    }

    BaUnionPeelOtResult(BaUnionPeelOtConfig config, int bucketNum, BaUpotPartyBenchmarkResult senderResult,
                        BaUpotPartyBenchmarkResult receiverResult, List<BaUpotBucketOutput> decodedOutputs,
                        int decodedOutputCount, boolean decodedOutputsRetained) {
        if (decodedOutputs == null) {
            throw new IllegalArgumentException("decodedOutputs must be non-null");
        }
        if (decodedOutputCount < 0) {
            throw new IllegalArgumentException("decodedOutputCount must be non-negative");
        }
        if (decodedOutputsRetained && decodedOutputs.size() != decodedOutputCount) {
            throw new IllegalArgumentException("retained decoded output count mismatch");
        }
        this.config = config;
        this.bucketNum = bucketNum;
        this.senderResult = senderResult;
        this.receiverResult = receiverResult;
        this.decodedOutputs = List.copyOf(decodedOutputs);
        this.decodedOutputCount = decodedOutputCount;
        this.decodedOutputsRetained = decodedOutputsRetained;
    }

    public BaUnionPeelOtConfig getConfig() {
        return config;
    }

    public int getBucketNum() {
        return bucketNum;
    }

    public BaUpotPartyBenchmarkResult getSenderResult() {
        return senderResult;
    }

    public BaUpotPartyBenchmarkResult getReceiverResult() {
        return receiverResult;
    }

    public List<BaUpotBucketOutput> getDecodedOutputs() {
        return decodedOutputs;
    }

    public int getDecodedOutputCount() {
        return decodedOutputCount;
    }

    public boolean isDecodedOutputsRetained() {
        return decodedOutputsRetained;
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
        return ((double) getOnlineSendBytes()) / bucketNum;
    }
}
