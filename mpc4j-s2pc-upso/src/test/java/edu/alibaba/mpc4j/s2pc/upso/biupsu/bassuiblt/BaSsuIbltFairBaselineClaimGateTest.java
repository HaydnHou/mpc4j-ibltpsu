package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import org.junit.Assert;
import org.junit.Test;

/**
 * P55 fair baseline claim gate tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltFairBaselineClaimGateTest {

    @Test
    public void testMeasuredProductionAndMeasuredBaselineDoNotAutoEmitSpeedup()
        throws InterruptedException, MpcAbortException {
        BaSsuIbltMeasuredProductionBenchmark.Result productionResult =
            BaSsuIbltMeasuredProductionBenchmark.run(
                new BaSsuIbltMeasuredProductionBenchmark.Config()
                    .setSenderSize(5)
                    .setReceiverSize(6)
                    .setOverlap(2)
                    .setAlpha(5.0)
                    .setSeed(20260610L)
            );
        BaSsuIbltFairMeasuredH5BaselineBenchmark.Result baselineResult =
            BaSsuIbltFairMeasuredH5BaselineBenchmark.run(
                new BaSsuIbltFairMeasuredH5BaselineBenchmark.Config()
                    .setSenderSize(5)
                    .setReceiverSize(6)
                    .setOverlap(2)
                    .setSeed(20260610L)
                    .setMetadataOnly(true)
            );
        String productionDisplay = productionResult.toDisplayString();
        String baselineDisplay = baselineResult.toDisplayString();
        Assert.assertTrue(productionResult.isMeasuredProduction());
        Assert.assertTrue(productionResult.isProductionReady());
        Assert.assertFalse(baselineResult.isMeasuredBaseline());
        Assert.assertTrue(baselineResult.isMetadataOnly());
        Assert.assertTrue(baselineResult.isTwoOutputBaseline());
        Assert.assertFalse(baselineResult.isPaperSemantics());
        Assert.assertFalse(baselineResult.isNativePaperOneRunBaseline());
        Assert.assertEquals(2, baselineResult.getWrapperRuns());
        Assert.assertFalse(baselineResult.isSpeedupClaimReady());
        Assert.assertEquals(productionResult.getUnionSize(), baselineResult.getUnionSize());
        Assert.assertFalse(productionDisplay.contains("speedup="));
        Assert.assertFalse(baselineDisplay.contains("speedup="));
        Assert.assertTrue(baselineDisplay.contains("measurementMode=METADATA_ONLY"));
        Assert.assertTrue(baselineDisplay.contains("metadataOnly=true"));
        Assert.assertTrue(baselineDisplay.contains("speedupClaimReady=false"));
        Assert.assertTrue(baselineDisplay.contains("paperSemantics=false"));
        Assert.assertTrue(baselineDisplay.contains("nativePaperOneRunBaseline=false"));
    }
}
