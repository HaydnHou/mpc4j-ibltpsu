package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Source-agnostic public output of one queue-peel union probe.
 *
 * <p>The output deliberately exposes only bottom/singleton and the singleton payload. It does not expose anchor,
 * shadow, shared, or blocked case labels.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUnionProbeOutput {
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * singleton element, null for bottom.
     */
    private final byte[] element;
    /**
     * public element byte length.
     */
    private final int elementByteLength;

    private BaSsuIbltUnionProbeOutput(int bucketIndex, byte[] element, int elementByteLength) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (element != null && element.length != elementByteLength) {
            throw new IllegalArgumentException("element length must equal public elementByteLength");
        }
        this.bucketIndex = bucketIndex;
        this.element = element == null ? null : Arrays.copyOf(element, element.length);
        this.elementByteLength = elementByteLength;
    }

    public static BaSsuIbltUnionProbeOutput bottom(int bucketIndex, int elementByteLength) {
        return new BaSsuIbltUnionProbeOutput(bucketIndex, null, elementByteLength);
    }

    public static BaSsuIbltUnionProbeOutput singleton(int bucketIndex, byte[] element) {
        if (element == null || element.length == 0) {
            throw new IllegalArgumentException("element must be non-empty");
        }
        return singleton(bucketIndex, element, element.length);
    }

    public static BaSsuIbltUnionProbeOutput singleton(int bucketIndex, byte[] element, int elementByteLength) {
        if (element == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        return new BaSsuIbltUnionProbeOutput(bucketIndex, element, elementByteLength);
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    public boolean isSingleton() {
        return element != null;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public byte[] getElement() {
        if (element == null) {
            throw new IllegalStateException("output is bottom");
        }
        return Arrays.copyOf(element, element.length);
    }
}
