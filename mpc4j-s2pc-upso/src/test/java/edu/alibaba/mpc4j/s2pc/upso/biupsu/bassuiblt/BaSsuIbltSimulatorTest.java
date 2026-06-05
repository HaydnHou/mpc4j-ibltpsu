package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * BA-SSU-IBLT simulator tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltSimulatorTest {
    /**
     * small set size.
     */
    private static final int SMALL_LARGE_SIZE = 1 << 7;
    /**
     * small shadow test size.
     */
    private static final int SMALL_SHADOW_SIZE = 1 << 4;

    @Test
    public void testPlacementUsesCommonPublicPositions() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1 << 10, 1 << 5)
            .setDegree(3)
            .setAlphaAnchor(1.75)
            .setPublicPlaceSeed(20260603L)
            .build();
        int[] positions = BaSsuIbltPlacement.positions(params, 0, 7);
        Assert.assertEquals(params.getDegree(), positions.length);
        for (int i = 0; i < positions.length; i++) {
            int partitionStart = (int) (((long) i * params.getTableLength()) / params.getDegree());
            int partitionEnd = (int) (((long) (i + 1) * params.getTableLength()) / params.getDegree());
            Assert.assertTrue(positions[i] >= partitionStart);
            Assert.assertTrue(positions[i] < partitionEnd);
        }
        int[] samePositions = BaSsuIbltPlacement.positions(params, 0, 7);
        Assert.assertArrayEquals(positions, samePositions);
    }

    @Test
    public void testCheckBitsUseUnionBound() {
        long nTests = (1L << 20) * BaSsuIbltBiUpsuParams.DEFAULT_CHECKS_PER_BUCKET;
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1 << 10, 1 << 5)
            .setNTests(nTests)
            .build();
        int expected = BaSsuIbltBiUpsuParams.DEFAULT_LAMBDA
            + BaSsuIbltBiUpsuParams.ceilLog2(nTests)
            + BaSsuIbltBiUpsuParams.DEFAULT_MARGIN_BITS;
        Assert.assertEquals(expected, params.getCheckBits());
        Assert.assertTrue(params.getTagBits() >= params.getCheckBits());
    }

    @Test
    public void testSmallNoIntersection() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(SMALL_LARGE_SIZE, SMALL_SHADOW_SIZE)
            .setDegree(3)
            .setAlphaAnchor(3.0)
            .setPublicPlaceSeed(1L)
            .build();
        BaSsuIbltSimulator.Result result = BaSsuIbltSimulator.simulate(params, 0.0);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(SMALL_LARGE_SIZE + SMALL_SHADOW_SIZE, result.getUnion().length);
        Assert.assertEquals(result.getExpectedUnionSize(), result.getUnion().length);
    }

    @Test
    public void testSmallAllIntersection() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(SMALL_LARGE_SIZE, SMALL_SHADOW_SIZE)
            .setDegree(3)
            .setAlphaAnchor(3.0)
            .setPublicPlaceSeed(2L)
            .build();
        BaSsuIbltSimulator.Result result = BaSsuIbltSimulator.simulate(params, 1.0);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(SMALL_LARGE_SIZE, result.getUnion().length);
        Assert.assertEquals(result.getExpectedUnionSize(), result.getUnion().length);
    }

    @Test
    public void testSmallPartialIntersection() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(SMALL_LARGE_SIZE, SMALL_SHADOW_SIZE)
            .setDegree(4)
            .setAlphaAnchor(3.0)
            .setPublicPlaceSeed(3L)
            .build();
        BaSsuIbltSimulator.Result result = BaSsuIbltSimulator.simulate(params, 0.5);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(SMALL_LARGE_SIZE + SMALL_SHADOW_SIZE / 2, result.getUnion().length);
        Assert.assertEquals(result.getExpectedUnionSize(), result.getUnion().length);
    }

    @Test
    public void testSevereImbalanceProfileSearch() {
        double[] overlapRates = new double[] {0.0, 0.01, 0.25, 0.5, 0.95, 1.0};
        int[] degrees = new int[] {3, 4};
        double[] alphas = new double[] {1.55, 1.65, 1.75, 2.0};
        List<BaSsuIbltProfileSearcher.ProfileReport> reports = BaSsuIbltProfileSearcher.search(
            1 << 18, 1 << 10, overlapRates, degrees, alphas, 1
        );
        Assert.assertEquals(degrees.length * alphas.length, reports.size());
        for (BaSsuIbltProfileSearcher.ProfileReport report : reports) {
            Assert.assertEquals(1 << 18, report.getNLarge());
            Assert.assertEquals(1 << 10, report.getNShadow());
            Assert.assertTrue(report.getAverageScheduledBucketCount() > 0);
            Assert.assertTrue(report.getMaxRecommendedCheckBits() > BaSsuIbltBiUpsuParams.DEFAULT_LAMBDA);
        }
    }
}
