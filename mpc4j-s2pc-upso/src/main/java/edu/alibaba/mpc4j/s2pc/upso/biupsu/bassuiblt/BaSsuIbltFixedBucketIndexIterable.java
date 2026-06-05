package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Fixed direct-all-buckets index iterable.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltFixedBucketIndexIterable implements Iterable<Integer> {
    /**
     * fixed schedule.
     */
    private final BaSsuIbltFixedBucketSchedule schedule;
    /**
     * bucket count.
     */
    private final int bucketNum;

    BaSsuIbltFixedBucketIndexIterable(BaSsuIbltFixedBucketSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        long totalBucketCount = schedule.getTotalBucketCount();
        if (totalBucketCount <= 0 || totalBucketCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("schedule bucket count must be in int range");
        }
        this.schedule = schedule;
        bucketNum = (int) totalBucketCount;
    }

    int size() {
        return bucketNum;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<>() {
            /**
             * retry index.
             */
            private int retryIndex;
            /**
             * bucket offset in current retry.
             */
            private int offset;
            /**
             * produced count.
             */
            private int produced;

            @Override
            public boolean hasNext() {
                return produced < bucketNum;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int tableLength = schedule.getTableLength();
                int bucketIndex = offset;
                offset++;
                produced++;
                if (offset == tableLength) {
                    offset = 0;
                    retryIndex++;
                }
                if (retryIndex > schedule.getRetryCount()) {
                    throw new IllegalStateException("iterator advanced past retry schedule");
                }
                return bucketIndex;
            }
        };
    }
}
