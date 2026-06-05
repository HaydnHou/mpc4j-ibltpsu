package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BA-SSU-IBLT OPRF tag/check pipeline tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltOprfTagPipelineTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testPlacementIndependentFromTagDomain() {
        BaSsuIbltBiUpsuParams params = params(20260604L);
        byte[] element = element(7);
        int[] before = BaSsuIbltPlacement.positions(params, 0, element);
        byte[] defaultTag = BaSsuIbltOprfTagPipeline.referenceTagWithDomain(
            BaSsuIbltOprfTagPipeline.DOMAIN_TAG, element, 23
        );
        byte[] alternateTag = BaSsuIbltOprfTagPipeline.referenceTagWithDomain(
            BaSsuIbltOprfTagPipeline.DOMAIN_TEST_TAG, element, 23
        );
        int[] after = BaSsuIbltPlacement.positions(params, 0, element);
        Assert.assertFalse(Arrays.equals(defaultTag, alternateTag));
        Assert.assertArrayEquals(before, after);
    }

    @Test
    public void testPlacementSeedDoesNotAffectTagDerivation() {
        byte[] element = element(11);
        BaSsuIbltBiUpsuParams firstParams = params(20260604L);
        BaSsuIbltBiUpsuParams secondParams = params(20260605L);
        Assert.assertArrayEquals(
            BaSsuIbltOprfTagPipeline.referenceTag(firstParams, element),
            BaSsuIbltOprfTagPipeline.referenceTag(secondParams, element)
        );
        Assert.assertArrayEquals(
            BaSsuIbltOprfTagPipeline.referenceCheck(firstParams, element),
            BaSsuIbltOprfTagPipeline.referenceCheck(secondParams, element)
        );
        Assert.assertFalse(samePositionForAllElements(firstParams, secondParams));
    }

    @Test
    public void testSeparatedTagCheckAndUpotDomains() {
        byte[] element = element(13);
        byte[] tag = BaSsuIbltOprfTagPipeline.referenceTag(element, 23);
        byte[] check = BaSsuIbltOprfTagPipeline.checkFromTag(tag, 23);
        byte[] upot = BaSsuIbltOprfTagPipeline.upotLocal(3, 1, tag, 23);
        Assert.assertFalse(Arrays.equals(tag, check));
        Assert.assertFalse(Arrays.equals(tag, upot));
        Assert.assertFalse(Arrays.equals(check, upot));
        Assert.assertArrayEquals(check, BaUpotIdeal.digest(element, 23));
    }

    @Test
    public void testParamsCheckMatchesBucketCheckWhenTagBitsExceedCheckBits() {
        byte[] element = element(17);
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(16, 4)
            .setCheckBits(128)
            .setTagBits(192)
            .setPublicPlaceSeed(20260604L)
            .build();
        Assert.assertArrayEquals(
            BaSsuIbltOprfTagPipeline.referenceCheck(params, element),
            BaUpotIdeal.digest(element, BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits()))
        );
    }

    @Test
    public void testAnchorTableCheckWorksWhenTagBitsExceedCheckBits() {
        byte[] element = element(19);
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(16, 4)
            .setCheckBits(128)
            .setTagBits(192)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        table.insertAnchor(element);
        boolean foundSingleton = false;
        for (int position : table.positions(element)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            if (output.isSingleton()) {
                Assert.assertEquals(BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, output.getCaseType());
                Assert.assertArrayEquals(element, output.getElement());
                foundSingleton = true;
            }
        }
        Assert.assertTrue(foundSingleton);
    }

    @Test
    public void testMalformedInputsRejected() {
        BaSsuIbltBiUpsuParams params = params(20260604L);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagPipeline.referenceTag(null, 16));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagPipeline.referenceCheck((BaSsuIbltBiUpsuParams) null, element(1)));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagPipeline.referenceCheck(element(1), 0));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagPipeline.equalCheck(element(1), null));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltAnchorTable(null, 0, ELEMENT_BYTE_LENGTH));
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        Assert.assertThrows(IllegalArgumentException.class, () -> table.insertAnchors(null));
        Assert.assertThrows(IllegalArgumentException.class, () -> table.insertAnchor((byte[]) null));
        Assert.assertThrows(IllegalArgumentException.class, () -> table.insertAnchor((ByteBuffer) null));
    }

    private static boolean samePositionForAllElements(BaSsuIbltBiUpsuParams firstParams,
                                                      BaSsuIbltBiUpsuParams secondParams) {
        for (int i = 0; i < 128; i++) {
            if (!Arrays.equals(
                BaSsuIbltPlacement.positions(firstParams, 0, element(i)),
                BaSsuIbltPlacement.positions(secondParams, 0, element(i))
            )) {
                return false;
            }
        }
        return true;
    }

    private static BaSsuIbltBiUpsuParams params(long seed) {
        return new BaSsuIbltBiUpsuParams.Builder(64, 16)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(seed)
            .build();
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }
}
