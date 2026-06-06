package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Objects;

/**
 * Fixed public schedule for UP-BA-UPOT offline material.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public final class BaSsuIbltUpBaUpotOfflineSchedule {
    /**
     * public profile identifier.
     */
    private final String profileId;
    /**
     * public retry count.
     */
    private final int retryNum;
    /**
     * public max probe count per retry.
     */
    private final int maxProbeNum;
    /**
     * public source-split table length.
     */
    private final int tableLength;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * auth tag byte length.
     */
    private final int authTagByteLength;

    public BaSsuIbltUpBaUpotOfflineSchedule(String profileId, int retryNum, int maxProbeNum, int tableLength,
                                            int elementByteLength, int tagByteLength, int checkByteLength,
                                            int authTagByteLength) {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must be non-blank");
        }
        if (retryNum <= 0) {
            throw new IllegalArgumentException("retryNum must be positive");
        }
        if (maxProbeNum <= 0) {
            throw new IllegalArgumentException("maxProbeNum must be positive");
        }
        if (tableLength <= 0) {
            throw new IllegalArgumentException("tableLength must be positive");
        }
        if (elementByteLength <= 0 || tagByteLength <= 0 || checkByteLength <= 0 || authTagByteLength <= 0) {
            throw new IllegalArgumentException("byte lengths must be positive");
        }
        this.profileId = profileId;
        this.retryNum = retryNum;
        this.maxProbeNum = maxProbeNum;
        this.tableLength = tableLength;
        this.elementByteLength = elementByteLength;
        this.tagByteLength = tagByteLength;
        this.checkByteLength = checkByteLength;
        this.authTagByteLength = authTagByteLength;
        validateAccountingShape();
    }

    public String getProfileId() {
        return profileId;
    }

    public int getRetryNum() {
        return retryNum;
    }

    public int getMaxProbeNum() {
        return maxProbeNum;
    }

    public int getTableLength() {
        return tableLength;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public int getTagByteLength() {
        return tagByteLength;
    }

    public int getCheckByteLength() {
        return checkByteLength;
    }

    public int getAuthTagByteLength() {
        return authTagByteLength;
    }

    public int getMaterialCount() {
        return Math.multiplyExact(retryNum, maxProbeNum);
    }

    public int getProbeMaterialByteLength() {
        int byteLength = 0;
        for (BaSsuIbltUpBaUpotOfflineLabel label : BaSsuIbltUpBaUpotOfflineLabel.values()) {
            byteLength = Math.addExact(byteLength, label.byteLength(this));
        }
        return byteLength;
    }

    public long getTotalMaterialByteLength() {
        return Math.multiplyExact((long) getMaterialCount(), getProbeMaterialByteLength());
    }

    private void validateAccountingShape() {
        try {
            getProbeMaterialByteLength();
            getTotalMaterialByteLength();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("offline schedule material accounting overflows", e);
        }
    }

    BaSsuIbltUpBaUpotPublicInput publicInput(int retryId, int bucketIndex, int probeOrdinal) {
        if (retryId < 0 || retryId >= retryNum) {
            throw new IllegalArgumentException("retryId must be in range");
        }
        if (probeOrdinal < 0 || probeOrdinal >= maxProbeNum) {
            throw new IllegalArgumentException("probeOrdinal must be in range");
        }
        if (bucketIndex < 0 || bucketIndex >= tableLength) {
            throw new IllegalArgumentException("bucketIndex must be in table range");
        }
        return new BaSsuIbltUpBaUpotPublicInput(
            profileId, retryId, bucketIndex, probeOrdinal, elementByteLength, tagByteLength * Byte.SIZE,
            checkByteLength * Byte.SIZE, authTagByteLength * Byte.SIZE
        );
    }

    void validate(BaSsuIbltUpBaUpotPublicInput publicInput) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (!profileId.equals(publicInput.getProfileId())) {
            throw new IllegalArgumentException("publicInput profileId must match offline schedule");
        }
        if (publicInput.getRetryId() < 0 || publicInput.getRetryId() >= retryNum) {
            throw new IllegalArgumentException("publicInput retryId must be in offline schedule range");
        }
        if (publicInput.getProbeOrdinal() < 0 || publicInput.getProbeOrdinal() >= maxProbeNum) {
            throw new IllegalArgumentException("publicInput probeOrdinal must be in offline schedule range");
        }
        if (publicInput.getBucketIndex() < 0 || publicInput.getBucketIndex() >= tableLength) {
            throw new IllegalArgumentException("publicInput bucketIndex must be in offline schedule table range");
        }
        if (publicInput.getElementByteLength() != elementByteLength
            || publicInput.getTagByteLength() != tagByteLength
            || publicInput.getCheckByteLength() != checkByteLength
            || publicInput.getAuthTagByteLength() != authTagByteLength) {
            throw new IllegalArgumentException("publicInput shape must match offline schedule");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BaSsuIbltUpBaUpotOfflineSchedule that)) {
            return false;
        }
        return retryNum == that.retryNum && maxProbeNum == that.maxProbeNum
            && tableLength == that.tableLength
            && elementByteLength == that.elementByteLength && tagByteLength == that.tagByteLength
            && checkByteLength == that.checkByteLength && authTagByteLength == that.authTagByteLength
            && profileId.equals(that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            profileId, retryNum, maxProbeNum, tableLength, elementByteLength, tagByteLength, checkByteLength,
            authTagByteLength
        );
    }
}
