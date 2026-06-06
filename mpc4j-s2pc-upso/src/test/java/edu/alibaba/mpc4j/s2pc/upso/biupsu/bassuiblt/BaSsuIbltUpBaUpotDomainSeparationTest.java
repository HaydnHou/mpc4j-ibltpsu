package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * P42 offline domain-separation tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotDomainSeparationTest {

    @Test
    public void testDomainIncludesRoleLabelRetryBucketAndProbeOrdinal() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(2, 8);
        byte[] seed = BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x55);
        BaSsuIbltUpBaUpotOfflineSenderOutput senderOutput =
            BaSsuIbltUpBaUpotOfflineSenderOutput.create(schedule, seed, 0L);
        BaSsuIbltUpBaUpotOfflineReceiverOutput receiverOutput =
            BaSsuIbltUpBaUpotOfflineReceiverOutput.create(schedule, seed, new boolean[schedule.getMaterialCount()], 0L);
        byte[] base = senderOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 0, 3, 4);
        Assert.assertFalse(Arrays.equals(
            base, senderOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.AUTH_MASK, 0, 3, 4)
        ));
        Assert.assertFalse(Arrays.equals(
            base, senderOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 1, 3, 4)
        ));
        Assert.assertFalse(Arrays.equals(
            base, senderOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 0, 4, 4)
        ));
        Assert.assertFalse(Arrays.equals(
            base, senderOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 0, 3, 5)
        ));
        Assert.assertFalse(Arrays.equals(
            base, receiverOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 0, 3, 4)
        ));
    }

    @Test
    public void testDomainIncludesProfileAndShape() {
        byte[] seed = BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x66);
        BaSsuIbltUpBaUpotOfflineSchedule first = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(2, 8);
        BaSsuIbltUpBaUpotOfflineSchedule second = new BaSsuIbltUpBaUpotOfflineSchedule(
            "M12_N18_D3", first.getRetryNum(), first.getMaxProbeNum(), first.getTableLength(),
            first.getElementByteLength(), first.getTagByteLength(), first.getCheckByteLength(),
            first.getAuthTagByteLength()
        );
        BaSsuIbltUpBaUpotOfflineSchedule third = new BaSsuIbltUpBaUpotOfflineSchedule(
            first.getProfileId(), first.getRetryNum(), first.getMaxProbeNum(), first.getTableLength(),
            first.getElementByteLength(), first.getTagByteLength() + 1, first.getCheckByteLength(),
            first.getAuthTagByteLength()
        );
        BaSsuIbltUpBaUpotOfflineSchedule fourth = new BaSsuIbltUpBaUpotOfflineSchedule(
            first.getProfileId(), first.getRetryNum(), first.getMaxProbeNum(), first.getTableLength() + 1,
            first.getElementByteLength(), first.getTagByteLength(), first.getCheckByteLength(),
            first.getAuthTagByteLength()
        );
        byte[] base = BaSsuIbltUpBaUpotOfflineSenderOutput.create(first, seed, 0L)
            .derive(BaSsuIbltUpBaUpotOfflineLabel.TRANSCRIPT_MASK, 0, 1, 2);
        Assert.assertFalse(Arrays.equals(
            base, BaSsuIbltUpBaUpotOfflineSenderOutput.create(second, seed, 0L)
                .derive(BaSsuIbltUpBaUpotOfflineLabel.TRANSCRIPT_MASK, 0, 1, 2)
        ));
        Assert.assertFalse(Arrays.equals(
            base, BaSsuIbltUpBaUpotOfflineSenderOutput.create(third, seed, 0L)
                .derive(BaSsuIbltUpBaUpotOfflineLabel.TRANSCRIPT_MASK, 0, 1, 2)
        ));
        Assert.assertFalse(Arrays.equals(
            base, BaSsuIbltUpBaUpotOfflineSenderOutput.create(fourth, seed, 0L)
                .derive(BaSsuIbltUpBaUpotOfflineLabel.TRANSCRIPT_MASK, 0, 1, 2)
        ));
    }

    @Test
    public void testLabelDerivationUsesFixedLengthsOnly() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(1, 8);
        BaSsuIbltUpBaUpotOfflineSenderOutput senderOutput = BaSsuIbltUpBaUpotOfflineSenderOutput.create(
            schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x77), 0L
        );
        int sum = 0;
        for (BaSsuIbltUpBaUpotOfflineLabel label : BaSsuIbltUpBaUpotOfflineLabel.values()) {
            int length = label.byteLength(schedule);
            Assert.assertEquals(length, senderOutput.derive(label, 0, 1, 2).length);
            sum += length;
        }
        Assert.assertEquals(schedule.getProbeMaterialByteLength(), sum);
        Assert.assertEquals(
            (long) schedule.getProbeMaterialByteLength() * schedule.getMaterialCount(),
            schedule.getTotalMaterialByteLength()
        );
    }

    @Test
    public void testMaterialDerivationRejectsOutOfTableBucket() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltUpBaUpotOfflineShapeTest.schedule(1, 8);
        BaSsuIbltUpBaUpotOfflineSenderOutput senderOutput = BaSsuIbltUpBaUpotOfflineSenderOutput.create(
            schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x78), 0L
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> senderOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 0, schedule.getTableLength(), 0));
        BaSsuIbltUpBaUpotOfflineReceiverOutput receiverOutput = BaSsuIbltUpBaUpotOfflineReceiverOutput.create(
            schedule, BaSsuIbltUpBaUpotOfflineShapeTest.seed((byte) 0x79),
            new boolean[schedule.getMaterialCount()], 0L
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> receiverOutput.derive(BaSsuIbltUpBaUpotOfflineLabel.PAYLOAD_MASK, 0, -1, 0));
    }
}
