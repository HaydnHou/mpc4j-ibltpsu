package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import java.nio.ByteBuffer;

/**
 * Canonical vector codec for the Mersenne-61 field.
 *
 * <p>The packed representation is a little-endian bit stream with exactly 61 bits per element. The public protocol
 * shape supplies the element count, so no per-vector length header is needed.</p>
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class Mersenne61VectorCodec {
    private static final int FIELD_BIT_LENGTH = 61;

    private Mersenne61VectorCodec() {
        // empty
    }

    public static byte[] encode(long[] values, Mersenne61WireFormat wireFormat) {
        return encode(values, 0, values.length, wireFormat);
    }

    public static byte[] encode(long[] values, int offset, int length, Mersenne61WireFormat wireFormat) {
        checkRange(values.length, offset, length);
        return switch (wireFormat) {
            case LONG_64 -> encodeLong64(values, offset, length);
            case PACKED_61 -> encodePacked61(values, offset, length);
        };
    }

    public static long[] decode(byte[] payload, int elementCount, Mersenne61WireFormat wireFormat) {
        if (elementCount < 0) {
            throw new IllegalArgumentException("elementCount must be non-negative: " + elementCount);
        }
        return switch (wireFormat) {
            case LONG_64 -> decodeLong64(payload, elementCount);
            case PACKED_61 -> decodePacked61(payload, elementCount);
        };
    }

    public static int encodedByteLength(int elementCount, Mersenne61WireFormat wireFormat) {
        if (elementCount < 0) {
            throw new IllegalArgumentException("elementCount must be non-negative: " + elementCount);
        }
        if (wireFormat == Mersenne61WireFormat.LONG_64) {
            return Math.multiplyExact(elementCount, Long.BYTES);
        }
        long bitLength = Math.multiplyExact((long) elementCount, FIELD_BIT_LENGTH);
        long byteLength = (bitLength + Byte.SIZE - 1L) / Byte.SIZE;
        if (byteLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("packed vector is too large: " + elementCount);
        }
        return (int) byteLength;
    }

    private static byte[] encodeLong64(long[] values, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(encodedByteLength(length, Mersenne61WireFormat.LONG_64));
        for (int index = 0; index < length; index++) {
            long value = values[offset + index];
            checkElement(value);
            buffer.putLong(value);
        }
        return buffer.array();
    }

    private static long[] decodeLong64(byte[] payload, int elementCount) {
        int expectedLength = encodedByteLength(elementCount, Mersenne61WireFormat.LONG_64);
        if (payload.length != expectedLength) {
            throw new IllegalArgumentException("invalid 64-bit field-vector payload length: " + payload.length
                + " != " + expectedLength);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        long[] values = new long[elementCount];
        for (int index = 0; index < values.length; index++) {
            values[index] = buffer.getLong();
            checkElement(values[index]);
        }
        return values;
    }

    private static byte[] encodePacked61(long[] values, int offset, int length) {
        byte[] payload = new byte[encodedByteLength(length, Mersenne61WireFormat.PACKED_61)];
        for (int index = 0; index < length; index++) {
            long value = values[offset + index];
            checkElement(value);
            long bitOffset = (long) index * FIELD_BIT_LENGTH;
            int byteOffset = (int) (bitOffset >>> 3);
            int shift = (int) (bitOffset & 7L);
            orLittleEndianLong(payload, byteOffset, value << shift);
            if (shift > 3) {
                payload[byteOffset + Long.BYTES] |= (byte) (value >>> (Long.SIZE - shift));
            }
        }
        return payload;
    }

    private static long[] decodePacked61(byte[] payload, int elementCount) {
        int expectedLength = encodedByteLength(elementCount, Mersenne61WireFormat.PACKED_61);
        if (payload.length != expectedLength) {
            throw new IllegalArgumentException("invalid packed field-vector payload length: " + payload.length
                + " != " + expectedLength);
        }
        checkTrailingBits(payload, elementCount);
        long[] values = new long[elementCount];
        for (int index = 0; index < values.length; index++) {
            long bitOffset = (long) index * FIELD_BIT_LENGTH;
            int byteOffset = (int) (bitOffset >>> 3);
            int shift = (int) (bitOffset & 7L);
            long value = readLittleEndianLong(payload, byteOffset) >>> shift;
            if (shift > 3 && byteOffset + Long.BYTES < payload.length) {
                value |= (long) (payload[byteOffset + Long.BYTES] & 0xFF) << (Long.SIZE - shift);
            }
            values[index] = value & Mersenne61Field.PRIME;
            checkElement(values[index]);
        }
        return values;
    }

    private static void orLittleEndianLong(byte[] output, int offset, long value) {
        int byteNum = Math.min(Long.BYTES, output.length - offset);
        for (int byteIndex = 0; byteIndex < byteNum; byteIndex++) {
            output[offset + byteIndex] |= (byte) (value >>> (Byte.SIZE * byteIndex));
        }
    }

    private static long readLittleEndianLong(byte[] input, int offset) {
        long value = 0L;
        int byteNum = Math.min(Long.BYTES, input.length - offset);
        for (int byteIndex = 0; byteIndex < byteNum; byteIndex++) {
            value |= (long) (input[offset + byteIndex] & 0xFF) << (Byte.SIZE * byteIndex);
        }
        return value;
    }

    private static void checkTrailingBits(byte[] payload, int elementCount) {
        if (payload.length == 0) {
            return;
        }
        int usedBits = (elementCount * FIELD_BIT_LENGTH) & 7;
        if (usedBits == 0) {
            return;
        }
        int usedMask = (1 << usedBits) - 1;
        if (((payload[payload.length - 1] & 0xFF) & ~usedMask) != 0) {
            throw new IllegalArgumentException("non-zero trailing bits in packed field-vector payload");
        }
    }

    private static void checkRange(int arrayLength, int offset, int length) {
        if (offset < 0 || length < 0 || offset > arrayLength - length) {
            throw new IndexOutOfBoundsException("invalid vector range: offset=" + offset + ", length=" + length
                + ", arrayLength=" + arrayLength);
        }
    }

    private static void checkElement(long value) {
        if (!Mersenne61Field.isElement(value)) {
            throw new IllegalArgumentException("not a canonical Mersenne-61 field element: " + value);
        }
    }
}
