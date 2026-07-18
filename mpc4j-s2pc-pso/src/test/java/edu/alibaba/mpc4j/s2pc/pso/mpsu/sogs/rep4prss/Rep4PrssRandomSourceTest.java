package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PrssPhase;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * REP4 PRSS domain-separation tests.
 *
 * @author donghai hou
 * @date 2026/07/18
 */
public class Rep4PrssRandomSourceTest {
    @Test
    public void testStructuredDomainSeparation() {
        byte[] seed = new byte[16];
        Arrays.fill(seed, (byte) 0x5A);
        Rep4PrssRandomSource source = new Rep4PrssRandomSource(7_100_000L);
        long[] reference = source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 17, 3L, 2, 0, 3);

        Assert.assertArrayEquals(reference,
            source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 17, 3L, 2, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 2L, PrssPhase.FULL, 1, 17, 3L, 2, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 1L, PrssPhase.SELECTED, 1, 17, 3L, 2, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 1L, PrssPhase.FULL, 2, 17, 3L, 2, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 18, 3L, 2, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 17, 4L, 2, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 17, 3L, 3, 0, 3));
        assertDifferent(reference, source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 17, 3L, 2, 1, 3));
        long[] differentBlockNum = source.componentRandom(seed, 1L, PrssPhase.FULL, 1, 17, 3L, 2, 0, 4);
        Assert.assertNotEquals(reference[0], differentBlockNum[0]);
    }

    private static void assertDifferent(long[] left, long[] right) {
        Assert.assertFalse(Arrays.equals(left, right));
    }
}
