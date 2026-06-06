package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P39 H5 / IBLT-PSU baseline accounting tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltIbltPsuBaselineAccountingTest {

    @Test
    public void testBaselineNameAndProbeCountAreExplicit() throws InterruptedException {
        BaSsuIbltQueuePeelBenchmark.Result result =
            BaSsuIbltProductionBenchmarkOutputTest.sampleQueuePeelResult();
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.H5_BASELINE_NAME, result.getBaselineName());
        Assert.assertTrue(result.getBaselineName().endsWith("_ESTIMATE"));
        Assert.assertTrue(result.getH5BucketProbes() > 0);
        Assert.assertTrue(result.getQueuePeelVsH5ProbeRatio() > 0.0);
        Assert.assertTrue(result.toDisplayString().contains(
            "baselineName=" + BaSsuIbltSecureFairBenchmark.H5_BASELINE_NAME
        ));
        Assert.assertTrue(result.toDisplayString().contains("queuePeelVsH5EstimatedProbeRatio"));
        Assert.assertFalse(result.toDisplayString().contains("queuePeelVsH5Measured"));
        Assert.assertFalse(result.toDisplayString().contains("speedup="));
    }
}
