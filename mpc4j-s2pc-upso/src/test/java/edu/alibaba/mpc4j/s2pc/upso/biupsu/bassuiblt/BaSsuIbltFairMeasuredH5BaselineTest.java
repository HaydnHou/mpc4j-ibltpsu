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
            );
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.BENCHMARK_KIND, result.getBenchmarkKind());
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.BASELINE_NAME, result.getBaselineName());
        Assert.assertEquals(BaSsuIbltFairMeasuredH5BaselineBenchmark.OUTPUT_SEMANTICS, result.getOutputSemantics());
        Assert.assertTrue(result.isMeasuredBaseline());
        Assert.assertTrue(result.isTwoOutputBaseline());
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertFalse(result.isSpeedupClaimReady());
        Assert.assertEquals(11, result.getUnionSize());
        Assert.assertEquals(2, result.getPsiCa());
        Assert.assertTrue(result.getOfflineTimeNanos() > 0);
        Assert.assertTrue(result.getOnlineTimeNanos() > 0);
        Assert.assertTrue(result.getTotalTimeNanos() > result.getOfflineTimeNanos());
        Assert.assertTrue(result.getOfflineTotalBytes() > 0);
        Assert.assertTrue(result.getOnlineTotalBytes() > 0);
        Assert.assertTrue(result.getTotalBytes() > result.getOfflineTotalBytes());
        Assert.assertTrue(result.getOfflinePayloadBytes() > 0);
        Assert.assertTrue(result.getOnlinePayloadBytes() > 0);
        Assert.assertTrue(result.getOfflinePacketNum() > 0);
        Assert.assertTrue(result.getOnlinePacketNum() > 0);
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
            );
        String display = result.toDisplayString();
        Assert.assertTrue(display.contains("benchmarkKind=MEASURED_H5_IBLT_PSU_BASELINE"));
        Assert.assertTrue(display.contains("baselineName=H5_IBLT_PSU_TWO_OUTPUT_MEASURED"));
        Assert.assertTrue(display.contains("measuredBaseline=true"));
        Assert.assertTrue(display.contains("twoOutputBaseline=true"));
        Assert.assertTrue(display.contains("measuredProduction=false"));
        Assert.assertTrue(display.contains("productionReady=false"));
        Assert.assertTrue(display.contains("outputSemantics=TWO_OUTPUT_BY_ROLE_SWAP"));
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
