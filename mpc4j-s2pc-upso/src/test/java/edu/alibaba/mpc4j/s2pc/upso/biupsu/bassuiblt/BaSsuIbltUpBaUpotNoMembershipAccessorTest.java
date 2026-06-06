package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * P41 API leakage regression tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotNoMembershipAccessorTest {

    @Test
    public void testApiTypesDoNotExposeMembershipSurface() {
        assertNoForbiddenPublicMethod(BaSsuIbltUpBaUpotSender.class);
        assertNoForbiddenPublicMethod(BaSsuIbltUpBaUpotReceiver.class);
        assertNoForbiddenPublicMethod(BaSsuIbltProductionUnionProbeCapsule.class);
        assertNoForbiddenPublicMethod(BaSsuIbltProductionUnionProbeOutput.class);
    }

    @Test
    public void testLocalInputIsNotPublicApiSurface() {
        Assert.assertFalse(Modifier.isPublic(BaSsuIbltUpBaUpotLocalInput.class.getModifiers()));
        for (Method method : BaSsuIbltUpBaUpotLocalInput.class.getMethods()) {
            Assert.assertEquals(Object.class, method.getDeclaringClass());
        }
    }

    @Test
    public void testPublicInputContainsOnlyPublicShapeAccessors() {
        for (Method method : BaSsuIbltUpBaUpotPublicInput.class.getMethods()) {
            if (method.getDeclaringClass().equals(Object.class)) {
                continue;
            }
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
            Assert.assertFalse(name.contains("choice"));
            Assert.assertFalse(name.contains("member"));
            Assert.assertFalse(name.contains("raw"));
        }
    }

    private static void assertNoForbiddenPublicMethod(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass().equals(Object.class)) {
                continue;
            }
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
            Assert.assertFalse(name.contains("raw"));
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("check"));
            Assert.assertFalse(name.contains("choice"));
            Assert.assertFalse(name.contains("member"));
            Assert.assertFalse(name.contains("decode"));
            Assert.assertFalse(name.contains("open"));
        }
    }
}
