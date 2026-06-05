package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BA-SSU-IBLT fixed-schedule secure protocol core result.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltSecureProtocolResult {
    /**
     * success flag.
     */
    private final boolean success;
    /**
     * fixed round count.
     */
    private final int fixedRoundCount;
    /**
     * scheduled bucket count.
     */
    private final long scheduledBucketCount;
    /**
     * actual probe count.
     */
    private final long actualProbeCount;
    /**
     * protocol schedule shape.
     */
    private final BaSsuIbltProtocolSchedule.Shape shape;
    /**
     * public queue-peel retry statuses.
     */
    private final List<BaSsuIbltQueuePeelRetryStatus> retryStatuses;
    /**
     * peeled element count.
     */
    private final int peeledElementCount;
    /**
     * left union output.
     */
    private final Set<ByteBuffer> leftUnion;
    /**
     * right union output.
     */
    private final Set<ByteBuffer> rightUnion;

    BaSsuIbltSecureProtocolResult(boolean success, int fixedRoundCount, long scheduledBucketCount,
                                  int peeledElementCount, Set<ByteBuffer> leftUnion, Set<ByteBuffer> rightUnion) {
        this(
            success, fixedRoundCount, scheduledBucketCount, scheduledBucketCount,
            BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A, List.of(), peeledElementCount, leftUnion,
            rightUnion
        );
    }

    BaSsuIbltSecureProtocolResult(boolean success, int fixedRoundCount, long scheduledBucketCount,
                                  long actualProbeCount, BaSsuIbltProtocolSchedule.Shape shape,
                                  int peeledElementCount, Set<ByteBuffer> leftUnion, Set<ByteBuffer> rightUnion) {
        this(
            success, fixedRoundCount, scheduledBucketCount, actualProbeCount, shape, List.of(),
            peeledElementCount, leftUnion, rightUnion
        );
    }

    BaSsuIbltSecureProtocolResult(boolean success, int fixedRoundCount, long scheduledBucketCount,
                                  long actualProbeCount, BaSsuIbltProtocolSchedule.Shape shape,
                                  List<BaSsuIbltQueuePeelRetryStatus> retryStatuses, int peeledElementCount,
                                  Set<ByteBuffer> leftUnion, Set<ByteBuffer> rightUnion) {
        if (fixedRoundCount <= 0) {
            throw new IllegalArgumentException("fixedRoundCount must be positive");
        }
        if (scheduledBucketCount <= 0) {
            throw new IllegalArgumentException("scheduledBucketCount must be positive");
        }
        if (actualProbeCount < 0) {
            throw new IllegalArgumentException("actualProbeCount must be non-negative");
        }
        if (shape == null) {
            throw new IllegalArgumentException("shape must be non-null");
        }
        if (retryStatuses == null) {
            throw new IllegalArgumentException("retryStatuses must be non-null");
        }
        if (!retryStatuses.isEmpty()) {
            BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(retryStatuses);
        }
        if (peeledElementCount < 0) {
            throw new IllegalArgumentException("peeledElementCount must be non-negative");
        }
        if (leftUnion == null || rightUnion == null) {
            throw new IllegalArgumentException("union outputs must be non-null");
        }
        this.success = success;
        this.fixedRoundCount = fixedRoundCount;
        this.scheduledBucketCount = scheduledBucketCount;
        this.actualProbeCount = actualProbeCount;
        this.shape = shape;
        this.retryStatuses = List.copyOf(retryStatuses);
        this.peeledElementCount = peeledElementCount;
        this.leftUnion = copySet(leftUnion);
        this.rightUnion = copySet(rightUnion);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getFixedRoundCount() {
        return fixedRoundCount;
    }

    public long getScheduledBucketCount() {
        return scheduledBucketCount;
    }

    public long getActualProbeCount() {
        return actualProbeCount;
    }

    public BaSsuIbltProtocolSchedule.Shape getShape() {
        return shape;
    }

    public List<BaSsuIbltQueuePeelRetryStatus> getRetryStatuses() {
        return retryStatuses;
    }

    public int getPeeledElementCount() {
        return peeledElementCount;
    }

    public Set<ByteBuffer> getLeftUnion() {
        return copySet(leftUnion);
    }

    public Set<ByteBuffer> getRightUnion() {
        return copySet(rightUnion);
    }

    private static Set<ByteBuffer> copySet(Set<ByteBuffer> input) {
        return input.stream()
            .map(buffer -> {
                ByteBuffer duplicate = buffer.asReadOnlyBuffer();
                duplicate.rewind();
                byte[] bytes = new byte[duplicate.remaining()];
                duplicate.get(bytes);
                return ByteBuffer.wrap(Arrays.copyOf(bytes, bytes.length));
            })
            .collect(Collectors.toUnmodifiableSet());
    }
}
