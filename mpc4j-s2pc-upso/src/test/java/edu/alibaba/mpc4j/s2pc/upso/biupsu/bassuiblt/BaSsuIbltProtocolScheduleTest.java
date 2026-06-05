package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * BA-SSU-IBLT protocol schedule tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProtocolScheduleTest {
    @Test
    public void testCurrentTargetAndQueueBucketCounts() {
        BaSsuIbltBiUpsuParams params = queueReadyParams(64, 8, 4.0, 3, 2);
        BaSsuIbltProtocolSchedule current = BaSsuIbltProtocolSchedule.currentFixedLoopM14a(params);
        BaSsuIbltProtocolSchedule target = BaSsuIbltProtocolSchedule.targetOnePass(params);
        BaSsuIbltProtocolSchedule queuePeel = BaSsuIbltProtocolSchedule.queuePeelAligned(params);
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A, current.getShape());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.TARGET_ONE_PASS, target.getShape());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED, queuePeel.getShape());
        Assert.assertEquals(73, current.getFixedRoundCount());
        Assert.assertEquals(current.getFixedRoundCount(), target.getFixedRoundCount());
        Assert.assertEquals(2L * 256, target.getScheduledBucketCount());
        Assert.assertEquals(target.getScheduledBucketCount() * current.getFixedRoundCount(),
            current.getScheduledBucketCount());
        Assert.assertEquals(2L * (256 + 3L * (64 + 8)), queuePeel.getScheduledBucketCount());
        Assert.assertEquals(queuePeel.getScheduledBucketCount(),
            BaSsuIbltProtocolSchedule.queuePeelMaxProbes(params));
        Assert.assertEquals(queuePeel.getScheduledBucketCount() * params.getChecksPerBucket(),
            BaSsuIbltProtocolSchedule.queuePeelNTests(params));
    }

    @Test
    public void testQueuePeelCheckBitsGuard() {
        BaSsuIbltBiUpsuParams underBudgetParams = new BaSsuIbltBiUpsuParams.Builder(64, 8)
            .setAlphaAnchor(4.0)
            .setRetryCount(2)
            .setCheckBits(BaSsuIbltBiUpsuParams.DEFAULT_LAMBDA)
            .build();
        Assert.assertTrue(BaSsuIbltProtocolSchedule.queuePeelMinCheckBits(underBudgetParams)
            > underBudgetParams.getCheckBits());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.queuePeelNTests(underBudgetParams),
            BaSsuIbltProtocolSchedule.nTests(underBudgetParams,
                BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProtocolSchedule.queuePeelAligned(underBudgetParams));
        BaSsuIbltBiUpsuParams readyParams = queueReadyParams(64, 8, 4.0, 3, 2);
        BaSsuIbltProtocolSchedule.queuePeelAligned(readyParams);
    }

    @Test
    public void testQueuePeelOverflowRejected() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1_500_000_000, 1_500_000_000)
            .setTableLength(Integer.MAX_VALUE)
            .setDegree(4)
            .setRetryCount(Integer.MAX_VALUE)
            .setCheckBits(256)
            .build();
        Assert.assertThrows(ArithmeticException.class, () -> BaSsuIbltProtocolSchedule.queuePeelMaxProbes(params));
    }

    @Test
    public void testInvalidInputsRejected() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(8, 4).build();
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltProtocolSchedule.of(null, params));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProtocolSchedule.of(BaSsuIbltProtocolSchedule.Shape.TARGET_ONE_PASS, null));
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltProtocolSchedule.fixedRoundCount(null));
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltProtocolSchedule.queuePeelMaxProbes(null));
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltProtocolSchedule.queuePeelNTests(null));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuParams.Builder(8, 4).setChecksPerBucket(0).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltBiUpsuParams.Builder(8, 4).setChecksPerBucket(-1).build());
    }

    private static BaSsuIbltBiUpsuParams queueReadyParams(int nLarge, int nShadow, double alphaAnchor, int degree,
                                                          int retryCount) {
        BaSsuIbltBiUpsuParams draft = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setAlphaAnchor(alphaAnchor)
            .setDegree(degree)
            .setRetryCount(retryCount)
            .build();
        int checkBits = BaSsuIbltProtocolSchedule.queuePeelMinCheckBits(draft);
        return new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setAlphaAnchor(alphaAnchor)
            .setDegree(degree)
            .setRetryCount(retryCount)
            .setCheckBits(checkBits)
            .build();
    }
}
