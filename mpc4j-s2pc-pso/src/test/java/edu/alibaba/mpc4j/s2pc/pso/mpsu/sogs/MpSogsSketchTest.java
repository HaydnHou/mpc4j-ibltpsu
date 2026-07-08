package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * MP-SOGS sketch tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsSketchTest {
    @Test
    public void testInsertAndDelete() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 64).build();
        MpSogsSketch sketch = MpSogsSketch.encode(Collections.singleton(11L), params);
        for (int cell : MpSogsHashUtils.cells(11L, params)) {
            Assert.assertEquals(MpSogsCellState.SINGLETON, sketch.state(cell));
            Assert.assertEquals(11L, sketch.singletonValue(cell));
        }
        Assert.assertTrue(sketch.deleteIfPresentOnce(11L));
        for (int cell : MpSogsHashUtils.cells(11L, params)) {
            Assert.assertEquals(MpSogsCellState.EMPTY, sketch.state(cell));
        }
        Assert.assertFalse(sketch.deleteIfPresentOnce(11L));
    }

    @Test
    public void testDuplicateInputCanonicalized() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 64).build();
        MpSogsSketch sketch = new MpSogsSketch(params);
        sketch.insert(17L);
        sketch.insert(17L);
        for (int cell : MpSogsHashUtils.cells(17L, params)) {
            Assert.assertEquals(1, sketch.cellCount(cell));
            Assert.assertEquals(MpSogsCellState.SINGLETON, sketch.state(cell));
        }
    }

    @Test
    public void testTwoTierInsertAndDelete() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 64)
            .setTwoTier(true)
            .setAuxiliaryCellNum(66)
            .build();
        MpSogsSketch sketch = MpSogsSketch.encode(Collections.singleton(23L), params);
        for (int cell : MpSogsHashUtils.cells(23L, params, MpSogsTier.MAIN)) {
            Assert.assertEquals(MpSogsCellState.SINGLETON, sketch.state(MpSogsTier.MAIN, cell));
            Assert.assertEquals(23L, sketch.singletonValue(MpSogsTier.MAIN, cell));
        }
        for (int cell : MpSogsHashUtils.cells(23L, params, MpSogsTier.AUXILIARY)) {
            Assert.assertEquals(MpSogsCellState.SINGLETON, sketch.state(MpSogsTier.AUXILIARY, cell));
            Assert.assertEquals(23L, sketch.singletonValue(MpSogsTier.AUXILIARY, cell));
        }
        Assert.assertTrue(sketch.deleteIfPresentOnce(23L));
        for (int cell : MpSogsHashUtils.cells(23L, params, MpSogsTier.MAIN)) {
            Assert.assertEquals(MpSogsCellState.EMPTY, sketch.state(MpSogsTier.MAIN, cell));
        }
        for (int cell : MpSogsHashUtils.cells(23L, params, MpSogsTier.AUXILIARY)) {
            Assert.assertEquals(MpSogsCellState.EMPTY, sketch.state(MpSogsTier.AUXILIARY, cell));
        }
    }

    @Test
    public void testCellHeavyState() {
        MpSogsCell cell = new MpSogsCell();
        cell.add(5L);
        cell.add(9L);
        Assert.assertEquals(MpSogsCellState.HEAVY, cell.state());
    }
}
