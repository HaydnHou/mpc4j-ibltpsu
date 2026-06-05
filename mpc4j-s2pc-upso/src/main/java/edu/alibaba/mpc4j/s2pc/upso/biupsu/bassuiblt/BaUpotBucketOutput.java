package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Payload-bound BA-UPOT bucket output.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotBucketOutput {
    /**
     * bucket case.
     */
    enum CaseType {
        /**
         * empty bucket.
         */
        EMPTY,
        /**
         * anchor-only singleton.
         */
        ANCHOR_SINGLETON,
        /**
         * shadow-only singleton.
         */
        SHADOW_SINGLETON,
        /**
         * same singleton in both source layers.
         */
        SHARED_SINGLETON,
        /**
         * blocked or invalid bucket.
         */
        BLOCKED
    }

    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * case type.
     */
    private final CaseType caseType;
    /**
     * singleton element, null for non-singleton cases.
     */
    private final byte[] element;

    private BaUpotBucketOutput(int bucketIndex, CaseType caseType, byte[] element) {
        this.bucketIndex = bucketIndex;
        this.caseType = caseType;
        this.element = element == null ? null : Arrays.copyOf(element, element.length);
    }

    /**
     * Creates an empty output.
     *
     * @param bucketIndex bucket index.
     * @return empty output.
     */
    static BaUpotBucketOutput empty(int bucketIndex) {
        return new BaUpotBucketOutput(bucketIndex, CaseType.EMPTY, null);
    }

    /**
     * Creates a blocked output.
     *
     * @param bucketIndex bucket index.
     * @return blocked output.
     */
    static BaUpotBucketOutput blocked(int bucketIndex) {
        return new BaUpotBucketOutput(bucketIndex, CaseType.BLOCKED, null);
    }

    /**
     * Creates a singleton output.
     *
     * @param bucketIndex bucket index.
     * @param caseType singleton case type.
     * @param element singleton element.
     * @return singleton output.
     */
    static BaUpotBucketOutput singleton(int bucketIndex, CaseType caseType, byte[] element) {
        if (caseType != CaseType.ANCHOR_SINGLETON && caseType != CaseType.SHADOW_SINGLETON
            && caseType != CaseType.SHARED_SINGLETON) {
            throw new IllegalArgumentException("caseType must be a singleton case");
        }
        if (element == null || element.length == 0) {
            throw new IllegalArgumentException("element must be non-empty");
        }
        return new BaUpotBucketOutput(bucketIndex, caseType, element);
    }

    int getBucketIndex() {
        return bucketIndex;
    }

    CaseType getCaseType() {
        return caseType;
    }

    boolean isSingleton() {
        return element != null;
    }

    byte[] getElement() {
        if (element == null) {
            throw new IllegalStateException("output does not contain a singleton");
        }
        return Arrays.copyOf(element, element.length);
    }

    byte[] getElementReference() {
        return element;
    }
}
