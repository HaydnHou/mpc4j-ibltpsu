package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Fixed-size masked capsule for one queue-peel union probe.
 *
 * <p>The capsule is an opaque transport object. It intentionally has no source/case-label accessor.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUnionProbeCapsule {
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * encoded masked payload.
     */
    private final byte[] encoded;

    public BaSsuIbltUnionProbeCapsule(int bucketIndex, byte[] encoded, int expectedEncodedByteLength) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (expectedEncodedByteLength <= 0) {
            throw new IllegalArgumentException("expectedEncodedByteLength must be positive");
        }
        if (encoded == null || encoded.length != expectedEncodedByteLength) {
            throw new IllegalArgumentException("encoded capsule length must equal expectedEncodedByteLength");
        }
        this.bucketIndex = bucketIndex;
        this.encoded = Arrays.copyOf(encoded, encoded.length);
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    public byte[] getEncoded() {
        return Arrays.copyOf(encoded, encoded.length);
    }
}
