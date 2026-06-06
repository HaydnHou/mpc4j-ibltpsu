package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P43 fixed online capsule shape tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotFixedOnlineShapeTest {

    @Test
    public void testOnlineCapsuleLengthIsIndependentOfLocalState() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(3, 1)
            );
        BaSsuIbltUpBaUpotPublicInput emptyInput = BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
        BaSsuIbltUpBaUpotPublicInput singletonInput = BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 1);
        BaSsuIbltUpBaUpotPublicInput blockedInput = BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 2);
        sender.init(3);
        int expected = config.capsuleByteLength();
        Assert.assertEquals(expected, sender.buildCapsule(emptyInput, BaSsuIbltUpBaUpotLocalInput.empty(emptyInput))
            .getEncoded().length);
        Assert.assertEquals(expected, sender.buildCapsule(
            singletonInput, BaSsuIbltUpBaUpotApiTest.singletonLocalInput(singletonInput, 1L)
        ).getEncoded().length);
        Assert.assertEquals(expected, sender.buildCapsule(blockedInput, blockedLocalInput(blockedInput, 2L))
            .getEncoded().length);
    }

    @Test
    public void testOnlineCapsuleDoesNotCarryClearSingletonMaterial() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 2)
            );
        BaSsuIbltUpBaUpotPublicInput publicInput = BaSsuIbltUpBaUpotApiTest.publicInput(1);
        sender.init(1);
        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(9L);
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(
            element, BaSsuIbltProductionUnionProbeTestUtils.TAG_BYTE_LENGTH
        );
        byte[] check = BaSsuIbltOprfTagPipeline.checkFromTag(
            tag, BaSsuIbltProductionUnionProbeTestUtils.CHECK_BYTE_LENGTH
        );
        byte[] encoded = sender.buildCapsule(publicInput, BaSsuIbltUpBaUpotLocalInput.singleton(
            publicInput, element, tag, check, new byte[publicInput.getAuthTagByteLength()]
        )).getEncoded();
        Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(encoded, element));
        Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(encoded, tag));
        Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(encoded, check));
    }

    static BaSsuIbltUpBaUpotLocalInput blockedLocalInput(BaSsuIbltUpBaUpotPublicInput publicInput, long value) {
        BaSsuIbltSecureCellView cell = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(value);
        return BaSsuIbltUpBaUpotLocalInput.blocked(
            publicInput, cell.getKeyXor(), cell.getTagXor(), cell.getCheckXor(),
            new byte[publicInput.getAuthTagByteLength()]
        );
    }
}
