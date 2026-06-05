package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * BA-SSU-IBLT costed two-party benchmark tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltTwoPartyBenchmarkTest {
    /**
     * small example config path.
     */
    private static final String SMALL_CONF_PATH =
        "src/test/resources/conf_biupsu_ba_ssu_iblt_small_example.conf";
    /**
     * unbalanced example config path.
     */
    private static final String UNBALANCED_CONF_PATH =
        "src/test/resources/conf_biupsu_ba_ssu_iblt_unbalanced_example.conf";

    @Test
    public void testSmallCostedTwoPartyRun() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=128", "shadow=16", "overlap=8", "degree=3", "alpha=4.0", "retry=1", "seed=20260603",
            "checkBits=182", "tagBits=182", "cot=2", "batch=512", "plainPayload=true", "maskedPayload=true",
            "caseGateWireMasked=true"
        });
        BaSsuIbltTwoPartyBenchmark.Result result = BaSsuIbltTwoPartyBenchmark.run(config);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(result.isBiOutputDelivery());
        Assert.assertEquals(
            BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT,
            result.getDeliveryMode()
        );
        Assert.assertTrue(result.getPlainBenchmarkResult().getPlainResult().getScheduledBucketCount() > 0);
        Assert.assertTrue(result.getPlainBenchmarkResult().hasCaseGateStats());
        Assert.assertEquals(
            result.getPlainBenchmarkResult().getPlainResult().getScheduledBucketCount(),
            result.getPlainBenchmarkResult().getCaseGateBucketCount()
        );
        Assert.assertEquals(
            result.getPlainBenchmarkResult().getPlainResult().getScheduledBucketCount() * 8,
            result.getPlainBenchmarkResult().getCaseGateStats().getAndGateCount()
        );
        Assert.assertTrue(result.getPlainBenchmarkResult().getLivePayloadCapsuleByteLength() > 0);
        Assert.assertEquals(
            result.getPlainBenchmarkResult().getPlainResult().getScheduledBucketCount()
                * result.getPlainBenchmarkResult().getLivePayloadCapsuleByteLength(),
            result.getPlainBenchmarkResult().getLivePayloadBytes()
        );
        Assert.assertTrue(result.getUpotResult().getOfflineSendBytes() > 0);
        Assert.assertTrue(result.getUpotResult().getOnlineSendBytes() > 0);
        Assert.assertNull(result.getReverseUpotResult());
        Assert.assertEquals(0L, result.getReverseSendBytes());
        Assert.assertEquals(result.getForwardSendBytes(), result.getTotalSendBytes());
        Assert.assertEquals(result.getForwardSendBytes(), result.getSignedSourceSplitSendBytes());
        Assert.assertNotNull(result.getPlainPayloadResult());
        Assert.assertTrue(result.getPlainPayloadResult().isChecksumEqual());
        Assert.assertTrue(result.getPlainPayloadSendBytes() > 0);
        Assert.assertEquals(
            result.getUpotResult().getConfig().getBucketNum(),
            result.getPlainPayloadResult().getBucketNum()
        );
        Assert.assertNotNull(result.getMaskedPayloadResult());
        Assert.assertTrue(result.getMaskedPayloadResult().isChecksumEqual());
        Assert.assertTrue(result.getMaskedPayloadSendBytes() > 0);
        Assert.assertEquals(
            result.getUpotResult().getConfig().getBucketNum(),
            result.getMaskedPayloadResult().getBucketNum()
        );
        Assert.assertTrue(result.getBridgeTotalTimeNanos() > 0);
        Assert.assertTrue(result.getBridgeTotalSendBytes() > 0);
        Assert.assertTrue(result.getTotalTimeNanos() > result.getPlainBenchmarkResult().getPlainTimeNanos());
        Assert.assertTrue(result.getTotalSendBytes() > 0);
    }

    @Test
    public void testBiOutputIsDefaultAndSingleOutputIsComparisonOnly() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config biOutputConfig = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=64", "shadow=8", "overlap=4", "degree=3", "alpha=4.0", "retry=1", "seed=20260604",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256"
        });
        BaSsuIbltTwoPartyBenchmark.Result biOutputResult = BaSsuIbltTwoPartyBenchmark.run(biOutputConfig);
        Assert.assertTrue(biOutputResult.isSuccess());
        Assert.assertTrue(biOutputResult.isBiOutputDelivery());
        Assert.assertEquals(BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT, biOutputResult.getDeliveryMode());
        Assert.assertNull(biOutputResult.getReverseUpotResult());
        Assert.assertEquals(biOutputResult.getForwardSendBytes(), biOutputResult.getTotalSendBytes());

        BaSsuIbltTwoPartyBenchmark.Config singleOutputConfig = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=64", "shadow=8", "overlap=4", "degree=3", "alpha=4.0", "retry=1", "seed=20260604",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256", "singleOutput=true"
        });
        BaSsuIbltTwoPartyBenchmark.Result singleOutputResult = BaSsuIbltTwoPartyBenchmark.run(singleOutputConfig);
        Assert.assertTrue(singleOutputResult.isSuccess());
        Assert.assertFalse(singleOutputResult.isBiOutputDelivery());
        Assert.assertNull(singleOutputResult.getReverseUpotResult());
        Assert.assertEquals(singleOutputResult.getForwardSendBytes(), singleOutputResult.getTotalSendBytes());
    }

    @Test
    public void testTwoPassDeliveryIsLegacyComparisonMode() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=64", "shadow=8", "overlap=4", "degree=3", "alpha=4.0", "retry=1", "seed=20260605",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256", "delivery=TWO_PASS"
        });
        BaSsuIbltTwoPartyBenchmark.Result result = BaSsuIbltTwoPartyBenchmark.run(config);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(result.isBiOutputDelivery());
        Assert.assertEquals(BaSsuIbltBiOutputDeliveryMode.TWO_PASS, result.getDeliveryMode());
        Assert.assertNotNull(result.getReverseUpotResult());
        Assert.assertTrue(result.getReverseSendBytes() > 0);
        Assert.assertEquals(result.getForwardSendBytes() + result.getReverseSendBytes(), result.getTotalSendBytes());
    }

    @Test
    public void testBenchmarkRetryTwoUsesFixedPublicBucketCount() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=2", "seed=20260606",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256", "maskedPayload=true"
        });
        BaSsuIbltTwoPartyBenchmark.Result result = BaSsuIbltTwoPartyBenchmark.run(config);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(256, result.getUpotResult().getConfig().getBucketNum());
        Assert.assertNotNull(result.getMaskedPayloadResult());
        Assert.assertEquals(256, result.getMaskedPayloadResult().getBucketNum());
        Assert.assertEquals(256L * 8, result.getCaseGateEstimate().getAndGateCount());
    }

    @Test
    public void testBenchmarkRejectsNullConfig() {
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltTwoPartyBenchmark.run(null));
    }

    @Test
    public void testConfigFromPropertiesUsesLogAliasesBeforeOverlapRate() {
        Properties properties = new Properties();
        properties.setProperty("mLog", "5");
        properties.setProperty("nLog", "10");
        properties.setProperty("overlapRate", "0.25");
        properties.setProperty("maskedPayload", "true");
        properties.setProperty("evaluatorMode", "case-gate-wire-masked");
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromProperties(properties);
        BaSsuIbltTwoPartyBenchmark.Result result;
        try {
            result = BaSsuIbltTwoPartyBenchmark.run(config);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(1024 + 32 - 8, result.getPlainBenchmarkResult().getPlainResult().getLeftUnion().size());
        Assert.assertEquals(1024 - 8,
            result.getPlainBenchmarkResult().getPlainResult().getSignedPeelOutput().getAnchorOnlyCount());
        Assert.assertEquals(32 - 8,
            result.getPlainBenchmarkResult().getPlainResult().getSignedPeelOutput().getShadowOnlyCount());
        Assert.assertEquals(8, result.getPlainBenchmarkResult().getPlainResult().getSharedSingletonCount());
        Assert.assertTrue(result.getCostedProtocolOfflineSendBytes() > 0);
        Assert.assertTrue(result.getCostedProtocolOnlineSendBytes() > 0);
    }

    @Test
    public void testConfigParsesRepeatWarmupAndTsv() throws IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260607",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256", "repeat=2", "warmup=1", "tsv=true"
        });
        Assert.assertEquals(2, config.getRepeatCount());
        Assert.assertEquals(1, config.getWarmupCount());
        Assert.assertTrue(config.isTsv());
    }

    @Test
    public void testRunRepeatedProducesStableTsvShape() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260608",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256", "repeat=2", "warmup=1", "tsv=true"
        });
        BaSsuIbltTwoPartyBenchmark.RepeatedResult repeatedResult = BaSsuIbltTwoPartyBenchmark.runRepeated(config);
        Assert.assertTrue(repeatedResult.isSuccess());
        Assert.assertEquals(2, repeatedResult.getResults().size());
        Assert.assertEquals(
            repeatedResult.getResults().get(0).getCostedProtocolOfflineSendBytes(),
            repeatedResult.getResults().get(1).getCostedProtocolOfflineSendBytes()
        );
        Assert.assertEquals(
            repeatedResult.getResults().get(0).getCostedProtocolOnlineSendBytes(),
            repeatedResult.getResults().get(1).getCostedProtocolOnlineSendBytes()
        );
        String tsv = repeatedResult.toTsvString();
        Assert.assertTrue(tsv.startsWith(BaSsuIbltTwoPartyBenchmark.Result.tsvHeader()));
        Assert.assertEquals(3, tsv.split("\\R").length);
        Assert.assertTrue(repeatedResult.toDisplayString().contains("repeat=2, warmup=1"));
        Assert.assertTrue(repeatedResult.toDisplayString().contains(BaSsuIbltTwoPartyBenchmark.SECURITY_NOTICE));
    }

    @Test
    public void testTsvHeaderIsStable() {
        Assert.assertEquals(
            "runIndex\tlarge\tshadow\toverlap\tdegree\talpha\tretry\tevaluator\tdelivery\tsuccess\tunionSize"
                + "\tanchorOnly\tshadowOnly\tsharedSingleton\tdynamicPeelBuckets\tfixedDeliveryBuckets"
                + "\trecommendedCheckBits\tplainPeelMs\tofflineMs\tonlineMs\ttotalMs"
                + "\tcostedProtocolOfflineBytes\tcostedProtocolOnlineBytes\tcostedProtocolTotalBytes"
                + "\tplainPayloadBytes\tmaskedPayloadBytes\tpayloadBridgeBytes\tgrandTotalBytes\tsecurityNotice",
            BaSsuIbltTwoPartyBenchmark.Result.tsvHeader()
        );
    }

    @Test
    public void testResultTsvLineContainsCoreMetrics() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260609",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256"
        });
        BaSsuIbltTwoPartyBenchmark.Result result = BaSsuIbltTwoPartyBenchmark.run(config);
        String line = result.toTsvLine(7);
        Assert.assertTrue(line.startsWith("7\t32\t8\t2\t3"));
        Assert.assertTrue(line.contains("\tSIGNED_SOURCE_SPLIT\t"));
        Assert.assertTrue(line.endsWith("\t" + BaSsuIbltTwoPartyBenchmark.SECURITY_NOTICE_ID));
        Assert.assertEquals(BaSsuIbltTwoPartyBenchmark.Result.tsvHeader().split("\t").length,
            line.split("\t").length);
        Assert.assertTrue(result.toDisplayString().contains(BaSsuIbltTwoPartyBenchmark.SECURITY_NOTICE));
    }

    @Test
    public void testPayloadTsvSeparatesCostedAndGrandTotalBytes() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260610",
            "checkBits=182", "tagBits=182", "cot=2", "batch=256", "maskedPayload=true"
        });
        BaSsuIbltTwoPartyBenchmark.Result result = BaSsuIbltTwoPartyBenchmark.run(config);
        String[] fields = result.toTsvLine(0).split("\t");
        long costedTotalBytes = Long.parseLong(fields[23]);
        long payloadBridgeBytes = Long.parseLong(fields[26]);
        long grandTotalBytes = Long.parseLong(fields[27]);
        Assert.assertTrue(payloadBridgeBytes > 0);
        Assert.assertEquals(costedTotalBytes + payloadBridgeBytes, grandTotalBytes);
    }

    @Test
    public void testMainWritesTsvFile() throws InterruptedException, IOException {
        Path tsvPath = Files.createTempFile("ba-ssu-iblt-benchmark", ".tsv");
        try {
            BaSsuIbltTwoPartyBenchmark.main(new String[]{
                "large=32", "shadow=8", "overlap=2", "degree=3", "alpha=4.0", "retry=1", "seed=20260611",
                "checkBits=182", "tagBits=182", "cot=2", "batch=256", "tsvFile=" + tsvPath
            });
            String output = Files.readString(tsvPath);
            Assert.assertTrue(output.startsWith(BaSsuIbltTwoPartyBenchmark.Result.tsvHeader()));
            Assert.assertTrue(output.contains(BaSsuIbltTwoPartyBenchmark.SECURITY_NOTICE_ID));
            Assert.assertEquals(2, output.strip().split("\\R").length);
        } finally {
            Files.deleteIfExists(tsvPath);
        }
    }

    @Test
    public void testInvalidRepeatWarmupAndShapeRejected() throws IOException {
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltTwoPartyBenchmark.Config.fromArgs(
            new String[]{"repeat=0"}
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltTwoPartyBenchmark.Config.fromArgs(
            new String[]{"warmup=-1"}
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltTwoPartyBenchmark.Config.fromArgs(
            new String[]{"large=32", "shadow=8", "overlap=2", "alpha=Infinity"}
        ));
        BaSsuIbltTwoPartyBenchmark.Config hugeSizeConfig = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "large=16777217", "shadow=8", "overlap=2", "alpha=1.0"
        });
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltTwoPartyBenchmark.run(hugeSizeConfig));
    }

    @Test
    public void testSmallConfPathParsesAndRuns() throws InterruptedException, IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "conf=" + SMALL_CONF_PATH
        });
        Assert.assertEquals(32, config.getLargeSize());
        Assert.assertEquals(32, config.getShadowSize());
        Assert.assertEquals(8, config.getOverlap());
        Assert.assertTrue(config.isMaskedPayload());
        BaSsuIbltTwoPartyBenchmark.Result result = BaSsuIbltTwoPartyBenchmark.run(config);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(128, result.getUpotResult().getConfig().getBucketNum());
        Assert.assertNotNull(result.getMaskedPayloadResult());
    }

    @Test
    public void testUnbalancedConfParsesWithoutPayloadBridgeByDefault() throws IOException {
        BaSsuIbltTwoPartyBenchmark.Config config = BaSsuIbltTwoPartyBenchmark.Config.fromArgs(new String[]{
            "conf=" + UNBALANCED_CONF_PATH
        });
        Assert.assertEquals(1 << 18, config.getLargeSize());
        Assert.assertEquals(1 << 10, config.getShadowSize());
        Assert.assertEquals(256, config.getOverlap());
        Assert.assertFalse(config.isMaskedPayload());
        Assert.assertFalse(config.isPlainPayload());
        Assert.assertEquals(BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT, config.getDeliveryMode());
    }

    @Test
    public void testPropertiesRejectDuplicateAliases() {
        Properties duplicateSizeProperties = new Properties();
        duplicateSizeProperties.setProperty("m", "32");
        duplicateSizeProperties.setProperty("mLog", "5");
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltTwoPartyBenchmark.Config.fromProperties(duplicateSizeProperties));

        Properties duplicateOverlapProperties = new Properties();
        duplicateOverlapProperties.setProperty("overlap", "8");
        duplicateOverlapProperties.setProperty("overlapRate", "0.25");
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltTwoPartyBenchmark.Config.fromProperties(duplicateOverlapProperties));
    }
}
