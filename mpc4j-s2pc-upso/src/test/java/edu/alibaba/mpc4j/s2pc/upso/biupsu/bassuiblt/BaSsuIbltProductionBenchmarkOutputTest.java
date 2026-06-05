package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P39 benchmark output labeling tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionBenchmarkOutputTest {

    @Test
    public void testQueuePeelOutputCannotBeMistakenForMeasuredProduction() throws InterruptedException {
        BaSsuIbltQueuePeelBenchmark.Result result = sampleQueuePeelResult();
        String display = result.toDisplayString();
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.BENCHMARK_KIND_ESTIMATE, result.getBenchmarkKind());
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertTrue(display.contains("benchmarkKind=ESTIMATE"));
        Assert.assertTrue(display.contains("measuredProduction=false"));
        Assert.assertTrue(display.contains("productionReady=false"));
        Assert.assertTrue(display.contains(BaSsuIbltSecureFairBenchmark.SECURITY_NOTICE));
    }

    static BaSsuIbltQueuePeelBenchmark.Result sampleQueuePeelResult() throws InterruptedException {
        return BaSsuIbltQueuePeelBenchmark.run(BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260605",
            "checkBits=182", "tagBits=182", "calibrationBuckets=256", "warmup=0", "measure=1"
        }));
    }
}
