package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Tests for {@link SogsPsuSketchBackendBenchmark} and {@link SogsPsuSketchBackendSweep}.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchBackendBenchmarkTest {
    @Test
    public void testSmallBenchmark() {
        SogsPsuSketchBackendBenchmark.Config config = new SogsPsuSketchBackendBenchmark.Config(
            1 << 12, 1 << 8, 1 << 7, 16,
            1.45, 3, 20260609L, H5LongIblt.DEFAULT_MULTIPLIER, 2
        );
        SogsPsuSketchBackendBenchmark.Summary summary = SogsPsuSketchBackendBenchmark.run(config);
        Assert.assertEquals((1 << 12) + (1 << 8) - (1 << 7), summary.unionSize());
        Assert.assertEquals(config.trials(), summary.sogsSuccessCount());
        Assert.assertEquals(config.trials(), summary.h5SuccessCount());
        Assert.assertTrue(summary.allMatched());
        Assert.assertTrue(summary.sogsTableSize() < summary.h5TableSize());
        Assert.assertEquals(3.0 / 5.0, summary.hashProbeRatio(), 0.0);
    }

    @Test
    public void testTargetProfileBenchmark() {
        SogsPsuSketchBackendBenchmark.Config config = new SogsPsuSketchBackendBenchmark.Config(
            1 << 18, 1 << 10, 1 << 9, 16,
            1.45, 3, 20260609L, H5LongIblt.DEFAULT_MULTIPLIER, 1
        );
        SogsPsuSketchBackendBenchmark.Summary summary = SogsPsuSketchBackendBenchmark.run(config);
        System.out.println(summary.toDisplayString());
        Assert.assertEquals((1 << 18) + (1 << 10) - (1 << 9), summary.unionSize());
        Assert.assertEquals(config.trials(), summary.sogsSuccessCount());
        Assert.assertEquals(config.trials(), summary.h5SuccessCount());
        Assert.assertTrue(summary.allMatched());
        Assert.assertTrue(summary.sogsTableSize() < summary.h5TableSize());
    }

    @Test
    public void testSmallSweep() {
        SogsPsuSketchBackendSweep.Config config = new SogsPsuSketchBackendSweep.Config(
            1 << 12, 1 << 8,
            new int[]{0, 1 << 7},
            new int[]{16},
            new double[]{1.35, 1.45},
            new int[]{3, 4},
            1, 20260609L, H5LongIblt.DEFAULT_MULTIPLIER
        );
        List<SogsPsuSketchBackendSweep.Row> rows = SogsPsuSketchBackendSweep.run(config);
        Assert.assertEquals(8, rows.size());
        Assert.assertTrue(SogsPsuSketchBackendSweep.bestFullSuccessByTableSize(rows).isPresent());
        for (SogsPsuSketchBackendSweep.Row row : rows) {
            Assert.assertTrue(row.h5Success() == row.trials());
            Assert.assertTrue(row.h5Payload() == row.trials());
        }
    }
}
