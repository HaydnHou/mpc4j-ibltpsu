package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * BA-UPOT two-party benchmark tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotTwoPartyBenchmarkTest {
    @Test
    public void testParseArgs() {
        BaUpotTwoPartyBenchmark.BenchmarkConfig config = BaUpotTwoPartyBenchmark.BenchmarkConfig.fromArgs(new String[]{
            "buckets=1024", "elementBytes=16", "checkBits=182", "tagBits=192", "caseNum=4", "cot=2",
            "batch=256"
        });
        Assert.assertEquals(1024, config.getBucketNum());
        Assert.assertEquals(16, config.getElementByteLength());
        Assert.assertEquals(182, config.getCheckBits());
        Assert.assertEquals(192, config.getTagBits());
        Assert.assertEquals(4, config.getCaseNum());
        Assert.assertEquals(2, config.getCotNumPerBucket());
        Assert.assertEquals(256, config.getOnlineBatchSize());
    }

    @Test
    public void testSmallTwoPartyRun() throws InterruptedException {
        BaUpotTwoPartyBenchmark.BenchmarkConfig config = new BaUpotTwoPartyBenchmark.BenchmarkConfig()
            .setBucketNum(4096)
            .setElementByteLength(Long.BYTES)
            .setCheckBits(182)
            .setTagBits(182)
            .setCotNumPerBucket(2)
            .setOnlineBatchSize(512);
        BaUpotTwoPartyBenchmark.Result result = BaUpotTwoPartyBenchmark.runMemoryBenchmark(config);
        Assert.assertTrue(result.getOfflineTimeNanos() > 0);
        Assert.assertTrue(result.getOnlineTimeNanos() > 0);
        Assert.assertTrue(result.getOfflineSendBytes() > 0);
        long expectedCapsuleBytes = 4096L * 94;
        Assert.assertTrue(result.getSenderResult().getOnlineSendBytes() >= expectedCapsuleBytes);
        Assert.assertTrue(result.getSenderResult().getOnlineSendBytes() < expectedCapsuleBytes + 1024);
        Assert.assertTrue(result.getOnlineSendBytes() >= expectedCapsuleBytes);
        Assert.assertTrue(result.getOnlineBytesPerBucket() >= 94.0);
        Assert.assertTrue(result.getOnlineBytesPerBucket() < 95.0);
        Assert.assertNotEquals(0L, result.getSenderResult().getChecksum());
        Assert.assertNotEquals(0L, result.getReceiverResult().getChecksum());
    }
}
