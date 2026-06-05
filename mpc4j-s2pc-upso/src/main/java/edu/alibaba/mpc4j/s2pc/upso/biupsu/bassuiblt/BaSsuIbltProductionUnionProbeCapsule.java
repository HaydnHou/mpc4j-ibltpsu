package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Fixed-size production UP-BA-UPOT local probe capsule.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeCapsule {
    /**
     * public bucket index.
     */
    private final int bucketIndex;
    /**
     * fixed-size opaque encoding.
     */
    private final byte[] encoded;

    public BaSsuIbltProductionUnionProbeCapsule(int bucketIndex, byte[] encoded, int expectedEncodedByteLength) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (expectedEncodedByteLength <= 0) {
            throw new IllegalArgumentException("expectedEncodedByteLength must be positive");
        }
        if (encoded == null || encoded.length != expectedEncodedByteLength) {
            throw new IllegalArgumentException("encoded length must equal expectedEncodedByteLength");
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

    BaSsuIbltUnionProbeCapsule toUnionProbeCapsule() {
        return new BaSsuIbltUnionProbeCapsule(bucketIndex, encoded, encoded.length);
    }

    static BaSsuIbltProductionUnionProbeCapsule fromUnionProbeCapsule(
        BaSsuIbltUnionProbeCapsule capsule, int expectedEncodedByteLength) {
        if (capsule == null) {
            throw new IllegalArgumentException("capsule must be non-null");
        }
        return new BaSsuIbltProductionUnionProbeCapsule(
            capsule.getBucketIndex(), capsule.getEncoded(), expectedEncodedByteLength
        );
    }
}
