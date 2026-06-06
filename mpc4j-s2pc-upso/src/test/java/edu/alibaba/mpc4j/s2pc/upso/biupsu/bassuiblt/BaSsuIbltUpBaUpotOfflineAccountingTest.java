package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Modifier;

/**
 * P42 offline accounting tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotOfflineAccountingTest {

    @Test
    public void testSenderReceiverOfflineAccounting() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(3, 11);
        BaSsuIbltUpBaUpotOfflineSenderOutput senderOutput = BaSsuIbltUpBaUpotOfflineSenderOutput.create(
            schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x11), 123L
        );
        BaSsuIbltUpBaUpotOfflineReceiverOutput receiverOutput = BaSsuIbltUpBaUpotOfflineReceiverOutput.create(
            schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x22),
            new boolean[schedule.getMaterialCount()], 456L
        );
        Assert.assertEquals(schedule, senderOutput.getSchedule());
        Assert.assertEquals(schedule, receiverOutput.getSchedule());
        Assert.assertEquals(33, senderOutput.getMaterialCount());
        Assert.assertEquals(senderOutput.getMaterialCount(), receiverOutput.getMaterialCount());
        Assert.assertEquals(schedule.getTotalMaterialByteLength(), senderOutput.getOfflineSendBytes());
        Assert.assertEquals(schedule.getTotalMaterialByteLength(), receiverOutput.getOfflineSendBytes());
        Assert.assertEquals(123L, senderOutput.getOfflineTimeNanos());
        Assert.assertEquals(456L, receiverOutput.getOfflineTimeNanos());
    }

    @Test
    public void testReceiverChoiceShapeIsFixed() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(2, 4);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            BaSsuIbltUpBaUpotOfflineReceiverOutput.create(
                schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x33), new boolean[7], 0L
            ));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            BaSsuIbltUpBaUpotOfflineSenderOutput.create(
                schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x33), -1L
            ));
    }

    @Test
    public void testScheduleCannotBeSubclassedForAccounting() {
        Assert.assertTrue(Modifier.isFinal(BaSsuIbltUpBaUpotOfflineSchedule.class.getModifiers()));
    }

    @Test
    public void testScheduleRejectsOverflowingAccountingShape() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltUpBaUpotOfflineSchedule(
            "overflow", 1, 1, 1, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltUpBaUpotOfflineSchedule(
            "overflow", Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 8, 24, 16, 16
        ));
    }
}
