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
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setOnlineBatchSize(0).build());
    }

    @Test
    public void testLocalDecodeCandidateIsFailClosed() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        Assert.assertTrue(config.isSpecializedBucketProbe());
        Assert.assertFalse(config.isQueuePeelProductionReady());
        Assert.assertTrue(config.getProductionReadinessReason().contains("local capsule decoding"));
        Assert.assertFalse(config.getUnionProbeBackendName().toLowerCase().contains("production-ready"));
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
        Assert.assertEquals("BA_SSU_IBLT_PRODUCTION_UNION_PROBE",
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
