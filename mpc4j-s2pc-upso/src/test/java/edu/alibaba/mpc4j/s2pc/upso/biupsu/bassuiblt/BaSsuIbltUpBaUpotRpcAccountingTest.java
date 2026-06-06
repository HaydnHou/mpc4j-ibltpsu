package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * P51 RPC-backed UP-BA-UPOT accounting tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltUpBaUpotRpcAccountingTest {

    @Test
    public void testFixedAccountingHelpersArePackagePrivate() throws Exception {
        for (Class<?> clazz : new Class<?>[]{
            BaSsuIbltRpcUpBaUpotSender.class, BaSsuIbltRpcUpBaUpotReceiver.class
        }) {
            assertPackagePrivateLongHelper(clazz, "getPrecomputedCotNum");
            assertPackagePrivateIntHelper(clazz, "getFixedProbeCapsuleByteLength");
            assertPackagePrivateIntHelper(clazz, "getFixedResultByteLength");
        }
    }

    @Test
    public void testInitComputesFixedCotAndPayloadAccounting() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(3, 5, 16);

        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
            receiverRpc, senderRpc.ownParty(), config
        );
        sender.init(schedule);
        receiver.init(schedule);

        long expectedCotNum = (long) schedule.getMaterialCount() * config.getCotNumPerProbe();
        int expectedProbeBytes = BaSsuIbltUpBaUpotBucketProbeGadget.maskedRowsByteLength(schedule);
        int expectedResultBytes = 1 + config.getElementByteLength();
        Assert.assertEquals(expectedCotNum, sender.getPrecomputedCotNum());
        Assert.assertEquals(expectedCotNum, receiver.getPrecomputedCotNum());
        Assert.assertEquals(expectedProbeBytes, sender.getFixedProbeCapsuleByteLength());
        Assert.assertEquals(expectedProbeBytes, receiver.getFixedProbeCapsuleByteLength());
        Assert.assertEquals(expectedResultBytes, sender.getFixedResultByteLength());
        Assert.assertEquals(expectedResultBytes, receiver.getFixedResultByteLength());
        Assert.assertFalse(config.isQueuePeelProductionReady());
    }

    @Test
    public void testMaterialCountAccountingUsesRetryTimesProbeCap() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(4, 7, 32);
        Assert.assertEquals(28, schedule.getMaterialCount());
        Assert.assertEquals(7, schedule.getMaxProbeNum());
        Assert.assertEquals(4, schedule.getRetryNum());
    }

    @Test
    public void testCustomCotAndAuthShapeAccounting() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        BaSsuIbltProductionUnionProbeBackendConfig config =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
                .setElementByteLength(Long.BYTES)
                .setTagByteLength(24)
                .setCheckByteLength(16)
                .setAuthTagByteLength(12)
                .setCotNumPerProbe(5)
                .build();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = new BaSsuIbltUpBaUpotOfflineSchedule(
            "M10_N18_D3", 2, 11, 32, config.getElementByteLength(), config.getTagByteLength(),
            config.getCheckByteLength(), config.getAuthTagByteLength()
        );

        BaSsuIbltRpcUpBaUpotSender sender = new BaSsuIbltRpcUpBaUpotSender(
            senderRpc, receiverRpc.ownParty(), config
        );
        BaSsuIbltRpcUpBaUpotReceiver receiver = new BaSsuIbltRpcUpBaUpotReceiver(
            receiverRpc, senderRpc.ownParty(), config
        );
        sender.init(schedule);
        receiver.init(schedule);

        Assert.assertEquals(110L, sender.getPrecomputedCotNum());
        Assert.assertEquals(sender.getPrecomputedCotNum(), receiver.getPrecomputedCotNum());
        Assert.assertEquals(3 * (1 + Long.BYTES + 12), sender.getFixedProbeCapsuleByteLength());
        Assert.assertEquals(sender.getFixedProbeCapsuleByteLength(), receiver.getFixedProbeCapsuleByteLength());
        Assert.assertEquals(1 + Long.BYTES, sender.getFixedResultByteLength());
        Assert.assertEquals(sender.getFixedResultByteLength(), receiver.getFixedResultByteLength());
    }

    private static void assertPackagePrivateLongHelper(Class<?> clazz, String methodName) throws Exception {
        Method method = clazz.getDeclaredMethod(methodName);
        Assert.assertEquals(long.class, method.getReturnType());
        assertPackagePrivate(method);
    }

    private static void assertPackagePrivateIntHelper(Class<?> clazz, String methodName) throws Exception {
        Method method = clazz.getDeclaredMethod(methodName);
        Assert.assertEquals(int.class, method.getReturnType());
        assertPackagePrivate(method);
    }

    private static void assertPackagePrivate(Method method) {
        int modifiers = method.getModifiers();
        Assert.assertFalse(Modifier.isPublic(modifiers));
        Assert.assertFalse(Modifier.isProtected(modifiers));
        Assert.assertFalse(Modifier.isPrivate(modifiers));
    }
}
