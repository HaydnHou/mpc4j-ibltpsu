package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P43 malformed capsule tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotMalformedCapsuleTest {

    @Test
    public void testMalformedCapsuleLengthRejected() {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        Assert.assertThrows(IllegalArgumentException.class, () ->
            new BaSsuIbltProductionUnionProbeCapsule(0, new byte[config.capsuleByteLength() - 1],
                config.capsuleByteLength()));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            new BaSsuIbltProductionUnionProbeCapsule(0, new byte[config.capsuleByteLength() + 1],
                config.capsuleByteLength()));
    }

    @Test
    public void testMalformedFixedResultLengthRejected() {
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(Long.BYTES);
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, null));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, new byte[codec.byteLength() - 1]));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, new byte[codec.byteLength() + 1]));
    }

    @Test
    public void testMalformedFixedResultTypeRejected() {
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(Long.BYTES);
        byte[] encoded = new byte[codec.byteLength()];
        encoded[0] = 0x7F;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, encoded));
    }

    @Test
    public void testMalformedFixedResultBottomPayloadRejected() {
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(Long.BYTES);
        byte[] encoded = codec.encode(BaSsuIbltProductionUnionProbeOutput.bottom(0, Long.BYTES));
        encoded[encoded.length - 1] = 0x01;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, encoded));
    }

    @Test
    public void testMalformedFixedResultBucketIndexRejected() {
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(Long.BYTES);
        byte[] encoded = codec.encode(BaSsuIbltProductionUnionProbeOutput.bottom(0, Long.BYTES));
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(-1, encoded));
    }

    @Test
    public void testFixedResultDecodeMakesDefensiveSingletonCopy() {
        BaSsuIbltProductionUnionProbeResultCodec codec =
            new BaSsuIbltProductionUnionProbeResultCodec(Long.BYTES);
        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(7L);
        byte[] encoded = codec.encode(BaSsuIbltProductionUnionProbeOutput.singleton(0, element, element.length));
        BaSsuIbltProductionUnionProbeOutput decoded = codec.decode(0, encoded);
        encoded[1] ^= 0x01;
        Assert.assertArrayEquals(element, decoded.getElement());
    }
}
