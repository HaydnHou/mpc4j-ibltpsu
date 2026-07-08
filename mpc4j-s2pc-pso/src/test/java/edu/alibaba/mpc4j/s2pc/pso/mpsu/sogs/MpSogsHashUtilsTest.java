package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * MP-SOGS hash utility tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsHashUtilsTest {
    @Test
    public void testCellsDistinctDeterministicAndInRange() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 128)
            .setAlpha(1.4)
            .setHashNum(3)
            .setHashSeed(12345L)
            .build();
        int[] first = MpSogsHashUtils.cells(42L, params);
        int[] second = MpSogsHashUtils.cells(42L, params);
        Assert.assertArrayEquals(first, second);
        Set<Integer> distinct = new HashSet<>();
        for (int hashIndex = 0; hashIndex < first.length; hashIndex++) {
            int cell = first[hashIndex];
            Assert.assertTrue(cell >= 0);
            Assert.assertTrue(cell < params.getCellNum());
            Assert.assertEquals(hashIndex, cell / params.getRowCellNum());
            distinct.add(cell);
        }
        Assert.assertEquals(params.getHashNum(), distinct.size());
    }

    @Test
    public void testRowDisjointCapacity() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 128)
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        Assert.assertEquals(params.getHashNum() * params.getRowCellNum(), params.getCellNum());
        Assert.assertTrue(params.getCellNum() >= Math.ceil(params.getAlpha() * params.getTauMax()));
        Assert.assertTrue(params.getCellNum() < Math.ceil(params.getAlpha() * params.getTauMax()) + params.getHashNum());
    }

    @Test
    public void testTwoTierCellsInRange() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 128)
            .setTwoTier(true)
            .setAuxiliaryHashNum(3)
            .setAuxiliaryCellNum(66)
            .setHashSeed(12345L)
            .build();
        int[] mainCells = MpSogsHashUtils.cells(42L, params, MpSogsTier.MAIN);
        int[] auxiliaryCells = MpSogsHashUtils.cells(42L, params, MpSogsTier.AUXILIARY);
        Assert.assertEquals(params.getHashNum(MpSogsTier.MAIN), mainCells.length);
        Assert.assertEquals(params.getHashNum(MpSogsTier.AUXILIARY), auxiliaryCells.length);
        for (int cell : mainCells) {
            Assert.assertTrue(cell >= 0);
            Assert.assertTrue(cell < params.getCellNum(MpSogsTier.MAIN));
        }
        for (int cell : auxiliaryCells) {
            Assert.assertTrue(cell >= 0);
            Assert.assertTrue(cell < params.getCellNum(MpSogsTier.AUXILIARY));
        }
    }

    @Test
    public void testCheckDeterministic() {
        Assert.assertEquals(MpSogsHashUtils.check(7L), MpSogsHashUtils.check(7L));
        Assert.assertNotEquals(MpSogsHashUtils.check(7L), MpSogsHashUtils.check(8L));
    }
}
