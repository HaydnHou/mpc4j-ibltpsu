package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P51 fixed-shape RPC payload tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotRpcFixedShapeTest {

    @Test
    public void testFixedResultShapeForPublicStates() {
        int elementByteLength = Long.BYTES;
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(elementByteLength);
        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(9L);
        BaSsuIbltProductionUnionProbeOutput bottom =
            BaSsuIbltProductionUnionProbeOutput.bottom(0, elementByteLength);
        BaSsuIbltProductionUnionProbeOutput singleton =
            BaSsuIbltProductionUnionProbeOutput.singleton(0, element, elementByteLength);
        byte[] bottomEncoded = codec.encode(bottom);
        byte[] singletonEncoded = codec.encode(singleton);
        Assert.assertEquals(codec.byteLength(), bottomEncoded.length);
        Assert.assertEquals(bottomEncoded.length, singletonEncoded.length);
        Assert.assertTrue(codec.decode(3, bottomEncoded).isBottom());
        Assert.assertTrue(codec.decode(3, singletonEncoded).isSingleton());
        Assert.assertArrayEquals(element, codec.decode(3, singletonEncoded).getElement());
    }

    @Test
    public void testNonSingletonResultRejectsNonZeroPayload() {
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(Long.BYTES);
        byte[] malformedBottom = codec.encode(BaSsuIbltProductionUnionProbeOutput.bottom(0, Long.BYTES));
        malformedBottom[1] = 0x01;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, malformedBottom));
        byte[] unknownType = codec.encode(BaSsuIbltProductionUnionProbeOutput.bottom(0, Long.BYTES));
        unknownType[0] = 0x02;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, unknownType));
    }

    @Test
    public void testSenderCapsuleShapeStillCaseIndependent() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(
            config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
            BaSsuIbltProductionUnionProbeTestUtils.schedule(3, 1)
        );
        sender.init(3);
        BaSsuIbltUpBaUpotPublicInput emptyInput =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
        BaSsuIbltUpBaUpotPublicInput singletonInput =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 1);
        BaSsuIbltUpBaUpotPublicInput blockedInput =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 2);
        int expected = config.capsuleByteLength();
        Assert.assertEquals(expected, sender.buildCapsule(
            emptyInput, BaSsuIbltUpBaUpotLocalInput.empty(emptyInput)
        ).getEncoded().length);
        Assert.assertEquals(expected, sender.buildCapsule(
            singletonInput, BaSsuIbltUpBaUpotApiTest.singletonLocalInput(singletonInput, 1L)
        ).getEncoded().length);
        Assert.assertEquals(expected, sender.buildCapsule(
            blockedInput, BaSsuIbltUpBaUpotFixedOnlineShapeTest.blockedLocalInput(blockedInput, 2L)
        ).getEncoded().length);
    }
}
