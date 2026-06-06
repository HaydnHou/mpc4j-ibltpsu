package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P55 measured candidate endpoint benchmark tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltCandidateEndpointBenchmarkTest {

    @Test
    public void testSmallCandidateEndpointBenchmarkIsMeasuredButNotProduction()
        throws InterruptedException, MpcAbortException {
        BaSsuIbltCandidateEndpointBenchmark.Result result = BaSsuIbltCandidateEndpointBenchmark.run(
            new BaSsuIbltCandidateEndpointBenchmark.Config()
                .setSenderSize(8)
                .setReceiverSize(6)
                .setOverlap(2)
                .setAlpha(5.0)
                .setSeed(20260606L)
        );
        Assert.assertEquals(
            BaSsuIbltCandidateEndpointBenchmark.BENCHMARK_KIND_MEASURED_CANDIDATE_ENDPOINT,
            result.getBenchmarkKind()
        );
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            result.getProductionReadinessReason()
        );
        Assert.assertEquals(12, result.getUnionSize());
        Assert.assertTrue(result.getOfflineTimeNanos() > 0);
        Assert.assertTrue(result.getOnlineTimeNanos() > 0);
        Assert.assertTrue(result.getOfflineTotalBytes() > 0);
        Assert.assertTrue(result.getOnlineTotalBytes() > 0);
        Assert.assertTrue(result.getTotalBytes() > result.getOfflineTotalBytes());
        Assert.assertTrue(result.getPhaseBarrierTimeNanos() >= 0);
        Assert.assertTrue(result.getPhaseBarrierBytes() >= 0);
        Assert.assertTrue(result.getProbeCount() > 0);
        Assert.assertTrue(result.getRetryStatus().startsWith("success@retry="));
    }

    @Test
    public void testCandidateEndpointBenchmarkDisplayCannotBeMistakenForProduction()
        throws InterruptedException, MpcAbortException {
        BaSsuIbltCandidateEndpointBenchmark.Result result = BaSsuIbltCandidateEndpointBenchmark.run(
            new BaSsuIbltCandidateEndpointBenchmark.Config()
                .setSenderSize(5)
                .setReceiverSize(9)
                .setOverlap(2)
                .setAlpha(5.0)
                .setSeed(20260607L)
        );
        String display = result.toDisplayString();
        Assert.assertTrue(display.contains("benchmarkKind=MEASURED_CANDIDATE_ENDPOINT"));
        Assert.assertTrue(display.contains("measuredProduction=false"));
        Assert.assertTrue(display.contains("productionReady=false"));
        Assert.assertTrue(display.contains("securityNotice=" + BaSsuIbltCandidateEndpointBenchmark.SECURITY_NOTICE_ID));
        Assert.assertTrue(display.contains("offlineTimeMs="));
        Assert.assertTrue(display.contains("onlineTimeMs="));
        Assert.assertTrue(display.contains("offlineTotalBytes="));
        Assert.assertTrue(display.contains("onlineTotalBytes="));
        Assert.assertTrue(display.contains("rpcOfflineBytes="));
        Assert.assertTrue(display.contains("rpcOnlineBytes="));
        Assert.assertTrue(display.contains("phaseBarrierExcluded=true"));
        Assert.assertTrue(display.contains("phaseBarrierBytes="));
        Assert.assertTrue(display.contains("rawCommand=BaSsuIbltCandidateEndpointBenchmark"));
        Assert.assertFalse(display.contains(BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION));
        Assert.assertFalse(display.contains("measuredProduction=true"));
        Assert.assertFalse(display.contains("productionReady=true"));
        Assert.assertFalse(display.contains("securityNotice=NONE"));
    }

    @Test
    public void testCandidateEndpointBenchmarkCannotBecomeProductionRunnerByAccident() throws Exception {
        Assert.assertTrue(Modifier.isFinal(BaSsuIbltCandidateEndpointBenchmark.Result.class.getModifiers()));
        String source = Files.readString(sourcePath("BaSsuIbltCandidateEndpointBenchmark.java"));
        Assert.assertTrue(source.contains("protocolConfig.isProductionReady()"));
        Assert.assertTrue(source.contains("candidate endpoint benchmark refuses productionReady=true"));
        Assert.assertFalse(source.contains(BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION));
        Assert.assertFalse(source.contains("finalAuditPassed()"));
        Assert.assertFalse(source.contains("setProductionReadinessCertificate("));
    }

    @Test
    public void testPowerOfTwoArgs() {
        BaSsuIbltCandidateEndpointBenchmark.Config config =
            BaSsuIbltCandidateEndpointBenchmark.Config.fromArgs(new String[]{
                "m=2^5", "n=2^5", "overlap=4", "elementBytes=8", "alpha=5.0"
        });
        Assert.assertNotNull(config);
    }

    private static Path sourcePath(String fileName) {
        Path modulePath = Path.of(
            "src/main/java/edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt", fileName
        );
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of(
            "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt", fileName
        );
    }
}
