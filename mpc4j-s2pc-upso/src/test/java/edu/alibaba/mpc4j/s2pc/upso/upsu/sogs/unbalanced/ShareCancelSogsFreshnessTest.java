package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Freshness tests for the share-cancel SOGS tail.
 *
 * <p>The same logical selected miss set must peel to the same output when rerun, but the one-time token graph and
 * aggregate cell payload must change. This catches accidental token reuse or deterministic graph material.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsFreshnessTest {
    /**
     * Row num.
     */
    private static final int ROW_NUM = 128;
    /**
     * Payload byte length.
     */
    private static final int PAYLOAD_BYTE_LENGTH = 16;
    /**
     * Cell num.
     */
    private static final int CELL_NUM = 512;
    /**
     * SOGS degree.
     */
    private static final int DEGREE = 3;

    @Test
    public void testFreshTokensChangeGraphButNotOutput() throws Exception {
        CaseData data = createCase();
        RunData first = runWithSeed(data, "share-cancel-freshness:first");
        RunData second = runWithSeed(data, "share-cancel-freshness:second");

        Assert.assertEquals(data.expectedMisses, first.peelResult.recovered);
        Assert.assertEquals(data.expectedMisses, second.peelResult.recovered);
        Assert.assertTrue(first.peelResult.success);
        Assert.assertTrue(second.peelResult.success);

        Assert.assertFalse(Arrays.deepEquals(first.tokens, second.tokens));
        Assert.assertTrue(hasDifferentPositions(first.tokens, second.tokens));
        Assert.assertFalse(equalPayload(first.cellPayload, second.cellPayload));
    }

    private static RunData runWithSeed(CaseData data, String seed) throws Exception {
        SecureRandom tokenRandom = SecureRandom.getInstance("SHA1PRNG");
        tokenRandom.setSeed(seed.getBytes());
        TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
            data.payloads, data.realRowBits, CELL_NUM, DEGREE, tokenRandom, 100
        );
        SecureRandom shareRandom = SecureRandom.getInstance("SHA1PRNG");
        shareRandom.setSeed((seed + ":shares").getBytes());
        TokenKeyedSogsSketch aggregate = TokenKeyedSogsSketch.aggregate(
            data.releaseBits, data.realRowBits, sample.tokens, data.payloads, CELL_NUM, DEGREE, shareRandom
        );
        List<byte[]> cellPayload = aggregate.toCellPayload();
        TokenKeyedSogsSketch.PeelResult peelResult = TokenKeyedSogsSketch
            .fromCellPayload(cellPayload, PAYLOAD_BYTE_LENGTH, DEGREE)
            .peel();
        return new RunData(sample.tokens, cellPayload, peelResult);
    }

    private static boolean hasDifferentPositions(byte[][] firstTokens, byte[][] secondTokens) {
        TokenKeyedSogsSketch helper = new TokenKeyedSogsSketch(CELL_NUM, PAYLOAD_BYTE_LENGTH, DEGREE);
        for (int i = 0; i < firstTokens.length; i++) {
            if (!Arrays.equals(helper.positions(firstTokens[i]), helper.positions(secondTokens[i]))) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalPayload(List<byte[]> left, List<byte[]> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!Arrays.equals(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static CaseData createCase() {
        byte[][] payloads = new byte[ROW_NUM][PAYLOAD_BYTE_LENGTH];
        boolean[] realRowBits = new boolean[ROW_NUM];
        boolean[] releaseBits = new boolean[ROW_NUM];
        Set<ByteBuffer> expectedMisses = new HashSet<>();
        for (int i = 0; i < ROW_NUM; i++) {
            payloads[i] = payload(i);
            boolean real = (i % 4) != 0;
            boolean hit = real && (i % 5 == 0);
            realRowBits[i] = real;
            releaseBits[i] = real && !hit;
            if (releaseBits[i]) {
                expectedMisses.add(ByteBuffer.wrap(payloads[i]));
            }
        }
        return new CaseData(payloads, realRowBits, releaseBits, expectedMisses);
    }

    private static byte[] payload(int index) {
        byte[] payload = new byte[PAYLOAD_BYTE_LENGTH];
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).putInt(index);
        byte[] indexBytes = buffer.array();
        for (int offset = 0; offset < payload.length; offset += indexBytes.length) {
            System.arraycopy(indexBytes, 0, payload, offset, Math.min(indexBytes.length, payload.length - offset));
        }
        return payload;
    }

    private static class CaseData {
        private final byte[][] payloads;
        private final boolean[] realRowBits;
        private final boolean[] releaseBits;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(byte[][] payloads, boolean[] realRowBits, boolean[] releaseBits,
                         Set<ByteBuffer> expectedMisses) {
            this.payloads = payloads;
            this.realRowBits = realRowBits;
            this.releaseBits = releaseBits;
            this.expectedMisses = expectedMisses;
        }
    }

    private static class RunData {
        private final byte[][] tokens;
        private final List<byte[]> cellPayload;
        private final TokenKeyedSogsSketch.PeelResult peelResult;

        private RunData(byte[][] tokens, List<byte[]> cellPayload, TokenKeyedSogsSketch.PeelResult peelResult) {
            this.tokens = tokens;
            this.cellPayload = cellPayload;
            this.peelResult = peelResult;
        }
    }
}
