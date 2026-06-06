package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P48 final production-claim gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltFinalClaimGateTest {

    @Test
    public void testCurrentBenchmarkSurfacesCannotClaimProduction() throws InterruptedException {
        BaSsuIbltQueuePeelBenchmark.Result result =
            BaSsuIbltProductionBenchmarkOutputTest.sampleQueuePeelResult();
        String display = result.toDisplayString();
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.BENCHMARK_KIND_ESTIMATE, result.getBenchmarkKind());
        Assert.assertFalse(result.isMeasuredProduction());
        Assert.assertFalse(result.isProductionReady());
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.SECURITY_NOTICE, result.getSecurityNotice());
        Assert.assertTrue(result.getSecurityNotice().contains("RPC/Core-COT candidate endpoint"));
        Assert.assertTrue(result.getSecurityNotice().contains("not production-certified"));
        Assert.assertTrue(result.getSecurityNotice().contains("measured-production wired"));
        Assert.assertEquals(BaSsuIbltSecureFairBenchmark.H5_BASELINE_NAME, result.getBaselineName());
        Assert.assertTrue(display.contains("benchmarkKind=ESTIMATE"));
        Assert.assertTrue(display.contains("measuredProduction=false"));
        Assert.assertTrue(display.contains("productionReady=false"));
        Assert.assertTrue(display.contains("baselineName=H5_IBLT_PSU_BUCKET_PROBE_ESTIMATE"));
        assertNoProductionClaim(display);
    }

    @Test
    public void testMeasuredProductionGateRejectsEveryCurrentEndpointMode() {
        BaSsuIbltBiUpsuConfig defaultConfig = new BaSsuIbltBiUpsuConfig.Builder().build();
        assertMeasuredProductionRejected(defaultConfig, BaSsuIbltBiUpsuConfig.COSTED_BENCHMARK_NOT_READY_REASON);

        BaSsuIbltBiUpsuConfig referenceConfig = new BaSsuIbltBiUpsuConfig.Builder()
            .setEnableFixedLayerReferenceEndpoint(true)
            .build();
        assertMeasuredProductionRejected(referenceConfig, BaSsuIbltBiUpsuConfig.REFERENCE_ENDPOINT_NOT_READY_REASON);

        BaSsuIbltBiUpsuConfig materialReadyConfig =
            BaSsuIbltBiUpsuEndpointProductionTest.secureFixedLoopConfigWithFakeM14b();
        assertMeasuredProductionRejected(materialReadyConfig, "endpoint adapter remains fail-closed");
    }

    @Test
    public void testQueuePeelCandidateBackendCannotOpenProductionPath() {
        BaSsuIbltProductionUnionProbeBackendConfig backendConfig =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        Assert.assertTrue(backendConfig.isSpecializedBucketProbe());
        Assert.assertTrue(backendConfig.isLocalRemoteDecodeFree());
        Assert.assertTrue(backendConfig.hasFixedShapeCapsules());
        Assert.assertTrue(backendConfig.opensOnlySourceAgnosticOutput());
        Assert.assertTrue(backendConfig.hasRemoteStateHidingEvaluator());
        Assert.assertFalse(backendConfig.hasAcceptedAdaptiveQueueTranscriptLeakage());
        Assert.assertFalse(backendConfig.hasProductionAuditPassed());
        Assert.assertFalse(backendConfig.hasNoReferenceFallbackCertificate());
        Assert.assertFalse(backendConfig.hasMeasuredEndpointWired());
        Assert.assertFalse(backendConfig.isQueuePeelProductionReady());

        BaSsuIbltProductionUnionProbeBackendConfig adaptiveBackendConfig =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setAcceptAdaptiveQueueTranscriptLeakage(true)
                .build();
        Assert.assertTrue(adaptiveBackendConfig.hasAcceptedAdaptiveQueueTranscriptLeakage());
        Assert.assertFalse(adaptiveBackendConfig.hasProductionAuditPassed());
        Assert.assertFalse(adaptiveBackendConfig.hasNoReferenceFallbackCertificate());
        Assert.assertFalse(adaptiveBackendConfig.hasMeasuredEndpointWired());
        Assert.assertFalse(adaptiveBackendConfig.isQueuePeelProductionReady());

        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(backendConfig)
            .build();
        Assert.assertFalse(config.isProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            config.getProductionReadinessReason()
        );
    }

    @Test
    public void testAdaptiveTranscriptAcceptanceDoesNotBypassHardProductionCertificates() {
        BaSsuIbltProductionUnionProbeBackendConfig adaptiveBackendConfig =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setAcceptAdaptiveQueueTranscriptLeakage(true)
                .build();
        Assert.assertTrue(adaptiveBackendConfig.hasAcceptedAdaptiveQueueTranscriptLeakage());
        Assert.assertTrue(adaptiveBackendConfig.hasRemoteStateHidingEvaluator());
        Assert.assertTrue(adaptiveBackendConfig.hasQueuePeelEndpointIntegration());
        Assert.assertFalse(adaptiveBackendConfig.hasProductionAuditPassed());
        Assert.assertFalse(adaptiveBackendConfig.hasNoReferenceFallbackCertificate());
        Assert.assertFalse(adaptiveBackendConfig.hasMeasuredEndpointWired());
        Assert.assertFalse(adaptiveBackendConfig.isQueuePeelProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            adaptiveBackendConfig.getProductionReadinessReason()
        );

        BaSsuIbltBiUpsuConfig adaptiveConfig = new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(adaptiveBackendConfig)
            .build();
        Assert.assertFalse(adaptiveConfig.isProductionReady());
        MpcAbortException abort = Assert.assertThrows(
            MpcAbortException.class, () -> BaSsuIbltBiUpsuProductionGate.checkEndpointReady(adaptiveConfig)
        );
        Assert.assertTrue(abort.getMessage().contains(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON
        ));
    }

    @Test
    public void testProductionHardGatesAreNotPublicBuilderBooleans() {
        for (Method method : BaSsuIbltProductionUnionProbeBackendConfig.Builder.class.getMethods()) {
            Assert.assertNotEquals("setProductionAuditPassed", method.getName());
            Assert.assertNotEquals("setNoReferenceFallbackCertificate", method.getName());
            Assert.assertNotEquals("setMeasuredEndpointWired", method.getName());
            Assert.assertNotEquals("setProductionReadinessCertificate", method.getName());
        }
        for (Method method : BaSsuIbltProductionUnionProbeBackendConfig.Builder.class.getDeclaredMethods()) {
            if ("setProductionReadinessCertificate".equals(method.getName())) {
                Assert.assertFalse(Modifier.isPublic(method.getModifiers()));
            }
        }
        for (Constructor<?> constructor : BaSsuIbltProductionReadinessCertificate.class.getDeclaredConstructors()) {
            Assert.assertFalse(Modifier.isPublic(constructor.getModifiers()));
        }
        for (Method method : BaSsuIbltProductionReadinessCertificate.class.getDeclaredMethods()) {
            if ("finalAuditPassed".equals(method.getName())) {
                Assert.assertFalse(Modifier.isPublic(method.getModifiers()));
            }
        }
    }

    @Test
    public void testPartialInternalCertificateCannotOpenProductionGate() throws Exception {
        assertInternalCertificateRejected(
            internalCertificate(true, false, false),
            true,
            BaSsuIbltProductionUnionProbeBackendConfig.NO_REFERENCE_FALLBACK_NOT_READY_REASON
        );
        assertInternalCertificateRejected(
            internalCertificate(true, true, false),
            true,
            BaSsuIbltProductionUnionProbeBackendConfig.MEASURED_ENDPOINT_NOT_READY_REASON
        );
        assertInternalCertificateRejected(
            internalCertificate(true, true, true),
            false,
            BaSsuIbltProductionUnionProbeBackendConfig.NOT_PRODUCTION_READY_REASON
        );
    }

    @Test
    public void testMeasuredProductionLabelIsReservedOnlyForMeasuredRunner() {
        Assert.assertNotEquals(
            BaSsuIbltSecureFairBenchmark.BENCHMARK_KIND_ESTIMATE,
            BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION
        );
        Assert.assertTrue(BaSsuIbltMeasuredProductionBenchmark.UNAVAILABLE_NOTICE.contains(
            BaSsuIbltMeasuredProductionBenchmark.BENCHMARK_KIND_MEASURED_PRODUCTION
        ));
        Assert.assertTrue(BaSsuIbltMeasuredProductionBenchmark.notReadyMessage(
            new BaSsuIbltBiUpsuConfig.Builder().build()
        ).contains("productionReady=false"));
    }

    @Test
    public void testMeasuredProductionRunnerCannotDelegateToCandidateEndpoint() throws Exception {
        String source = Files.readString(sourcePath("BaSsuIbltMeasuredProductionBenchmark.java"));
        Assert.assertTrue(source.contains("checkProtocolConfig(protocolConfig);"));
        Assert.assertTrue(source.contains("BaSsuIbltQueuePeelEndpoint.runProductionEndpoint("));
        Assert.assertTrue(source.contains("BaSsuIbltProductionReadinessCertificate.finalAuditPassed()"));
        Assert.assertFalse(source.contains("BaSsuIbltCandidateEndpointBenchmark"));
        Assert.assertFalse(source.contains("runCandidateEndpoint("));
        Assert.assertFalse(source.contains("BENCHMARK_KIND_MEASURED_CANDIDATE_ENDPOINT"));
    }

    private static void assertMeasuredProductionRejected(BaSsuIbltBiUpsuConfig config, String reasonFragment) {
        Assert.assertFalse(config.isProductionReady());
        IllegalStateException abort = Assert.assertThrows(
            IllegalStateException.class, () -> BaSsuIbltMeasuredProductionBenchmark.run(config)
        );
        Assert.assertTrue(abort.getMessage().contains(BaSsuIbltMeasuredProductionBenchmark.UNAVAILABLE_NOTICE_ID));
        Assert.assertTrue(abort.getMessage().contains("productionReady=false"));
        Assert.assertTrue(abort.getMessage().contains(reasonFragment));
        assertNoProductionClaim(abort.getMessage());
    }

    private static void assertNoProductionClaim(String text) {
        Assert.assertFalse(text.contains("measuredProduction=true"));
        Assert.assertFalse(text.contains("productionReady=true"));
        Assert.assertFalse(text.contains("securityNotice=NONE"));
        Assert.assertFalse(text.contains("speedup="));
    }

    private static void assertInternalCertificateRejected(
        BaSsuIbltProductionReadinessCertificate certificate, boolean acceptAdaptiveTranscript, String reason)
        throws MpcAbortException {
        BaSsuIbltProductionUnionProbeBackendConfig backendConfig =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setAcceptAdaptiveQueueTranscriptLeakage(acceptAdaptiveTranscript)
                .setProductionReadinessCertificate(certificate)
                .build();
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(backendConfig)
            .build();
        Assert.assertFalse(backendConfig.isQueuePeelProductionReady());
        Assert.assertFalse(config.isProductionReady());
        Assert.assertEquals(reason, config.getProductionReadinessReason());
        MpcAbortException abort = Assert.assertThrows(
            MpcAbortException.class, () -> BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config)
        );
        Assert.assertTrue(abort.getMessage().contains(reason));
    }

    private static BaSsuIbltProductionReadinessCertificate internalCertificate(
        boolean productionAuditPassed, boolean noReferenceFallbackCertificate, boolean measuredEndpointWired)
        throws Exception {
        Constructor<BaSsuIbltProductionReadinessCertificate> constructor =
            BaSsuIbltProductionReadinessCertificate.class.getDeclaredConstructor(
                boolean.class, boolean.class, boolean.class
            );
        constructor.setAccessible(true);
        return constructor.newInstance(productionAuditPassed, noReferenceFallbackCertificate, measuredEndpointWired);
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
