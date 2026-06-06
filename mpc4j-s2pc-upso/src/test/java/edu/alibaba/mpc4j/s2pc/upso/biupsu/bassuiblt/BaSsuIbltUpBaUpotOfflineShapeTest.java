package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P42 fixed offline shape tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotOfflineShapeTest {

    @Test
    public void testMaterialCountDependsOnlyOnPublicSchedule() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = schedule(2, 16);
        boolean[] allFalse = new boolean[schedule.getMaterialCount()];
        boolean[] alternating = new boolean[schedule.getMaterialCount()];
        for (int i = 0; i < alternating.length; i++) {
            alternating[i] = (i & 1) == 1;
        }
        BaSsuIbltUpBaUpotOfflineReceiverOutput first = BaSsuIbltUpBaUpotOfflineReceiverOutput.create(
            schedule, seed((byte) 0x01), allFalse, 7L
        );
        BaSsuIbltUpBaUpotOfflineReceiverOutput second = BaSsuIbltUpBaUpotOfflineReceiverOutput.create(
            schedule, seed((byte) 0x02), alternating, 9L
        );
        Assert.assertEquals(32, first.getMaterialCount());
        Assert.assertEquals(first.getMaterialCount(), second.getMaterialCount());
        Assert.assertEquals(schedule.getTotalMaterialByteLength(), first.getOfflineSendBytes());
        Assert.assertEquals(schedule.getTotalMaterialByteLength(), second.getOfflineSendBytes());
    }

    @Test
    public void testScheduleRejectsNonPublicShape() {
        Assert.assertThrows(IllegalArgumentException.class, () -> schedule(0, 16));
        Assert.assertThrows(IllegalArgumentException.class, () -> schedule(2, 0));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotOfflineSchedule("", 1, 1, 8, 8, 24, 16, 16));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotOfflineSchedule("p", 1, 1, 0, 8, 24, 16, 16));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotOfflineSchedule("p", 1, 1, 8, 0, 24, 16, 16));
    }

    static BaSsuIbltUpBaUpotOfflineSchedule schedule(int retryNum, int maxProbeNum) {
        return schedule("M10_N18_D3", retryNum, maxProbeNum);
    }

    static BaSsuIbltUpBaUpotOfflineSchedule schedule(String profileId, int retryNum, int maxProbeNum) {
        return new BaSsuIbltUpBaUpotOfflineSchedule(
            profileId, retryNum, maxProbeNum, 16, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH,
            BaSsuIbltProductionUnionProbeTestUtils.TAG_BYTE_LENGTH,
            BaSsuIbltProductionUnionProbeTestUtils.CHECK_BYTE_LENGTH,
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build().getAuthTagByteLength()
        );
    }

    static byte[] seed(byte value) {
        byte[] seed = new byte[32];
        for (int i = 0; i < seed.length; i++) {
            seed[i] = value;
        }
        return seed;
    }
}
