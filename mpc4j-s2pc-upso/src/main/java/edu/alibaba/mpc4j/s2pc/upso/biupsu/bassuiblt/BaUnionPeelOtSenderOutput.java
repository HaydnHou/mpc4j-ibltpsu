package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UnionPeel-OT sender output summary.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtSenderOutput {
    /**
     * bucket count.
     */
    private final int bucketNum;
    /**
     * COT count.
     */
    private final int cotNum;
    /**
     * fixed capsule byte length.
     */
    private final int capsuleByteLength;
    /**
     * checksum.
     */
    private final long checksum;

    BaUnionPeelOtSenderOutput(int bucketNum, int cotNum, int capsuleByteLength, long checksum) {
        if (bucketNum <= 0 || cotNum <= 0 || capsuleByteLength <= 0) {
            throw new IllegalArgumentException("counts and byte lengths must be positive");
        }
        this.bucketNum = bucketNum;
        this.cotNum = cotNum;
        this.capsuleByteLength = capsuleByteLength;
        this.checksum = checksum;
    }

    public int getBucketNum() {
        return bucketNum;
    }

    public int getCotNum() {
        return cotNum;
    }

    public int getCapsuleByteLength() {
        return capsuleByteLength;
    }

    public long getChecksum() {
        return checksum;
    }
}
