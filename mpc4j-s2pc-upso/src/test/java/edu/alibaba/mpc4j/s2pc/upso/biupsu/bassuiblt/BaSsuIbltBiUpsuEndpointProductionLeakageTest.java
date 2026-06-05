package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * P38 endpoint leakage surface tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltBiUpsuEndpointProductionLeakageTest {

    @Test
    public void testEndpointProductionGateIsPackagePrivateAndMinimal() {
        int modifiers = BaSsuIbltBiUpsuProductionGate.class.getModifiers();
        Assert.assertFalse(Modifier.isPublic(modifiers));
        Assert.assertFalse(Modifier.isProtected(modifiers));
        Assert.assertFalse(Modifier.isPrivate(modifiers));
        for (Method method : BaSsuIbltBiUpsuProductionGate.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("reference"));
            Assert.assertFalse(name.contains("fixed"));
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("membership"));
        }
    }

    @Test
    public void testSecureEndpointReasonDoesNotExposeInputDependentFields() {
        String reason = BaSsuIbltBiUpsuEndpointProductionTest.secureFixedLoopConfigWithFakeM14b()
            .getProductionReadinessReason()
            .toLowerCase();
        Assert.assertFalse(reason.contains("bucket index"));
        Assert.assertFalse(reason.contains("case"));
        Assert.assertFalse(reason.contains("tag"));
        Assert.assertFalse(reason.contains("membership"));
    }
}
