package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Internal utility methods for MC-SOGS UPSU.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
class McSogsUpsuUtils {
    /**
     * Domain separator for alpha-to-bin pad folding.
     */
    private static final byte[] BIN_PAD_DOMAIN = "mc-sogs-bin-pad".getBytes(StandardCharsets.UTF_8);

    /**
     * Private constructor.
     */
    private McSogsUpsuUtils() {
        // empty
    }

    /**
     * Samples a fresh random permutation of [0, n).
     *
     * @param n            permutation size.
     * @param secureRandom secure random.
     * @return random permutation.
     */
    static int[] freshPermutation(int n, SecureRandom secureRandom) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive: " + n);
        }
        Objects.requireNonNull(secureRandom, "secureRandom");
        int[] permutation = IntStream.range(0, n).toArray();
        for (int i = n - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            int tmp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = tmp;
        }
        return permutation;
    }

    /**
     * Folds alpha label rows into one pad per anonymous carrier column.
     *
     * @param envType environment type.
     * @param labels  flattened labels indexed as row * binNum + anonymousColumn.
     * @param alpha   alpha rows.
     * @param binNum  anonymous carrier column number.
     * @return one folded pad per anonymous carrier column.
     */
    static byte[][] foldLabelsByAnonymousColumn(EnvType envType, byte[][] labels, int alpha, int binNum) {
        Objects.requireNonNull(envType, "envType");
        if (alpha <= 0) {
            throw new IllegalArgumentException("alpha must be positive: " + alpha);
        }
        if (binNum <= 0) {
            throw new IllegalArgumentException("binNum must be positive: " + binNum);
        }
        if (labels == null) {
            throw new IllegalArgumentException("labels must not be null");
        }
        if (labels.length != alpha * binNum) {
            throw new IllegalArgumentException(
                "labels length must equal alpha * binNum: " + labels.length + " != " + alpha + " * " + binNum
            );
        }
        int labelByteLength = validateLabels(labels);
        Hash hash = HashFactory.createInstance(envType, labelByteLength);
        byte[][] binPads = new byte[binNum][];
        int inputByteLength = BIN_PAD_DOMAIN.length + Integer.BYTES * 4 + alpha * labelByteLength;
        for (int c = 0; c < binNum; c++) {
            ByteBuffer buffer = ByteBuffer.allocate(inputByteLength);
            buffer.put(BIN_PAD_DOMAIN);
            buffer.putInt(alpha);
            buffer.putInt(binNum);
            buffer.putInt(c);
            buffer.putInt(labelByteLength);
            for (int r = 0; r < alpha; r++) {
                buffer.put(labels[r * binNum + c]);
            }
            binPads[c] = hash.digestToBytes(buffer.array());
        }
        return binPads;
    }

    /**
     * Validates flattened labels and returns their common byte length.
     *
     * @param labels flattened labels.
     * @return label byte length.
     */
    private static int validateLabels(byte[][] labels) {
        if (labels.length == 0 || labels[0] == null) {
            throw new IllegalArgumentException("labels must contain non-null labels");
        }
        int labelByteLength = labels[0].length;
        if (labelByteLength <= 0) {
            throw new IllegalArgumentException("label byte length must be positive");
        }
        for (byte[] label : labels) {
            if (label == null) {
                throw new IllegalArgumentException("labels must contain non-null labels");
            }
            if (label.length != labelByteLength) {
                throw new IllegalArgumentException("labels must have the same byte length");
            }
        }
        return labelByteLength;
    }
}
