package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT fixed bucket transcript tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltFixedBucketTranscriptTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testDirectAllBucketsScheduleCanonical() {
        BaSsuIbltBiUpsuParams params = params(64, 16, 2);
        BaSsuIbltFixedBucketSchedule schedule = BaSsuIbltFixedBucketSchedule.directAllBuckets(params);
        Assert.assertEquals(BaSsuIbltFixedBucketSchedule.ScheduleMode.DIRECT_ALL_BUCKETS, schedule.getMode());
        Assert.assertEquals(2, schedule.getRetryCount());
        Assert.assertEquals(params.getTableLength(), schedule.getTableLength());
        Assert.assertEquals((long) 2 * params.getTableLength(), schedule.getTotalBucketCount());
        for (int retryIndex = 0; retryIndex < schedule.getRetryCount(); retryIndex++) {
            int[] bucketIndices = schedule.getBucketIndices(retryIndex);
            Assert.assertEquals(params.getTableLength(), bucketIndices.length);
            for (int offset = 0; offset < bucketIndices.length; offset++) {
                Assert.assertEquals(offset, bucketIndices[offset]);
                Assert.assertEquals((long) retryIndex * params.getTableLength() + offset,
                    schedule.globalOrdinal(retryIndex, offset));
            }
        }
    }

    @Test
    public void testScheduleIdenticalAcrossOverlapRates() {
        BaSsuIbltBiUpsuParams params = params(64, 16, 2);
        List<BaSsuIbltTranscriptResult> results = List.of(
            BaSsuIbltTranscriptResult.runBiOutput(
                createSets(64, 16, 0, 1).leftSet,
                createSets(64, 16, 0, 1).rightSet,
                ELEMENT_BYTE_LENGTH, params
            ),
            BaSsuIbltTranscriptResult.runBiOutput(
                createSets(64, 16, 8, 1).leftSet,
                createSets(64, 16, 8, 1).rightSet,
                ELEMENT_BYTE_LENGTH, params
            ),
            BaSsuIbltTranscriptResult.runBiOutput(
                createSets(64, 16, 16, 1).leftSet,
                createSets(64, 16, 16, 1).rightSet,
                ELEMENT_BYTE_LENGTH, params
            )
        );
        BaSsuIbltFixedBucketSchedule expected = results.get(0).getSchedule();
        for (BaSsuIbltTranscriptResult result : results) {
            Assert.assertEquals(expected.getTotalBucketCount(), result.getTotalBucketTranscriptCount());
            for (int retryIndex = 0; retryIndex < expected.getRetryCount(); retryIndex++) {
                Assert.assertArrayEquals(
                    expected.getBucketIndices(retryIndex),
                    result.getSchedule().getBucketIndices(retryIndex)
                );
                Assert.assertArrayEquals(
                    expected.getBucketIndices(retryIndex),
                    result.getRetryTranscripts().get(retryIndex).getBucketIndices()
                );
            }
        }
    }

    @Test
    public void testTranscriptOutputMatchesPlainProtocolWithoutPlainTrace() {
        SetPair pair = createSets(64, 16, 4, 11);
        BaSsuIbltBiUpsuParams params = params(64, 16, 1);
        BaSsuIbltPlainResult plainResult = BaSsuIbltPlainProtocol.runBiOutput(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        BaSsuIbltTranscriptResult transcriptResult = BaSsuIbltTranscriptResult.runBiOutput(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params
        );
        Assert.assertFalse(plainResult.hasBucketTrace());
        Assert.assertFalse(transcriptResult.getPlainResult().hasBucketTrace());
        Assert.assertEquals(plainResult.isSuccess(), transcriptResult.getPlainResult().isSuccess());
        Assert.assertEquals(plainResult.getLeftUnion(), transcriptResult.getPlainResult().getLeftUnion());
        Assert.assertEquals(plainResult.getRightUnion(), transcriptResult.getPlainResult().getRightUnion());
        Assert.assertEquals(plainResult.getPsica(), transcriptResult.getPlainResult().getPsica());
        Assert.assertEquals(params.getTableLength(), transcriptResult.getBucketTranscripts().size());
    }

    @Test
    public void testTranscriptEvaluatorSeesFixedBucketCount() {
        SetPair pair = createSets(32, 8, 2, 21);
        BaSsuIbltBiUpsuParams params = params(32, 8, 2);
        BaUpotConfig upotConfig = new BaUpotConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(params.getCheckBits())
            .setTagBits(params.getTagBits())
            .build();
        BaUpotPlainPayloadTransducer transducer = BaUpotPlainPayloadTransducer.fromConfig(upotConfig);
        BaSsuIbltTranscriptResult result = BaSsuIbltTranscriptResult.runBiOutput(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params, transducer
        );
        Assert.assertTrue(result.getPlainResult().isSuccess());
        Assert.assertEquals(result.getSchedule().getTotalBucketCount(), transducer.getBucketCount());
        Assert.assertEquals(result.getSchedule().getTotalBucketCount(), result.getBucketTranscripts().size());
        Assert.assertEquals(transducer.getBucketCount() * transducer.getCapsuleByteLength(),
            transducer.getCapsuleBytes());
    }

    @Test
    public void testDebugTranscriptEvaluatorDoesNotDrivePlainOutput() {
        SetPair pair = createSets(32, 8, 2, 31);
        BaSsuIbltBiUpsuParams params = params(32, 8, 1);
        BaUpotBucketEvaluator blockedEvaluator = input -> BaUpotBucketOutput.blocked(input.getBucketIndex());
        BaSsuIbltTranscriptResult result = BaSsuIbltTranscriptResult.runBiOutput(
            pair.leftSet, pair.rightSet, ELEMENT_BYTE_LENGTH, params, blockedEvaluator
        );
        Assert.assertTrue(result.getPlainResult().isSuccess());
        Set<ByteBuffer> expectedUnion = new HashSet<>(pair.leftSet);
        expectedUnion.addAll(pair.rightSet);
        Assert.assertEquals(expectedUnion, result.getPlainResult().getLeftUnion());
        for (BaSsuIbltBucketTranscript transcript : result.getBucketTranscripts()) {
            Assert.assertEquals(BaUpotBucketOutput.CaseType.BLOCKED, transcript.getDebugOutput().getCaseType());
        }
    }

    @Test
    public void testMalformedRetryTranscriptRejected() {
        BaSsuIbltBiUpsuParams params = params(2, 1, 1);
        BaSsuIbltFixedBucketSchedule schedule = BaSsuIbltFixedBucketSchedule.directAllBuckets(params);
        BaUpotBucketInput input = BaUpotBucketInput.empty(1, ELEMENT_BYTE_LENGTH, 16);
        BaSsuIbltBucketTranscript wrongFirstBucket = BaSsuIbltBucketTranscript.of(
            0, 0, 1, input, BaUpotBucketOutput.empty(1)
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltRetryTranscript(schedule, 0, List.of(wrongFirstBucket)));
    }

    private static BaSsuIbltBiUpsuParams params(int nLarge, int nShadow, int retryCount) {
        return new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setDegree(3)
            .setAlphaAnchor(6.0)
            .setRetryCount(retryCount)
            .setPublicPlaceSeed(20260604L)
            .build();
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
