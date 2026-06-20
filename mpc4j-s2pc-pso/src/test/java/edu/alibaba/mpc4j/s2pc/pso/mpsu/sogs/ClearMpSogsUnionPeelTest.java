package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/**
 * Clear union-peel truth table tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class ClearMpSogsUnionPeelTest {
    @Test
    public void testSameSingletonPeels() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 64).build();
        MpSogsSketch a = MpSogsSketch.encode(Collections.singleton(5L), params);
        MpSogsSketch b = MpSogsSketch.encode(Collections.singleton(5L), params);
        MpSogsSketch c = MpSogsSketch.encode(Collections.emptySet(), params);
        for (int cell : MpSogsHashUtils.cells(5L, params)) {
            Assert.assertEquals(MpSogsPeelResult.element(5L), ClearMpSogsUnionPeel.uPeel(Arrays.asList(a, b, c), cell));
        }
    }

    @Test
    public void testAllEmptyIsBottom() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 64).build();
        MpSogsSketch a = MpSogsSketch.encode(Collections.emptySet(), params);
        MpSogsSketch b = MpSogsSketch.encode(Collections.emptySet(), params);
        MpSogsSketch c = MpSogsSketch.encode(Collections.emptySet(), params);
        Assert.assertEquals(MpSogsPeelResult.bottom(), ClearMpSogsUnionPeel.uPeel(Arrays.asList(a, b, c), 0));
    }

    @Test
    public void testDifferentSingletonCollisionIsBottom() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 8)
            .setAlpha(1.25)
            .setHashNum(3)
            .build();
        Collision collision = findDifferentSingletonCollision(params);
        MpSogsSketch a = MpSogsSketch.encode(Collections.singleton(collision.left), params);
        MpSogsSketch b = MpSogsSketch.encode(Collections.singleton(collision.right), params);
        MpSogsSketch c = MpSogsSketch.encode(Collections.emptySet(), params);
        Assert.assertEquals(MpSogsPeelResult.bottom(), ClearMpSogsUnionPeel.uPeel(Arrays.asList(a, b, c), collision.cell));
    }

    @Test
    public void testHeavyIsBottom() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 8)
            .setAlpha(1.25)
            .setHashNum(3)
            .build();
        Collision collision = findDifferentSingletonCollision(params);
        MpSogsSketch heavy = MpSogsSketch.encode(new HashSet<>(Arrays.asList(collision.left, collision.right)), params);
        MpSogsSketch empty = MpSogsSketch.encode(Collections.emptySet(), params);
        Assert.assertEquals(MpSogsPeelResult.bottom(), ClearMpSogsUnionPeel.uPeel(Arrays.asList(heavy, empty, empty), collision.cell));
    }

    private Collision findDifferentSingletonCollision(MpSogsMpsuParams params) {
        for (long left = 1; left < 5000; left++) {
            int[] leftCells = MpSogsHashUtils.cells(left, params);
            for (long right = left + 1; right < 5000; right++) {
                int[] rightCells = MpSogsHashUtils.cells(right, params);
                for (int leftCell : leftCells) {
                    for (int rightCell : rightCells) {
                        if (leftCell == rightCell) {
                            return new Collision(left, right, leftCell);
                        }
                    }
                }
            }
        }
        throw new AssertionError("could not find collision");
    }

    private static class Collision {
        private final long left;
        private final long right;
        private final int cell;

        private Collision(long left, long right, int cell) {
            this.left = left;
            this.right = right;
            this.cell = cell;
        }
    }
}
