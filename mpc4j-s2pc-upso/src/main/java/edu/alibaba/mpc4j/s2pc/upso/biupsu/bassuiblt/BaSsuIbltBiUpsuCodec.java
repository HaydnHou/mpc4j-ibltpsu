package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT bi-output UPSU payload codec.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaSsuIbltBiUpsuCodec {
    /**
     * output header byte length.
     */
    private static final int OUTPUT_HEADER_BYTE_LENGTH = Integer.BYTES * 2;

    /**
     * private constructor.
     */
    private BaSsuIbltBiUpsuCodec() {
        // empty
    }

    static List<byte[]> encodeElementSet(Set<ByteBuffer> elementSet, int elementByteLength) {
        List<byte[]> payload = new ArrayList<>(elementSet.size());
        for (ByteBuffer element : sortedElements(elementSet)) {
            byte[] bytes = toBytes(element);
            if (bytes.length != elementByteLength) {
                throw new IllegalArgumentException("element byte length must be " + elementByteLength);
            }
            payload.add(bytes);
        }
        return payload;
    }

    static Set<ByteBuffer> decodeElementSet(List<byte[]> payload, int elementByteLength) throws MpcAbortException {
        Set<ByteBuffer> elementSet = new LinkedHashSet<>(payload.size());
        for (byte[] element : payload) {
            MpcAbortPreconditions.checkArgument(
                element.length == elementByteLength,
                "element byte length must be %s", elementByteLength
            );
            elementSet.add(ByteBuffer.wrap(Arrays.copyOf(element, element.length)));
        }
        return elementSet;
    }

    static List<byte[]> encodeOutput(BiUpsuPartyOutput output, int elementByteLength) {
        Set<ByteBuffer> union = output.getUnion();
        List<byte[]> payload = new ArrayList<>(union.size() + 1);
        ByteBuffer header = ByteBuffer.allocate(OUTPUT_HEADER_BYTE_LENGTH);
        header.putInt(output.getPsica());
        header.putInt(union.size());
        payload.add(header.array());
        payload.addAll(encodeElementSet(union, elementByteLength));
        return payload;
    }

    static BiUpsuPartyOutput decodeOutput(List<byte[]> payload, int elementByteLength) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(!payload.isEmpty(), "output payload must be non-empty");
        byte[] headerBytes = payload.get(0);
        MpcAbortPreconditions.checkArgument(
            headerBytes.length == OUTPUT_HEADER_BYTE_LENGTH,
            "invalid output header length"
        );
        ByteBuffer header = ByteBuffer.wrap(headerBytes);
        int psica = header.getInt();
        int unionSize = header.getInt();
        MpcAbortPreconditions.checkArgument(unionSize >= 0, "union size must be non-negative");
        MpcAbortPreconditions.checkArgument(payload.size() == unionSize + 1, "invalid output payload size");
        Set<ByteBuffer> union = decodeElementSet(payload.subList(1, payload.size()), elementByteLength);
        MpcAbortPreconditions.checkArgument(union.size() == unionSize, "duplicated output element");
        return new BiUpsuPartyOutput(union, psica);
    }

    private static List<ByteBuffer> sortedElements(Set<ByteBuffer> elementSet) {
        List<ByteBuffer> elements = new ArrayList<>(elementSet);
        elements.sort(Comparator.comparing(BaSsuIbltBiUpsuCodec::toBytes, BaSsuIbltBiUpsuCodec::compareBytes));
        return elements;
    }

    private static int compareBytes(byte[] left, byte[] right) {
        int minLength = Math.min(left.length, right.length);
        for (int i = 0; i < minLength; i++) {
            int leftValue = left[i] & 0xFF;
            int rightValue = right[i] & 0xFF;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
