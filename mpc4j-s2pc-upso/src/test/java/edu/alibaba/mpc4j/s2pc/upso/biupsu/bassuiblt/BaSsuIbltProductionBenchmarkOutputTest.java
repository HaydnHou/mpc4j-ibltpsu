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
        Assert.assertTrue(display.contains("RPC/Core-COT candidate endpoint"));
        Assert.assertTrue(display.contains("not production-certified"));
        Assert.assertFalse(display.contains("production union-probe BA-UPOT is not implemented"));
        Assert.assertFalse(display.contains(BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION));
        Assert.assertFalse(display.contains("measuredProduction=true"));
        Assert.assertFalse(display.contains("securityNotice=NONE"));
    }

    @Test
    public void testMeasuredProductionBenchmarkGateRejectsNotReadyConfigs() {
        BaSsuIbltBiUpsuConfig defaultConfig = new BaSsuIbltBiUpsuConfig.Builder().build();
        IllegalStateException defaultAbort = Assert.assertThrows(
            IllegalStateException.class, () -> BaSsuIbltMeasuredProductionBenchmark.run(defaultConfig)
        );
        Assert.assertTrue(defaultAbort.getMessage().contains(
            BaSsuIbltMeasuredProductionBenchmark.UNAVAILABLE_NOTICE_ID
        ));
        Assert.assertTrue(defaultAbort.getMessage().contains("productionReady=false"));
        Assert.assertTrue(defaultAbort.getMessage().contains(
            BaSsuIbltBiUpsuConfig.COSTED_BENCHMARK_NOT_READY_REASON
        ));

        BaSsuIbltBiUpsuConfig materialReadyConfig =
            BaSsuIbltBiUpsuEndpointProductionTest.secureFixedLoopConfigWithFakeM14b();
        IllegalStateException materialAbort = Assert.assertThrows(
            IllegalStateException.class, () -> BaSsuIbltMeasuredProductionBenchmark.run(materialReadyConfig)
        );
        Assert.assertTrue(materialAbort.getMessage().contains("productionReady=false"));
        Assert.assertTrue(materialAbort.getMessage().contains("endpoint adapter remains fail-closed"));
    }

    @Test
    public void testMeasuredProductionBenchmarkAcceptsOnlineBatchSize() throws Exception {
        BaSsuIbltMeasuredProductionBenchmark.Result result = BaSsuIbltMeasuredProductionBenchmark.run(
            BaSsuIbltMeasuredProductionBenchmark.Config.fromArgs(new String[]{
                "m=8", "n=16", "overlap=4", "alpha=4.0", "onlineBatchSize=4", "seed=20260606",
                "timeoutMillis=120000"
            })
        );
        String display = result.toDisplayString();
        Assert.assertEquals(BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION,
            result.getBenchmarkKind());
        Assert.assertTrue(display.contains("rawCommand=BaSsuIbltMeasuredProductionBenchmark"));
        Assert.assertTrue(display.contains("onlineBatchSize=4"));
        Assert.assertTrue(result.getMaxProbeBatchSize() <= 4);
    }

    @Test
    public void testProfileSweepBenchmarkOutput() {
        BaSsuIbltProfileSweepBenchmark.Result result = BaSsuIbltProfileSweepBenchmark.run(
            BaSsuIbltProfileSweepBenchmark.Config.fromArgs(new String[]{
                "m=8", "n=16", "overlap=4", "alphas=3.5,4.0", "degrees=3", "onlineBatchSizes=4",
                "seed=20260606", "timeoutMillis=120000"
            })
        );
        String display = result.toDisplayString();
        Assert.assertTrue(display.contains("benchmarkKind=" + BaSsuIbltProfileSweepBenchmark.BENCHMARK_KIND));
        Assert.assertTrue(display.contains("securityNotice=" + BaSsuIbltProfileSweepBenchmark.SECURITY_NOTICE));
        Assert.assertTrue(display.contains("rowCount=2"));
        Assert.assertTrue(display.contains("bestRow="));
        Assert.assertTrue(display.contains("onlineBatchSize=4"));
        Assert.assertTrue(display.contains("rawCommand=BaSsuIbltProfileSweepBenchmark"));
    }

    static BaSsuIbltQueuePeelBenchmark.Result sampleQueuePeelResult() throws InterruptedException {
        return BaSsuIbltQueuePeelBenchmark.run(BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260605",
            "checkBits=182", "tagBits=182", "calibrationBuckets=256", "warmup=0", "measure=1"
        }));
    }
}
