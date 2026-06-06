package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuSender;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * P38 SECURE_SEMI_HONEST endpoint production gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltBiUpsuEndpointProductionTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testSecureEndpointRemainsFailClosedWithMaterialReadyConfig() throws MpcAbortException {
        BaSsuIbltBiUpsuConfig config = secureFixedLoopConfigWithFakeM14b();
        Assert.assertFalse(config.isProductionReady());
        Assert.assertTrue(config.getProductionReadinessReason().contains("endpoint adapter remains fail-closed"));

        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BiUpsuSender sender = BiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), config);
        sender.init(1, 1, ELEMENT_BYTE_LENGTH);
        MpcAbortException abort = Assert.assertThrows(MpcAbortException.class, () -> sender.psu(Set.of(element(1L))));
        Assert.assertTrue(abort.getMessage().contains("endpoint adapter remains fail-closed"));
    }

    @Test
    public void testQueuePeelSecureEndpointRequiresTranscriptOptIn() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setElementByteLength(ELEMENT_BYTE_LENGTH)
                .setTagByteLength(5)
                .setCheckByteLength(5)
                .build();
        Assert.assertTrue(backend.hasQueuePeelEndpointIntegration());
        Assert.assertFalse(backend.hasAcceptedAdaptiveQueueTranscriptLeakage());
        Assert.assertFalse(backend.isQueuePeelProductionReady());
        BaSsuIbltBiUpsuConfig config = queuePeelSecureConfig(8, 6, backend);
        Assert.assertFalse(config.isProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            config.getProductionReadinessReason()
        );
    }

    @Test
    public void testQueuePeelProductionEndpointHasInternalGate() {
        BaSsuIbltBiUpsuConfig config = queuePeelSecureConfig(8, 6, productionBackendConfig(8, 6));
        MpcAbortException abort = Assert.assertThrows(MpcAbortException.class,
            () -> BaSsuIbltQueuePeelEndpoint.runProductionEndpoint(
                null, null, true, null, 8, 6, ELEMENT_BYTE_LENGTH, config, 1, false
            ));
        Assert.assertTrue(abort.getMessage().contains(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON
        ));
    }

    @Test
    public void testQueuePeelCandidateEndpointSenderAnchor() throws InterruptedException, MpcAbortException {
        runQueuePeelCandidateEndpoint(8, 6, 2);
    }

    @Test
    public void testQueuePeelCandidateEndpointReceiverAnchor() throws InterruptedException, MpcAbortException {
        runQueuePeelCandidateEndpoint(5, 9, 2);
    }

    @Test
    public void testQueuePeelCandidateEndpointPadsUnitShadowOprfBatch() throws InterruptedException, MpcAbortException {
        runQueuePeelCandidateEndpoint(3, 1, 1);
    }

    static BaSsuIbltBiUpsuConfig secureFixedLoopConfigWithFakeM14b() {
        return new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new FakeObliviousBaUpotBackendConfig())
            .build();
    }

    private static void runQueuePeelCandidateEndpoint(int senderSize, int receiverSize, int overlap)
        throws InterruptedException, MpcAbortException {
        Set<ByteBuffer> senderSet = new HashSet<>();
        Set<ByteBuffer> receiverSet = new HashSet<>();
        for (int index = 0; index < senderSize; index++) {
            senderSet.add(element(index + 1L));
        }
        for (int index = 0; index < overlap; index++) {
            receiverSet.add(element(index + 1L));
        }
        for (int index = overlap; index < receiverSize; index++) {
            receiverSet.add(element(senderSize + index - overlap + 1L));
        }
        BaSsuIbltBiUpsuConfig config = queuePeelSecureConfig(senderSize, receiverSize, productionBackendConfig(
            senderSize, receiverSize
        ));
        Assert.assertFalse(config.isProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON,
            config.getProductionReadinessReason()
        );

        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            int taskId = Math.abs(new SecureRandom().nextInt());
            EndpointThread senderThread = new EndpointThread(
                senderRpc, receiverRpc.ownParty(), true, senderSet, senderSize, receiverSize, config, taskId
            );
            EndpointThread receiverThread = new EndpointThread(
                receiverRpc, senderRpc.ownParty(), false, receiverSet, senderSize, receiverSize, config, taskId
            );
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

    private static BaSsuIbltBiUpsuConfig queuePeelSecureConfig(
        int senderCapacity, int receiverCapacity, BaSsuIbltProductionUnionProbeBackendConfig backend) {
        return new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(backend)
            .setAlphaAnchor(5.0)
            .setLambda(32)
            .setMarginBits(0)
            .setChecksPerBucket(1)
            .setPublicPlaceSeed(seedBytes(senderCapacity, receiverCapacity))
            .build();
    }

    private static BaSsuIbltProductionUnionProbeBackendConfig productionBackendConfig(
        int senderCapacity, int receiverCapacity) {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuConfig.Builder()
            .setAlphaAnchor(5.0)
            .setLambda(32)
            .setMarginBits(0)
            .setChecksPerBucket(1)
            .setPublicPlaceSeed(seedBytes(senderCapacity, receiverCapacity))
            .build()
            .createParams(senderCapacity, receiverCapacity);
        int tagByteLength = BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits());
        int checkByteLength = BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits());
        return new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setTagByteLength(tagByteLength)
            .setCheckByteLength(checkByteLength)
            .setAcceptAdaptiveQueueTranscriptLeakage(true)
            .build();
    }

    private static byte[] seedBytes(int senderCapacity, int receiverCapacity) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 2 + Long.BYTES);
        byteBuffer.putInt(senderCapacity);
        byteBuffer.putInt(receiverCapacity);
        byteBuffer.putLong(20260605L);
        return byteBuffer.array();
    }

    static ByteBuffer element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return ByteBuffer.wrap(byteBuffer.array());
    }

    /**
     * fake M14b-like BA-UPOT backend used only to exercise endpoint fail-closed readiness.
     */
    static class FakeObliviousBaUpotBackendConfig extends AbstractMultiPartyPtoConfig
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

    /**
     * direct candidate endpoint thread.
     */
    private static class EndpointThread extends Thread {
        /**
         * local RPC.
         */
        private final Rpc rpc;
        /**
         * other party.
         */
        private final edu.alibaba.mpc4j.common.rpc.Party otherParty;
        /**
         * whether the local API role is protocol sender.
         */
        private final boolean localIsProtocolSender;
        /**
         * local set.
         */
        private final Set<ByteBuffer> localSet;
        /**
         * sender capacity.
         */
        private final int senderCapacity;
        /**
         * receiver capacity.
         */
        private final int receiverCapacity;
        /**
         * config.
         */
        private final BaSsuIbltBiUpsuConfig config;
        /**
         * task ID.
         */
        private final int taskId;
        /**
         * output.
         */
        private BiUpsuPartyOutput output;
        /**
         * exception.
         */
        private Exception exception;

        EndpointThread(Rpc rpc, edu.alibaba.mpc4j.common.rpc.Party otherParty, boolean localIsProtocolSender,
                       Set<ByteBuffer> localSet, int senderCapacity, int receiverCapacity,
                       BaSsuIbltBiUpsuConfig config, int taskId) {
            this.rpc = rpc;
            this.otherParty = otherParty;
            this.localIsProtocolSender = localIsProtocolSender;
            this.localSet = localSet;
            this.senderCapacity = senderCapacity;
            this.receiverCapacity = receiverCapacity;
            this.config = config;
            this.taskId = taskId;
        }

        @Override
        public void run() {
            try {
                output = BaSsuIbltQueuePeelEndpoint.runCandidateEndpoint(
                    rpc, otherParty, localIsProtocolSender, localSet, senderCapacity, receiverCapacity,
                    ELEMENT_BYTE_LENGTH, config, taskId, false
                );
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
}
