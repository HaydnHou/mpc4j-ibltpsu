package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * MP-SOGS ideal union-peel functionality tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsUnionPeelFunctionTest {
    @Test
    public void testAllEmpty() {
        Assert.assertEquals(
            MpSogsPeelResult.bottom(),
            MpSogsUnionPeelFunction.evaluate(Arrays.asList(
                MpSogsLocalCellView.empty(),
                MpSogsLocalCellView.empty(),
                MpSogsLocalCellView.empty()
            ))
        );
    }

    @Test
    public void testOneSingleton() {
        Assert.assertEquals(
            MpSogsPeelResult.element(5L),
            MpSogsUnionPeelFunction.evaluate(Arrays.asList(
                MpSogsLocalCellView.empty(),
                MpSogsLocalCellView.singleton(5L),
                MpSogsLocalCellView.empty()
            ))
        );
    }

    @Test
    public void testSameSingletonsOpenOnce() {
        Assert.assertEquals(
            MpSogsPeelResult.element(5L),
            MpSogsUnionPeelFunction.evaluate(Arrays.asList(
                MpSogsLocalCellView.singleton(5L),
                MpSogsLocalCellView.singleton(5L),
                MpSogsLocalCellView.singleton(5L)
            ))
        );
    }

    @Test
    public void testDifferentSingletonsBottom() {
        Assert.assertEquals(
            MpSogsPeelResult.bottom(),
            MpSogsUnionPeelFunction.evaluate(Arrays.asList(
                MpSogsLocalCellView.singleton(5L),
                MpSogsLocalCellView.singleton(9L),
                MpSogsLocalCellView.empty()
            ))
        );
    }

    @Test
    public void testAnyHeavyBottom() {
        Assert.assertEquals(
            MpSogsPeelResult.bottom(),
            MpSogsUnionPeelFunction.evaluate(Arrays.asList(
                MpSogsLocalCellView.singleton(5L),
                MpSogsLocalCellView.heavy(),
                MpSogsLocalCellView.empty()
            ))
        );
    }
}
