package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P36 fixed-shape capsule tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeFixedShapeTest {

    @Test
    public void testCapsuleLengthIsIndependentOfLocalState() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeCodec codec = new BaSsuIbltProductionUnionProbeCodec(
            config, BaSsuIbltProductionUnionProbeTestUtils.seed()
        );
        int expectedLength = config.capsuleByteLength();
        Assert.assertEquals(expectedLength, codec.encode(0, BaSsuIbltProductionUnionProbeTestUtils.emptyCell())
            .getEncoded().length);
        Assert.assertEquals(expectedLength, codec.encode(0, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(11L))
            .getEncoded().length);
        Assert.assertEquals(expectedLength, codec.encode(0, BaSsuIbltProductionUnionProbeTestUtils.blockedCell(12L))
            .getEncoded().length);
    }

    @Test
    public void testCapsuleDoesNotCarryClearSingletonMaterial() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeCodec codec = new BaSsuIbltProductionUnionProbeCodec(
            config, BaSsuIbltProductionUnionProbeTestUtils.seed()
        );
        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(13L);
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(
            element, BaSsuIbltProductionUnionProbeTestUtils.TAG_BYTE_LENGTH
        );
        byte[] check = BaSsuIbltOprfTagPipeline.checkFromTag(
            tag, BaSsuIbltProductionUnionProbeTestUtils.CHECK_BYTE_LENGTH
        );
        byte[] encoded = codec.encode(1, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(element)).getEncoded();
        Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(encoded, element));
        Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(encoded, tag));
        Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(encoded, check));
    }
}
