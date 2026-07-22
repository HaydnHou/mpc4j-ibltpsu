package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import org.junit.Assert;
import org.junit.Test;

/**
 * RTT-aware public opening-policy tests.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class SsmOpeningModeTest {
    @Test
    public void testExplicitModes() {
        Assert.assertFalse(SsmOpeningMode.BALANCED_TWO_PHASE.useAllToAll(5, 1024, 100.0, 100.0));
        Assert.assertTrue(SsmOpeningMode.ALL_TO_ALL_ONE_PHASE.useAllToAll(5, 1024, 0.0, 0.0));
    }

    @Test
    public void testAutoUsesPublicBandwidthDelayProduct() {
        Assert.assertFalse(SsmOpeningMode.AUTO.useAllToAll(5, 1 << 20, 20.0, 50.0));
        Assert.assertTrue(SsmOpeningMode.AUTO.useAllToAll(5, 1024, 20.0, 400.0));
        Assert.assertFalse(SsmOpeningMode.AUTO.useAllToAll(5, 1024, 0.0, 400.0));
        Assert.assertFalse(SsmOpeningMode.AUTO.useAllToAll(5, 1024, 20.0, 0.0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsNegativeLength() {
        SsmOpeningMode.AUTO.useAllToAll(5, -1, 20.0, 100.0);
    }
}
