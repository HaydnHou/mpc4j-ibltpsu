package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P36 remote-state-hiding readiness tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeRemoteHidingTest {

    @Test
    public void testBackendReadinessRequiresRemoteStateHidingEvaluator() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        Assert.assertTrue(config.isLocalRemoteDecodeFree());
        Assert.assertTrue(config.hasFixedShapeCapsules());
        Assert.assertTrue(config.opensOnlySourceAgnosticOutput());
        Assert.assertFalse(config.hasRemoteStateHidingEvaluator());
        Assert.assertFalse(config.isQueuePeelProductionReady());
        Assert.assertTrue(config.getProductionReadinessReason().contains("remote bucket state"));
    }

    @Test
    public void testReferenceDecoderRejectsProductionOpaqueCapsulesEvenWithSameSeed() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltProductionUnionProbeCodec productionCodec =
            new BaSsuIbltProductionUnionProbeCodec(config, seed);
        BaSsuIbltProductionUnionProbeReferenceCodec referenceCodec =
            new BaSsuIbltProductionUnionProbeReferenceCodec(config, seed);
        BaSsuIbltProductionUnionProbeCapsule productionSingleton =
            productionCodec.encode(2, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(2L));
        BaSsuIbltProductionUnionProbeCapsule productionEmpty =
            productionCodec.encode(2, BaSsuIbltProductionUnionProbeTestUtils.emptyCell());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> referenceCodec.open(2, productionSingleton, productionEmpty));
    }

    @Test
    public void testPublicOutputCannotRepresentSourceOrCase() {
        BaSsuIbltProductionUnionProbeOutput bottom =
            BaSsuIbltProductionUnionProbeOutput.bottom(0, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH);
        Assert.assertFalse(bottom.isSingleton());
        BaSsuIbltProductionUnionProbeOutput singleton =
            BaSsuIbltProductionUnionProbeOutput.singleton(
                1,
                BaSsuIbltProductionUnionProbeTestUtils.element(1L),
                BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH
            );
        Assert.assertTrue(singleton.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(1L), singleton.getElement());
    }
}
