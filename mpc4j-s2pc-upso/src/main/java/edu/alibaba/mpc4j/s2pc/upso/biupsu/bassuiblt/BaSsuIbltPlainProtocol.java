package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plain BA-SSU-IBLT end-to-end protocol.
 *
 * <p>This class replaces BA-UPOT by exact local case evaluation. It is only a correctness harness for the IBLT peel,
 * retry, duplicate coalescing, and bi-output union semantics.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltPlainProtocol {
    /**
     * private constructor.
     */
    private BaSsuIbltPlainProtocol() {
        // empty
    }

    /**
     * Runs the plain bi-output protocol. The larger input is used as the anchor side.
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength fixed element byte length.
     * @param params parameters.
     * @return result.
     */
    public static BaSsuIbltPlainResult runBiOutput(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                   int elementByteLength, BaSsuIbltBiUpsuParams params) {
        return runBiOutputInternal(leftSet, rightSet, elementByteLength, params, false);
    }

    /**
     * Runs the plain bi-output protocol and records all fixed-retry bucket traces.
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength fixed element byte length.
     * @param params parameters.
     * @return result with bucket trace.
     */
    static BaSsuIbltPlainResult runBiOutputWithTrace(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                     int elementByteLength,
                                                     BaSsuIbltBiUpsuParams params) {
        return runBiOutputInternal(leftSet, rightSet, elementByteLength, params, true);
    }

    private static BaSsuIbltPlainResult runBiOutputInternal(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                            int elementByteLength,
                                                            BaSsuIbltBiUpsuParams params, boolean collectTrace) {
        return runBiOutputInternal(leftSet, rightSet, elementByteLength, params, collectTrace,
            BaUpotIdealEvaluator.getInstance());
    }

    /**
     * Runs the plain bi-output protocol through a live payload transducer.
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength fixed element byte length.
     * @param params parameters.
     * @param transducer plain payload transducer.
     * @return result with bucket trace.
     */
    static BaSsuIbltPlainResult runBiOutputWithPlainPayloadTransducer(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        BaUpotPlainPayloadTransducer transducer) {
        return runBiOutputWithBucketEvaluator(leftSet, rightSet, elementByteLength, params, transducer);
    }

    /**
     * Runs the plain bi-output protocol with an explicit bucket evaluator.
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength fixed element byte length.
     * @param params parameters.
     * @param bucketEvaluator bucket evaluator.
     * @return result with bucket trace.
     */
    static BaSsuIbltPlainResult runBiOutputWithBucketEvaluator(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        BaUpotBucketEvaluator bucketEvaluator) {
        return runBiOutputInternal(leftSet, rightSet, elementByteLength, params, true, bucketEvaluator);
    }

    private static BaSsuIbltPlainResult runBiOutputInternal(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                            int elementByteLength,
                                                            BaSsuIbltBiUpsuParams params, boolean collectTrace,
                                                            BaUpotBucketEvaluator bucketEvaluator) {
        Set<ByteKey> left = normalize(leftSet, elementByteLength);
        Set<ByteKey> right = normalize(rightSet, elementByteLength);
        boolean leftAnchor = left.size() >= right.size();
        Set<ByteKey> anchorSet = leftAnchor ? left : right;
        Set<ByteKey> shadowSet = leftAnchor ? right : left;
        if (anchorSet.size() > params.getNLarge()) {
            throw new IllegalArgumentException("anchor set exceeds nLarge");
        }
        if (shadowSet.size() > params.getNShadow()) {
            throw new IllegalArgumentException("shadow set exceeds nShadow");
        }
        Set<ByteKey> expectedUnion = new TreeSet<>();
        expectedUnion.addAll(anchorSet);
        expectedUnion.addAll(shadowSet);
        int psica = left.size() + right.size() - expectedUnion.size();

        AttemptResult selectedAttempt = null;
        List<BaSsuIbltBucketTrace> allBucketTrace = collectTrace ? new ArrayList<>() : Collections.emptyList();
        long scheduledBucketCount = 0L;
        long crossLayerBlockingCount = 0L;
        int maxRoundCount = 0;
        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            AttemptResult attempt = runAttempt(params, retryIndex, anchorSet, shadowSet, expectedUnion.size(),
                elementByteLength, collectTrace, bucketEvaluator);
            if (collectTrace) {
                allBucketTrace.addAll(attempt.bucketTrace);
            }
            scheduledBucketCount += attempt.scheduledBucketCount;
            crossLayerBlockingCount += attempt.crossLayerBlockingCount;
            maxRoundCount = Math.max(maxRoundCount, attempt.roundCount);
            if (selectedAttempt == null && attempt.success) {
                selectedAttempt = attempt;
            }
        }

        Set<ByteBuffer> leftUnionOutput;
        Set<ByteBuffer> rightUnionOutput;
        Set<ByteBuffer> leftReceivedDifference;
        Set<ByteBuffer> rightReceivedDifference;
        BaSsuIbltSignedPeelOutput signedPeelOutput;
        if (selectedAttempt == null) {
            leftUnionOutput = Collections.emptySet();
            rightUnionOutput = Collections.emptySet();
            leftReceivedDifference = Collections.emptySet();
            rightReceivedDifference = Collections.emptySet();
            signedPeelOutput = BaSsuIbltSignedPeelOutput.empty();
        } else {
            Set<ByteKey> anchorReceivedDifference = selectedAttempt.shadowOnlyElements;
            Set<ByteKey> shadowReceivedDifference = selectedAttempt.anchorOnlyElements;
            Set<ByteKey> leftReceived = leftAnchor ? anchorReceivedDifference : shadowReceivedDifference;
            Set<ByteKey> rightReceived = leftAnchor ? shadowReceivedDifference : anchorReceivedDifference;
            leftReceivedDifference = toByteBufferSet(leftReceived);
            rightReceivedDifference = toByteBufferSet(rightReceived);
            leftUnionOutput = toByteBufferSet(unionOf(left, leftReceived));
            rightUnionOutput = toByteBufferSet(unionOf(right, rightReceived));
            signedPeelOutput = new BaSsuIbltSignedPeelOutput(
                toByteBufferSet(selectedAttempt.anchorOnlyElements),
                toByteBufferSet(selectedAttempt.shadowOnlyElements),
                selectedAttempt.sharedElements.size()
            );
        }
        long nTests = Math.max(1L, scheduledBucketCount * params.getChecksPerBucket());
        int recommendedCheckBits = BaSsuIbltBiUpsuParams.minCheckBits(params.getLambda(), nTests,
            params.getMarginBits());
        return new BaSsuIbltPlainResult(
            selectedAttempt != null,
            selectedAttempt == null ? -1 : selectedAttempt.retryIndex,
            expectedUnion.size(),
            leftAnchor,
            leftUnionOutput,
            rightUnionOutput,
            leftReceivedDifference,
            rightReceivedDifference,
            signedPeelOutput,
            psica,
            scheduledBucketCount,
            maxRoundCount,
            selectedAttempt == null ? 0 : selectedAttempt.roundCount,
            crossLayerBlockingCount,
            selectedAttempt == null ? expectedUnion.size() : selectedAttempt.anchorResidualCount,
            selectedAttempt == null ? expectedUnion.size() : selectedAttempt.shadowResidualCount,
            nTests,
            recommendedCheckBits,
            allBucketTrace
        );
    }

    private static AttemptResult runAttempt(BaSsuIbltBiUpsuParams params, int retryIndex, Set<ByteKey> anchorSet,
                                            Set<ByteKey> shadowSet, int unionSize, int elementByteLength,
                                            boolean collectTrace, BaUpotBucketEvaluator bucketEvaluator) {
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, retryIndex, elementByteLength);
        insertElements(table, anchorSet, true);
        insertElements(table, shadowSet, false);
        Set<ByteKey> anchorRemaining = new HashSet<>(anchorSet);
        Set<ByteKey> shadowRemaining = new HashSet<>(shadowSet);
        TreeSet<ByteKey> peeledElements = new TreeSet<>();
        TreeSet<ByteKey> anchorOnlyElements = new TreeSet<>();
        TreeSet<ByteKey> shadowOnlyElements = new TreeSet<>();
        TreeSet<ByteKey> sharedElements = new TreeSet<>();
        List<BaSsuIbltBucketTrace> bucketTrace = collectTrace ? new ArrayList<>() : null;
        long scheduledBucketCount = 0L;
        long crossLayerBlockingCount = 0L;
        int roundCount = 0;
        int[] schedule = null;
        int maxRounds = Math.max(1, unionSize + 1);
        while (roundCount < maxRounds) {
            int scheduleSize = schedule == null ? params.getTableLength() : schedule.length;
            if (scheduleSize == 0) {
                break;
            }
            scheduledBucketCount += scheduleSize;
            List<ByteKey> roundNew = new ArrayList<>();
            if (schedule == null) {
                for (int bucketIndex = 0; bucketIndex < table.getTableLength(); bucketIndex++) {
                    crossLayerBlockingCount += evaluateBucket(retryIndex, roundCount, bucketIndex, table,
                        peeledElements, anchorOnlyElements, shadowOnlyElements, sharedElements, roundNew, bucketTrace,
                        bucketEvaluator);
                }
            } else {
                for (int bucketIndex : schedule) {
                    crossLayerBlockingCount += evaluateBucket(retryIndex, roundCount, bucketIndex, table,
                        peeledElements, anchorOnlyElements, shadowOnlyElements, sharedElements, roundNew, bucketTrace,
                        bucketEvaluator);
                }
            }
            if (roundNew.isEmpty()) {
                break;
            }
            for (ByteKey element : roundNew) {
                if (anchorRemaining.remove(element)) {
                    deleteElement(table, element, true);
                }
                if (shadowRemaining.remove(element)) {
                    deleteElement(table, element, false);
                }
            }
            schedule = touchedPositions(params, retryIndex, roundNew);
            roundCount++;
            if (anchorRemaining.isEmpty() && shadowRemaining.isEmpty()) {
                break;
            }
        }
        boolean success = anchorRemaining.isEmpty() && shadowRemaining.isEmpty();
        return new AttemptResult(
            retryIndex, success, roundCount, peeledElements, anchorOnlyElements, shadowOnlyElements, sharedElements,
            scheduledBucketCount, crossLayerBlockingCount,
            anchorRemaining.size(), shadowRemaining.size(), bucketTrace
        );
    }

    private static long evaluateBucket(int retryIndex, int roundIndex, int bucketIndex, BaSsuIbltAnchorTable table,
                                       TreeSet<ByteKey> peeledElements, TreeSet<ByteKey> anchorOnlyElements,
                                       TreeSet<ByteKey> shadowOnlyElements, TreeSet<ByteKey> sharedElements,
                                       List<ByteKey> roundNew,
                                       List<BaSsuIbltBucketTrace> bucketTrace,
                                       BaUpotBucketEvaluator bucketEvaluator) {
        BaUpotBucketInput input = table.getBucketInput(bucketIndex);
        BaUpotBucketOutput output = bucketEvaluator.evaluate(input);
        boolean accepted = false;
        if (output.isSingleton()) {
            ByteKey outputKey = new ByteKey(output.getElementReference());
            if (peeledElements.add(outputKey)) {
                roundNew.add(outputKey);
                switch (output.getCaseType()) {
                    case ANCHOR_SINGLETON:
                        anchorOnlyElements.add(outputKey);
                        break;
                    case SHADOW_SINGLETON:
                        shadowOnlyElements.add(outputKey);
                        break;
                    case SHARED_SINGLETON:
                        sharedElements.add(outputKey);
                        break;
                    default:
                        throw new IllegalStateException("singleton output has non-singleton case");
                }
                accepted = true;
            }
        }
        if (bucketTrace != null) {
            bucketTrace.add(new BaSsuIbltBucketTrace(retryIndex, roundIndex, bucketIndex, input, output, accepted));
        }
        return isCrossLayerBlocking(input) ? 1L : 0L;
    }

    private static boolean isCrossLayerBlocking(BaUpotBucketInput input) {
        return input.getAnchorCount() == 1 && input.getShadowCount() == 1
            && !Arrays.equals(input.getAnchorKeyXorReference(), input.getShadowKeyXorReference());
    }

    private static void insertElements(BaSsuIbltAnchorTable table, Set<ByteKey> elements, boolean anchor) {
        for (ByteKey element : elements) {
            if (anchor) {
                table.insertAnchor(element.bytes);
            } else {
                table.insertShadow(element.bytes);
            }
        }
    }

    private static void deleteElement(BaSsuIbltAnchorTable table, ByteKey element, boolean anchor) {
        if (anchor) {
            table.deleteAnchor(element.bytes);
        } else {
            table.deleteShadow(element.bytes);
        }
    }

    private static int[] touchedPositions(BaSsuIbltBiUpsuParams params, int retryIndex, List<ByteKey> roundNew) {
        BitSet touched = new BitSet(params.getTableLength());
        for (ByteKey element : roundNew) {
            for (int position : BaSsuIbltPlacement.positions(params, retryIndex, element.bytes)) {
                touched.set(position);
            }
        }
        int[] positions = new int[touched.cardinality()];
        int index = 0;
        for (int position = touched.nextSetBit(0); position >= 0; position = touched.nextSetBit(position + 1)) {
            positions[index++] = position;
        }
        return positions;
    }

    private static Set<ByteKey> normalize(Set<ByteBuffer> input, int elementByteLength) {
        Set<ByteKey> output = new LinkedHashSet<>(input.size());
        for (ByteBuffer element : input) {
            byte[] bytes = toBytes(element);
            if (bytes.length != elementByteLength) {
                throw new IllegalArgumentException("element byte length must be " + elementByteLength);
            }
            output.add(new ByteKey(bytes));
        }
        return output;
    }

    private static Set<ByteBuffer> toByteBufferSet(Set<ByteKey> keys) {
        Set<ByteBuffer> output = new LinkedHashSet<>(keys.size());
        for (ByteKey key : keys) {
            output.add(ByteBuffer.wrap(Arrays.copyOf(key.bytes, key.bytes.length)));
        }
        return output;
    }

    private static TreeSet<ByteKey> unionOf(Set<ByteKey> ownSet, Set<ByteKey> receivedDifference) {
        TreeSet<ByteKey> union = new TreeSet<>(ownSet);
        union.addAll(receivedDifference);
        return union;
    }

    private static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    /**
     * Attempt result.
     */
    private static class AttemptResult {
        /**
         * retry index.
         */
        private final int retryIndex;
        /**
         * success.
         */
        private final boolean success;
        /**
         * round count.
         */
        private final int roundCount;
        /**
         * accepted singleton elements.
         */
        private final TreeSet<ByteKey> peeledElements;
        /**
         * anchor-only elements.
         */
        private final TreeSet<ByteKey> anchorOnlyElements;
        /**
         * shadow-only elements.
         */
        private final TreeSet<ByteKey> shadowOnlyElements;
        /**
         * shared singleton elements.
         */
        private final TreeSet<ByteKey> sharedElements;
        /**
         * scheduled bucket count.
         */
        private final long scheduledBucketCount;
        /**
         * cross-layer blocking count.
         */
        private final long crossLayerBlockingCount;
        /**
         * anchor residual count.
         */
        private final int anchorResidualCount;
        /**
         * shadow residual count.
         */
        private final int shadowResidualCount;
        /**
         * bucket trace.
         */
        private final List<BaSsuIbltBucketTrace> bucketTrace;

        AttemptResult(int retryIndex, boolean success, int roundCount, TreeSet<ByteKey> peeledElements,
                      TreeSet<ByteKey> anchorOnlyElements, TreeSet<ByteKey> shadowOnlyElements,
                      TreeSet<ByteKey> sharedElements, long scheduledBucketCount, long crossLayerBlockingCount,
                      int anchorResidualCount, int shadowResidualCount, List<BaSsuIbltBucketTrace> bucketTrace) {
            this.retryIndex = retryIndex;
            this.success = success;
            this.roundCount = roundCount;
            this.peeledElements = new TreeSet<>(peeledElements);
            this.anchorOnlyElements = new TreeSet<>(anchorOnlyElements);
            this.shadowOnlyElements = new TreeSet<>(shadowOnlyElements);
            this.sharedElements = new TreeSet<>(sharedElements);
            this.scheduledBucketCount = scheduledBucketCount;
            this.crossLayerBlockingCount = crossLayerBlockingCount;
            this.anchorResidualCount = anchorResidualCount;
            this.shadowResidualCount = shadowResidualCount;
            this.bucketTrace = bucketTrace == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(bucketTrace));
        }
    }

    /**
     * Immutable byte-array key.
     */
    private static class ByteKey implements Comparable<ByteKey> {
        /**
         * element bytes.
         */
        private final byte[] bytes;

        ByteKey(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public int compareTo(ByteKey that) {
            int minLength = Math.min(this.bytes.length, that.bytes.length);
            for (int i = 0; i < minLength; i++) {
                int left = this.bytes[i] & 0xFF;
                int right = that.bytes[i] & 0xFF;
                if (left != right) {
                    return Integer.compare(left, right);
                }
            }
            return Integer.compare(this.bytes.length, that.bytes.length);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ByteKey that)) {
                return false;
            }
            return Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
