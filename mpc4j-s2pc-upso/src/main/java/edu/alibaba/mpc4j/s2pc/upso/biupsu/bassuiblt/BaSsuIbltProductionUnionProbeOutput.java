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
     * fixed public result type.
     */
    public enum Type {
        /**
         * non-output bottom.
         */
        BOTTOM,
        /**
         * source-agnostic union singleton.
         */
        UNION_SINGLETON
    }

    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * output type.
     */
    private final Type type;
    /**
     * singleton element, null for bottom.
     */
    private final byte[] element;
    /**
     * public element byte length.
     */
    private final int elementByteLength;

    private BaSsuIbltProductionUnionProbeOutput(int bucketIndex, Type type, byte[] element, int elementByteLength) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must be non-null");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (type == Type.UNION_SINGLETON && element == null) {
            throw new IllegalArgumentException("singleton element must be non-null");
        }
        if (type != Type.UNION_SINGLETON && element != null) {
            throw new IllegalArgumentException("only singleton output may carry element payload");
        }
        if (type == Type.UNION_SINGLETON && element.length != elementByteLength) {
            throw new IllegalArgumentException("element length must equal elementByteLength");
        }
        this.bucketIndex = bucketIndex;
        this.type = type;
        this.element = element == null ? null : Arrays.copyOf(element, element.length);
        this.elementByteLength = elementByteLength;
    }

    public static BaSsuIbltProductionUnionProbeOutput bottom(int bucketIndex, int elementByteLength) {
        return new BaSsuIbltProductionUnionProbeOutput(bucketIndex, Type.BOTTOM, null, elementByteLength);
    }

    public static BaSsuIbltProductionUnionProbeOutput singleton(int bucketIndex, byte[] element,
                                                                int elementByteLength) {
        return new BaSsuIbltProductionUnionProbeOutput(bucketIndex, Type.UNION_SINGLETON, element, elementByteLength);
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    public Type getType() {
        return type;
    }

    public boolean isBottom() {
        return type == Type.BOTTOM;
    }

    public boolean isSingleton() {
        return type == Type.UNION_SINGLETON;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public byte[] getElement() {
        if (!isSingleton()) {
            throw new IllegalStateException("output does not carry a union singleton element");
        }
        return Arrays.copyOf(element, element.length);
    }

    BaSsuIbltUnionProbeOutput toUnionProbeOutput() {
        return isSingleton()
            ? BaSsuIbltUnionProbeOutput.singleton(bucketIndex, element, elementByteLength)
            : BaSsuIbltUnionProbeOutput.bottom(bucketIndex, elementByteLength);
    }
}
