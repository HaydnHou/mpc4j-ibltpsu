package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BA-SSU-IBLT secure source-layer builder tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltSecureLayerBuilderTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * tag byte length.
     */
    private static final int TAG_BYTE_LENGTH = 24;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 16;

    @Test
    public void testInactivePaddingIsIgnored() {
        BaSsuIbltBiUpsuParams params = params();
        byte[][] fixedInputs = new byte[][]{
            element(1L), element(2L), element(3L), element(4L), element(5L), element(6L)
        };
        boolean[] activeFlags = new boolean[]{true, false, true, false, false, false};
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, 0, ELEMENT_BYTE_LENGTH
        );
        builder.insertAnchors(fixedInputs, activeFlags, tagOutput(fixedInputs));

        long anchorCountSum = 0L;
        long shadowCountSum = 0L;
        for (BaSsuIbltSecureBucketInput bucketInput : builder.getBucketInputs()) {
            anchorCountSum += bucketInput.getAnchor().getCount();
            shadowCountSum += bucketInput.getShadow().getCount();
        }
        Assert.assertEquals(2L * params.getDegree(), anchorCountSum);
        Assert.assertEquals(0L, shadowCountSum);
    }

    @Test
    public void testZeroByteElementIsAValidSingleton() {
        BaSsuIbltBiUpsuParams params = params();
        byte[][] fixedInputs = new byte[][]{new byte[ELEMENT_BYTE_LENGTH], element(11L)};
        boolean[] activeFlags = new boolean[]{true, false};
        BaSsuIbltOprfTagOutput tagOutput = tagOutput(fixedInputs);
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, 0, ELEMENT_BYTE_LENGTH
        );
        builder.insertAnchors(fixedInputs, activeFlags, tagOutput);

        for (int position : builder.positions(fixedInputs[0])) {
            BaSsuIbltSecureBucketInput bucketInput = builder.getBucketInput(position);
            Assert.assertEquals(1, bucketInput.getAnchor().getCount());
            Assert.assertArrayEquals(new byte[ELEMENT_BYTE_LENGTH], bucketInput.getAnchor().getKeyXor());
            Assert.assertArrayEquals(tagOutput.getTag(0), bucketInput.getAnchor().getTagXor());
            Assert.assertTrue(bucketInput.getAnchor().isValidSingleton());
            Assert.assertEquals(0, bucketInput.getShadow().getCount());
        }
    }

    @Test
    public void testSharedSingletonUsesMatchingOprfTags() {
        BaSsuIbltBiUpsuParams params = params();
        byte[] common = element(23L);
        byte[][] anchorInputs = new byte[][]{common};
        byte[][] shadowInputs = new byte[][]{Arrays.copyOf(common, common.length)};
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, 0, ELEMENT_BYTE_LENGTH
        );
        builder.insertAnchors(anchorInputs, tagOutput(anchorInputs));
        builder.insertShadows(shadowInputs, tagOutput(shadowInputs));

        for (int position : builder.positions(common)) {
            BaSsuIbltSecureBucketInput bucketInput = builder.getBucketInput(position);
            Assert.assertTrue(bucketInput.isSharedSingleton());
            Assert.assertFalse(bucketInput.isCrossLayerBlocking());
            Assert.assertArrayEquals(bucketInput.getAnchor().getTagXor(), bucketInput.getShadow().getTagXor());
            Assert.assertArrayEquals(bucketInput.getAnchor().getCheckXor(), bucketInput.getShadow().getCheckXor());
        }
    }

    @Test
    public void testDeleteReturnsBucketToEmpty() {
        BaSsuIbltBiUpsuParams params = params();
        byte[][] fixedInputs = new byte[][]{element(31L)};
        BaSsuIbltOprfTagOutput tagOutput = tagOutput(fixedInputs);
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, 0, ELEMENT_BYTE_LENGTH
        );
        builder.insertAnchors(fixedInputs, tagOutput);
        builder.deleteAnchor(fixedInputs[0], tagOutput.getTag(0), tagOutput.getCheck(0));

        for (int position : builder.positions(fixedInputs[0])) {
            Assert.assertTrue(builder.getBucketInput(position).isEmpty());
        }
    }

    @Test
    public void testFailedDeleteDoesNotMutateTouchedBuckets() {
        BaSsuIbltBiUpsuParams params = params();
        byte[] insertedElement = element(41L);
        byte[] partiallyOverlappingElement = findPartiallyOverlappingElement(params, insertedElement);
        byte[][] fixedInputs = new byte[][]{insertedElement};
        BaSsuIbltOprfTagOutput tagOutput = tagOutput(fixedInputs);
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, 0, ELEMENT_BYTE_LENGTH
        );
        builder.insertAnchors(fixedInputs, tagOutput);
        int[] touchedPositions = builder.positions(insertedElement);
        byte[][] beforeKeys = new byte[touchedPositions.length][];
        byte[][] beforeTags = new byte[touchedPositions.length][];
        byte[][] beforeChecks = new byte[touchedPositions.length][];
        for (int index = 0; index < touchedPositions.length; index++) {
            BaSsuIbltSecureCellView anchor = builder.getBucketInput(touchedPositions[index]).getAnchor();
            beforeKeys[index] = anchor.getKeyXor();
            beforeTags[index] = anchor.getTagXor();
            beforeChecks[index] = anchor.getCheckXor();
        }
        byte[][] failedInputs = new byte[][]{partiallyOverlappingElement};
        BaSsuIbltOprfTagOutput failedTagOutput = tagOutput(failedInputs);
        Assert.assertThrows(IllegalStateException.class,
            () -> builder.deleteAnchor(failedInputs[0], failedTagOutput.getTag(0), failedTagOutput.getCheck(0)));
        for (int index = 0; index < touchedPositions.length; index++) {
            BaSsuIbltSecureCellView anchor = builder.getBucketInput(touchedPositions[index]).getAnchor();
            Assert.assertEquals(1, anchor.getCount());
            Assert.assertArrayEquals(beforeKeys[index], anchor.getKeyXor());
            Assert.assertArrayEquals(beforeTags[index], anchor.getTagXor());
            Assert.assertArrayEquals(beforeChecks[index], anchor.getCheckXor());
        }
    }

    @Test
    public void testMalformedInputsRejected() {
        BaSsuIbltBiUpsuParams params = params();
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureLayerBuilder.fromParams(null, 0, ELEMENT_BYTE_LENGTH));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureLayerBuilder.fromParams(params, params.getRetryCount(), ELEMENT_BYTE_LENGTH));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltSecureLayerBuilder(params, 0, ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH,
                TAG_BYTE_LENGTH));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureCellView.of(-1, element(1L), filled(TAG_BYTE_LENGTH, (byte) 1),
                filled(CHECK_BYTE_LENGTH, (byte) 2)));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureBucketInput.of(0,
                BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH),
                BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH + 1, CHECK_BYTE_LENGTH)));

        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(params, 0, ELEMENT_BYTE_LENGTH);
        byte[][] fixedInputs = new byte[][]{element(1L), element(2L)};
        BaSsuIbltOprfTagOutput tagOutput = tagOutput(fixedInputs);
        Assert.assertThrows(IllegalArgumentException.class, () -> builder.insertAnchors(null, tagOutput));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> builder.insertAnchors(fixedInputs, null, tagOutput));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> builder.insertAnchors(fixedInputs, new boolean[]{true}, tagOutput));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> builder.insertAnchors(new byte[][]{new byte[ELEMENT_BYTE_LENGTH - 1], element(2L)}, tagOutput));
        BaSsuIbltOprfTagOutput wrongTagLengthOutput = new BaSsuIbltOprfTagOutput(
            TAG_BYTE_LENGTH + 1, CHECK_BYTE_LENGTH,
            new byte[][]{filled(TAG_BYTE_LENGTH + 1, (byte) 1), filled(TAG_BYTE_LENGTH + 1, (byte) 2)},
            new byte[][]{filled(CHECK_BYTE_LENGTH, (byte) 3), filled(CHECK_BYTE_LENGTH, (byte) 4)}
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> builder.insertAnchors(fixedInputs, wrongTagLengthOutput));
        Assert.assertThrows(IllegalStateException.class,
            () -> builder.deleteAnchor(element(99L), tagOutput.getTag(0), tagOutput.getCheck(0)));
        Assert.assertThrows(IllegalArgumentException.class, () -> builder.getBucketInput(-1));
    }

    private static BaSsuIbltBiUpsuParams params() {
        return new BaSsuIbltBiUpsuParams.Builder(64, 16)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(CHECK_BYTE_LENGTH * Byte.SIZE)
            .setTagBits(TAG_BYTE_LENGTH * Byte.SIZE)
            .setPublicPlaceSeed(20260604L)
            .build();
    }

    private static BaSsuIbltOprfTagOutput tagOutput(byte[][] inputs) {
        byte[][] tags = new byte[inputs.length][];
        byte[][] checks = new byte[inputs.length][];
        for (int index = 0; index < inputs.length; index++) {
            tags[index] = BaSsuIbltOprfTagPipeline.tagFromPrf(inputs[index], TAG_BYTE_LENGTH);
            checks[index] = BaSsuIbltOprfTagPipeline.checkFromTag(tags[index], CHECK_BYTE_LENGTH);
        }
        return new BaSsuIbltOprfTagOutput(TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH, tags, checks);
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }

    private static byte[] filled(int byteLength, byte value) {
        byte[] bytes = new byte[byteLength];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] findPartiallyOverlappingElement(BaSsuIbltBiUpsuParams params, byte[] insertedElement) {
        int[] insertedPositions = BaSsuIbltPlacement.positions(params, 0, insertedElement);
        for (long value = 1_000L; value < 10_000L; value++) {
            byte[] candidate = element(value);
            int[] candidatePositions = BaSsuIbltPlacement.positions(params, 0, candidate);
            boolean hasOverlap = false;
            boolean hasNonOverlap = false;
            for (int candidatePosition : candidatePositions) {
                boolean overlap = false;
                for (int insertedPosition : insertedPositions) {
                    if (candidatePosition == insertedPosition) {
                        overlap = true;
                        break;
                    }
                }
                hasOverlap |= overlap;
                hasNonOverlap |= !overlap;
            }
            if (hasOverlap && hasNonOverlap) {
                return candidate;
            }
        }
        throw new AssertionError("could not find a partially overlapping element");
    }
}
