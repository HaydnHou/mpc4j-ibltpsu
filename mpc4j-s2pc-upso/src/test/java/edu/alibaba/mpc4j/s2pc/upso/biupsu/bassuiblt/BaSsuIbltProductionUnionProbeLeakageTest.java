package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * Production UP-BA-UPOT leakage regression tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeLeakageTest {

    @Test
    public void testPublicTypesHaveNoForbiddenAccessors() {
        assertNoForbiddenPublicAccessor(BaSsuIbltProductionUnionProbeOutput.class);
        assertNoForbiddenPublicAccessor(BaSsuIbltProductionUnionProbeCapsule.class);
        assertNoForbiddenPublicAccessor(BaSsuIbltUnionProbeOutput.class);
        assertNoForbiddenPublicAccessor(BaSsuIbltUnionProbeCapsule.class);
    }

    @Test
    public void testProductionConfigRejectsBadDimensions() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setElementByteLength(0).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setTagByteLength(0).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setCheckByteLength(0).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setAuthTagByteLength(0).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setCotNumPerProbe(2).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setOnlineBatchSize(0).build());
    }

    @Test
    public void testLocalDecodeCandidateIsFailClosed() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        Assert.assertTrue(config.isSpecializedBucketProbe());
        Assert.assertTrue(config.isLocalRemoteDecodeFree());
        Assert.assertTrue(config.hasFixedShapeCapsules());
        Assert.assertTrue(config.opensOnlySourceAgnosticOutput());
        Assert.assertTrue(config.hasRemoteStateHidingEvaluator());
        Assert.assertFalse(config.isQueuePeelProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            config.getProductionReadinessReason()
        );
        Assert.assertFalse(config.getUnionProbeBackendName().toLowerCase().contains("production-ready"));
    }

    @Test
    public void testOptInStillFailClosedUntilHardProductionGatesPass() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setAcceptAdaptiveQueueTranscriptLeakage(true)
                .build();
        Assert.assertTrue(config.isLocalRemoteDecodeFree());
        Assert.assertTrue(config.hasQueuePeelEndpointIntegration());
        Assert.assertTrue(config.opensOnlySourceAgnosticOutput());
        Assert.assertFalse(config.isQueuePeelProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            config.getProductionReadinessReason()
        );
    }

    @Test
    public void testCodecDoesNotExposePublicDecodeApi() {
        for (Method method : BaSsuIbltProductionUnionProbeCodec.class.getMethods()) {
            Assert.assertFalse(method.getName().toLowerCase().contains("decode"));
        }
    }

    @Test
    public void testMainCodecHasNoLocalRemoteOpenBackdoor() {
        for (Method method : BaSsuIbltProductionUnionProbeCodec.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("decode"));
            Assert.assertFalse(name.contains("open"));
            Assert.assertFalse(name.contains("parseplaintext"));
        }
        for (Class<?> declaredClass : BaSsuIbltProductionUnionProbeCodec.class.getDeclaredClasses()) {
            Assert.assertFalse(declaredClass.getSimpleName().toLowerCase().contains("decoded"));
        }
    }

    @Test
    public void testPtoDescContainsOnlyProbeLevelSteps() {
        for (BaSsuIbltProductionUnionProbePtoDesc.PtoStep step
            : BaSsuIbltProductionUnionProbePtoDesc.PtoStep.values()) {
            String name = step.name().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
            Assert.assertFalse(name.contains("member"));
            Assert.assertFalse(name.contains("peqt"));
            Assert.assertFalse(name.contains("okvs"));
            Assert.assertFalse(name.contains("pir"));
        }
        Assert.assertEquals("BA_SSU_IBLT_PRODUCTION_UP_BA_UPOT",
            BaSsuIbltProductionUnionProbePtoDesc.getInstance().getPtoName());
    }

    private static void assertNoForbiddenPublicAccessor(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
            Assert.assertFalse(name.contains("raw"));
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("check"));
            Assert.assertFalse(name.contains("choice"));
            Assert.assertFalse(name.contains("member"));
        }
    }
}
