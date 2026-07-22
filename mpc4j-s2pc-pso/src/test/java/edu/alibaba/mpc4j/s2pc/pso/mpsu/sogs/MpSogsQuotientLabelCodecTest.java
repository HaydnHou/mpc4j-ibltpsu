package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * Exact quotient-label codec tests.
 *
 * @author donghai hou
 * @date 2026/07/18
 */
public class MpSogsQuotientLabelCodecTest {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Test
    public void testBoundaryAndRandomRoundTrips() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(5, 1 << 18)
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryHashNum(3)
            .setAuxiliaryCellNum(4098)
            .setHashSeed(0x1234_5678_9ABC_DEF0L)
            .build();
        long[] boundaries = new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (MpSogsTier tier : MpSogsTier.values()) {
            for (long value : boundaries) {
                assertRoundTrip(value, params, tier);
            }
            for (int index = 0; index < 10_000; index++) {
                assertRoundTrip(SECURE_RANDOM.nextLong(), params, tier);
            }
        }
    }

    @Test
    public void testExpectedLabelWidths() {
        assertMainWidth(4, 40_960, 50);
        assertMainWidth(4, 655_360, 46);
        assertMainWidth(4, 2_621_440, 44);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(4, 40_960)
            .setTwoTier(true)
            .setAuxiliaryCellNum(4098)
            .build();
        Assert.assertEquals(54, MpSogsQuotientLabelCodec.bitLength(params, MpSogsTier.AUXILIARY));
    }

    @Test
    public void testInverseSplitMix64() {
        long[] boundaries = new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long value : boundaries) {
            Assert.assertEquals(value, MpSogsHashUtils.inverseSplitMix64(MpSogsHashUtils.splitMix64(value)));
        }
        for (int index = 0; index < 10_000; index++) {
            long value = SECURE_RANDOM.nextLong();
            Assert.assertEquals(value, MpSogsHashUtils.inverseSplitMix64(MpSogsHashUtils.splitMix64(value)));
        }
    }

    private static void assertRoundTrip(long value, MpSogsMpsuParams params, MpSogsTier tier) {
        int bitLength = MpSogsQuotientLabelCodec.bitLength(params, tier);
        for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
            long quotient = MpSogsQuotientLabelCodec.encode(value, params, tier, cellIndex);
            if (bitLength < Long.SIZE) {
                Assert.assertEquals(0L, quotient >>> bitLength);
            }
            Assert.assertEquals(value, MpSogsQuotientLabelCodec.decode(quotient, params, tier, cellIndex));
        }
    }

    private static void assertMainWidth(int parties, int tauMax, int expectedWidth) {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(parties, tauMax)
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        Assert.assertEquals(expectedWidth, MpSogsQuotientLabelCodec.bitLength(params, MpSogsTier.MAIN));
    }
}
