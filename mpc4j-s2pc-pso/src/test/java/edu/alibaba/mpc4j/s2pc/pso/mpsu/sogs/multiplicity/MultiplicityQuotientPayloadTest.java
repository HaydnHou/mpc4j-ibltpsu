package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsQuotientLabelCodec;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Exact-quotient multiplicity Cell tests.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class MultiplicityQuotientPayloadTest {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Test
    public void testBoundaryAndRandomInsertionDeletion() {
        MpSogsMpsuParams params = params(1 << 12);
        long[] boundaries = new long[]{0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long value : boundaries) {
            assertInsertionDeletion(value, params);
        }
        for (int index = 0; index < 10_000; index++) {
            assertInsertionDeletion(SECURE_RANDOM.nextLong(), params);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidQuotientRejected() {
        MpSogsMpsuParams params = params(1 << 12);
        long value = 7L;
        int cellIndex = MpSogsHashUtils.cells(value, params, MpSogsTier.MAIN)[0];
        int bitLength = MpSogsQuotientLabelCodec.bitLength(params, MpSogsTier.MAIN);
        MpSogsQuotientLabelCodec.decode(1L << bitLength, params, MpSogsTier.MAIN, cellIndex);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFieldIncompatibleSmallGraphRejected() {
        MultiplicityPayloadEncoding.EXACT_QUOTIENT.validate(
            new MpSogsMpsuParams.Builder(3, 1).setAlpha(1.01).setHashNum(3).setTwoTier(false).build()
        );
    }

    private static void assertInsertionDeletion(long value, MpSogsMpsuParams params) {
        MultiplicitySogsSketch sketch = MultiplicitySogsSketch.encode(
            Set.of(value), params, 0x1234_5678L, MultiplicityPayloadEncoding.EXACT_QUOTIENT
        );
        for (MpSogsTier tier : MpSogsTier.values()) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
                long[] words = sketch.getCellWords(tier, cellIndex);
                Assert.assertEquals(MultiplicityPayloadEncoding.EXACT_QUOTIENT.getCellWordNum(), words.length);
                Assert.assertEquals(1L, words[MultiplicitySogsCell.COUNT_OFFSET]);
                Assert.assertEquals(
                    MpSogsQuotientLabelCodec.encode(value, params, tier, cellIndex),
                    words[MultiplicitySogsCell.PAYLOAD_QUOTIENT_OFFSET]
                );
            }
        }
        Assert.assertTrue(sketch.deleteIfPresentOnce(value));
        for (MpSogsTier tier : MpSogsTier.values()) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
                for (long word : sketch.getCellWords(tier, cellIndex)) {
                    Assert.assertEquals(0L, word);
                }
            }
        }
    }

    private static MpSogsMpsuParams params(int tauMax) {
        return new MpSogsMpsuParams.Builder(5, tauMax)
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryHashNum(3)
            .setAuxiliaryCellNum(4098)
            .build();
    }
}
