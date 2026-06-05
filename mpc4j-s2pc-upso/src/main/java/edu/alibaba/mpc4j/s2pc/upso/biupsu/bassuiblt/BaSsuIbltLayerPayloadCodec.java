package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Fixed-shape BA-SSU-IBLT source-layer payload codec.
 *
 * <p>This codec serializes one local source layer as fixed-size bucket cells. It does not serialize raw elements or raw
 * element sets. The decoded cells are still a reference payload and reveal source-layer bucket summaries, so this codec
 * is not the final case-hiding BA-UPOT realization.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltLayerPayloadCodec {
    /**
     * private constructor.
     */
    private BaSsuIbltLayerPayloadCodec() {
        // empty
    }

    /**
     * Encodes one party's fixed source layer for all retries and all buckets.
     *
     * @param elementSet element set.
     * @param anchorLayer true if this source is the anchor layer.
     * @param elementByteLength element byte length.
     * @param params parameters.
     * @return fixed layer payload.
     */
    static List<byte[]> encodeLayer(Set<ByteBuffer> elementSet, boolean anchorLayer, int elementByteLength,
                                    BaSsuIbltBiUpsuParams params) {
        int checkByteLength = checkByteLength(params);
        List<byte[]> payload = new ArrayList<>(Math.toIntExact((long) params.getRetryCount() * params.getTableLength()));
        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, retryIndex, elementByteLength);
            if (anchorLayer) {
                table.insertAnchors(elementSet);
            } else {
                table.insertShadows(elementSet);
            }
            for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
                BaUpotBucketInput input = table.getBucketInput(bucketIndex);
                payload.add(encodeCell(anchorLayer, input, elementByteLength, checkByteLength));
            }
        }
        return payload;
    }

    /**
     * Decodes a fixed source-layer payload.
     *
     * @param payload payload.
     * @param elementByteLength element byte length.
     * @param params parameters.
     * @return decoded cells.
     * @throws MpcAbortException the protocol aborts.
     */
    static LayerCell[] decodeLayer(List<byte[]> payload, int elementByteLength, BaSsuIbltBiUpsuParams params)
        throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(payload != null, "layer payload must not be null");
        int checkByteLength = checkByteLength(params);
        int expectedCellCount = Math.toIntExact((long) params.getRetryCount() * params.getTableLength());
        MpcAbortPreconditions.checkArgument(
            payload.size() == expectedCellCount,
            "layer payload must contain a fixed public number of cells"
        );
        LayerCell[] cells = new LayerCell[payload.size()];
        int cellByteLength = cellByteLength(elementByteLength, checkByteLength);
        for (int index = 0; index < payload.size(); index++) {
            byte[] encodedCell = payload.get(index);
            MpcAbortPreconditions.checkArgument(encodedCell != null, "layer cell must not be null");
            MpcAbortPreconditions.checkArgument(
                encodedCell.length == cellByteLength,
                "invalid fixed layer cell length"
            );
            cells[index] = decodeCell(encodedCell, elementByteLength, checkByteLength);
        }
        return cells;
    }

    static int cellByteLength(int elementByteLength, int checkByteLength) {
        return Integer.BYTES + elementByteLength + checkByteLength;
    }

    static int checkByteLength(BaSsuIbltBiUpsuParams params) {
        return (params.getCheckBits() + Byte.SIZE - 1) / Byte.SIZE;
    }

    private static byte[] encodeCell(boolean anchorLayer, BaUpotBucketInput input, int elementByteLength,
                                     int checkByteLength) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(cellByteLength(elementByteLength, checkByteLength));
        if (anchorLayer) {
            byteBuffer.putInt(input.getAnchorCount());
            byteBuffer.put(input.getAnchorKeyXor());
            byteBuffer.put(input.getAnchorCheckXor());
        } else {
            byteBuffer.putInt(input.getShadowCount());
            byteBuffer.put(input.getShadowKeyXor());
            byteBuffer.put(input.getShadowCheckXor());
        }
        return byteBuffer.array();
    }

    private static LayerCell decodeCell(byte[] encodedCell, int elementByteLength, int checkByteLength)
        throws MpcAbortException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(encodedCell);
        int count = byteBuffer.getInt();
        MpcAbortPreconditions.checkArgument(count >= 0, "source-layer count must be non-negative");
        byte[] keyXor = new byte[elementByteLength];
        byteBuffer.get(keyXor);
        byte[] checkXor = new byte[checkByteLength];
        byteBuffer.get(checkXor);
        return new LayerCell(count, keyXor, checkXor);
    }

    /**
     * Decoded source-layer cell.
     */
    static class LayerCell {
        /**
         * source count.
         */
        private final int count;
        /**
         * key xor.
         */
        private final byte[] keyXor;
        /**
         * check xor.
         */
        private final byte[] checkXor;

        LayerCell(int count, byte[] keyXor, byte[] checkXor) {
            this.count = count;
            this.keyXor = Arrays.copyOf(keyXor, keyXor.length);
            this.checkXor = Arrays.copyOf(checkXor, checkXor.length);
        }

        int getCount() {
            return count;
        }

        byte[] getKeyXor() {
            return Arrays.copyOf(keyXor, keyXor.length);
        }

        byte[] getCheckXor() {
            return Arrays.copyOf(checkXor, checkXor.length);
        }
    }
}
