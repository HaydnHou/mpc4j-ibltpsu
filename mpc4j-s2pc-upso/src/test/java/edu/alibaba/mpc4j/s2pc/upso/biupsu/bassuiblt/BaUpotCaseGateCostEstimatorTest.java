package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * BA-UPOT case-gate cost estimator tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotCaseGateCostEstimatorTest {
    @Test
    public void testEstimate() {
        BaUpotCaseGateCostEstimator.Estimate estimate = BaUpotCaseGateCostEstimator.estimate(123);
        Assert.assertEquals(123, estimate.getBucketCount());
        Assert.assertEquals(984, estimate.getAndGateCount());
        Assert.assertEquals(738, estimate.getXorGateCount());
        Assert.assertEquals(123, estimate.getNotGateCount());
        Assert.assertEquals(1845, estimate.getTotalGateCount());
    }

    @Test
    public void testRejectNegativeBucketCount() {
        Assert.assertThrows(IllegalArgumentException.class, () -> BaUpotCaseGateCostEstimator.estimate(-1));
    }
}
