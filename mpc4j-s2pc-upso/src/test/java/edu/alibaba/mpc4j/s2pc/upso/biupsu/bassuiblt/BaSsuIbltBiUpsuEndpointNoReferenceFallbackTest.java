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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * P38 endpoint no-reference-fallback tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltBiUpsuEndpointNoReferenceFallbackTest {

    @Test
    public void testSecureEndpointHasSeparateExecutionMethods() throws NoSuchMethodException {
        Method senderMethod = BaSsuIbltBiUpsuSender.class.getDeclaredMethod("runSecureSemiHonest", Set.class);
        Method receiverMethod = BaSsuIbltBiUpsuReceiver.class.getDeclaredMethod("runSecureSemiHonest", Set.class);
        Assert.assertTrue(Modifier.isPrivate(senderMethod.getModifiers()));
        Assert.assertTrue(Modifier.isPrivate(receiverMethod.getModifiers()));
    }

    @Test
    public void testSecureSenderDoesNotFallbackToFixedLayerEndpoint() throws MpcAbortException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltBiUpsuConfig config = BaSsuIbltBiUpsuEndpointProductionTest.secureFixedLoopConfigWithFakeM14b();
        BiUpsuSender sender = BiUpsuFactory.createSender(senderRpc, receiverRpc.ownParty(), config);
        sender.init(1, 1, Long.BYTES);
        MpcAbortException abort = Assert.assertThrows(MpcAbortException.class,
            () -> sender.psu(Set.of(BaSsuIbltBiUpsuEndpointProductionTest.element(1L))));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
        Assert.assertFalse(abort.getMessage().contains("fixed-layer"));
    }

    @Test
    public void testSecureReceiverDoesNotFallbackToFixedLayerEndpoint() throws MpcAbortException {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltBiUpsuConfig config = BaSsuIbltBiUpsuEndpointProductionTest.secureFixedLoopConfigWithFakeM14b();
        BiUpsuReceiver receiver = BiUpsuFactory.createReceiver(receiverRpc, senderRpc.ownParty(), config);
        receiver.init(1, 1, Long.BYTES);
        MpcAbortException abort = Assert.assertThrows(MpcAbortException.class,
            () -> receiver.psu(Set.of(BaSsuIbltBiUpsuEndpointProductionTest.element(1L))));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
        Assert.assertFalse(abort.getMessage().contains("fixed-layer"));
    }
}
