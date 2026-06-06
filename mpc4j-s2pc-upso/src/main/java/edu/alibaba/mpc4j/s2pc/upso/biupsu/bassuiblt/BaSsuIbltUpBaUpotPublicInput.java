package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Fixed public input for one queue-peel UP-BA-UPOT bucket probe.
 *
 * <p>This type contains only public schedule and shape data. It deliberately does not carry case labels, source labels,
 * membership labels, OT choices, or raw tag/check material.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotPublicInput {
    /**
     * public profile identifier.
     */
    private final String profileId;
    /**
     * public retry identifier.
     */
    private final int retryId;
    /**
     * public bucket index.
     */
    private final int bucketIndex;
    /**
     * public probe ordinal within this retry.
     */
    private final int probeOrdinal;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * OPRF tag bit length.
     */
    private final int tagBitLength;
    /**
     * check bit length.
     */
    private final int checkBitLength;
    /**
     * transport auth tag bit length.
     */
    private final int authTagBitLength;

    public BaSsuIbltUpBaUpotPublicInput(String profileId, int retryId, int bucketIndex, int probeOrdinal,
                                        int elementByteLength, int tagBitLength, int checkBitLength,
                                        int authTagBitLength) {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must be non-blank");
        }
        if (retryId < 0) {
            throw new IllegalArgumentException("retryId must be non-negative");
        }
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (probeOrdinal < 0) {
            throw new IllegalArgumentException("probeOrdinal must be non-negative");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (tagBitLength <= 0 || checkBitLength <= 0 || authTagBitLength <= 0) {
            throw new IllegalArgumentException("bit lengths must be positive");
        }
        if (tagBitLength % Byte.SIZE != 0 || checkBitLength % Byte.SIZE != 0
            || authTagBitLength % Byte.SIZE != 0) {
            throw new IllegalArgumentException("tag/check/auth bit lengths must be byte-aligned");
        }
        this.profileId = profileId;
        this.retryId = retryId;
        this.bucketIndex = bucketIndex;
        this.probeOrdinal = probeOrdinal;
        this.elementByteLength = elementByteLength;
        this.tagBitLength = tagBitLength;
        this.checkBitLength = checkBitLength;
        this.authTagBitLength = authTagBitLength;
    }

    public String getProfileId() {
        return profileId;
    }

    public int getRetryId() {
        return retryId;
    }

    public int getBucketIndex() {
        return bucketIndex;
    }

    public int getProbeOrdinal() {
        return probeOrdinal;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public int getTagBitLength() {
        return tagBitLength;
    }

    public int getCheckBitLength() {
        return checkBitLength;
    }

    public int getAuthTagBitLength() {
        return authTagBitLength;
    }

    int getTagByteLength() {
        return byteLength(tagBitLength);
    }

    int getCheckByteLength() {
        return byteLength(checkBitLength);
    }

    int getAuthTagByteLength() {
        return byteLength(authTagBitLength);
    }

    private static int byteLength(int bitLength) {
        return (bitLength + Byte.SIZE - 1) / Byte.SIZE;
    }
}
