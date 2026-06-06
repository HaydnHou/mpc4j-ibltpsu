package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * P56 measured-production claim freeze tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltProductionMeasuredClaimTest {

    @Test
    public void testMeasuredProductionRunnerRemainsFailClosed() {
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder().build();
        IllegalStateException abort = Assert.assertThrows(
            IllegalStateException.class, () -> BaSsuIbltMeasuredProductionBenchmark.run(config)
        );
        Assert.assertTrue(abort.getMessage().contains(BaSsuIbltMeasuredProductionBenchmark.UNAVAILABLE_NOTICE_ID));
        Assert.assertTrue(abort.getMessage().contains("productionReady=false"));
        assertNoPositiveProductionClaim(abort.getMessage());
    }

    @Test
    public void testQueuePeelEstimateRowCannotClaimMeasuredProduction() throws InterruptedException {
        BaSsuIbltQueuePeelBenchmark.Result result =
            BaSsuIbltProductionBenchmarkOutputTest.sampleQueuePeelResult();
        String display = result.toDisplayString();
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.BENCHMARK_KIND_ESTIMATE, result.getBenchmarkKind());
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        assertNoProductionRow(display);
    }

    @Test
    public void testCandidateEndpointMeasuredRowCannotClaimProduction()
        throws InterruptedException, MpcAbortException {
        BaSsuIbltCandidateEndpointBenchmark.Result result = BaSsuIbltCandidateEndpointBenchmark.run(
            new BaSsuIbltCandidateEndpointBenchmark.Config()
                .setSenderSize(6)
                .setReceiverSize(7)
                .setOverlap(2)
                .setAlpha(5.0)
                .setSeed(20260608L)
        );
        String display = result.toDisplayString();
        Assert.assertEquals(
            BaSsuIbltCandidateEndpointBenchmark.BENCHMARK_KIND_MEASURED_CANDIDATE_ENDPOINT,
            result.getBenchmarkKind()
        );
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertTrue(display.contains("phaseBarrierExcluded=true"));
        Assert.assertTrue(display.contains("phaseBarrierBytes="));
        assertNoProductionRow(display);
    }

    @Test
    public void testMeasuredProductionRunnerEmitsProductionRowWithoutSpeedupClaim()
        throws InterruptedException, MpcAbortException {
        BaSsuIbltMeasuredProductionBenchmark.Result result = BaSsuIbltMeasuredProductionBenchmark.run(
            new BaSsuIbltMeasuredProductionBenchmark.Config()
                .setSenderSize(6)
                .setReceiverSize(7)
                .setOverlap(2)
                .setAlpha(5.0)
                .setSeed(20260609L)
        );
        String display = result.toDisplayString();
        Assert.assertEquals(
            BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION,
            result.getBenchmarkKind()
        );
        Assert.assertTrue(result.isMeasuredProduction());
        Assert.assertTrue(result.isProductionReady());
        Assert.assertEquals("production-ready queue-peel endpoint", result.getProductionReadinessReason());
        Assert.assertEquals(11, result.getUnionSize());
        Assert.assertTrue(result.getOfflineTimeNanos() > 0);
        Assert.assertTrue(result.getOnlineTimeNanos() > 0);
        Assert.assertTrue(result.getOfflineTotalBytes() > 0);
        Assert.assertTrue(result.getOnlineTotalBytes() > 0);
        Assert.assertTrue(result.getRetryStatus().startsWith("success@retry="));
        Assert.assertTrue(display.contains("benchmarkKind=MEASURED_PRODUCTION"));
        Assert.assertTrue(display.contains("measuredProduction=true"));
        Assert.assertTrue(display.contains("productionReady=true"));
        Assert.assertTrue(display.contains("securityNotice=NONE"));
        Assert.assertTrue(display.contains("rawCommand=BaSsuIbltMeasuredProductionBenchmark"));
        Assert.assertTrue(display.contains("timeoutMillis="));
        Assert.assertFalse(display.contains("MEASURED_CANDIDATE_ENDPOINT"));
        Assert.assertFalse(display.contains("speedup="));
    }

    @Test
    public void testMeasuredProductionRunnerParsesTimeoutArgument() {
        BaSsuIbltMeasuredProductionBenchmark.Config config =
            BaSsuIbltMeasuredProductionBenchmark.Config.fromArgs(new String[]{
                "m=2^5", "n=2^5", "overlap=4", "timeoutMillis=1000"
            });
        Assert.assertNotNull(config);
    }

    @Test
    public void testLegacyProtocolConfigOverloadCannotEmitProductionRowEvenWhenReady() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setAcceptAdaptiveQueueTranscriptLeakage(true)
                .setProductionReadinessCertificate(BaSsuIbltProductionReadinessCertificate.finalAuditPassed())
                .build();
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(backend)
            .build();
        Assert.assertTrue(config.isProductionReady());
        IllegalStateException abort = Assert.assertThrows(
            IllegalStateException.class, () -> BaSsuIbltMeasuredProductionBenchmark.run(config)
        );
        Assert.assertTrue(abort.getMessage().contains("requires benchmark input sets"));
        assertNoPositiveProductionClaim(abort.getMessage());
    }

    private static void assertNoProductionRow(String text) {
        Assert.assertFalse(text.contains(BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION));
        assertNoPositiveProductionClaim(text);
    }

    private static void assertNoPositiveProductionClaim(String text) {
        Assert.assertFalse(text.contains("measuredProduction=true"));
        Assert.assertFalse(text.contains("productionReady=true"));
        Assert.assertFalse(text.contains("securityNotice=NONE"));
        Assert.assertFalse(text.contains("speedup="));
    }
}
