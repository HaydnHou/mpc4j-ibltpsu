package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Packed payload codec for fixed-length online batches.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
final class BaSsuIbltFixedLengthBatchPayloadCodec {
    /**
     * single packed payload count.
     */
    private static final int PACKED_PAYLOAD_COUNT = 1;

    private BaSsuIbltFixedLengthBatchPayloadCodec() {
        // empty
    }

    static List<byte[]> pack(List<byte[]> chunks, int chunkByteLength) {
        validateChunkByteLength(chunkByteLength);
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must be non-empty");
        }
        byte[] packed = new byte[Math.multiplyExact(chunks.size(), chunkByteLength)];
        int offset = 0;
        for (byte[] chunk : chunks) {
            if (chunk == null || chunk.length != chunkByteLength) {
                throw new IllegalArgumentException("all chunks must match the fixed public byte length");
            }
            System.arraycopy(chunk, 0, packed, offset, chunkByteLength);
            offset += chunkByteLength;
        }
        return List.of(packed);
    }

    static List<byte[]> unpack(List<byte[]> payload, int chunkNum, int chunkByteLength) {
        validateChunkByteLength(chunkByteLength);
        if (chunkNum <= 0) {
            throw new IllegalArgumentException("chunkNum must be positive");
        }
        if (payload == null || payload.size() != PACKED_PAYLOAD_COUNT) {
            throw new IllegalArgumentException("packed payload must contain exactly one byte array");
        }
        byte[] packed = payload.get(0);
        int expectedLength = Math.multiplyExact(chunkNum, chunkByteLength);
        if (packed == null || packed.length != expectedLength) {
            throw new IllegalArgumentException("packed payload length must match the fixed public batch shape");
        }
        List<byte[]> chunks = new ArrayList<>(chunkNum);
        for (int chunkIndex = 0; chunkIndex < chunkNum; chunkIndex++) {
            int offset = Math.multiplyExact(chunkIndex, chunkByteLength);
            chunks.add(Arrays.copyOfRange(packed, offset, offset + chunkByteLength));
        }
        return chunks;
    }

    private static void validateChunkByteLength(int chunkByteLength) {
        if (chunkByteLength <= 0) {
            throw new IllegalArgumentException("chunkByteLength must be positive");
        }
    }
}
