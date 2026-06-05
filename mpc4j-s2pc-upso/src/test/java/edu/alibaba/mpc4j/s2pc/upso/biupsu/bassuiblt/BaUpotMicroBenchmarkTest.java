package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * BA-UPOT micro-benchmark tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotMicroBenchmarkTest {
    @Test
    public void testParseArgs() {
        BaUpotMicroBenchmark.Config config = BaUpotMicroBenchmark.Config.fromArgs(new String[]{
            "buckets=1024", "warmup=0", "measure=2", "elementBytes=16", "checkBits=182", "tagBits=192",
            "caseNum=4", "lcot=2", "seed=0x1234"
        });
        Assert.assertEquals(1024, config.getBucketNum());
        Assert.assertEquals(0, config.getWarmupRounds());
        Assert.assertEquals(2, config.getMeasureRounds());
        Assert.assertEquals(16, config.getElementByteLength());
        Assert.assertEquals(182, config.getCheckBits());
        Assert.assertEquals(192, config.getTagBits());
        Assert.assertEquals(4, config.getCaseNum());
        Assert.assertEquals(2, config.getLcotNumPerBucket());
        Assert.assertEquals(0x1234L, config.getSeed());
    }

    @Test
    public void testFixedShapeBytes() {
        BaUpotMicroBenchmark.Config config = new BaUpotMicroBenchmark.Config()
            .setElementByteLength(Long.BYTES)
            .setCheckBits(182)
            .setTagBits(182)
            .setLcotNumPerBucket(2);
        Assert.assertEquals(23, config.checkByteLength());
        Assert.assertEquals(23, config.tagByteLength());
        Assert.assertEquals(11, config.hashCallsPerBucket());
        Assert.assertEquals(94, config.onlineBytesPerBucket());
        Assert.assertEquals(32, config.offlineCotBytesPerBucket());
    }

    @Test
    public void testSmallRun() {
        BaUpotMicroBenchmark.Config config = new BaUpotMicroBenchmark.Config()
            .setBucketNum(4096)
            .setWarmupRounds(1)
            .setMeasureRounds(1)
            .setElementByteLength(Long.BYTES)
            .setCheckBits(182)
            .setTagBits(182);
        BaUpotMicroBenchmark.Result result = BaUpotMicroBenchmark.run(config);
        Assert.assertEquals(4096L, result.getMeasuredBuckets());
        Assert.assertEquals(4096L * config.hashCallsPerBucket(), result.getHashCalls());
        Assert.assertTrue(result.getElapsedNanos() > 0);
        Assert.assertTrue(result.getNanosPerBucket() > 0);
        Assert.assertTrue(result.getBucketsPerSecond() > 0);
        Assert.assertEquals(94, result.getOnlineBytesPerBucket());
        Assert.assertEquals(32, result.getOfflineCotBytesPerBucket());
        Assert.assertNotEquals(0L, result.getChecksum());
    }
}
