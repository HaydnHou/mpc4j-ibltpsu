package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.TokenKeyedSogsSketch;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Internal utility methods for SC-SOGS UPSU.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
class ScSogsUpsuUtils {
    /**
     * Conservative retry multiplier for sampling unique anonymous row tokens.
     */
    private static final int TOKEN_RETRY_MULTIPLIER = 4;

    private ScSogsUpsuUtils() {
        // empty
    }

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

    static AnonymousSogsRowCarrier buildAnonymousSogsRowCarrier(
        int[] columnPermutationMap, byte[][] originalBinPayloads, byte[] botElement, SecureRandom secureRandom
    ) {
        Objects.requireNonNull(columnPermutationMap, "columnPermutationMap");
        Objects.requireNonNull(originalBinPayloads, "originalBinPayloads");
        Objects.requireNonNull(botElement, "botElement");
        Objects.requireNonNull(secureRandom, "secureRandom");
        if (columnPermutationMap.length == 0) {
            throw new IllegalArgumentException("empty column permutation");
        }
        if (columnPermutationMap.length != originalBinPayloads.length) {
            throw new IllegalArgumentException("column permutation and payload length mismatch");
        }
        int payloadByteLength = originalBinPayloads[0].length;
        byte[][] payloads = new byte[columnPermutationMap.length][];
        boolean[] realRowBits = new boolean[columnPermutationMap.length];
        byte[][] tokens = new byte[columnPermutationMap.length][TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH];
        Set<ByteBuffer> usedTokens = new HashSet<>(columnPermutationMap.length);
        boolean[] seen = new boolean[columnPermutationMap.length];
        for (int c = 0; c < columnPermutationMap.length; c++) {
            int originalColumn = columnPermutationMap[c];
            if (originalColumn < 0 || originalColumn >= columnPermutationMap.length || seen[originalColumn]) {
                throw new IllegalArgumentException("invalid column permutation");
            }
            seen[originalColumn] = true;
            if (originalBinPayloads[originalColumn].length != payloadByteLength) {
                throw new IllegalArgumentException("payload length mismatch");
            }
            payloads[c] = BytesUtils.clone(originalBinPayloads[originalColumn]);
            realRowBits[c] = !Arrays.equals(payloads[c], botElement);
            sampleUniqueToken(tokens[c], usedTokens, secureRandom, columnPermutationMap.length);
        }
        return new AnonymousSogsRowCarrier(realRowBits, tokens, payloads);
    }

    private static void sampleUniqueToken(
        byte[] token, Set<ByteBuffer> usedTokens, SecureRandom secureRandom, int rowNum
    ) {
        int maxRetries = Math.max(1, rowNum * TOKEN_RETRY_MULTIPLIER);
        for (int retry = 0; retry < maxRetries; retry++) {
            secureRandom.nextBytes(token);
            ByteBuffer tokenKey = ByteBuffer.wrap(Arrays.copyOf(token, token.length));
            if (usedTokens.add(tokenKey)) {
                return;
            }
        }
        throw new IllegalStateException("could not sample unique one-time row token");
    }

    /**
     * Sender-local anonymous row material consumed by {@code ShareCancelSogsTailSender}.
     */
    static class AnonymousSogsRowCarrier {
        final boolean[] realRowBits;
        final byte[][] tokens;
        final byte[][] payloads;

        private AnonymousSogsRowCarrier(boolean[] realRowBits, byte[][] tokens, byte[][] payloads) {
            this.realRowBits = realRowBits;
            this.tokens = tokens;
            this.payloads = payloads;
        }
    }
}
