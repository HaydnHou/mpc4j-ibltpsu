package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilities for MCRG-token SOGS conditional release.
 *
 * @author donghai hou
 * @date 2026/06/17
 */
class McrgTokenSogsReleaseUtils {
    /**
     * Private constructor.
     */
    private McrgTokenSogsReleaseUtils() {
        // empty
    }

    /**
     * Expands a pnMCRG pad into one record-length one-time pad.
     *
     * <p>{@code rowIndex} is only domain separation inside a one-time anonymous carrier order. It must not be a
     * locator-linked index such as a Y element, bucket, bin, candidate, graph cell, or stable cross-round row.</p>
     */
    static byte[] expandPad(byte[] pad, int rowIndex, int outputByteLength) {
        if (pad == null || pad.length == 0) {
            throw new IllegalArgumentException("pad must be non-empty");
        }
        return hash(
            outputByteLength, "mcrg-token-sogs-pad", pad, ByteBuffer.allocate(Integer.BYTES).putInt(rowIndex).array()
        );
    }

    /**
     * Decodes a serialized atom if its tag verifies.
     */
    static DecodedAtom decodeValidAtom(byte[] atomBytes, int payloadByteLength) {
        int atomByteLength = TokenKeyedSogsSketch.atomByteLength(payloadByteLength);
        if (atomBytes.length != atomByteLength) {
            throw new IllegalArgumentException("invalid atom byte length");
        }
        ByteBuffer buffer = ByteBuffer.wrap(atomBytes);
        byte[] token = new byte[TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH];
        byte[] payload = new byte[payloadByteLength];
        byte[] tag = new byte[TokenKeyedSogsSketch.TAG_BYTE_LENGTH];
        buffer.get(token);
        buffer.get(payload);
        buffer.get(tag);
        byte[] expectedTag = hash(TokenKeyedSogsSketch.TAG_BYTE_LENGTH, "tag", token, payload);
        return Arrays.equals(tag, expectedTag) ? new DecodedAtom(token, payload) : null;
    }

    /**
     * Hashes variable-length data into the requested byte length.
     */
    private static byte[] hash(int outputByteLength, String domain, byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain.getBytes(StandardCharsets.UTF_8));
            for (byte[] part : parts) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(part.length).array());
                digest.update(part);
            }
            byte[] full = digest.digest();
            if (outputByteLength <= full.length) {
                return Arrays.copyOf(full, outputByteLength);
            }
            List<byte[]> blocks = new ArrayList<>();
            blocks.add(full);
            int counter = 1;
            int current = full.length;
            while (current < outputByteLength) {
                digest.reset();
                digest.update(full);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(counter).array());
                byte[] next = digest.digest();
                blocks.add(next);
                current += next.length;
                counter++;
            }
            byte[] output = new byte[outputByteLength];
            int offset = 0;
            for (byte[] block : blocks) {
                int copyLength = Math.min(block.length, output.length - offset);
                System.arraycopy(block, 0, output, offset, copyLength);
                offset += copyLength;
                if (offset == output.length) {
                    break;
                }
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Valid decoded atom.
     */
    static class DecodedAtom {
        /**
         * Token.
         */
        final byte[] token;
        /**
         * Payload.
         */
        final byte[] payload;

        DecodedAtom(byte[] token, byte[] payload) {
            this.token = token;
            this.payload = payload;
        }
    }
}
