package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Tests for the token-keyed aggregate SOGS tail.
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsSketchTest {
    /**
     * Sender size.
     */
    private static final int M = 1024;
    /**
     * Cuckoo row count used by the target m = 2^10 setting.
     */
    private static final int ROW_NUM = 1639;
    /**
     * SOGS cell count at gamma = 1.45.
     */
    private static final int CELL_NUM = 1485;
    /**
     * Degree.
     */
    private static final int DEGREE = 3;

    @Test
    public void testHalfHitAndAllMissPeel() {
        for (int payloadByteLength : new int[]{16, 64}) {
            runCase(payloadByteLength, M / 2, 3);
            runCase(payloadByteLength, 0, 3);
        }
    }

    @Test
    public void testTailByteEstimate() {
        TokenKeyedSogsSketch.TailByteEstimate n22x16 = TokenKeyedSogsSketch.estimateTailBytes(
            ROW_NUM, 7, CELL_NUM, 16
        );
        Assert.assertEquals(26_224, n22x16.tokenBytes);
        Assert.assertEquals(71_280, n22x16.cellBytes);
        Assert.assertEquals(157_344, n22x16.rowCorrectionBytes);
        Assert.assertEquals(321_440, n22x16.hiddenPeqtBytes);
        Assert.assertEquals(576_288, n22x16.totalBytes);

        TokenKeyedSogsSketch.TailByteEstimate n22x64 = TokenKeyedSogsSketch.estimateTailBytes(
            ROW_NUM, 7, CELL_NUM, 64
        );
        Assert.assertEquals(26_224, n22x64.tokenBytes);
        Assert.assertEquals(142_560, n22x64.cellBytes);
        Assert.assertEquals(314_688, n22x64.rowCorrectionBytes);
        Assert.assertEquals(321_440, n22x64.hiddenPeqtBytes);
        Assert.assertEquals(804_912, n22x64.totalBytes);
    }

    private static void runCase(int payloadByteLength, int intersectionSize, int trials) {
        for (int trial = 0; trial < trials; trial++) {
            CaseData data = createCase(payloadByteLength, intersectionSize, trial);
            SecureRandom secureRandom = seededSecureRandom("agg", payloadByteLength, intersectionSize, trial);
            TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
                data.payloads, data.validBits, CELL_NUM, DEGREE, secureRandom, 100
            );
            TokenKeyedSogsSketch sketch = TokenKeyedSogsSketch.aggregate(
                data.releaseBits, data.validBits, sample.tokens, data.payloads, CELL_NUM, DEGREE, secureRandom
            );
            TokenKeyedSogsSketch.PeelResult peelResult = sketch.peel();
            Assert.assertTrue(peelResult.success);
            Assert.assertEquals(0, peelResult.residualCells);
            Assert.assertEquals(data.expectedMisses, peelResult.recovered);
        }
    }

    private static CaseData createCase(int payloadByteLength, int intersectionSize, int trial) {
        Random random = new Random(20260617L + payloadByteLength * 1000L + intersectionSize * 17L + trial);
        List<Integer> rowIndexes = new ArrayList<>(ROW_NUM);
        for (int i = 0; i < ROW_NUM; i++) {
            rowIndexes.add(i);
        }
        Collections.shuffle(rowIndexes, random);
        List<Integer> realRows = rowIndexes.subList(0, M);
        Set<Integer> hitRows = new HashSet<>(realRows.subList(0, intersectionSize));
        byte[][] payloads = new byte[ROW_NUM][payloadByteLength];
        boolean[] validBits = new boolean[ROW_NUM];
        boolean[] releaseBits = new boolean[ROW_NUM];
        Set<ByteBuffer> expectedMisses = new HashSet<>(M - intersectionSize);
        for (int itemIndex = 0; itemIndex < M; itemIndex++) {
            int row = realRows.get(itemIndex);
            byte[] payload = payload(payloadByteLength, trial, itemIndex);
            payloads[row] = payload;
            validBits[row] = true;
            releaseBits[row] = !hitRows.contains(row);
            if (releaseBits[row]) {
                expectedMisses.add(ByteBuffer.wrap(payload));
            }
        }
        return new CaseData(payloads, validBits, releaseBits, expectedMisses);
    }

    private static byte[] payload(int payloadByteLength, int trial, int itemIndex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("payload".getBytes(StandardCharsets.UTF_8));
            digest.update((byte) trial);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(itemIndex).array());
            byte[] first = digest.digest();
            if (payloadByteLength <= first.length) {
                return java.util.Arrays.copyOf(first, payloadByteLength);
            }
            byte[] output = new byte[payloadByteLength];
            System.arraycopy(first, 0, output, 0, first.length);
            int offset = first.length;
            int counter = 1;
            while (offset < payloadByteLength) {
                digest.reset();
                digest.update(first);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(counter).array());
                byte[] next = digest.digest();
                int copyLength = Math.min(next.length, payloadByteLength - offset);
                System.arraycopy(next, 0, output, offset, copyLength);
                offset += copyLength;
                counter++;
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecureRandom seededSecureRandom(String domain, int payloadByteLength, int intersectionSize, int trial) {
        byte[] seed = (domain + ":" + payloadByteLength + ":" + intersectionSize + ":" + trial)
            .getBytes(StandardCharsets.UTF_8);
        return new SecureRandom(seed);
    }

    private static class CaseData {
        private final byte[][] payloads;
        private final boolean[] validBits;
        private final boolean[] releaseBits;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(byte[][] payloads, boolean[] validBits, boolean[] releaseBits, Set<ByteBuffer> expectedMisses) {
            this.payloads = payloads;
            this.validBits = validBits;
            this.releaseBits = releaseBits;
            this.expectedMisses = expectedMisses;
        }
    }
}
