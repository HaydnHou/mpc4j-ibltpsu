package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Payload-bound BA-UPOT bucket input.
 *
 * <p>The object is the public shape of one scheduled BA-SSU bucket after the two source layers have been
 * accumulated. A real BA-UPOT implementation should receive secret-shared or masked equivalents of these fields and
 * return the same bucket output as {@link BaUpotIdeal} without revealing the case.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotBucketInput {
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * anchor count.
     */
    private final int anchorCount;
    /**
     * shadow count.
     */
    private final int shadowCount;
    /**
     * anchor key xor.
     */
    private final byte[] anchorKeyXor;
    /**
     * shadow key xor.
     */
    private final byte[] shadowKeyXor;
    /**
     * anchor check xor.
     */
    private final byte[] anchorCheckXor;
    /**
     * shadow check xor.
     */
    private final byte[] shadowCheckXor;

    private BaUpotBucketInput(int bucketIndex, int anchorCount, int shadowCount, byte[] anchorKeyXor,
                              byte[] shadowKeyXor, byte[] anchorCheckXor, byte[] shadowCheckXor) {
        this.bucketIndex = bucketIndex;
        this.anchorCount = anchorCount;
        this.shadowCount = shadowCount;
        this.anchorKeyXor = Arrays.copyOf(anchorKeyXor, anchorKeyXor.length);
        this.shadowKeyXor = Arrays.copyOf(shadowKeyXor, shadowKeyXor.length);
        this.anchorCheckXor = Arrays.copyOf(anchorCheckXor, anchorCheckXor.length);
        this.shadowCheckXor = Arrays.copyOf(shadowCheckXor, shadowCheckXor.length);
        validate();
    }

    /**
     * Creates a bucket input.
     *
     * @param bucketIndex bucket index.
     * @param anchorCount anchor count.
     * @param shadowCount shadow count.
     * @param anchorKeyXor anchor key xor.
     * @param shadowKeyXor shadow key xor.
     * @param anchorCheckXor anchor check xor.
     * @param shadowCheckXor shadow check xor.
     * @return bucket input.
     */
    static BaUpotBucketInput of(int bucketIndex, int anchorCount, int shadowCount, byte[] anchorKeyXor,
                                byte[] shadowKeyXor, byte[] anchorCheckXor, byte[] shadowCheckXor) {
        return new BaUpotBucketInput(bucketIndex, anchorCount, shadowCount, anchorKeyXor, shadowKeyXor,
            anchorCheckXor, shadowCheckXor);
    }

    /**
     * Creates an empty bucket input.
     *
     * @param bucketIndex bucket index.
     * @param elementByteLength element byte length.
     * @param checkByteLength check byte length.
     * @return empty bucket input.
     */
    static BaUpotBucketInput empty(int bucketIndex, int elementByteLength, int checkByteLength) {
        return new BaUpotBucketInput(bucketIndex, 0, 0, new byte[elementByteLength], new byte[elementByteLength],
            new byte[checkByteLength], new byte[checkByteLength]);
    }

    private void validate() {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (anchorCount < 0 || shadowCount < 0) {
            throw new IllegalArgumentException("source counts must be non-negative");
        }
        if (anchorKeyXor.length == 0 || anchorKeyXor.length != shadowKeyXor.length) {
            throw new IllegalArgumentException("key xor lengths must be positive and equal");
        }
        if (anchorCheckXor.length == 0 || anchorCheckXor.length != shadowCheckXor.length) {
            throw new IllegalArgumentException("check xor lengths must be positive and equal");
        }
    }

    int getBucketIndex() {
        return bucketIndex;
    }

    int getAnchorCount() {
        return anchorCount;
    }

    int getShadowCount() {
        return shadowCount;
    }

    int getElementByteLength() {
        return anchorKeyXor.length;
    }

    int getCheckByteLength() {
        return anchorCheckXor.length;
    }

    byte[] getAnchorKeyXor() {
        return Arrays.copyOf(anchorKeyXor, anchorKeyXor.length);
    }

    byte[] getShadowKeyXor() {
        return Arrays.copyOf(shadowKeyXor, shadowKeyXor.length);
    }

    byte[] getAnchorCheckXor() {
        return Arrays.copyOf(anchorCheckXor, anchorCheckXor.length);
    }

    byte[] getShadowCheckXor() {
        return Arrays.copyOf(shadowCheckXor, shadowCheckXor.length);
    }

    byte[] getAnchorKeyXorReference() {
        return anchorKeyXor;
    }

    byte[] getShadowKeyXorReference() {
        return shadowKeyXor;
    }

    byte[] getAnchorCheckXorReference() {
        return anchorCheckXor;
    }

    byte[] getShadowCheckXorReference() {
        return shadowCheckXor;
    }
}
