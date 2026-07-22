package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * Tests for Mersenne-61 vector wire formats.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class Mersenne61VectorCodecTest {
    private final SecureRandom secureRandom = new SecureRandom();

    @Test
    public void testRoundTrip() {
        for (Mersenne61WireFormat wireFormat : Mersenne61WireFormat.values()) {
            for (int length : new int[]{0, 1, 2, 7, 8, 9, 31, 64, 257}) {
                long[] values = randomVector(length);
                if (length > 0) {
                    values[0] = 0L;
                    values[length - 1] = Mersenne61Field.PRIME - 1L;
                }
                byte[] encoded = Mersenne61VectorCodec.encode(values, wireFormat);
                Assert.assertEquals(Mersenne61VectorCodec.encodedByteLength(length, wireFormat), encoded.length);
                Assert.assertArrayEquals(values, Mersenne61VectorCodec.decode(encoded, length, wireFormat));
            }
        }
    }

    @Test
    public void testRangeEncoding() {
        long[] values = randomVector(37);
        byte[] encoded = Mersenne61VectorCodec.encode(values, 5, 23, Mersenne61WireFormat.PACKED_61);
        long[] expected = new long[23];
        System.arraycopy(values, 5, expected, 0, expected.length);
        Assert.assertArrayEquals(
            expected, Mersenne61VectorCodec.decode(encoded, expected.length, Mersenne61WireFormat.PACKED_61)
        );
    }

    @Test
    public void testCompressionRatio() {
        int length = 4096;
        Assert.assertEquals(32_768,
            Mersenne61VectorCodec.encodedByteLength(length, Mersenne61WireFormat.LONG_64));
        Assert.assertEquals(31_232,
            Mersenne61VectorCodec.encodedByteLength(length, Mersenne61WireFormat.PACKED_61));
    }

    @Test
    public void testRejectsMalformedEncoding() {
        Assert.assertThrows(IllegalArgumentException.class, () -> Mersenne61VectorCodec.encode(
            new long[]{Mersenne61Field.PRIME}, Mersenne61WireFormat.PACKED_61
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> Mersenne61VectorCodec.decode(
            new byte[7], 1, Mersenne61WireFormat.PACKED_61
        ));
        byte[] nonCanonical = new byte[]{-1, -1, -1, -1, -1, -1, -1, 0x1F};
        Assert.assertThrows(IllegalArgumentException.class, () -> Mersenne61VectorCodec.decode(
            nonCanonical, 1, Mersenne61WireFormat.PACKED_61
        ));
        byte[] trailingBits = new byte[8];
        trailingBits[7] = (byte) 0x80;
        Assert.assertThrows(IllegalArgumentException.class, () -> Mersenne61VectorCodec.decode(
            trailingBits, 1, Mersenne61WireFormat.PACKED_61
        ));
    }

    private long[] randomVector(int length) {
        long[] values = new long[length];
        for (int index = 0; index < values.length; index++) {
            values[index] = Mersenne61Field.fromUnsignedLong(secureRandom.nextLong());
        }
        return values;
    }
}
