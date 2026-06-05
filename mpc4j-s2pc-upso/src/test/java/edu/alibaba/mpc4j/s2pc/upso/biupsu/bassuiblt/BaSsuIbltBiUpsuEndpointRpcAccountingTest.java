package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuSender;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

/**
 * P38 endpoint RPC accounting tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltBiUpsuEndpointRpcAccountingTest {

    @Test
    public void testSecureEndpointAbortSendsNoProtocolPayload() throws MpcAbortException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            senderRpc.reset();
            receiverRpc.reset();
            BaSsuIbltBiUpsuConfig config = BaSsuIbltBiUpsuEndpointProductionTest.secureFixedLoopConfigWithFakeM14b();
            BiUpsuSender sender = BiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), config);
            BiUpsuReceiver receiver = BiUpsuFactory.createReceiver(receiverRpc, senderRpc.ownParty(), config);
            sender.init(1, 1, Long.BYTES);
            receiver.init(1, 1, Long.BYTES);
            Assert.assertThrows(MpcAbortException.class,
                () -> sender.psu(Set.of(BaSsuIbltBiUpsuEndpointProductionTest.element(1L))));
            Assert.assertThrows(MpcAbortException.class,
                () -> receiver.psu(Set.of(BaSsuIbltBiUpsuEndpointProductionTest.element(1L))));
            Assert.assertEquals(0L, senderRpc.getSendByteLength());
            Assert.assertEquals(0L, receiverRpc.getSendByteLength());
            Assert.assertEquals(0L, senderRpc.getSendDataPacketNum());
            Assert.assertEquals(0L, receiverRpc.getSendDataPacketNum());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }
}
