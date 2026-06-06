package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import org.junit.Assert;
import org.junit.Test;

/**
 * P55 fair measured H5 / IBLT-PSU baseline tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltFairMeasuredH5BaselineTest {

    @Test
    public void testSmallTwoOutputMeasuredBaseline() throws InterruptedException, MpcAbortException {
        BaSsuIbltFairMeasuredH5BaselineBenchmark.Result result =
            BaSsuIbltFairMeasuredH5BaselineBenchmark.run(
                new BaSsuIbltFairMeasuredH5BaselineBenchmark.Config()
                    .setSenderSize(6)
                    .setReceiverSize(7)
                    .setOverlap(2)
                    .setSeed(20260606L)
                    .setMetadataOnly(true)
            );
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.BENCHMARK_KIND, result.getBenchmarkKind());
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.BASELINE_NAME, result.getBaselineName());
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.OUTPUT_SEMANTICS, result.getOutputSemantics());
        Assert.assertFalse(result.isMeasuredBaseline());
        Assert.assertTrue(result.isMetadataOnly());
        Assert.assertTrue(result.isTwoOutputBaseline());
        Assert.assertFalse(result.isPaperSemantics());
        Assert.assertFalse(result.isNativePaperOneRunBaseline());
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.WRAPPER_RUNS, result.getWrapperRuns());
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertFalse(result.isSpeedupClaimReady());
        Assert.assertEquals(11, result.getUnionSize());
        Assert.assertEquals(2, result.getPsiCa());
        Assert.assertEquals(0L, result.getOfflineTimeNanos());
        Assert.assertEquals(0L, result.getOnlineTimeNanos());
        Assert.assertEquals(0L, result.getTotalTimeNanos());
        Assert.assertEquals(0L, result.getOfflineTotalBytes());
        Assert.assertEquals(0L, result.getOnlineTotalBytes());
        Assert.assertEquals(0L, result.getTotalBytes());
        Assert.assertEquals(0L, result.getOfflinePayloadBytes());
        Assert.assertEquals(0L, result.getOnlinePayloadBytes());
        Assert.assertEquals(0L, result.getOfflinePacketNum());
        Assert.assertEquals(0L, result.getOnlinePacketNum());
    }

    @Test
    public void testDisplayIsBaselineOnlyAndNotSpeedupClaim() throws InterruptedException, MpcAbortException {
        BaSsuIbltFairMeasuredH5BaselineBenchmark.Result result =
            BaSsuIbltFairMeasuredH5BaselineBenchmark.run(
                new BaSsuIbltFairMeasuredH5BaselineBenchmark.Config()
                    .setSenderSize(5)
                    .setReceiverSize(5)
                    .setOverlap(5)
                    .setSeed(20260607L)
                    .setMetadataOnly(true)
            );
        String display = result.toDisplayString();
        Assert.assertTrue(display.contains("benchmarkKind="
            + BaSsuIbltFairMeasuredH5BaselineBenchmark.BENCHMARK_KIND));
        Assert.assertTrue(display.contains("baselineName="
            + BaSsuIbltFairMeasuredH5BaselineBenchmark.BASELINE_NAME));
        Assert.assertTrue(display.contains("measurementMode=METADATA_ONLY"));
        Assert.assertTrue(display.contains("metadataOnly=true"));
        Assert.assertTrue(display.contains("measuredBaseline=false"));
        Assert.assertTrue(display.contains("twoOutputBaseline=true"));
        Assert.assertTrue(display.contains("paperSemantics=false"));
        Assert.assertTrue(display.contains("nativePaperOneRunBaseline=false"));
        Assert.assertTrue(display.contains("wrapperRuns=2"));
        Assert.assertTrue(display.contains("measuredProduction=false"));
        Assert.assertTrue(display.contains("productionReady=false"));
        Assert.assertTrue(display.contains("outputSemantics="
            + BaSsuIbltFairMeasuredH5BaselineBenchmark.OUTPUT_SEMANTICS));
        Assert.assertTrue(display.contains("speedupClaimReady=false"));
        Assert.assertTrue(display.contains("rawCommand=BaSsuIbltFairMeasuredH5BaselineBenchmark"));
        Assert.assertFalse(display.contains("speedup="));
        Assert.assertFalse(display.contains("measuredProduction=true"));
        Assert.assertFalse(display.contains("productionReady=true"));
    }

    @Test
    public void testPowerOfTwoArgs() {
        BaSsuIbltFairMeasuredH5BaselineBenchmark.Config config =
            BaSsuIbltFairMeasuredH5BaselineBenchmark.Config.fromArgs(new String[]{
                "m=2^5", "n=2^5", "overlap=4", "elementBytes=8"
            });
        Assert.assertNotNull(config);
    }
}
