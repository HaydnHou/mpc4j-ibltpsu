package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Public BA-SSU-IBLT placement hash.
 *
 * <p>The placement hash is public and is deliberately independent from OPRF tags.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltPlacement {
    /**
     * private constructor.
     */
    private BaSsuIbltPlacement() {
        // empty
    }

    /**
     * Computes positions for an integer simulation element.
     *
     * @param params parameters.
     * @param retryIndex retry index.
     * @param element positive element id.
     * @return positions.
     */
    public static int[] positions(BaSsuIbltBiUpsuParams params, int retryIndex, int element) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);
        byteBuffer.putInt(element);
        return positions(params, retryIndex, byteBuffer.array());
    }

    /**
     * Computes positions for a byte-array element.
     *
     * @param params parameters.
     * @param retryIndex retry index.
     * @param element element bytes.
     * @return positions.
     */
    public static int[] positions(BaSsuIbltBiUpsuParams params, int retryIndex, byte[] element) {
        int degree = params.getDegree();
        int tableLength = params.getTableLength();
        int[] positions = new int[degree];
        for (int i = 0; i < degree; i++) {
            int partitionStart = (int) (((long) i * tableLength) / degree);
            int partitionEnd = (int) (((long) (i + 1) * tableLength) / degree);
            int partitionSize = Math.max(1, partitionEnd - partitionStart);
            int offset = Math.floorMod(hashToInt(params.getPublicPlaceSeedReference(), retryIndex, i, element),
                partitionSize);
            positions[i] = partitionStart + offset;
        }
        return positions;
    }

    private static int hashToInt(byte[] publicPlaceSeed, int retryIndex, int hashIndex, byte[] element) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((byte) 0x42);
            digest.update(publicPlaceSeed);
            digest.update(ByteBuffer.allocate(Integer.BYTES * 2)
                .putInt(retryIndex)
                .putInt(hashIndex)
                .array());
            digest.update(element);
            byte[] output = digest.digest();
            return ByteBuffer.wrap(output, 0, Integer.BYTES).getInt();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
