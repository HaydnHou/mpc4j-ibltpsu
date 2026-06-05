package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Streaming fixed-bucket BA-UPOT output iterable.
 *
 * <p>This iterable materializes one retry table at a time and evaluates buckets in the public direct-all-buckets
 * schedule. It avoids building a full debug transcript for large unbalanced benchmark runs.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltFixedBucketOutputIterable implements Iterable<BaUpotBucketOutput> {
    /**
     * anchor set.
     */
    private final Set<ByteBuffer> anchorSet;
    /**
     * shadow set.
     */
    private final Set<ByteBuffer> shadowSet;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * parameters.
     */
    private final BaSsuIbltBiUpsuParams params;
    /**
     * fixed schedule.
     */
    private final BaSsuIbltFixedBucketSchedule schedule;
    /**
     * evaluator.
     */
    private final BaUpotBucketEvaluator evaluator;
    /**
     * bucket count.
     */
    private final int bucketNum;

    BaSsuIbltFixedBucketOutputIterable(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength,
                                       BaSsuIbltBiUpsuParams params, BaUpotBucketEvaluator evaluator) {
        if (leftSet == null || rightSet == null) {
            throw new IllegalArgumentException("input sets must be non-null");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        if (evaluator == null) {
            throw new IllegalArgumentException("evaluator must be non-null");
        }
        Set<ByteBuffer> leftCopy = copySet(leftSet, elementByteLength);
        Set<ByteBuffer> rightCopy = copySet(rightSet, elementByteLength);
        anchorSet = leftCopy.size() >= rightCopy.size() ? leftCopy : rightCopy;
        shadowSet = leftCopy.size() >= rightCopy.size() ? rightCopy : leftCopy;
        if (anchorSet.size() > params.getNLarge()) {
            throw new IllegalArgumentException("anchor set exceeds nLarge");
        }
        if (shadowSet.size() > params.getNShadow()) {
            throw new IllegalArgumentException("shadow set exceeds nShadow");
        }
        this.elementByteLength = elementByteLength;
        this.params = params;
        this.evaluator = evaluator;
        schedule = BaSsuIbltFixedBucketSchedule.directAllBuckets(params);
        long totalBucketCount = schedule.getTotalBucketCount();
        if (totalBucketCount <= 0 || totalBucketCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("schedule bucket count must be in int range");
        }
        bucketNum = (int) totalBucketCount;
    }

    int size() {
        return bucketNum;
    }

    BaSsuIbltFixedBucketSchedule getSchedule() {
        return schedule;
    }

    @Override
    public Iterator<BaUpotBucketOutput> iterator() {
        return new Iterator<>() {
            /**
             * retry index.
             */
            private int retryIndex;
            /**
             * current retry bucket offset.
             */
            private int offset;
            /**
             * current retry bucket indices.
             */
            private int[] bucketIndices;
            /**
             * current retry table.
             */
            private BaSsuIbltAnchorTable table;
            /**
             * produced count.
             */
            private int produced;

            @Override
            public boolean hasNext() {
                return produced < bucketNum;
            }

            @Override
            public BaUpotBucketOutput next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                loadRetryIfNeeded();
                int bucketIndex = bucketIndices[offset];
                offset++;
                produced++;
                return evaluator.evaluate(table.getBucketInput(bucketIndex));
            }

            private void loadRetryIfNeeded() {
                if (table != null && offset < bucketIndices.length) {
                    return;
                }
                if (retryIndex >= schedule.getRetryCount()) {
                    throw new NoSuchElementException();
                }
                table = new BaSsuIbltAnchorTable(params, retryIndex, elementByteLength);
                table.insertAnchors(anchorSet);
                table.insertShadows(shadowSet);
                bucketIndices = schedule.getBucketIndices(retryIndex);
                offset = 0;
                retryIndex++;
            }
        };
    }

    private static Set<ByteBuffer> copySet(Set<ByteBuffer> input, int elementByteLength) {
        Set<ByteBuffer> output = new LinkedHashSet<>(input.size());
        for (ByteBuffer element : input) {
            if (element == null) {
                throw new IllegalArgumentException("input sets must not contain null elements");
            }
            ByteBuffer duplicate = element.asReadOnlyBuffer();
            duplicate.rewind();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            if (bytes.length != elementByteLength) {
                throw new IllegalArgumentException("element byte length must be " + elementByteLength);
            }
            output.add(ByteBuffer.wrap(Arrays.copyOf(bytes, bytes.length)));
        }
        return output;
    }
}
