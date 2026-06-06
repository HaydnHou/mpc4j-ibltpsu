package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * P42 OT-choice hiding surface tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotOfflineChoiceHidingTest {

    @Test
    public void testOfflineOutputsHaveNoPublicChoiceOrMaskGetters() {
        assertNoPublicLeakageAccessor(BaSsuIbltUpBaUpotOfflineSenderOutput.class);
        assertNoPublicLeakageAccessor(BaSsuIbltUpBaUpotOfflineReceiverOutput.class);
    }

    @Test
    public void testToStringDoesNotPrintChoiceVector() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(1, 8);
        boolean[] choices = new boolean[schedule.getMaterialCount()];
        choices[0] = true;
        BaSsuIbltUpBaUpotOfflineReceiverOutput receiverOutput = BaSsuIbltUpBaUpotOfflineReceiverOutput.create(
            schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x44), choices, 0L
        );
        String text = receiverOutput.toString().toLowerCase();
        Assert.assertFalse(text.contains("choice"));
        Assert.assertFalse(text.contains("true"));
        Assert.assertFalse(text.contains("false"));
    }

    private static void assertNoPublicLeakageAccessor(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass().equals(Object.class)) {
                continue;
            }
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("choice"));
            Assert.assertFalse(name.contains("mask"));
            Assert.assertFalse(name.contains("branch"));
            Assert.assertFalse(name.contains("payload"));
            Assert.assertFalse(name.contains("auth"));
            Assert.assertFalse(name.contains("seed"));
            Assert.assertFalse(name.contains("cot"));
            Assert.assertFalse(name.contains("rot"));
        }
    }
}
