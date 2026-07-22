package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

/**
 * Tests for party-local multiplicity SOGS cells.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class MultiplicitySogsSketchTest {
    @Test
    public void testPayloadLimbs() {
        long[] values = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x1234_5678_9ABC_DEF0L};
        for (long value : values) {
            Assert.assertEquals(value, MultiplicitySogsHash.joinLimbs(
                MultiplicitySogsHash.lowLimb(value), MultiplicitySogsHash.highLimb(value)
            ));
        }
    }

    @Test
    public void testInsertDeduplicateAndDelete() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(5, 64)
            .setAlpha(2.0)
            .setTwoTier(true)
            .build();
        long value = 0x1234_5678_9ABC_DEF0L;
        MultiplicitySogsSketch sketch = MultiplicitySogsSketch.encode(
            Arrays.asList(value, value), params, 12345L
        );
        for (MpSogsTier tier : MpSogsTier.values()) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
                Assert.assertEquals(1L, sketch.getCellWords(tier, cellIndex)[MultiplicitySogsCell.COUNT_OFFSET]);
            }
        }
        Assert.assertTrue(sketch.deleteIfPresentOnce(value));
        Assert.assertFalse(sketch.deleteIfPresentOnce(value));
        Assert.assertTrue(sketch.getRemainingElements().isEmpty());
        for (MpSogsTier tier : MpSogsTier.values()) {
            for (int cellIndex = 0; cellIndex < sketch.getCellNum(tier); cellIndex++) {
                Assert.assertArrayEquals(new long[MultiplicitySogsCell.WORD_NUM], sketch.getCellWords(tier, cellIndex));
            }
        }
    }

    @Test
    public void testSingleDistinctMultiplicityAndMultiDistinct() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(5, 32)
            .setAlpha(4.0)
            .setTwoTier(false)
            .build();
        long seed = 987654321L;
        long value = 42L;
        int cellIndex = MpSogsHashUtils.cells(value, params, MpSogsTier.MAIN)[0];
        MultiplicitySogsSketch[] same = new MultiplicitySogsSketch[5];
        for (int partyIndex = 0; partyIndex < same.length; partyIndex++) {
            same[partyIndex] = MultiplicitySogsSketch.encode(Set.of(value), params, seed);
        }
        long[] aggregate = aggregate(same, cellIndex);
        Assert.assertTrue(ClearMultiplicityUnionPeel.isSingleDistinct(aggregate, 5));
        Assert.assertEquals(5L, aggregate[MultiplicitySogsCell.COUNT_OFFSET]);

        long other = findSameCell(value, params, cellIndex);
        MultiplicitySogsSketch otherSketch = MultiplicitySogsSketch.encode(Set.of(other), params, seed);
        long[] multi = aggregate(same, cellIndex);
        long[] otherWords = otherSketch.getCellWords(MpSogsTier.MAIN, cellIndex);
        for (int wordIndex = 0; wordIndex < multi.length; wordIndex++) {
            multi[wordIndex] = Mersenne61Field.add(multi[wordIndex], otherWords[wordIndex]);
        }
        Assert.assertFalse(ClearMultiplicityUnionPeel.isSingleDistinct(multi, 5));
    }

    private static long[] aggregate(MultiplicitySogsSketch[] sketches, int cellIndex) {
        long[] aggregate = new long[MultiplicitySogsCell.WORD_NUM];
        for (MultiplicitySogsSketch sketch : sketches) {
            long[] words = sketch.getCellWords(MpSogsTier.MAIN, cellIndex);
            for (int wordIndex = 0; wordIndex < aggregate.length; wordIndex++) {
                aggregate[wordIndex] = Mersenne61Field.add(aggregate[wordIndex], words[wordIndex]);
            }
        }
        return aggregate;
    }

    private static long findSameCell(long value, MpSogsMpsuParams params, int targetCell) {
        for (long candidate = value + 1; candidate < Long.MAX_VALUE; candidate++) {
            if (MpSogsHashUtils.cells(candidate, params, MpSogsTier.MAIN)[0] == targetCell) {
                return candidate;
            }
        }
        throw new AssertionError("failed to find same-row cell collision");
    }
}
