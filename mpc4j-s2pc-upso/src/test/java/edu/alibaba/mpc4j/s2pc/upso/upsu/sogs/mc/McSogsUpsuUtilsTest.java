package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * Tests for MC-SOGS UPSU utility methods.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuUtilsTest {
    /**
     * Environment type.
     */
    private static final EnvType ENV_TYPE = EnvType.STANDARD;
    /**
     * Label byte length.
     */
    private static final int LABEL_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * Secure random.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Test
    public void testFreshPermutation() {
        int[] sizes = new int[]{1, 2, 17, 1024};
        for (int n : sizes) {
            assertPermutation(McSogsUpsuUtils.freshPermutation(n, SECURE_RANDOM), n);
        }
    }

    @Test
    public void testFreshPermutationInvalidInputs() {
        Assert.assertThrows(IllegalArgumentException.class, () -> McSogsUpsuUtils.freshPermutation(0, SECURE_RANDOM));
        Assert.assertThrows(IllegalArgumentException.class, () -> McSogsUpsuUtils.freshPermutation(-1, SECURE_RANDOM));
        Assert.assertThrows(NullPointerException.class, () -> McSogsUpsuUtils.freshPermutation(1, null));
    }

    @Test
    public void testFoldRejectsBadDimensions() {
        byte[][] labels = randomLabels(3, 5);
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, labels, 0, 5)
        );
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, labels, 3, 0)
        );
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, new byte[14][LABEL_BYTE_LENGTH], 3, 5)
        );
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, null, 3, 5)
        );

        byte[][] nullLabel = randomLabels(3, 5);
        nullLabel[2] = null;
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, nullLabel, 3, 5)
        );

        byte[][] mixedLength = randomLabels(3, 5);
        mixedLength[2] = new byte[LABEL_BYTE_LENGTH + 1];
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, mixedLength, 3, 5)
        );

        byte[][] zeroLength = new byte[15][0];
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, zeroLength, 3, 5)
        );
    }

    @Test
    public void testFoldDeterministic() {
        byte[][] labels = randomLabels(3, 5);
        byte[][] pads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, labels, 3, 5);
        byte[][] again = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, labels, 3, 5);
        Assert.assertArrayEquals(pads, again);
    }

    @Test
    public void testFoldLocality() {
        int alpha = 3;
        int binNum = 5;
        int changedColumn = 2;
        byte[][] labels = randomLabels(alpha, binNum);
        byte[][] changedLabels = BytesUtils.clone(labels);
        changedLabels[1 * binNum + changedColumn][0] ^= 0x01;

        byte[][] pads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, labels, alpha, binNum);
        byte[][] changedPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, changedLabels, alpha, binNum);
        for (int c = 0; c < binNum; c++) {
            if (c == changedColumn) {
                Assert.assertNotEquals(ByteBufferUtils.toHex(pads[c]), ByteBufferUtils.toHex(changedPads[c]));
            } else {
                Assert.assertArrayEquals(pads[c], changedPads[c]);
            }
        }
    }

    @Test
    public void testMissCaseFoldPadsEqual() {
        int alpha = 3;
        int binNum = 5;
        byte[][] senderLabels = randomLabels(alpha, binNum);
        byte[][] receiverLabels = BytesUtils.clone(senderLabels);

        byte[][] senderPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, senderLabels, alpha, binNum);
        byte[][] receiverPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, receiverLabels, alpha, binNum);
        for (int c = 0; c < binNum; c++) {
            Assert.assertArrayEquals(senderPads[c], receiverPads[c]);
        }
    }

    @Test
    public void testHitCaseFoldPadDiffers() {
        int alpha = 3;
        int binNum = 5;
        int hitColumn = 2;
        byte[][] senderLabels = randomLabels(alpha, binNum);
        byte[][] receiverLabels = BytesUtils.clone(senderLabels);
        receiverLabels[1 * binNum + hitColumn][0] ^= 0x01;

        byte[][] senderPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, senderLabels, alpha, binNum);
        byte[][] receiverPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, receiverLabels, alpha, binNum);
        Assert.assertNotEquals(ByteBufferUtils.toHex(senderPads[hitColumn]), ByteBufferUtils.toHex(receiverPads[hitColumn]));
    }

    @Test
    public void testCrossBinIsolation() {
        int alpha = 3;
        int binNum = 5;
        int hitColumn = 2;
        int missColumn = 4;
        byte[][] senderLabels = randomLabels(alpha, binNum);
        byte[][] receiverLabels = BytesUtils.clone(senderLabels);
        receiverLabels[1 * binNum + hitColumn][0] ^= 0x01;

        byte[][] senderPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, senderLabels, alpha, binNum);
        byte[][] receiverPads = McSogsUpsuUtils.foldLabelsByAnonymousColumn(ENV_TYPE, receiverLabels, alpha, binNum);
        Assert.assertNotEquals(ByteBufferUtils.toHex(senderPads[hitColumn]), ByteBufferUtils.toHex(receiverPads[hitColumn]));
        Assert.assertArrayEquals(senderPads[missColumn], receiverPads[missColumn]);
    }

    private static byte[][] randomLabels(int alpha, int binNum) {
        byte[][] labels = new byte[alpha * binNum][LABEL_BYTE_LENGTH];
        for (byte[] label : labels) {
            SECURE_RANDOM.nextBytes(label);
        }
        return labels;
    }

    private static void assertPermutation(int[] permutation, int n) {
        Assert.assertEquals(n, permutation.length);
        boolean[] seen = new boolean[n];
        for (int value : permutation) {
            Assert.assertTrue(value >= 0);
            Assert.assertTrue(value < n);
            Assert.assertFalse(seen[value]);
            seen[value] = true;
        }
    }

    /**
     * Small helper to make byte-array inequality assertions readable in JUnit 4.
     */
    private static class ByteBufferUtils {
        private ByteBufferUtils() {
            // empty
        }

        private static String toHex(byte[] input) {
            StringBuilder builder = new StringBuilder(input.length * 2);
            for (byte b : input) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        }
    }
}
