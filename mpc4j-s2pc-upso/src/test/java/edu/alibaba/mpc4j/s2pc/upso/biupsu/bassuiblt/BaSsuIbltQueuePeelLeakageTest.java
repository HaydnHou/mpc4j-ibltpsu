package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuSender;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT queue-peel leakage boundary tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltQueuePeelLeakageTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testQueueProbePublicTypesExposeOnlyAllowedFields() {
        assertNoForbiddenAccessor(BaSsuIbltUnionProbeOutput.class);
        assertNoForbiddenAccessor(BaSsuIbltUnionProbeCapsule.class);
        assertNoForbiddenAccessor(BaSsuIbltUnionProbeBatch.class);
        assertNoForbiddenAccessor(BaSsuIbltQueuePeelRetryStatus.class);
        Assert.assertNotNull(findMethod(BaSsuIbltUnionProbeOutput.class, "getBucketIndex"));
        Assert.assertNotNull(findMethod(BaSsuIbltUnionProbeOutput.class, "isSingleton"));
        Assert.assertNotNull(findMethod(BaSsuIbltUnionProbeOutput.class, "getElement"));
        Assert.assertNotNull(findMethod(BaSsuIbltQueuePeelRetryStatus.class, "isSuccess"));
        Assert.assertNotNull(findMethod(BaSsuIbltQueuePeelRetryStatus.class, "getProbeCount"));
    }

    @Test
    public void testQueuePeelSecureEndpointRejectsFixedLayerReferenceFlag() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(new FakeProductionUnionProbeBackendConfig())
            .setEnableFixedLayerReferenceEndpoint(true));
    }

    @Test
    public void testQueuePeelSecureConfigRejectsUntrustedFakeBeforeEndpoint() {
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .setUnionProbeBackendConfig(new FakeProductionUnionProbeBackendConfig())
                .build());
        Assert.assertTrue(abort.getMessage().contains("trusted production union-probe backend type"));
    }

    @Test
    public void testQueuePeelPtoStepHasNoDenseTransferStep() {
        for (BaSsuIbltBiUpsuPtoDesc.PtoStep step : BaSsuIbltBiUpsuPtoDesc.PtoStep.values()) {
            String name = step.name();
            Assert.assertFalse(name.contains("DENSE"));
            Assert.assertFalse(name.contains("PISF"));
            Assert.assertFalse(name.contains("PIS_IBLT"));
            Assert.assertFalse(name.contains("MEMBERSHIP"));
        }
    }

    @Test
    public void testRetryTranscriptRejectsSkippedPrefixBeforeSuccess() {
        BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(List.of(
            BaSsuIbltQueuePeelRetryStatus.executed(0, false, 4L, 10L),
            BaSsuIbltQueuePeelRetryStatus.executed(1, true, 7L, 10L),
            BaSsuIbltQueuePeelRetryStatus.notRunAfterSuccess(2, 10L)
        ));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(List.of(
                BaSsuIbltQueuePeelRetryStatus.notRunAfterSuccess(0, 10L),
                BaSsuIbltQueuePeelRetryStatus.executed(1, true, 7L, 10L)
            )));
    }

    @Test
    public void testRawTagAndCaseLabeledBridgeSurfacesArePackagePrivate() {
        assertPackagePrivate(BaSsuIbltSecureProtocol.class);
        assertPackagePrivate(BaSsuIbltOprfTagOutput.class);
        assertPackagePrivate(BaSsuIbltOprfTagSender.class);
        assertPackagePrivate(BaSsuIbltOprfTagReceiver.class);
        assertPackagePrivate(BaSsuIbltSecureCellView.class);
        assertPackagePrivate(BaSsuIbltSecureBucketInput.class);
        assertPackagePrivate(BaSsuIbltSecureLayerBuilder.class);
        assertPackagePrivate(BaSsuIbltBucketTrace.class);
        assertPackagePrivate(BaSsuIbltBucketTranscript.class);
        assertPackagePrivate(BaSsuIbltRetryTranscript.class);
        assertPackagePrivate(BaSsuIbltTranscriptResult.class);
        assertPackagePrivate(BaUpotBucketInput.class);
        assertPackagePrivate(BaUpotBucketEvaluator.class);
        assertPackagePrivate(BaUpotBucketOutput.class);
        assertPackagePrivate(BaUpotIdeal.class);
        assertPackagePrivate(BaUpotIdealEvaluator.class);
        assertPackagePrivate(BaUpotCaseGateEvaluator.class);
        assertPackagePrivate(BaUpotCaseGateCircuit.Evaluation.class);
        assertPackagePrivate(BaUpotPlainOutputCapsuleCodec.class);
        assertPackagePrivate(BaUpotWireMaskedOutputCapsuleCodec.class);
        assertPackagePrivate(BaUpotPlainPayloadTwoPartyBenchmark.class);
        assertPackagePrivate(BaUpotWireMaskedPayloadTwoPartyBenchmark.class);
        assertPackagePrivate(BaUpotPlainPayloadSender.class);
        assertPackagePrivate(BaUpotPlainPayloadReceiver.class);
        assertPackagePrivate(BaUpotPlainPayloadSenderThread.class);
        assertPackagePrivate(BaUpotPlainPayloadReceiverThread.class);
        assertPackagePrivate(BaUpotWireMaskedPayloadSender.class);
        assertPackagePrivate(BaUpotWireMaskedPayloadReceiver.class);
        assertPackagePrivate(BaUpotWireMaskedPayloadSenderThread.class);
        assertPackagePrivate(BaUpotWireMaskedPayloadReceiverThread.class);
        assertPackagePrivate(BaUpotPlainPayloadTransducer.class);
        assertPackagePrivate(BaUpotWireMaskedPayloadTransducer.class);
        assertPackagePrivate(BaUpotCaseGateWireMaskedPayloadTransducer.class);
        assertPackagePrivate(BaUnionPeelOtSender.class);
        assertPackagePrivate(BaUnionPeelOtReceiver.class);
        assertPackagePrivate(BaUnionPeelOtSenderOutput.class);
        assertPackagePrivate(BaUnionPeelOtReceiverOutput.class);
        assertPackagePrivate(BaUnionPeelOtResult.class);
        assertPackagePrivate(BaUnionPeelOtTwoPartyBridge.class);
        assertPackagePrivateMethod(BaSsuIbltPlainProtocol.class, "runBiOutputWithTrace",
            Set.class, Set.class, int.class, BaSsuIbltBiUpsuParams.class);
        assertPackagePrivateMethod(BaSsuIbltPlainResult.class, "getBucketTrace");
        assertPackagePrivateMethod(BaUpotCaseGateCircuit.class, "evaluate", BaUpotBucketInput.class);
    }

    private static void assertNoForbiddenAccessor(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
            Assert.assertFalse(name.contains("membership"));
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("check"));
            Assert.assertFalse(name.contains("oprf"));
            Assert.assertFalse(name.contains("choice"));
        }
    }

    private static void assertPackagePrivate(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        Assert.assertFalse(Modifier.isPublic(modifiers));
        Assert.assertFalse(Modifier.isProtected(modifiers));
        Assert.assertFalse(Modifier.isPrivate(modifiers));
    }

    private static void assertPackagePrivateMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            int modifiers = clazz.getDeclaredMethod(methodName, parameterTypes).getModifiers();
            Assert.assertFalse(Modifier.isPublic(modifiers));
            Assert.assertFalse(Modifier.isProtected(modifiers));
            Assert.assertFalse(Modifier.isPrivate(modifiers));
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static ByteBuffer element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return ByteBuffer.wrap(byteBuffer.array());
    }

    /**
     * fake production union-probe backend used only to keep secure config construction past validation.
     */
    private static class FakeProductionUnionProbeBackendConfig extends AbstractMultiPartyPtoConfig
        implements BaSsuIbltUnionProbeBackendConfig {

        FakeProductionUnionProbeBackendConfig() {
            super(SecurityModel.SEMI_HONEST);
        }

        @Override
        public String getUnionProbeBackendName() {
            return "fake production union-probe backend";
        }

        @Override
        public boolean isSpecializedBucketProbe() {
            return true;
        }

        @Override
        public boolean isQueuePeelProductionReady() {
            return true;
        }
    }
}
