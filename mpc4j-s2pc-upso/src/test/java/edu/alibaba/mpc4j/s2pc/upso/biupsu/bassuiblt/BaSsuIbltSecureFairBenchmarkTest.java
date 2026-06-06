package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * BA-SSU-IBLT secure-component fair benchmark tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltSecureFairBenchmarkTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testOprfTagBenchmarkAllowsUnbalancedSender() throws InterruptedException {
        BaSsuIbltOprfTagConfig tagConfig = new BaSsuIbltOprfTagConfig(
            OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST), 8, 24, 16
        );
        BaSsuIbltOprfTagBenchmark.Result result = BaSsuIbltOprfTagBenchmark.run(
            tagConfig, 20, ELEMENT_BYTE_LENGTH, 20260604L
        );
        Assert.assertEquals(8, result.getReceiverPublicCapacity());
        Assert.assertEquals(20, result.getSenderInputSize());
        Assert.assertEquals(20, result.getSenderOutputBatchSize());
        Assert.assertEquals(8, result.getReceiverOutputBatchSize());
        Assert.assertTrue(result.getOfflineSendBytes() > 0);
        Assert.assertTrue(result.getOnlineSendBytes() > 0);
        Assert.assertNotEquals(0L, result.getChecksum());
        Assert.assertTrue(result.toDisplayString().contains("receiverPublicCapacity=8"));
    }

    @Test
    public void testSmallFairBenchmarkSeparatesCurrentAndTargetRoutes() throws InterruptedException {
        BaSsuIbltSecureFairBenchmark.Config config = BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=64", "shadow=8", "overlap=4", "degree=3", "alpha=4.0", "retry=1", "seed=20260604",
            "checkBits=182", "tagBits=182", "cot=2", "calibrationBuckets=512", "warmup=0", "measure=1"
        });
        BaSsuIbltSecureFairBenchmark.Result result = BaSsuIbltSecureFairBenchmark.run(config);
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(64, 8)
            .setDegree(3)
            .setAlphaAnchor(4.0)
            .setRetryCount(1)
            .setPublicPlaceSeed(20260604L)
            .setCheckBits(182)
            .setTagBits(182)
            .build();
        Assert.assertEquals(BaSsuIbltProtocolSchedule.targetOnePass(params).getScheduledBucketCount(),
            result.getTargetOnePassBuckets());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.currentFixedLoopM14a(params).getScheduledBucketCount(),
            result.getCurrentFixedLoopBuckets());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.queuePeelAligned(params).getScheduledBucketCount(),
            result.getQueuePeelBuckets());
        Assert.assertTrue(result.getCurrentFixedLoopBuckets() > result.getTargetOnePassBuckets());
        Assert.assertTrue(result.getCurrentFixedLoopBuckets() > result.getQueuePeelBuckets());
        Assert.assertTrue(result.getQueuePeelBuckets() > result.getTargetOnePassBuckets());
        Assert.assertEquals((long) Math.ceil(3.5 * (64 + 8)), result.getH5Cells());
        Assert.assertTrue(result.getH5BucketProbes() > result.getH5Cells());
        Assert.assertTrue(result.getTargetOnePassVsH5ProbeRatio() < 1.0);
        Assert.assertTrue(result.getQueuePeelVsH5ProbeRatio() < 1.0);
        Assert.assertEquals(1 + ELEMENT_BYTE_LENGTH + 16, result.getM14aCapsuleBytesPerBucket());
        Assert.assertTrue(result.getCurrentM14aOfflineTotalBytes() > result.getTargetOnePassOfflineTotalBytes());
        Assert.assertTrue(result.getCurrentM14aOnlineTotalBytes() > result.getTargetOnePassOnlineTotalBytes());
        Assert.assertTrue(result.getCurrentM14aOfflineTotalBytes() > result.getQueuePeelOfflineTotalBytes());
        Assert.assertTrue(result.getCurrentM14aOnlineTotalBytes() > result.getQueuePeelOnlineTotalBytes());
        assertCurrentSecurityNotice(result.toDisplayString());
        Assert.assertTrue(result.toDisplayString().contains("historicalTargetOnePassLowerBoundBuckets=256"));
        Assert.assertTrue(result.toDisplayString().contains("queuePeelBuckets=472"));
    }

    @Test
    public void testLargeShapeQueuePeelAccountingIsStable() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1 << 18, 1 << 10)
            .setDegree(3)
            .setAlphaAnchor(1.55)
            .setRetryCount(1)
            .setPublicPlaceSeed(20260605L)
            .setCheckBits(182)
            .build();
        long queuePeelBuckets = BaSsuIbltProtocolSchedule.queuePeelAligned(params).getScheduledBucketCount();
        long h5BucketProbes = BaUpotCostEstimator.estimateH5BucketProbes(params.getNLarge(), params.getNShadow());
        Assert.assertEquals(1_195_828L, queuePeelBuckets);
        Assert.assertEquals(1_973_760L, h5BucketProbes);
        Assert.assertEquals(0.605863, ((double) queuePeelBuckets) / h5BucketProbes, 0.000001);
    }

    @Test
    public void testTsvShapeIsStable() throws InterruptedException {
        BaSsuIbltSecureFairBenchmark.Config config = BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260605",
            "checkBits=182", "tagBits=182", "calibrationBuckets=256", "warmup=0", "measure=1", "tsv=true"
        });
        BaSsuIbltSecureFairBenchmark.Result result = BaSsuIbltSecureFairBenchmark.run(config);
        String header = BaSsuIbltSecureFairBenchmark.Result.tsvHeader();
        String line = result.toTsvLine();
        Assert.assertTrue(header.startsWith("large\tshadow\toverlap"));
        Assert.assertTrue(line.startsWith("32\t8\t2"));
        Assert.assertTrue(line.endsWith("\t" + BaSsuIbltSecureFairBenchmark.SECURITY_NOTICE_ID));
        Assert.assertTrue(header.contains("\thistoricalTargetOnePassLowerBoundBuckets\t"));
        Assert.assertTrue(header.contains("\tqueuePeelBuckets\t"));
        Assert.assertTrue(header.contains("\tqueuePeelVsH5EstimatedProbeRatio\t"));
        Assert.assertTrue(header.contains("\tcurrentM14aOfflineTotalBytes\t"));
        Assert.assertTrue(header.contains("\thistoricalTargetOnePassOfflineTotalBytes\t"));
        Assert.assertTrue(header.contains("\tqueuePeelOfflineTotalBytes\t"));
        Assert.assertTrue(header.contains("\tqueuePeelOnlineTotalBytes\t"));
        Assert.assertTrue(header.contains("\tbenchmarkKind\t"));
        Assert.assertTrue(header.contains("\tmeasuredProduction\t"));
        Assert.assertTrue(header.contains("\tbaselineName\t"));
        Assert.assertTrue(header.contains("\tproductionReady\t"));
        Assert.assertTrue(header.contains("\tretryStatus\t"));
        Assert.assertTrue(line.contains("\t" + BaSsuIbltSecureFairBenchmark.BENCHMARK_KIND_ESTIMATE + "\tfalse\t"
            + BaSsuIbltSecureFairBenchmark.H5_BASELINE_NAME + "\tfalse\t"
            + BaSsuIbltSecureFairBenchmark.QUEUE_PEEL_ESTIMATE_RETRY_STATUS + "\t"));
        Assert.assertEquals(header.split("\t").length, line.split("\t").length);
    }

    @Test
    public void testQueuePeelBenchmarkWrapperUsesQueuePeelFields() throws InterruptedException {
        BaSsuIbltSecureFairBenchmark.Config config = BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260605",
            "checkBits=182", "tagBits=182", "calibrationBuckets=256", "warmup=0", "measure=1"
        });
        BaSsuIbltQueuePeelBenchmark.Result result = BaSsuIbltQueuePeelBenchmark.run(config);
        Assert.assertEquals(248L, result.getQueuePeelBuckets());
        Assert.assertEquals(result.getQueuePeelBuckets(), result.getProbeCount());
        Assert.assertTrue(result.getQueuePeelOfflineTotalBytes() > result.getQueuePeelOfflineBytes());
        Assert.assertTrue(result.getQueuePeelOnlineTotalBytes() > result.getQueuePeelOnlineBytes());
        Assert.assertEquals(result.getQueuePeelOfflineTotalBytes(), result.getOfflineBytes());
        Assert.assertEquals(result.getQueuePeelOnlineTotalBytes(), result.getOnlineBytes());
        Assert.assertEquals(result.getOfflineBytes() + result.getOnlineBytes(), result.getTotalBytes());
        Assert.assertEquals(result.getOfflineTimeNanos() + result.getOnlineTimeNanos(), result.getTotalTimeNanos());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.BENCHMARK_KIND_ESTIMATE, result.getBenchmarkKind());
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.H5_BASELINE_NAME, result.getBaselineName());
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.SECURITY_NOTICE, result.getSecurityNotice());
        assertCurrentSecurityNotice(result.getSecurityNotice());
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.QUEUE_PEEL_ESTIMATE_RETRY_STATUS, result.getRetryStatus());
        Assert.assertTrue(result.getQueuePeelVsH5ProbeRatio() < 1.0);
        Assert.assertEquals(result.getQueuePeelVsH5ProbeRatio(), result.getQueuePeelVsH5EstimatedProbeRatio(), 0.0);
        Assert.assertTrue(result.toDisplayString().contains("QUEUE_PEEL_ALIGNED"));
        Assert.assertTrue(result.toDisplayString().contains("benchmarkKind=ESTIMATE"));
        Assert.assertTrue(result.toDisplayString().contains("measuredProduction=false"));
        Assert.assertTrue(result.toDisplayString().contains("productionReady=false"));
        Assert.assertTrue(result.toDisplayString().contains(
            "baselineName=" + BaSsuIbltSecureFairBenchmark.H5_BASELINE_NAME
        ));
        Assert.assertTrue(result.toDisplayString().contains(
            "retryStatus=" + BaSsuIbltSecureFairBenchmark.QUEUE_PEEL_ESTIMATE_RETRY_STATUS
        ));
        Assert.assertTrue(result.toDisplayString().contains("offlineTimeMs="));
        Assert.assertTrue(result.toDisplayString().contains("onlineTimeMs="));
        Assert.assertTrue(result.toDisplayString().contains("totalTimeMs="));
        Assert.assertTrue(result.toDisplayString().contains("offlineTotalBytes="));
        Assert.assertTrue(result.toDisplayString().contains("onlineTotalBytes="));
        Assert.assertTrue(result.toDisplayString().contains("totalBytes="));
        Assert.assertTrue(result.toDisplayString().contains("probeCount=248"));
        Assert.assertTrue(result.toDisplayString().contains("h5BucketProbes"));
        Assert.assertTrue(result.toDisplayString().contains("queuePeelVsH5EstimatedProbeRatio"));
    }

    @Test
    public void testExplicitLowCheckBitsRejectedForQueuePeel() {
        BaSsuIbltSecureFairBenchmark.Config config = BaSsuIbltSecureFairBenchmark.Config.fromArgs(new String[]{
            "large=64", "shadow=8", "overlap=4", "degree=3", "alpha=4.0", "retry=1", "seed=20260604",
            "checkBits=128", "tagBits=182", "calibrationBuckets=256", "warmup=0", "measure=1"
        });
        IllegalArgumentException abort = Assert.assertThrows(
            IllegalArgumentException.class, () -> BaSsuIbltSecureFairBenchmark.run(config)
        );
        Assert.assertTrue(abort.getMessage().contains("QUEUE_PEEL_ALIGNED"));
    }

    @Test
    public void testConfigRejectsInvalidOverlap() {
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltSecureFairBenchmark.Config.fromArgs(
            new String[]{"large=32", "shadow=8", "overlap=9"}
        ));
    }

    private static void assertCurrentSecurityNotice(String text) {
        Assert.assertTrue(text.contains("RPC/Core-COT candidate endpoint"));
        Assert.assertTrue(text.contains("not production-certified"));
        Assert.assertTrue(text.contains("measured-production wired"));
        Assert.assertFalse(text.contains("production union-probe BA-UPOT is not implemented"));
    }
}
