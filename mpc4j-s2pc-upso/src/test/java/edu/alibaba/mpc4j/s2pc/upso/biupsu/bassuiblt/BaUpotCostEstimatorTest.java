package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * BA-UPOT cost estimator tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotCostEstimatorTest {
    @Test
    public void testGenericAndGatesIncreaseWithCheckBits() {
        int gates128 = BaUpotCostEstimator.estimateGenericAndGates(64, 128);
        int gates182 = BaUpotCostEstimator.estimateGenericAndGates(64, 182);
        Assert.assertTrue(gates128 > 0);
        Assert.assertTrue(gates182 > gates128);
    }

    @Test
    public void testGenericBytesPerBucket() {
        int gates = BaUpotCostEstimator.estimateGenericAndGates(64, 182);
        long halfGateBytes = BaUpotCostEstimator.estimateGenericBytesPerBucket(
            64, 182, BaUpotCostEstimator.HALF_GATES_BYTES_PER_AND
        );
        Assert.assertEquals((long) gates * BaUpotCostEstimator.HALF_GATES_BYTES_PER_AND, halfGateBytes);
    }

    @Test
    public void testSpecializedRelativeCost() {
        long baBuckets = 1_089_925L;
        long h5Buckets = BaUpotCostEstimator.estimateH5BucketProbes(1 << 10, 1 << 18);
        double relative = BaUpotCostEstimator.estimateSpecializedRelativeCost(baBuckets, h5Buckets, 1.0);
        Assert.assertTrue(relative > 0);
        Assert.assertTrue(relative < 1);
    }

    @Test
    public void testSevereImbalanceEstimate() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1 << 18, 1 << 10)
            .setDegree(3)
            .setAlphaAnchor(1.55)
            .setCheckBits(182)
            .setTagBits(182)
            .build();
        BaUpotCostEstimator.EstimateReport report = BaUpotCostEstimator.estimate(
            1 << 10, 1 << 18, params, 1_089_925L, Long.SIZE
        );
        Assert.assertEquals(921088L, report.getH5Cells());
        Assert.assertEquals(1_973_760L, report.getH5BucketProbes());
        Assert.assertEquals(406324L, report.getBaCells());
        Assert.assertTrue(report.getGenericAndGatesPerBucket() > params.getCheckBits());
        Assert.assertTrue(report.getGenericHalfGateBytes() > report.getGenericCotBytes());
        Assert.assertTrue(report.getSpecializedEqualRelativeCost() < 1.0);
    }
}
