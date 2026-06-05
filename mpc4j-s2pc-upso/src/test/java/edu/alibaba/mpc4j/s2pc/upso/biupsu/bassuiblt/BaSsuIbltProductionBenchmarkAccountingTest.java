package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P39 benchmark accounting tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionBenchmarkAccountingTest {

    @Test
    public void testOfflineOnlineAndTotalFieldsAreConsistent() throws InterruptedException {
        BaSsuIbltQueuePeelBenchmark.Result result =
            BaSsuIbltProductionBenchmarkOutputTest.sampleQueuePeelResult();
        Assert.assertEquals(result.getOfflineBytes() + result.getOnlineBytes(), result.getTotalBytes());
        Assert.assertEquals(result.getOfflineTimeNanos() + result.getOnlineTimeNanos(), result.getTotalTimeNanos());
        Assert.assertEquals(result.getQueuePeelOfflineTotalBytes(), result.getOfflineBytes());
        Assert.assertEquals(result.getQueuePeelOnlineTotalBytes(), result.getOnlineBytes());
        Assert.assertTrue(result.getOfflineBytes() > 0);
        Assert.assertTrue(result.getOnlineBytes() > 0);
        Assert.assertTrue(result.getOfflineTimeNanos() > 0);
        Assert.assertTrue(result.getOnlineTimeNanos() > 0);
    }

    @Test
    public void testFairBenchmarkUsesTotalByteLabelsForOprfInclusiveRoutes() throws InterruptedException {
        BaSsuIbltSecureFairBenchmark.Config config = BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260605",
            "checkBits=182", "tagBits=182", "calibrationBuckets=256", "warmup=0", "measure=1"
        });
        BaSsuIbltSecureFairBenchmark.Result result = BaSsuIbltSecureFairBenchmark.run(config);
        String header = BaSsuIbltSecureFairBenchmark.Result.tsvHeader();
        String display = result.toDisplayString();
        Assert.assertTrue(header.contains("\tcurrentM14aOfflineTotalBytes\t"));
        Assert.assertTrue(header.contains("\thistoricalTargetOnePassOfflineTotalBytes\t"));
        Assert.assertFalse(header.contains("\tcurrentM14aOfflineBytes\t"));
        Assert.assertFalse(header.contains("\thistoricalTargetOnePassOfflineBytes\t"));
        Assert.assertTrue(display.contains("currentM14aOfflineTotalBytes="));
        Assert.assertTrue(display.contains("historicalTargetOnePassOfflineTotalBytes="));
        Assert.assertFalse(display.contains("currentM14aOfflineBytes="));
        Assert.assertFalse(display.contains("historicalTargetOnePassOfflineBytes="));
        Assert.assertTrue(display.contains("queuePeelVsH5EstimatedProbeRatio="));
    }
}
