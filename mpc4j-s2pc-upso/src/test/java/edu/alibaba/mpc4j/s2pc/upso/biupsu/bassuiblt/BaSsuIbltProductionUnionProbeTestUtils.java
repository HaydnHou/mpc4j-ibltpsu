package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Shared test helpers for P36 production union-probe boundary tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeTestUtils {
    /**
     * element byte length.
     */
    static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * tag byte length.
     */
    static final int TAG_BYTE_LENGTH = 24;
    /**
     * check byte length.
     */
    static final int CHECK_BYTE_LENGTH = 16;

    private BaSsuIbltProductionUnionProbeTestUtils() {
        // empty
    }

    static BaSsuIbltProductionUnionProbeBackendConfig config() {
        return new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setTagByteLength(TAG_BYTE_LENGTH)
            .setCheckByteLength(CHECK_BYTE_LENGTH)
            .setCotNumPerProbe(3)
            .build();
    }

    static byte[] seed() {
        byte[] seed = new byte[32];
        Arrays.fill(seed, (byte) 0x42);
        return seed;
    }

    static BaSsuIbltSecureBucketInput input(int bucketIndex, BaSsuIbltSecureCellView anchor,
                                            BaSsuIbltSecureCellView shadow) {
        return BaSsuIbltSecureBucketInput.of(bucketIndex, anchor, shadow);
    }

    static BaSsuIbltSecureCellView emptyCell() {
        return BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH);
    }

    static BaSsuIbltSecureCellView singletonCell(long value) {
        return singletonCell(element(value));
    }

    static BaSsuIbltSecureCellView singletonCell(byte[] element) {
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(element, TAG_BYTE_LENGTH);
        return BaSsuIbltSecureCellView.of(1, element, tag, BaSsuIbltOprfTagPipeline.checkFromTag(tag, CHECK_BYTE_LENGTH));
    }

    static BaSsuIbltSecureCellView blockedCell(long value) {
        byte[] element = element(value);
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(element, TAG_BYTE_LENGTH);
        byte[] badCheck = BaSsuIbltOprfTagPipeline.checkFromTag(tag, CHECK_BYTE_LENGTH);
        badCheck[0] ^= 0x01;
        return BaSsuIbltSecureCellView.of(1, element, tag, badCheck);
    }

    static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }

    static boolean containsSubArray(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }
}
