package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * P37 production queue-peel execution gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltSecureProtocolProductionQueuePeelExecutionTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * public capacity.
     */
    private static final int PUBLIC_CAPACITY = 8;

    @Test
    public void testProductionAdapterRejectsCurrentEndpointBeforeInputUse() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProductionQueuePeelAdapter.run(
                null, null, Long.BYTES, null, null, null, null, null, null, null, backend
        ));
        Assert.assertTrue(abort.getMessage().contains("production-ready backend"));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
        Assert.assertTrue(abort.getMessage().contains(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON
        ));
    }

    @Test
    public void testSecureCoreUsesProductionAdapterGate() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureProtocol.runQueuePeelAligned(
                null, null, Long.BYTES, null, null, null, null, null, null, null, backend
            ));
        Assert.assertTrue(abort.getMessage().contains("production-ready backend"));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
    }

    @Test
    public void testLocalBridgeCoreRunsPartialIntersection() {
        Set<ByteBuffer> leftSet = setOf(1L, 2L, 3L, 4L, 5L);
        Set<ByteBuffer> rightSet = setOf(4L, 5L, 6L, 7L);
        BaSsuIbltSecureProtocolResult result = runProduction(leftSet, rightSet, params());
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
        Assert.assertEquals(7, result.getPeeledElementCount());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED, result.getShape());
        Assert.assertTrue(result.getActualProbeCount() <= result.getScheduledBucketCount());
        BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(result.getRetryStatuses());
    }

    @Test
    public void testLocalBridgeCoreRunsNoIntersectionAndZeroElement() {
        Set<ByteBuffer> leftSet = setOf(0L, 1L, 2L);
        Set<ByteBuffer> rightSet = setOf(9L, 10L, 11L);
        BaSsuIbltSecureProtocolResult result = runProduction(leftSet, rightSet, params());
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
        Assert.assertEquals(6, result.getPeeledElementCount());
    }

    @Test
    public void testLocalBridgeCoreRunsFullIntersectionAsSourceAgnosticSingletons() {
        Set<ByteBuffer> leftSet = setOf(21L, 22L, 23L, 24L);
        Set<ByteBuffer> rightSet = setOf(21L, 22L, 23L, 24L);
        BaSsuIbltSecureProtocolResult result = runProduction(leftSet, rightSet, params());
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
        Assert.assertEquals(4, result.getPeeledElementCount());
    }

    @Test
    public void testLocalBridgeCoreFailureDoesNotReturnPartialProgress() {
        Set<ByteBuffer> leftSet = setOf(41L, 42L);
        Set<ByteBuffer> rightSet = setOf(43L, 44L);
        BaSsuIbltBiUpsuParams failingParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setRetryCount(2)
            .setDegree(3)
            .setTableLength(1)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltSecureProtocolResult result = runProduction(leftSet, rightSet, failingParams);
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(0, result.getPeeledElementCount());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
        Assert.assertEquals(failingParams.getRetryCount(), result.getRetryStatuses().size());
        for (BaSsuIbltQueuePeelRetryStatus retryStatus : result.getRetryStatuses()) {
            Assert.assertTrue(retryStatus.isExecuted());
            Assert.assertFalse(retryStatus.isSuccess());
        }
    }

    @Test
    public void testAdaptiveTranscriptOptInAndEndpointIntegrationStillFailClosedOnHardGates() {
        BaSsuIbltProductionUnionProbeBackendConfig backend = bridgeBackend(params());
        Assert.assertTrue(backend.hasAcceptedAdaptiveQueueTranscriptLeakage());
        Assert.assertTrue(backend.hasQueuePeelEndpointIntegration());
        Assert.assertFalse(backend.isQueuePeelProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            backend.getProductionReadinessReason()
        );
    }

    private static BaSsuIbltSecureProtocolResult runProduction(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                               BaSsuIbltBiUpsuParams params) {
        FixedInput leftInput = fixedInput(leftSet, 10_000L);
        FixedInput rightInput = fixedInput(rightSet, 20_000L);
        BaSsuIbltProductionUnionProbeBackendConfig backend = bridgeBackend(params);
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionQueuePeelAdapter.offlineSchedule(
            params, ELEMENT_BYTE_LENGTH, backend
        );
        return BaSsuIbltSecureProtocol.runQueuePeelAlignedLocalBridgeCore(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params,
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs, params),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs, params),
            backend, schedule
        );
    }

    private static BaSsuIbltProductionUnionProbeBackendConfig bridgeBackend(BaSsuIbltBiUpsuParams params) {
        BaSsuIbltProductionUnionProbeBackendConfig backend = new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setTagByteLength(BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()))
            .setCheckByteLength(BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits()))
            .setAcceptAdaptiveQueueTranscriptLeakage(true)
            .build();
        Assert.assertTrue(backend.hasAcceptedAdaptiveQueueTranscriptLeakage());
        Assert.assertFalse(backend.isQueuePeelProductionReady());
        return backend;
    }

    private static BaSsuIbltOprfTagOutput localTags(byte[][] inputs, BaSsuIbltBiUpsuParams params) {
        byte[][] tags = new byte[inputs.length][];
        byte[][] checks = new byte[inputs.length][];
        int tagByteLength = BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits());
        int checkByteLength = BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits());
        for (int index = 0; index < inputs.length; index++) {
            tags[index] = BaSsuIbltOprfTagPipeline.tagFromPrf(inputs[index], tagByteLength);
            checks[index] = BaSsuIbltOprfTagPipeline.checkFromTag(tags[index], checkByteLength);
        }
        return new BaSsuIbltOprfTagOutput(tagByteLength, checkByteLength, tags, checks);
    }

    private static FixedInput fixedInput(Set<ByteBuffer> set, long dummyOffset) {
        byte[][] fixedInputs = new byte[PUBLIC_CAPACITY][];
        boolean[] activeFlags = new boolean[PUBLIC_CAPACITY];
        int index = 0;
        for (ByteBuffer element : set) {
            ByteBuffer duplicate = element.asReadOnlyBuffer();
            duplicate.rewind();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            fixedInputs[index] = bytes;
            activeFlags[index] = true;
            index++;
        }
        while (index < PUBLIC_CAPACITY) {
            fixedInputs[index] = element(dummyOffset + index);
            index++;
        }
        return new FixedInput(fixedInputs, activeFlags);
    }

    private static BaSsuIbltBiUpsuParams params() {
        return new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
    }

    private static Set<ByteBuffer> setOf(long... values) {
        Set<ByteBuffer> set = new HashSet<>();
        for (long value : values) {
            set.add(ByteBuffer.wrap(element(value)));
        }
        return set;
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }

    /**
     * fixed input.
     */
    private static class FixedInput {
        /**
         * fixed inputs.
         */
        private final byte[][] fixedInputs;
        /**
         * active flags.
         */
        private final boolean[] activeFlags;

        FixedInput(byte[][] fixedInputs, boolean[] activeFlags) {
            this.fixedInputs = fixedInputs;
            this.activeFlags = activeFlags;
        }
    }
}
