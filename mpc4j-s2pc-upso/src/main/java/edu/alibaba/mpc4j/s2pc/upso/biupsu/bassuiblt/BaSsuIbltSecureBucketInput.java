package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * BA-SSU-IBLT secure source-split bucket input.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltSecureBucketInput {
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * anchor source view.
     */
    private final BaSsuIbltSecureCellView anchor;
    /**
     * shadow source view.
     */
    private final BaSsuIbltSecureCellView shadow;

    private BaSsuIbltSecureBucketInput(int bucketIndex, BaSsuIbltSecureCellView anchor,
                                       BaSsuIbltSecureCellView shadow) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must be non-null");
        }
        if (shadow == null) {
            throw new IllegalArgumentException("shadow must be non-null");
        }
        if (anchor.getElementByteLength() != shadow.getElementByteLength()
            || anchor.getTagByteLength() != shadow.getTagByteLength()
            || anchor.getCheckByteLength() != shadow.getCheckByteLength()) {
            throw new IllegalArgumentException("anchor and shadow byte lengths must match");
        }
        this.bucketIndex = bucketIndex;
        this.anchor = anchor;
        this.shadow = shadow;
    }

    static BaSsuIbltSecureBucketInput of(int bucketIndex, BaSsuIbltSecureCellView anchor,
                                         BaSsuIbltSecureCellView shadow) {
        return new BaSsuIbltSecureBucketInput(bucketIndex, anchor, shadow);
    }

    static BaSsuIbltSecureBucketInput empty(int bucketIndex, int elementByteLength, int tagByteLength,
                                           int checkByteLength) {
        return new BaSsuIbltSecureBucketInput(
            bucketIndex,
            BaSsuIbltSecureCellView.empty(elementByteLength, tagByteLength, checkByteLength),
            BaSsuIbltSecureCellView.empty(elementByteLength, tagByteLength, checkByteLength)
        );
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    BaSsuIbltSecureCellView getAnchor() {
        return anchor;
    }

    BaSsuIbltSecureCellView getShadow() {
        return shadow;
    }

    public boolean isEmpty() {
        return anchor.getCount() == 0 && shadow.getCount() == 0
            && isZero(anchor.getKeyXorReference()) && isZero(anchor.getTagXorReference())
            && isZero(anchor.getCheckXorReference()) && isZero(shadow.getKeyXorReference())
            && isZero(shadow.getTagXorReference()) && isZero(shadow.getCheckXorReference());
    }

    public boolean isSharedSingleton() {
        return anchor.isValidSingleton() && shadow.isValidSingleton()
            && Arrays.equals(anchor.getKeyXorReference(), shadow.getKeyXorReference())
            && Arrays.equals(anchor.getTagXorReference(), shadow.getTagXorReference());
    }

    public boolean isCrossLayerBlocking() {
        return anchor.getCount() == 1 && shadow.getCount() == 1
            && !Arrays.equals(anchor.getKeyXorReference(), shadow.getKeyXorReference());
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
