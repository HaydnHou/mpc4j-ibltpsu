package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuSender;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT bi-output UPSU endpoint tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltBiUpsuEndpointTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testDefaultConfig() {
        BaSsuIbltBiUpsuConfig config = (BaSsuIbltBiUpsuConfig) BiUpsuFactory.createDefaultConfig(
            BiUpsuFactory.BiUpsuType.BA_SSU_IBLT
        );
        Assert.assertEquals(BiUpsuFactory.BiUpsuType.BA_SSU_IBLT, config.getPtoType());
        Assert.assertEquals(BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT, config.getDeliveryMode());
        Assert.assertEquals(BaSsuIbltProtocolMode.COSTED_BENCHMARK, config.getProtocolMode());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A, config.getScheduleShape());
        Assert.assertNull(config.getOprfConfig());
        Assert.assertNull(config.getSecureBaUpotConfig());
        Assert.assertNull(config.getUnionProbeBackendConfig());
        Assert.assertFalse(config.isProductionReady());
        Assert.assertFalse(BaSsuIbltBiUpsuFactory.isProductionReady(config));
        Assert.assertEquals(
            BaSsuIbltBiUpsuConfig.COSTED_BENCHMARK_NOT_READY_REASON,
            BaSsuIbltBiUpsuFactory.productionReadinessReason(config)
        );
        BaSsuIbltBiUpsuParams params = config.createParams(1 << 10, 1 << 18);
        Assert.assertEquals(1 << 18, params.getNLarge());
        Assert.assertEquals(1 << 10, params.getNShadow());
        Assert.assertEquals(3, params.getDegree());
        Assert.assertEquals(1, params.getRetryCount());
        Assert.assertFalse(config.isEnableFixedLayerReferenceEndpoint());
    }

    @Test
    public void testQueuePeelPtoStepOrder() {
        Assert.assertEquals(0, BaSsuIbltBiUpsuPtoDesc.PtoStep.INIT_OPRF_TAGS.ordinal());
        Assert.assertEquals(1, BaSsuIbltBiUpsuPtoDesc.PtoStep.QUEUE_PROBE.ordinal());
        Assert.assertEquals(2, BaSsuIbltBiUpsuPtoDesc.PtoStep.RETRY_STATUS.ordinal());
        Assert.assertEquals(3, BaSsuIbltBiUpsuPtoDesc.PtoStep.FINAL_UNION.ordinal());
        Assert.assertTrue(
            BaSsuIbltBiUpsuPtoDesc.PtoStep.SENDER_SEND_FIXED_LAYER.ordinal()
                > BaSsuIbltBiUpsuPtoDesc.PtoStep.FINAL_UNION.ordinal()
        );
        Assert.assertTrue(
            BaSsuIbltBiUpsuPtoDesc.PtoStep.RECEIVER_SEND_FIXED_LAYER.ordinal()
                > BaSsuIbltBiUpsuPtoDesc.PtoStep.FINAL_UNION.ordinal()
        );
    }

    @Test
    public void testQueuePeelRetryStatusTranscript() {
        BaSsuIbltQueuePeelRetryStatus success = BaSsuIbltQueuePeelRetryStatus.executed(1, true, 7L, 10L);
        Assert.assertEquals(1, success.getRetryIndex());
        Assert.assertTrue(success.isExecuted());
        Assert.assertTrue(success.isSuccess());
        Assert.assertEquals(7L, success.getProbeCount());
        Assert.assertEquals(10L, success.getMaxProbeCount());
        BaSsuIbltQueuePeelRetryStatus notRun = BaSsuIbltQueuePeelRetryStatus.notRunAfterSuccess(2, 10L);
        Assert.assertFalse(notRun.isExecuted());
        Assert.assertFalse(notRun.isSuccess());
        Assert.assertEquals(0L, notRun.getProbeCount());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltQueuePeelRetryStatus.executed(0, false, 11L, 10L));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltQueuePeelRetryStatus(0, false, true, 0L, 10L));
        BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(List.of(
            BaSsuIbltQueuePeelRetryStatus.executed(0, false, 4L, 10L),
            BaSsuIbltQueuePeelRetryStatus.executed(1, true, 7L, 10L),
            BaSsuIbltQueuePeelRetryStatus.notRunAfterSuccess(2, 10L)
        ));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(List.of(
                BaSsuIbltQueuePeelRetryStatus.executed(1, false, 4L, 10L)
            )));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(List.of(
                BaSsuIbltQueuePeelRetryStatus.executed(0, true, 4L, 10L),
                BaSsuIbltQueuePeelRetryStatus.executed(1, false, 5L, 10L)
            )));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(List.of(
                BaSsuIbltQueuePeelRetryStatus.executed(0, true, 4L, 10L),
                BaSsuIbltQueuePeelRetryStatus.executed(1, true, 5L, 10L)
            )));
        for (Method method : BaSsuIbltQueuePeelRetryStatus.class.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
            Assert.assertFalse(name.contains("membership"));
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("check"));
        }
    }

    @Test
    public void testReferenceModeRequiresExplicitEnableFlag() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.REFERENCE_FIXED_LAYER)
            .build());
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setEnableFixedLayerReferenceEndpoint(true)
            .build();
        Assert.assertEquals(BaSsuIbltProtocolMode.REFERENCE_FIXED_LAYER, config.getProtocolMode());
        Assert.assertTrue(config.isEnableFixedLayerReferenceEndpoint());
        Assert.assertFalse(config.isProductionReady());
        Assert.assertEquals(
            BaSsuIbltBiUpsuConfig.REFERENCE_ENDPOINT_NOT_READY_REASON,
            config.getProductionReadinessReason()
        );
    }

    @Test
    public void testSecureModeRequiresSecureSubConfigs() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .build());
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .build());
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setSecureBaUpotConfig(new BaUpotConfig.Builder().build())
            .build());
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new BaUpotConfig.Builder().build())
            .build());
        BaUpotConfig benchmarkBaUpotConfig = new BaUpotConfig.Builder().build();
        Assert.assertFalse(benchmarkBaUpotConfig.isObliviousBranchSelection());
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(benchmarkBaUpotConfig)
            .build());
        BaUnionPeelOtSecureConfig m14aBaUpotConfig = new BaUnionPeelOtSecureConfig.Builder().build();
        Assert.assertFalse(m14aBaUpotConfig.isObliviousBranchSelection());
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .setSecureBaUpotConfig(m14aBaUpotConfig)
                .build());
        Assert.assertTrue(abort.getMessage().contains("oblivious branch-selection"));
        Assert.assertTrue(abort.getMessage().contains(m14aBaUpotConfig.getBackendName()));
    }

    @Test
    public void testQueuePeelSecureModeRequiresProductionUnionProbeBackend() {
        IllegalArgumentException missing = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .build());
        Assert.assertTrue(missing.getMessage().contains("union-probe BA-UPOT"));

        BaUnionPeelOtSecureConfig m14aConfig = new BaUnionPeelOtSecureConfig.Builder().build();
        Assert.assertFalse(m14aConfig.isSpecializedBucketProbe());
        Assert.assertFalse(m14aConfig.isQueuePeelProductionReady());
        IllegalArgumentException m14aAbort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .setUnionProbeBackendConfig(m14aConfig)
                .build());
        Assert.assertTrue(m14aAbort.getMessage().contains("trusted production union-probe backend type"));
        Assert.assertTrue(m14aAbort.getMessage().contains(m14aConfig.getUnionProbeBackendName()));

        for (BaSsuIbltNonProductionUnionProbeBackendConfig.Route route
            : BaSsuIbltNonProductionUnionProbeBackendConfig.Route.values()) {
            BaSsuIbltNonProductionUnionProbeBackendConfig config =
                new BaSsuIbltNonProductionUnionProbeBackendConfig(route);
            Assert.assertFalse(config.isSpecializedBucketProbe());
            Assert.assertFalse(config.isQueuePeelProductionReady());
            IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
                () -> new BaSsuIbltBiUpsuConfig.Builder()
                    .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                    .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
                    .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                    .setUnionProbeBackendConfig(config)
                    .build());
            Assert.assertTrue(abort.getMessage().contains(config.getUnionProbeBackendName()));
        }
    }

    @Test
    public void testFakeProductionUnionProbeBackendRejectedByConfig() {
        FakeProductionUnionProbeBackendConfig fakeBackend = new FakeProductionUnionProbeBackendConfig();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .setUnionProbeBackendConfig(fakeBackend)
                .build());
        Assert.assertTrue(abort.getMessage().contains("trusted production union-probe backend type"));
    }

    @Test
    public void testQueuePeelCandidateBackendAbortReason() {
        BaSsuIbltProductionUnionProbeBackendConfig candidateBackend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setElementByteLength(ELEMENT_BYTE_LENGTH)
                .build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .setUnionProbeBackendConfig(candidateBackend)
                .build());
        Assert.assertTrue(abort.getMessage().contains("production-ready union-probe backend"));
        Assert.assertTrue(abort.getMessage().contains("not production ready"));
        Assert.assertTrue(abort.getMessage().contains("local capsule decoding"));
    }


    @Test
    public void testSecureModeRejectsHistoricalTargetOnePassSchedule() {
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
                .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
                .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.TARGET_ONE_PASS)
                .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
                .build());
        Assert.assertTrue(abort.getMessage().contains("historical schedule shape"));
    }

    @Test
    public void testSecureModeRejectsReferenceEndpointFlag() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setEnableFixedLayerReferenceEndpoint(true)
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new BaUpotConfig.Builder().build())
            .build());
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new BaUpotConfig.Builder().build())
            .setEnableFixedLayerReferenceEndpoint(true));
    }

    @Test
    public void testReferenceEndpointRejectsRetrySelection() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setEnableFixedLayerReferenceEndpoint(true)
            .setRetryCount(2)
            .build());
        BaSsuIbltBiUpsuConfig benchmarkOnlyConfig = new BaSsuIbltBiUpsuConfig.Builder()
            .setRetryCount(2)
            .build();
        Assert.assertFalse(benchmarkOnlyConfig.isEnableFixedLayerReferenceEndpoint());
        Assert.assertEquals(2, benchmarkOnlyConfig.getRetryCount());
    }

    @Test
    public void testNullConfigRejected() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltBiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), null));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltBiUpsuFactory.createReceiver(receiverRpc, senderRpc.ownParty(), null));
    }

    @Test
    public void testDefaultReferenceEndpointStaysDisabled() throws MpcAbortException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setAlphaAnchor(5.0)
            .build();
        BiUpsuSender sender = BiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), config);
        BiUpsuReceiver receiver = BiUpsuFactory.createReceiver(receiverRpc, senderRpc.ownParty(), config);
        sender.init(1, 1, ELEMENT_BYTE_LENGTH);
        receiver.init(1, 1, ELEMENT_BYTE_LENGTH);
        Assert.assertFalse(config.isEnableFixedLayerReferenceEndpoint());
        Assert.assertThrows(MpcAbortException.class, () -> sender.psu(Set.of(element(1))));
        Assert.assertThrows(MpcAbortException.class, () -> receiver.psu(Set.of(element(1))));
    }

    @Test
    public void testSecureEndpointConfigRequiresM14bBackend() {
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new BaUpotConfig.Builder().build())
            .build());
        Assert.assertTrue(abort.getMessage().contains("oblivious branch-selection"));
    }

    @Test
    public void testFakeM14bMaterialDoesNotMakeEndpointProductionReady() {
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new FakeObliviousBaUpotBackendConfig())
            .build();
        Assert.assertFalse(config.isProductionReady());
        Assert.assertFalse(BaSsuIbltBiUpsuFactory.isProductionReady(config));
        Assert.assertTrue(config.getProductionReadinessReason().contains("endpoint adapter remains fail-closed"));
    }

    @Test
    public void testSecureEndpointReadinessReasonRemainsFixed() {
        Assert.assertTrue(BaSsuIbltBiUpsuConfig.SECURE_SEMI_HONEST_NOT_READY_REASON.contains("M14b"));
        Assert.assertTrue(
            BaSsuIbltBiUpsuConfig.SECURE_SEMI_HONEST_NOT_READY_REASON.contains("oblivious branch-selection")
        );
    }

    @Test
    public void testEndpointRejectsNullInputSet() throws MpcAbortException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setAlphaAnchor(5.0)
            .setEnableFixedLayerReferenceEndpoint(true)
            .build();
        BiUpsuSender sender = BiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), config);
        BiUpsuReceiver receiver = BiUpsuFactory.createReceiver(receiverRpc, senderRpc.ownParty(), config);
        sender.init(1, 1, ELEMENT_BYTE_LENGTH);
        receiver.init(1, 1, ELEMENT_BYTE_LENGTH);
        Assert.assertThrows(IllegalArgumentException.class, () -> sender.psu(null));
        Assert.assertThrows(IllegalArgumentException.class, () -> receiver.psu(null));
    }

    @Test
    public void testFixedLayerEndpointSenderAnchor() throws InterruptedException, MpcAbortException {
        runFixedLayerEndpoint(64, 48, 16);
    }

    @Test
    public void testFixedLayerEndpointReceiverAnchor() throws InterruptedException, MpcAbortException {
        runFixedLayerEndpoint(32, 80, 16);
    }

    @Test
    public void testFixedLayerEndpointEqualSize() throws InterruptedException, MpcAbortException {
        runFixedLayerEndpoint(48, 48, 24);
    }

    @Test
    public void testMalformedLayerPayloadRejected() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(8, 4)
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(20260604L)
            .build();
        Assert.assertThrows(MpcAbortException.class,
            () -> BaSsuIbltLayerPayloadCodec.decodeLayer(null, ELEMENT_BYTE_LENGTH, params));
        List<byte[]> shortPayload = new ArrayList<>();
        shortPayload.add(new byte[BaSsuIbltLayerPayloadCodec.cellByteLength(
            ELEMENT_BYTE_LENGTH, BaSsuIbltLayerPayloadCodec.checkByteLength(params)
        )]);
        Assert.assertThrows(MpcAbortException.class,
            () -> BaSsuIbltLayerPayloadCodec.decodeLayer(shortPayload, ELEMENT_BYTE_LENGTH, params));
        int expectedCellCount = Math.toIntExact((long) params.getRetryCount() * params.getTableLength());
        List<byte[]> nullCellPayload = new ArrayList<>(expectedCellCount);
        for (int index = 0; index < expectedCellCount; index++) {
            nullCellPayload.add(new byte[BaSsuIbltLayerPayloadCodec.cellByteLength(
                ELEMENT_BYTE_LENGTH, BaSsuIbltLayerPayloadCodec.checkByteLength(params)
            )]);
        }
        nullCellPayload.set(0, null);
        Assert.assertThrows(MpcAbortException.class,
            () -> BaSsuIbltLayerPayloadCodec.decodeLayer(nullCellPayload, ELEMENT_BYTE_LENGTH, params));
        BaSsuIbltBiUpsuParams retryParams = new BaSsuIbltBiUpsuParams.Builder(8, 4)
            .setAlphaAnchor(5.0)
            .setRetryCount(2)
            .setPublicPlaceSeed(20260604L)
            .build();
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltFixedLayerEndpoint.encodeOwnLayer(Set.of(element(1)), true, ELEMENT_BYTE_LENGTH,
                retryParams));
    }

    private static void runFixedLayerEndpoint(int senderSize, int receiverSize, int overlap)
        throws InterruptedException, MpcAbortException {
        Set<ByteBuffer> senderSet = new HashSet<>();
        Set<ByteBuffer> receiverSet = new HashSet<>();
        for (int i = 0; i < senderSize; i++) {
            senderSet.add(element(i + 1L));
        }
        for (int i = 0; i < overlap; i++) {
            receiverSet.add(element(i + 1L));
        }
        for (int i = overlap; i < receiverSize; i++) {
            receiverSet.add(element(senderSize + i - overlap + 1L));
        }
        BaSsuIbltBiUpsuConfig config = new BaSsuIbltBiUpsuConfig.Builder()
            .setAlphaAnchor(5.0)
            .setEvaluatorMode(BaUpotBucketEvaluatorMode.IDEAL)
            .setEnableFixedLayerReferenceEndpoint(true)
            .build();
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BiUpsuSender sender = BiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), config);
            BiUpsuReceiver receiver = BiUpsuFactory.createReceiver(receiverRpc, senderRpc.ownParty(), config);
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            sender.init(senderSet.size(), receiverSet.size(), ELEMENT_BYTE_LENGTH);
            receiver.init(receiverSet.size(), senderSet.size(), ELEMENT_BYTE_LENGTH);
            SenderThread senderThread = new SenderThread(sender, senderSet);
            ReceiverThread receiverThread = new ReceiverThread(receiver, receiverSet);
            senderThread.start();
            receiverThread.start();
            senderThread.join();
            receiverThread.join();
            if (senderThread.getException() != null) {
                throw new AssertionError(senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new AssertionError(receiverThread.getException());
            }
            Set<ByteBuffer> expectedUnion = new HashSet<>(senderSet);
            expectedUnion.addAll(receiverSet);
            Assert.assertEquals(expectedUnion, senderThread.getOutput().getUnion());
            Assert.assertEquals(expectedUnion, receiverThread.getOutput().getUnion());
            Assert.assertEquals(BiUpsuPartyOutput.UNKNOWN_PSICA, senderThread.getOutput().getPsica());
            Assert.assertEquals(BiUpsuPartyOutput.UNKNOWN_PSICA, receiverThread.getOutput().getPsica());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testNoReferenceElementExchangeStep() {
        for (BaSsuIbltBiUpsuPtoDesc.PtoStep step : BaSsuIbltBiUpsuPtoDesc.PtoStep.values()) {
            Assert.assertFalse(step.name().contains("REFERENCE"));
            Assert.assertFalse(step.name().contains("ELEMENT"));
        }
    }

    private static ByteBuffer element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return ByteBuffer.wrap(byteBuffer.array());
    }

    /**
     * sender thread.
     */
    private static class SenderThread extends Thread {
        /**
         * sender.
         */
        private final BiUpsuSender sender;
        /**
         * sender elements.
         */
        private final Set<ByteBuffer> senderSet;
        /**
         * output.
         */
        private BiUpsuPartyOutput output;
        /**
         * exception.
         */
        private Exception exception;

        SenderThread(BiUpsuSender sender, Set<ByteBuffer> senderSet) {
            this.sender = sender;
            this.senderSet = senderSet;
        }

        @Override
        public void run() {
            try {
                output = sender.psu(senderSet);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        BiUpsuPartyOutput getOutput() {
            return output;
        }

        Exception getException() {
            return exception;
        }
    }

    /**
     * receiver thread.
     */
    private static class ReceiverThread extends Thread {
        /**
         * receiver.
         */
        private final BiUpsuReceiver receiver;
        /**
         * receiver elements.
         */
        private final Set<ByteBuffer> receiverSet;
        /**
         * output.
         */
        private BiUpsuPartyOutput output;
        /**
         * exception.
         */
        private Exception exception;

        ReceiverThread(BiUpsuReceiver receiver, Set<ByteBuffer> receiverSet) {
            this.receiver = receiver;
            this.receiverSet = receiverSet;
        }

        @Override
        public void run() {
            try {
                output = receiver.psu(receiverSet);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        BiUpsuPartyOutput getOutput() {
            return output;
        }

        Exception getException() {
            return exception;
        }
    }

    /**
     * fake production union-probe backend used only to test endpoint fail-closed readiness.
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

    /**
     * fake M14b-like BA-UPOT backend used only to test fail-closed endpoint readiness.
     */
    private static class FakeObliviousBaUpotBackendConfig extends AbstractMultiPartyPtoConfig
        implements BaSsuIbltBaUpotBackendConfig {

        FakeObliviousBaUpotBackendConfig() {
            super(SecurityModel.SEMI_HONEST);
        }

        @Override
        public String getBackendName() {
            return "fake oblivious BA-UPOT backend";
        }

        @Override
        public boolean isObliviousBranchSelection() {
            return true;
        }
    }
}
