package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT plain end-to-end result.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltPlainResult {
    /**
     * success.
     */
    private final boolean success;
    /**
     * selected retry index.
     */
    private final int selectedRetryIndex;
    /**
     * expected union size.
     */
    private final int expectedUnionSize;
    /**
     * PSI-CA.
     */
    private final int psica;
    /**
     * true if the left input is the anchor layer.
     */
    private final boolean leftAnchor;
    /**
     * left-party output.
     */
    private final BiUpsuPartyOutput leftOutput;
    /**
     * right-party output.
     */
    private final BiUpsuPartyOutput rightOutput;
    /**
     * left-party union output.
     */
    private final Set<ByteBuffer> leftUnion;
    /**
     * right-party union output.
     */
    private final Set<ByteBuffer> rightUnion;
    /**
     * elements delivered to the left party from the opposite side.
     */
    private final Set<ByteBuffer> leftReceivedDifference;
    /**
     * elements delivered to the right party from the opposite side.
     */
    private final Set<ByteBuffer> rightReceivedDifference;
    /**
     * signed/source-split peel summary.
     */
    private final BaSsuIbltSignedPeelOutput signedPeelOutput;
    /**
     * scheduled bucket count.
     */
    private final long scheduledBucketCount;
    /**
     * max round count across attempts.
     */
    private final int maxRoundCount;
    /**
     * selected attempt round count.
     */
    private final int selectedRoundCount;
    /**
     * cross-layer blocking count.
     */
    private final long crossLayerBlockingCount;
    /**
     * residual anchor count.
     */
    private final int anchorResidualCount;
    /**
     * residual shadow count.
     */
    private final int shadowResidualCount;
    /**
     * false-test budget.
     */
    private final long nTests;
    /**
     * recommended check bits.
     */
    private final int recommendedCheckBits;
    /**
     * all fixed-retry bucket traces.
     */
    private final List<BaSsuIbltBucketTrace> bucketTrace;

    BaSsuIbltPlainResult(boolean success, int selectedRetryIndex, int expectedUnionSize, boolean leftAnchor,
                         Set<ByteBuffer> leftUnion, Set<ByteBuffer> rightUnion,
                         Set<ByteBuffer> leftReceivedDifference, Set<ByteBuffer> rightReceivedDifference,
                         BaSsuIbltSignedPeelOutput signedPeelOutput, int psica, long scheduledBucketCount,
                         int maxRoundCount, int selectedRoundCount,
                         long crossLayerBlockingCount, int anchorResidualCount, int shadowResidualCount,
                         long nTests, int recommendedCheckBits, List<BaSsuIbltBucketTrace> bucketTrace) {
        this.success = success;
        this.selectedRetryIndex = selectedRetryIndex;
        this.expectedUnionSize = expectedUnionSize;
        this.psica = psica;
        this.leftAnchor = leftAnchor;
        leftOutput = new BiUpsuPartyOutput(leftUnion, psica);
        rightOutput = new BiUpsuPartyOutput(rightUnion, psica);
        this.leftUnion = immutableCopy(leftUnion);
        this.rightUnion = immutableCopy(rightUnion);
        this.leftReceivedDifference = immutableCopy(leftReceivedDifference);
        this.rightReceivedDifference = immutableCopy(rightReceivedDifference);
        this.signedPeelOutput = signedPeelOutput;
        this.scheduledBucketCount = scheduledBucketCount;
        this.maxRoundCount = maxRoundCount;
        this.selectedRoundCount = selectedRoundCount;
        this.crossLayerBlockingCount = crossLayerBlockingCount;
        this.anchorResidualCount = anchorResidualCount;
        this.shadowResidualCount = shadowResidualCount;
        this.nTests = nTests;
        this.recommendedCheckBits = recommendedCheckBits;
        this.bucketTrace = Collections.unmodifiableList(new ArrayList<>(bucketTrace));
    }

    public boolean isSuccess() {
        return success;
    }

    public int getSelectedRetryIndex() {
        return selectedRetryIndex;
    }

    public int getExpectedUnionSize() {
        return expectedUnionSize;
    }

    public int getPsica() {
        return psica;
    }

    public boolean isLeftAnchor() {
        return leftAnchor;
    }

    public BiUpsuPartyOutput getLeftOutput() {
        return leftOutput;
    }

    public BiUpsuPartyOutput getRightOutput() {
        return rightOutput;
    }

    public Set<ByteBuffer> getLeftUnion() {
        return immutableCopy(leftUnion);
    }

    public Set<ByteBuffer> getRightUnion() {
        return immutableCopy(rightUnion);
    }

    public Set<ByteBuffer> getLeftReceivedDifference() {
        return immutableCopy(leftReceivedDifference);
    }

    public Set<ByteBuffer> getRightReceivedDifference() {
        return immutableCopy(rightReceivedDifference);
    }

    public BaSsuIbltSignedPeelOutput getSignedPeelOutput() {
        return signedPeelOutput;
    }

    public Set<ByteBuffer> getAnchorOnlyElements() {
        return signedPeelOutput.getAnchorOnlyElements();
    }

    public Set<ByteBuffer> getShadowOnlyElements() {
        return signedPeelOutput.getShadowOnlyElements();
    }

    public int getSharedSingletonCount() {
        return signedPeelOutput.getSharedSingletonCount();
    }

    public long getScheduledBucketCount() {
        return scheduledBucketCount;
    }

    public int getMaxRoundCount() {
        return maxRoundCount;
    }

    public int getSelectedRoundCount() {
        return selectedRoundCount;
    }

    public long getCrossLayerBlockingCount() {
        return crossLayerBlockingCount;
    }

    public int getAnchorResidualCount() {
        return anchorResidualCount;
    }

    public int getShadowResidualCount() {
        return shadowResidualCount;
    }

    public long getNTests() {
        return nTests;
    }

    public int getRecommendedCheckBits() {
        return recommendedCheckBits;
    }

    public boolean hasBucketTrace() {
        return !bucketTrace.isEmpty();
    }

    List<BaSsuIbltBucketTrace> getBucketTrace() {
        return bucketTrace;
    }

    /**
     * Estimates BA-UPOT cost using a measured per-bucket model.
     *
     * @param offlineNanosPerBucket offline ns per bucket.
     * @param onlineNanosPerBucket online ns per bucket.
     * @param offlineBytesPerBucket offline bytes per bucket.
     * @param onlineBytesPerBucket online bytes per bucket.
     * @return estimate.
     */
    public CostEstimate estimateCost(double offlineNanosPerBucket, double onlineNanosPerBucket,
                                     double offlineBytesPerBucket, double onlineBytesPerBucket) {
        return new CostEstimate(
            scheduledBucketCount,
            Math.round(scheduledBucketCount * offlineNanosPerBucket),
            Math.round(scheduledBucketCount * onlineNanosPerBucket),
            Math.round(scheduledBucketCount * offlineBytesPerBucket),
            Math.round(scheduledBucketCount * onlineBytesPerBucket)
        );
    }

    private static Set<ByteBuffer> immutableCopy(Set<ByteBuffer> input) {
        Set<ByteBuffer> copy = new LinkedHashSet<>(input.size());
        for (ByteBuffer element : input) {
            copy.add(ByteBuffer.wrap(toBytes(element)));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    /**
     * Plain cost estimate.
     */
    public static class CostEstimate {
        /**
         * bucket count.
         */
        private final long bucketCount;
        /**
         * offline time.
         */
        private final long offlineTimeNanos;
        /**
         * online time.
         */
        private final long onlineTimeNanos;
        /**
         * offline communication.
         */
        private final long offlineBytes;
        /**
         * online communication.
         */
        private final long onlineBytes;

        CostEstimate(long bucketCount, long offlineTimeNanos, long onlineTimeNanos, long offlineBytes,
                     long onlineBytes) {
            this.bucketCount = bucketCount;
            this.offlineTimeNanos = offlineTimeNanos;
            this.onlineTimeNanos = onlineTimeNanos;
            this.offlineBytes = offlineBytes;
            this.onlineBytes = onlineBytes;
        }

        public long getBucketCount() {
            return bucketCount;
        }

        public long getOfflineTimeNanos() {
            return offlineTimeNanos;
        }

        public long getOnlineTimeNanos() {
            return onlineTimeNanos;
        }

        public long getTotalTimeNanos() {
            return offlineTimeNanos + onlineTimeNanos;
        }

        public long getOfflineBytes() {
            return offlineBytes;
        }

        public long getOnlineBytes() {
            return onlineBytes;
        }

        public long getTotalBytes() {
            return offlineBytes + onlineBytes;
        }
    }
}
