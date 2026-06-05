package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Production UP-BA-UPOT source-agnostic public output.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeOutput {
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

    private BaSsuIbltProductionUnionProbeOutput(int bucketIndex, byte[] element, int elementByteLength) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (element != null && element.length != elementByteLength) {
            throw new IllegalArgumentException("element length must equal elementByteLength");
        }
        this.bucketIndex = bucketIndex;
        this.element = element == null ? null : Arrays.copyOf(element, element.length);
        this.elementByteLength = elementByteLength;
    }

    public static BaSsuIbltProductionUnionProbeOutput bottom(int bucketIndex, int elementByteLength) {
        return new BaSsuIbltProductionUnionProbeOutput(bucketIndex, null, elementByteLength);
    }

    public static BaSsuIbltProductionUnionProbeOutput singleton(int bucketIndex, byte[] element,
                                                                int elementByteLength) {
        if (element == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        return new BaSsuIbltProductionUnionProbeOutput(bucketIndex, element, elementByteLength);
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

    BaSsuIbltUnionProbeOutput toUnionProbeOutput() {
        return isSingleton()
            ? BaSsuIbltUnionProbeOutput.singleton(bucketIndex, element, elementByteLength)
            : BaSsuIbltUnionProbeOutput.bottom(bucketIndex, elementByteLength);
    }
}
