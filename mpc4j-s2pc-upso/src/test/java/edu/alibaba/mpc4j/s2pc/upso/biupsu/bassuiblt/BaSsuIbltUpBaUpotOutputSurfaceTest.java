package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * P51 output-surface regression tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotOutputSurfaceTest {

    @Test
    public void testOutputExposesOnlyFixedTwoStateSurface() {
        BaSsuIbltProductionUnionProbeOutput bottom =
            BaSsuIbltProductionUnionProbeOutput.bottom(0, Long.BYTES);
        Assert.assertEquals(BaSsuIbltProductionUnionProbeOutput.Type.BOTTOM, bottom.getType());
        Assert.assertTrue(bottom.isBottom());
        Assert.assertFalse(bottom.isSingleton());
        Assert.assertThrows(IllegalStateException.class, bottom::getElement);

        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(5L);
        BaSsuIbltProductionUnionProbeOutput singleton =
            BaSsuIbltProductionUnionProbeOutput.singleton(1, element, element.length);
        Assert.assertEquals(BaSsuIbltProductionUnionProbeOutput.Type.UNION_SINGLETON, singleton.getType());
        Assert.assertTrue(singleton.isSingleton());
        Assert.assertFalse(singleton.isBottom());
        Assert.assertArrayEquals(element, singleton.getElement());
        byte[] copy = singleton.getElement();
        copy[0] ^= 0x01;
        Assert.assertArrayEquals(element, singleton.getElement());
    }

    @Test
    public void testOutputHasNoForbiddenPublicAccessor() {
        assertNoForbiddenPublicAccessor(BaSsuIbltProductionUnionProbeOutput.class);
        assertNoForbiddenPublicAccessor(BaSsuIbltUnionProbeOutput.class);
    }

    private static void assertNoForbiddenPublicAccessor(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
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
        }
    }
}
