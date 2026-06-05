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

import java.nio.ByteBuffer;
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

    static BaSsuIbltBiUpsuConfig secureFixedLoopConfigWithFakeM14b() {
        return new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setSecureBaUpotConfig(new FakeObliviousBaUpotBackendConfig())
            .build();
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
}
