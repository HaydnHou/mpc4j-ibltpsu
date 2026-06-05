package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT plain end-to-end protocol tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltPlainProtocolTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testNoIntersection() {
        SetPair pair = createSets(1 << 7, 1 << 4, 0, 1);
        BaSsuIbltPlainResult result = runSmall(pair, 11L);
        assertUnion(pair.leftSet, pair.rightSet, result);
    }

    @Test
    public void testAllIntersection() {
        SetPair pair = createSets(1 << 7, 1 << 4, 1 << 4, 1);
        BaSsuIbltPlainResult result = runSmall(pair, 12L);
        assertUnion(pair.leftSet, pair.rightSet, result);
    }

    @Test
    public void testPartialIntersection() {
        SetPair pair = createSets(1 << 7, 1 << 4, 1 << 3, 1);
        BaSsuIbltPlainResult result = runSmall(pair, 13L);
        assertUnion(pair.leftSet, pair.rightSet, result);
    }

    @Test
    public void testZeroElement() {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        leftSet.add(ByteBuffer.wrap(new byte[ELEMENT_BYTE_LENGTH]));
        leftSet.add(element(1));
        leftSet.add(element(2));
        rightSet.add(ByteBuffer.wrap(new byte[ELEMENT_BYTE_LENGTH]));
        rightSet.add(element(3));
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(leftSet.size(), rightSet.size())
            .setDegree(3)
            .setAlphaAnchor(16.0)
            .setPublicPlaceSeed(14L)
            .build();
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutput(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        assertUnion(leftSet, rightSet, result);
        Assert.assertTrue(result.getLeftUnion().contains(ByteBuffer.wrap(new byte[ELEMENT_BYTE_LENGTH])));
    }

    @Test
    public void testDuplicateCandidateSuppressed() {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        leftSet.add(element(1));
        rightSet.add(element(1));
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1, 1)
            .setDegree(3)
            .setAlphaAnchor(8.0)
            .setPublicPlaceSeed(15L)
            .build();
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutput(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        assertUnion(leftSet, rightSet, result);
        Assert.assertEquals(1, result.getLeftUnion().size());
    }

    @Test
    public void testTraceUsesPayloadBoundIdealGate() {
        SetPair pair = createSets(1 << 5, 1 << 3, 1 << 2, 7);
        int nLarge = Math.max(pair.leftSet.size(), pair.rightSet.size());
        int nShadow = Math.min(pair.leftSet.size(), pair.rightSet.size());
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(6.0)
            .setPublicPlaceSeed(16L)
            .build();
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        assertUnion(pair.leftSet, pair.rightSet, result);
        Assert.assertTrue(result.hasBucketTrace());
        Assert.assertEquals(result.getScheduledBucketCount(), result.getBucketTrace().size());
        Set<ByteBuffer> acceptedElements = new HashSet<>();
        for (BaSsuIbltBucketTrace trace : result.getBucketTrace()) {
            BaUpotBucketOutput expectedOutput = BaUpotIdeal.evaluate(trace.getInput());
            Assert.assertEquals(expectedOutput.getCaseType(), trace.getOutput().getCaseType());
            Assert.assertEquals(trace.getBucketIndex(), trace.getInput().getBucketIndex());
            Assert.assertEquals(trace.getBucketIndex(), trace.getOutput().getBucketIndex());
            if (expectedOutput.isSingleton()) {
                Assert.assertArrayEquals(expectedOutput.getElement(), trace.getOutput().getElement());
            }
            if (trace.isAccepted()) {
                Assert.assertTrue(trace.getOutput().isSingleton());
                Assert.assertTrue(acceptedElements.add(ByteBuffer.wrap(trace.getOutput().getElement())));
            }
        }
        Assert.assertEquals(result.getLeftUnion(), acceptedElements);
    }

    @Test
    public void testTraceIncludesAllFixedRetries() {
        SetPair pair = createSets(1 << 5, 1 << 3, 1 << 2, 17);
        int nLarge = Math.max(pair.leftSet.size(), pair.rightSet.size());
        int nShadow = Math.min(pair.leftSet.size(), pair.rightSet.size());
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(6.0)
            .setRetryCount(2)
            .setPublicPlaceSeed(20L)
            .build();
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        assertUnion(pair.leftSet, pair.rightSet, result);
        Assert.assertEquals(result.getScheduledBucketCount(), result.getBucketTrace().size());
        Set<Integer> retryIndexes = new HashSet<>();
        for (BaSsuIbltBucketTrace trace : result.getBucketTrace()) {
            retryIndexes.add(trace.getRetryIndex());
        }
        Assert.assertEquals(2, retryIndexes.size());
        Assert.assertTrue(retryIndexes.contains(0));
        Assert.assertTrue(retryIndexes.contains(1));
    }

    @Test
    public void testCrossLayerBlockingCountRecorded() {
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(1, 1)
            .setDegree(3)
            .setAlphaAnchor(16.0)
            .setPublicPlaceSeed(21L)
            .build();
        Collision collision = findCollision(params);
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        leftSet.add(element(collision.anchorValue));
        rightSet.add(element(collision.shadowValue));
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        assertUnion(leftSet, rightSet, result);
        Assert.assertTrue(result.getCrossLayerBlockingCount() > 0);
    }

    @Test
    public void testLivePlainPayloadTransducerMatchesIdealPeel() {
        SetPair pair = createSets(1 << 6, 1 << 4, 1 << 3, 23);
        int nLarge = Math.max(pair.leftSet.size(), pair.rightSet.size());
        int nShadow = Math.min(pair.leftSet.size(), pair.rightSet.size());
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(17L)
            .build();
        BaSsuIbltPlainResult idealResult = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        BaUpotConfig upotConfig = new BaUpotConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(idealResult.getRecommendedCheckBits())
            .setTagBits(idealResult.getRecommendedCheckBits())
            .build();
        BaUpotPlainPayloadTransducer transducer = BaUpotPlainPayloadTransducer.fromConfig(upotConfig);
        BaSsuIbltPlainResult transducerResult = BaSsuIbltPlainProtocol.runBiOutputWithPlainPayloadTransducer(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params, transducer
        );
        assertUnion(pair.leftSet, pair.rightSet, transducerResult);
        Assert.assertEquals(idealResult.getLeftUnion(), transducerResult.getLeftUnion());
        Assert.assertEquals(transducerResult.getScheduledBucketCount(), transducer.getBucketCount());
        Assert.assertEquals(
            transducerResult.getScheduledBucketCount() * transducer.getCapsuleByteLength(),
            transducer.getCapsuleBytes()
        );
    }

    @Test
    public void testLiveWireMaskedPayloadTransducerMatchesIdealPeel() {
        SetPair pair = createSets(1 << 6, 1 << 4, 1 << 2, 31);
        int nLarge = Math.max(pair.leftSet.size(), pair.rightSet.size());
        int nShadow = Math.min(pair.leftSet.size(), pair.rightSet.size());
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(18L)
            .build();
        BaSsuIbltPlainResult idealResult = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        BaUpotConfig upotConfig = new BaUpotConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(idealResult.getRecommendedCheckBits())
            .setTagBits(idealResult.getRecommendedCheckBits())
            .build();
        BaUpotWireMaskedPayloadTransducer transducer = BaUpotWireMaskedPayloadTransducer.fromConfig(
            upotConfig, new byte[]{0x42, 0x41, 0x2D, 0x55, 0x50, 0x4F, 0x54}
        );
        BaSsuIbltPlainResult transducerResult = BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params, transducer
        );
        assertUnion(pair.leftSet, pair.rightSet, transducerResult);
        Assert.assertEquals(idealResult.getLeftUnion(), transducerResult.getLeftUnion());
        Assert.assertEquals(transducerResult.getScheduledBucketCount(), transducer.getBucketCount());
        Assert.assertEquals(
            transducerResult.getScheduledBucketCount() * transducer.getCapsuleByteLength(),
            transducer.getCapsuleBytes()
        );
    }

    @Test
    public void testLiveCaseGateWireMaskedPayloadTransducerMatchesIdealPeel() {
        SetPair pair = createSets(1 << 6, 1 << 4, 1 << 2, 37);
        int nLarge = Math.max(pair.leftSet.size(), pair.rightSet.size());
        int nShadow = Math.min(pair.leftSet.size(), pair.rightSet.size());
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(19L)
            .build();
        BaSsuIbltPlainResult idealResult = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        BaUpotConfig upotConfig = new BaUpotConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(idealResult.getRecommendedCheckBits())
            .setTagBits(idealResult.getRecommendedCheckBits())
            .build();
        BaUpotCaseGateWireMaskedPayloadTransducer transducer =
            BaUpotCaseGateWireMaskedPayloadTransducer.fromConfig(
                upotConfig, new byte[]{0x43, 0x41, 0x53, 0x45, 0x2D, 0x47, 0x41, 0x54, 0x45}
            );
        BaSsuIbltPlainResult transducerResult = BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params, transducer
        );
        assertUnion(pair.leftSet, pair.rightSet, transducerResult);
        Assert.assertEquals(idealResult.getLeftUnion(), transducerResult.getLeftUnion());
        Assert.assertEquals(transducerResult.getScheduledBucketCount(), transducer.getBucketCount());
        Assert.assertEquals(
            transducerResult.getScheduledBucketCount() * 8,
            transducer.getAggregateGateStats().getAndGateCount()
        );
        Assert.assertEquals(
            transducerResult.getScheduledBucketCount() * transducer.getCapsuleByteLength(),
            transducer.getCapsuleBytes()
        );
    }

    @Test
    public void testSevereImbalanceEndToEnd() {
        int largeSize = 1 << 18;
        int shadowSize = 1 << 10;
        int overlap = shadowSize / 4;
        SetPair pair = createSets(largeSize, shadowSize, overlap, 1);
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(largeSize, shadowSize)
            .setDegree(3)
            .setAlphaAnchor(1.55)
            .setPublicPlaceSeed(20260603L)
            .build();
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutput(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        assertUnion(pair.leftSet, pair.rightSet, result);
        Assert.assertTrue(result.getScheduledBucketCount() > 1_000_000L);
        Assert.assertTrue(result.getScheduledBucketCount() < 1_200_000L);
        BaSsuIbltPlainResult.CostEstimate estimate = result.estimateCost(1596.19, 1033.97, 32.0, 94.0);
        Assert.assertEquals(result.getScheduledBucketCount(), estimate.getBucketCount());
        Assert.assertTrue(estimate.getTotalTimeNanos() > 0);
        Assert.assertTrue(estimate.getTotalBytes() > 0);
    }

    private static BaSsuIbltPlainResult runSmall(SetPair pair, long seed) {
        int nLarge = Math.max(pair.leftSet.size(), pair.rightSet.size());
        int nShadow = Math.min(pair.leftSet.size(), pair.rightSet.size());
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(4.0)
            .setPublicPlaceSeed(seed)
            .build();
        return BaSsuIbltPlainProtocol.runBiOutput(pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params);
    }

    private static void assertUnion(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, BaSsuIbltPlainResult result) {
        Assert.assertTrue(result.isSuccess());
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        int expectedPsica = leftSet.size() + rightSet.size() - expectedUnion.size();
        Assert.assertEquals(expectedUnion.size(), result.getExpectedUnionSize());
        Assert.assertEquals(expectedPsica, result.getPsica());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
        Assert.assertEquals(expectedUnion, result.getLeftOutput().getUnion());
        Assert.assertEquals(expectedUnion, result.getRightOutput().getUnion());
        Assert.assertEquals(expectedPsica, result.getLeftOutput().getPsica());
        Assert.assertEquals(expectedPsica, result.getRightOutput().getPsica());
        Set<ByteBuffer> leftOnly = difference(leftSet, rightSet);
        Set<ByteBuffer> rightOnly = difference(rightSet, leftSet);
        Set<ByteBuffer> expectedAnchorOnly = result.isLeftAnchor() ? leftOnly : rightOnly;
        Set<ByteBuffer> expectedShadowOnly = result.isLeftAnchor() ? rightOnly : leftOnly;
        Set<ByteBuffer> expectedLeftReceived = result.isLeftAnchor() ? rightOnly : leftOnly;
        Set<ByteBuffer> expectedRightReceived = result.isLeftAnchor() ? leftOnly : rightOnly;
        Assert.assertEquals(expectedAnchorOnly, result.getAnchorOnlyElements());
        Assert.assertEquals(expectedShadowOnly, result.getShadowOnlyElements());
        Assert.assertEquals(expectedLeftReceived, result.getLeftReceivedDifference());
        Assert.assertEquals(expectedRightReceived, result.getRightReceivedDifference());
        Assert.assertEquals(expectedAnchorOnly.size(), result.getSignedPeelOutput().getAnchorOnlyCount());
        Assert.assertEquals(expectedShadowOnly.size(), result.getSignedPeelOutput().getShadowOnlyCount());
        Assert.assertEquals(expectedPsica, result.getSharedSingletonCount());
        Assert.assertEquals(expectedUnion.size(), result.getSignedPeelOutput().getAcceptedSingletonCount());
        Assert.assertEquals(0, result.getAnchorResidualCount());
        Assert.assertEquals(0, result.getShadowResidualCount());
    }

    private static Set<ByteBuffer> difference(Set<ByteBuffer> minuend, Set<ByteBuffer> subtrahend) {
        Set<ByteBuffer> output = new HashSet<>(minuend);
        output.removeAll(subtrahend);
        return output;
    }

    private static SetPair createSets(int largeSize, int shadowSize, int overlap, long firstValue) {
        List<ByteBuffer> largeElements = new ArrayList<>(largeSize);
        for (int i = 0; i < largeSize; i++) {
            largeElements.add(element(firstValue + i));
        }
        Set<ByteBuffer> largeSet = new HashSet<>(largeElements);
        Set<ByteBuffer> shadowSet = new HashSet<>();
        for (int i = 0; i < overlap; i++) {
            shadowSet.add(cloneElement(largeElements.get(i)));
        }
        for (int i = overlap; i < shadowSize; i++) {
            shadowSet.add(element(firstValue + largeSize + i - overlap));
        }
        return new SetPair(largeSet, shadowSet);
    }

    private static ByteBuffer element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return ByteBuffer.wrap(byteBuffer.array());
    }

    private static ByteBuffer cloneElement(ByteBuffer element) {
        ByteBuffer duplicate = element.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return ByteBuffer.wrap(bytes);
    }

    private static byte[] toBytes(ByteBuffer element) {
        ByteBuffer duplicate = element.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static Collision findCollision(BaSsuIbltBiUpsuParams params) {
        for (long anchorValue = 1; anchorValue < 4096; anchorValue++) {
            int[] anchorPositions = BaSsuIbltPlacement.positions(params, 0, toBytes(element(anchorValue)));
            for (long shadowValue = 4096; shadowValue < 8192; shadowValue++) {
                int[] shadowPositions = BaSsuIbltPlacement.positions(params, 0, toBytes(element(shadowValue)));
                for (int anchorPosition : anchorPositions) {
                    for (int shadowPosition : shadowPositions) {
                        if (anchorPosition == shadowPosition) {
                            return new Collision(anchorValue, shadowValue);
                        }
                    }
                }
            }
        }
        throw new AssertionError("could not find a bucket collision");
    }

    /**
     * collision.
     */
    private static class Collision {
        /**
         * anchor value.
         */
        private final long anchorValue;
        /**
         * shadow value.
         */
        private final long shadowValue;

        Collision(long anchorValue, long shadowValue) {
            this.anchorValue = anchorValue;
            this.shadowValue = shadowValue;
        }
    }

    /**
     * Pair of sets.
     */
    private static class SetPair {
        /**
         * left set.
         */
        private final Set<ByteBuffer> leftSet;
        /**
         * right set.
         */
        private final Set<ByteBuffer> rightSet;

        SetPair(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet) {
            this.leftSet = leftSet;
            this.rightSet = rightSet;
        }
    }
}
