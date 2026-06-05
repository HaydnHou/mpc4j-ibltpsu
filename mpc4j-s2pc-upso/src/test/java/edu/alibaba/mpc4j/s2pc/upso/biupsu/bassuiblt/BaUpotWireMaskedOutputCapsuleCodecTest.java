package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Wire-masked BA-UPOT output capsule codec tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotWireMaskedOutputCapsuleCodecTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 23;
    /**
     * tag byte length.
     */
    private static final int TAG_BYTE_LENGTH = 23;
    /**
     * seed.
     */
    private static final byte[] SEED = new byte[]{0x42, 0x41, 0x2D, 0x55, 0x50, 0x4F, 0x54};

    @Test
    public void testRoundTripAllCasesWithoutClearCaseByte() {
        BaUpotWireMaskedOutputCapsuleCodec codec = new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, SEED
        );
        BaUpotPlainOutputCapsuleCodec plainCodec = new BaUpotPlainOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH
        );
        List<BaUpotBucketOutput> outputs = List.of(
            BaUpotBucketOutput.empty(0),
            BaUpotBucketOutput.singleton(1, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(1)),
            BaUpotBucketOutput.singleton(2, BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, element(2)),
            BaUpotBucketOutput.singleton(3, BaUpotBucketOutput.CaseType.SHARED_SINGLETON, element(3)),
            BaUpotBucketOutput.blocked(4)
        );
        for (BaUpotBucketOutput output : outputs) {
            byte[] capsule = codec.encode(output);
            Assert.assertEquals(1 + ELEMENT_BYTE_LENGTH + CHECK_BYTE_LENGTH + TAG_BYTE_LENGTH, capsule.length);
            Assert.assertFalse(Arrays.equals(plainCodec.encode(output), capsule));
            BaUpotBucketOutput decoded = codec.decode(output.getBucketIndex(), capsule);
            assertOutputEquals(output, decoded);
        }
    }

    @Test
    public void testTamperRejected() {
        BaUpotWireMaskedOutputCapsuleCodec codec = new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, SEED
        );
        byte[] capsule = codec.encode(BaUpotBucketOutput.singleton(
            7, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(7)
        ));
        capsule[1] ^= 1;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(7, capsule));
    }

    @Test
    public void testWrongBucketRejected() {
        BaUpotWireMaskedOutputCapsuleCodec codec = new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, SEED
        );
        byte[] capsule = codec.encode(BaUpotBucketOutput.singleton(
            9, BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, element(9)
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(10, capsule));
    }

    @Test
    public void testWrongSeedRejected() {
        BaUpotWireMaskedOutputCapsuleCodec codec = new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, SEED
        );
        BaUpotWireMaskedOutputCapsuleCodec wrongCodec = new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, new byte[]{0x01, 0x02, 0x03}
        );
        byte[] capsule = codec.encode(BaUpotBucketOutput.singleton(
            11, BaUpotBucketOutput.CaseType.SHARED_SINGLETON, element(11)
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> wrongCodec.decode(11, capsule));
    }

    @Test
    public void testWireIndexSeparatesRepeatedBucketIndex() {
        BaUpotWireMaskedOutputCapsuleCodec codec = new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, SEED
        );
        BaUpotBucketOutput output = BaUpotBucketOutput.singleton(
            3, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(3)
        );
        byte[] firstRetryCapsule = codec.encode(output, 3);
        byte[] secondRetryCapsule = codec.encode(output, 19);
        Assert.assertFalse(Arrays.equals(firstRetryCapsule, secondRetryCapsule));
        assertOutputEquals(output, codec.decode(3, 19, secondRetryCapsule));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(3, 3, secondRetryCapsule));
    }

    @Test
    public void testRejectsNullOrEmptySeed() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, null
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaUpotWireMaskedOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH, new byte[0]
        ));
    }

    private static void assertOutputEquals(BaUpotBucketOutput expected, BaUpotBucketOutput actual) {
        Assert.assertEquals(expected.getBucketIndex(), actual.getBucketIndex());
        Assert.assertEquals(expected.getCaseType(), actual.getCaseType());
        Assert.assertEquals(expected.isSingleton(), actual.isSingleton());
        if (expected.isSingleton()) {
            Assert.assertArrayEquals(expected.getElement(), actual.getElement());
        }
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }
}
