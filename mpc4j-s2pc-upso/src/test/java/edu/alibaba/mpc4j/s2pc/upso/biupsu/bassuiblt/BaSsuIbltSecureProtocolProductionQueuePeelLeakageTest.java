package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * P37 production queue-peel leakage gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltSecureProtocolProductionQueuePeelLeakageTest {

    @Test
    public void testProductionAdapterIsNotPublicProtocolSurface() {
        int modifiers = BaSsuIbltProductionQueuePeelAdapter.class.getModifiers();
        Assert.assertFalse(Modifier.isPublic(modifiers));
        Assert.assertFalse(Modifier.isProtected(modifiers));
        Assert.assertFalse(Modifier.isPrivate(modifiers));
    }

    @Test
    public void testProductionAdapterApiHasNoReferenceOrDebugSurface() {
        for (Method method : BaSsuIbltProductionQueuePeelAdapter.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("reference"));
            Assert.assertFalse(name.contains("debug"));
            Assert.assertFalse(name.contains("trace"));
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("membership"));
        }
    }

    @Test
    public void testTrustedBackendConfigCannotBeSubclassedIntoFakeReadyBackend() {
        Assert.assertTrue(Modifier.isFinal(BaSsuIbltProductionUnionProbeBackendConfig.class.getModifiers()));
    }
}
