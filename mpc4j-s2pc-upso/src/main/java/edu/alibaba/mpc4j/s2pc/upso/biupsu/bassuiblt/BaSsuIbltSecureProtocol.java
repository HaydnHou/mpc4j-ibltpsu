package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * BA-SSU-IBLT secure-core harness.
 *
 * <p>This package-private core connects MP-OPRF tag outputs, source-split encoding, and local BA-UnionPeel-OT
 * evaluators for fixed-loop and queue-peel tests. It is not the public endpoint surface: the secure endpoint remains
 * fail-closed until a production UP-BA-UPOT backend is available.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltSecureProtocol {
    /**
     * private constructor.
     */
    private BaSsuIbltSecureProtocol() {
        // empty
    }

    static BaSsuIbltSecureProtocolResult runFixedSchedule(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        byte[][] leftFixedInputs, boolean[] leftActiveFlags, BaSsuIbltOprfTagOutput leftTagOutput,
        byte[][] rightFixedInputs, boolean[] rightActiveFlags, BaSsuIbltOprfTagOutput rightTagOutput) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        checkCurrentFixedLoopCheckBudget(params);
        Set<ByteKey> left = normalizeSet(leftSet, elementByteLength);
        Set<ByteKey> right = normalizeSet(rightSet, elementByteLength);
        Set<ByteKey> leftActive = activeSet(leftFixedInputs, leftActiveFlags, elementByteLength);
        Set<ByteKey> rightActive = activeSet(rightFixedInputs, rightActiveFlags, elementByteLength);
        if (!left.equals(leftActive)) {
            throw new IllegalArgumentException("left active fixed inputs must equal leftSet");
        }
        if (!right.equals(rightActive)) {
            throw new IllegalArgumentException("right active fixed inputs must equal rightSet");
        }
        boolean leftAnchor = left.size() >= right.size();
        Set<ByteKey> anchorSet = leftAnchor ? left : right;
        Set<ByteKey> shadowSet = leftAnchor ? right : left;
        byte[][] anchorInputs = leftAnchor ? leftFixedInputs : rightFixedInputs;
        boolean[] anchorActiveFlags = leftAnchor ? leftActiveFlags : rightActiveFlags;
        BaSsuIbltOprfTagOutput anchorTagOutput = leftAnchor ? leftTagOutput : rightTagOutput;
        byte[][] shadowInputs = leftAnchor ? rightFixedInputs : leftFixedInputs;
        boolean[] shadowActiveFlags = leftAnchor ? rightActiveFlags : leftActiveFlags;
        BaSsuIbltOprfTagOutput shadowTagOutput = leftAnchor ? rightTagOutput : leftTagOutput;
        checkCapacity(anchorSet.size(), shadowSet.size(), params);

        Map<ByteKey, TagPair> anchorTagMap = tagMap(anchorInputs, anchorActiveFlags, anchorTagOutput, elementByteLength);
        Map<ByteKey, TagPair> shadowTagMap = tagMap(shadowInputs, shadowActiveFlags, shadowTagOutput, elementByteLength);
        BaSsuIbltProtocolSchedule schedule = BaSsuIbltProtocolSchedule.currentFixedLoopM14a(params);
        int fixedRoundCount = schedule.getFixedRoundCount();
        TreeSet<ByteKey> selectedPeeled = new TreeSet<>();
        boolean success = false;

        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            RetryResult retryResult = runRetry(
                params, retryIndex, fixedRoundCount, elementByteLength, anchorSet, shadowSet, anchorInputs,
                anchorActiveFlags, anchorTagOutput, shadowInputs, shadowActiveFlags, shadowTagOutput, anchorTagMap,
                shadowTagMap
            );
            if (retryResult.success && !success) {
                selectedPeeled.addAll(retryResult.peeled);
                success = true;
            }
        }
        Set<ByteBuffer> leftUnion = unionOutput(left, selectedPeeled);
        Set<ByteBuffer> rightUnion = unionOutput(right, selectedPeeled);
        return new BaSsuIbltSecureProtocolResult(
            success, fixedRoundCount, schedule.getScheduledBucketCount(),
            selectedPeeled.size(), leftUnion, rightUnion
        );
    }

    static BaSsuIbltSecureProtocolResult runQueuePeelAlignedReference(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        byte[][] leftFixedInputs, boolean[] leftActiveFlags, BaSsuIbltOprfTagOutput leftTagOutput,
        byte[][] rightFixedInputs, boolean[] rightActiveFlags, BaSsuIbltOprfTagOutput rightTagOutput) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        BaSsuIbltProtocolSchedule schedule = BaSsuIbltProtocolSchedule.queuePeelAligned(params);
        Set<ByteKey> left = normalizeSet(leftSet, elementByteLength);
        Set<ByteKey> right = normalizeSet(rightSet, elementByteLength);
        Set<ByteKey> leftActive = activeSet(leftFixedInputs, leftActiveFlags, elementByteLength);
        Set<ByteKey> rightActive = activeSet(rightFixedInputs, rightActiveFlags, elementByteLength);
        if (!left.equals(leftActive)) {
            throw new IllegalArgumentException("left active fixed inputs must equal leftSet");
        }
        if (!right.equals(rightActive)) {
            throw new IllegalArgumentException("right active fixed inputs must equal rightSet");
        }
        boolean leftAnchor = left.size() >= right.size();
        Set<ByteKey> anchorSet = leftAnchor ? left : right;
        Set<ByteKey> shadowSet = leftAnchor ? right : left;
        byte[][] anchorInputs = leftAnchor ? leftFixedInputs : rightFixedInputs;
        boolean[] anchorActiveFlags = leftAnchor ? leftActiveFlags : rightActiveFlags;
        BaSsuIbltOprfTagOutput anchorTagOutput = leftAnchor ? leftTagOutput : rightTagOutput;
        byte[][] shadowInputs = leftAnchor ? rightFixedInputs : leftFixedInputs;
        boolean[] shadowActiveFlags = leftAnchor ? rightActiveFlags : leftActiveFlags;
        BaSsuIbltOprfTagOutput shadowTagOutput = leftAnchor ? rightTagOutput : leftTagOutput;
        checkCapacity(anchorSet.size(), shadowSet.size(), params);

        Map<ByteKey, TagPair> anchorTagMap = tagMap(anchorInputs, anchorActiveFlags, anchorTagOutput, elementByteLength);
        Map<ByteKey, TagPair> shadowTagMap = tagMap(shadowInputs, shadowActiveFlags, shadowTagOutput, elementByteLength);
        QueuePeelUnionProbe unionProbe = input -> BaUnionPeelOtSecureEvaluator.evaluate(input);
        TreeSet<ByteKey> selectedPeeled = new TreeSet<>();
        boolean success = false;
        long actualProbeCount = 0L;
        long retryProbeCap = perRetryQueuePeelProbeCap(params);
        List<BaSsuIbltQueuePeelRetryStatus> retryStatuses = new ArrayList<>(params.getRetryCount());
        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            if (success) {
                retryStatuses.add(BaSsuIbltQueuePeelRetryStatus.notRunAfterSuccess(retryIndex, retryProbeCap));
                continue;
            }
            RetryResult retryResult = runQueuePeelRetry(
                params, retryIndex, elementByteLength, anchorSet, shadowSet, anchorInputs, anchorActiveFlags,
                anchorTagOutput, shadowInputs, shadowActiveFlags, shadowTagOutput, anchorTagMap, shadowTagMap,
                unionProbe
            );
            actualProbeCount = Math.addExact(actualProbeCount, retryResult.probeCount);
            retryStatuses.add(BaSsuIbltQueuePeelRetryStatus.executed(
                retryIndex, retryResult.success, retryResult.probeCount, retryProbeCap
            ));
            if (retryResult.success) {
                selectedPeeled.addAll(retryResult.peeled);
                success = true;
            }
        }
        if (!success) {
            selectedPeeled.clear();
        }
        Set<ByteBuffer> leftUnion = unionOutput(left, selectedPeeled);
        Set<ByteBuffer> rightUnion = unionOutput(right, selectedPeeled);
        return new BaSsuIbltSecureProtocolResult(
            success, schedule.getFixedRoundCount(), schedule.getScheduledBucketCount(), actualProbeCount,
            schedule.getShape(), retryStatuses, selectedPeeled.size(), leftUnion, rightUnion
        );
    }

    static BaSsuIbltSecureProtocolResult runQueuePeelAligned(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        byte[][] leftFixedInputs, boolean[] leftActiveFlags, BaSsuIbltOprfTagOutput leftTagOutput,
        byte[][] rightFixedInputs, boolean[] rightActiveFlags, BaSsuIbltOprfTagOutput rightTagOutput,
        BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        checkProductionQueuePeelBackend(unionProbeBackendConfig);
        throw new UnsupportedOperationException(
            "production queue-peel adapter requires a remote-state-hiding UP-BA-UPOT implementation"
        );
    }

    private static RetryResult runRetry(
        BaSsuIbltBiUpsuParams params, int retryIndex, int fixedRoundCount, int elementByteLength,
        Set<ByteKey> anchorSet, Set<ByteKey> shadowSet, byte[][] anchorInputs, boolean[] anchorActiveFlags,
        BaSsuIbltOprfTagOutput anchorTagOutput, byte[][] shadowInputs, boolean[] shadowActiveFlags,
        BaSsuIbltOprfTagOutput shadowTagOutput, Map<ByteKey, TagPair> anchorTagMap,
        Map<ByteKey, TagPair> shadowTagMap) {
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, retryIndex, elementByteLength
        );
        builder.insertAnchors(anchorInputs, anchorActiveFlags, anchorTagOutput);
        builder.insertShadows(shadowInputs, shadowActiveFlags, shadowTagOutput);
        Set<ByteKey> anchorRemaining = new HashSet<>(anchorSet);
        Set<ByteKey> shadowRemaining = new HashSet<>(shadowSet);
        TreeSet<ByteKey> peeled = new TreeSet<>();
        for (int round = 0; round < fixedRoundCount; round++) {
            TreeSet<ByteKey> roundCandidates = new TreeSet<>();
            for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
                BaUpotBucketOutput output = BaUnionPeelOtSecureEvaluator.evaluate(builder.getBucketInput(bucketIndex));
                if (output.isSingleton()) {
                    ByteKey outputKey = new ByteKey(output.getElementReference());
                    if (!peeled.contains(outputKey)) {
                        roundCandidates.add(outputKey);
                    }
                }
            }
            for (ByteKey element : roundCandidates) {
                boolean deleted = deleteSourceIfPresent(
                    element, anchorRemaining, anchorTagMap,
                    (bytes, tag, check) -> builder.deleteAnchor(bytes, tag, check), "anchor"
                );
                deleted |= deleteSourceIfPresent(
                    element, shadowRemaining, shadowTagMap,
                    (bytes, tag, check) -> builder.deleteShadow(bytes, tag, check), "shadow"
                );
                if (deleted) {
                    peeled.add(element);
                }
            }
        }
        long probeCount = Math.multiplyExact((long) fixedRoundCount, params.getTableLength());
        return new RetryResult(anchorRemaining.isEmpty() && shadowRemaining.isEmpty(), peeled, probeCount);
    }

    private static RetryResult runQueuePeelRetry(
        BaSsuIbltBiUpsuParams params, int retryIndex, int elementByteLength, Set<ByteKey> anchorSet,
        Set<ByteKey> shadowSet, byte[][] anchorInputs, boolean[] anchorActiveFlags,
        BaSsuIbltOprfTagOutput anchorTagOutput, byte[][] shadowInputs, boolean[] shadowActiveFlags,
        BaSsuIbltOprfTagOutput shadowTagOutput, Map<ByteKey, TagPair> anchorTagMap,
        Map<ByteKey, TagPair> shadowTagMap, QueuePeelUnionProbe unionProbe) {
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, retryIndex, elementByteLength
        );
        builder.insertAnchors(anchorInputs, anchorActiveFlags, anchorTagOutput);
        builder.insertShadows(shadowInputs, shadowActiveFlags, shadowTagOutput);
        Set<ByteKey> anchorRemaining = new HashSet<>(anchorSet);
        Set<ByteKey> shadowRemaining = new HashSet<>(shadowSet);
        TreeSet<ByteKey> peeled = new TreeSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>(params.getTableLength());
        for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
            queue.addLast(bucketIndex);
        }
        long retryProbeCap = perRetryQueuePeelProbeCap(params);
        long probeCount = 0L;
        while (!queue.isEmpty()) {
            if (probeCount == retryProbeCap) {
                return new RetryResult(false, peeled, probeCount);
            }
            int bucketIndex = queue.removeFirst();
            probeCount++;
            BaUpotBucketOutput output = unionProbe.probe(builder.getBucketInput(bucketIndex));
            if (output.isSingleton()) {
                ByteKey outputKey = new ByteKey(output.getElementReference());
                if (!peeled.contains(outputKey)) {
                    boolean deleted = deleteSourceIfPresent(
                        outputKey, anchorRemaining, anchorTagMap,
                        (bytes, tag, check) -> builder.deleteAnchor(bytes, tag, check), "anchor"
                    );
                    deleted |= deleteSourceIfPresent(
                        outputKey, shadowRemaining, shadowTagMap,
                        (bytes, tag, check) -> builder.deleteShadow(bytes, tag, check), "shadow"
                    );
                    if (deleted) {
                        peeled.add(outputKey);
                        for (int position : builder.positions(outputKey.bytes)) {
                            queue.addLast(position);
                        }
                    }
                }
            }
        }
        return new RetryResult(anchorRemaining.isEmpty() && shadowRemaining.isEmpty(), peeled, probeCount);
    }

    private static void checkProductionQueuePeelBackend(BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        if (unionProbeBackendConfig == null) {
            throw new IllegalArgumentException("unionProbeBackendConfig must be non-null");
        }
        if (!(unionProbeBackendConfig instanceof BaSsuIbltProductionUnionProbeBackendConfig)) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires the trusted production union-probe backend type; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is not trusted"
            );
        }
        if (!unionProbeBackendConfig.isSpecializedBucketProbe()) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires a specialized bucket-probe backend"
            );
        }
        if (!unionProbeBackendConfig.isQueuePeelProductionReady()) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires a production-ready backend; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is fail-closed"
            );
        }
    }

    static int fixedRoundCount(BaSsuIbltBiUpsuParams params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return BaSsuIbltProtocolSchedule.fixedRoundCount(params);
    }

    private static void checkCurrentFixedLoopCheckBudget(BaSsuIbltBiUpsuParams params) {
        int minCheckBits = BaSsuIbltProtocolSchedule.currentFixedLoopM14aMinCheckBits(params);
        if (params.getCheckBits() < minCheckBits) {
            throw new IllegalArgumentException(
                "checkBits must cover CURRENT_FIXED_LOOP_M14A nTests; required at least " + minCheckBits
            );
        }
    }

    private static long perRetryQueuePeelProbeCap(BaSsuIbltBiUpsuParams params) {
        return Math.addExact(
            params.getTableLength(),
            Math.multiplyExact((long) params.getDegree(), Math.addExact(params.getNLarge(), params.getNShadow()))
        );
    }

    private static void checkCapacity(int anchorSize, int shadowSize, BaSsuIbltBiUpsuParams params) {
        if (anchorSize > params.getNLarge()) {
            throw new IllegalArgumentException("anchor set exceeds nLarge");
        }
        if (shadowSize > params.getNShadow()) {
            throw new IllegalArgumentException("shadow set exceeds nShadow");
        }
    }

    private static boolean deleteSourceIfPresent(ByteKey element, Set<ByteKey> remaining, Map<ByteKey, TagPair> tagMap,
                                                 SourceLayerDelete sourceLayerDelete, String sourceName) {
        return BaSsuIbltSourceAgnosticFrontierDelete.apply(
            () -> remaining.remove(element),
            () -> {
                TagPair tagPair = tagMap.get(element);
                if (tagPair == null) {
                    throw new IllegalStateException("missing " + sourceName + " tag/check for peeled element");
                }
                sourceLayerDelete.delete(element.bytes, tagPair.tag, tagPair.check);
            }
        );
    }

    private static Map<ByteKey, TagPair> tagMap(byte[][] fixedInputs, boolean[] activeFlags,
                                                BaSsuIbltOprfTagOutput tagOutput, int elementByteLength) {
        checkFixedInputs(fixedInputs, activeFlags, tagOutput, elementByteLength);
        Map<ByteKey, TagPair> map = new HashMap<>();
        for (int index = 0; index < fixedInputs.length; index++) {
            if (activeFlags[index]) {
                ByteKey key = new ByteKey(fixedInputs[index]);
                if (map.put(key, new TagPair(tagOutput.getTag(index), tagOutput.getCheck(index))) != null) {
                    throw new IllegalArgumentException("active fixed inputs must not contain duplicates");
                }
            }
        }
        return map;
    }

    private static Set<ByteKey> activeSet(byte[][] fixedInputs, boolean[] activeFlags, int elementByteLength) {
        checkFixedInputs(fixedInputs, activeFlags, null, elementByteLength);
        Set<ByteKey> set = new HashSet<>();
        for (int index = 0; index < fixedInputs.length; index++) {
            if (activeFlags[index]) {
                if (!set.add(new ByteKey(fixedInputs[index]))) {
                    throw new IllegalArgumentException("active fixed inputs must not contain duplicates");
                }
            }
        }
        return set;
    }

    private static void checkFixedInputs(byte[][] fixedInputs, boolean[] activeFlags, BaSsuIbltOprfTagOutput tagOutput,
                                        int elementByteLength) {
        if (fixedInputs == null || activeFlags == null) {
            throw new IllegalArgumentException("fixedInputs and activeFlags must be non-null");
        }
        if (fixedInputs.length != activeFlags.length) {
            throw new IllegalArgumentException("fixedInputs and activeFlags must have equal length");
        }
        if (tagOutput != null && fixedInputs.length != tagOutput.getBatchSize()) {
            throw new IllegalArgumentException("tagOutput batch size must match fixedInputs");
        }
        for (byte[] input : fixedInputs) {
            if (input == null || input.length != elementByteLength) {
                throw new IllegalArgumentException("each fixed input must have elementByteLength bytes");
            }
        }
    }

    private static Set<ByteKey> normalizeSet(Set<ByteBuffer> inputSet, int elementByteLength) {
        if (inputSet == null) {
            throw new IllegalArgumentException("inputSet must be non-null");
        }
        return inputSet.stream()
            .map(buffer -> new ByteKey(toBytes(buffer, elementByteLength)))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static byte[] toBytes(ByteBuffer buffer, int elementByteLength) {
        if (buffer == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        if (bytes.length != elementByteLength) {
            throw new IllegalArgumentException("element byte length must be " + elementByteLength);
        }
        return bytes;
    }

    private static Set<ByteBuffer> unionOutput(Set<ByteKey> localSet, Set<ByteKey> peeled) {
        TreeSet<ByteKey> union = new TreeSet<>(localSet);
        union.addAll(peeled);
        return union.stream()
            .map(key -> ByteBuffer.wrap(Arrays.copyOf(key.bytes, key.bytes.length)))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * tag/check pair.
     */
    private static class TagPair {
        /**
         * tag.
         */
        private final byte[] tag;
        /**
         * check.
         */
        private final byte[] check;

        TagPair(byte[] tag, byte[] check) {
            this.tag = Arrays.copyOf(tag, tag.length);
            this.check = Arrays.copyOf(check, check.length);
        }
    }

    /**
     * retry result.
     */
    private static class RetryResult {
        /**
         * success.
         */
        private final boolean success;
        /**
         * peeled elements.
         */
        private final Set<ByteKey> peeled;
        /**
         * probe count.
         */
        private final long probeCount;

        RetryResult(boolean success, Set<ByteKey> peeled, long probeCount) {
            this.success = success;
            this.peeled = peeled;
            this.probeCount = probeCount;
        }
    }

    /**
     * source-layer delete action.
     */
    @FunctionalInterface
    private interface SourceLayerDelete {
        /**
         * Deletes one authenticated source-layer element.
         *
         * @param element element.
         * @param tag     tag.
         * @param check   check.
         */
        void delete(byte[] element, byte[] tag, byte[] check);
    }

    /**
     * public-bucket union probe used inside queue peel.
     */
    @FunctionalInterface
    private interface QueuePeelUnionProbe {
        /**
         * Probes one public bucket and returns a source-agnostic output case.
         *
         * @param bucketInput bucket input.
         * @return bucket output.
         */
        BaUpotBucketOutput probe(BaSsuIbltSecureBucketInput bucketInput);
    }

    /**
     * byte-array key.
     */
    private static class ByteKey implements Comparable<ByteKey> {
        /**
         * bytes.
         */
        private final byte[] bytes;

        ByteKey(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public int compareTo(ByteKey other) {
            for (int i = 0; i < bytes.length && i < other.bytes.length; i++) {
                int compare = Byte.compare(bytes[i], other.bytes[i]);
                if (compare != 0) {
                    return compare;
                }
            }
            return Integer.compare(bytes.length, other.bytes.length);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ByteKey)) {
                return false;
            }
            return Arrays.equals(bytes, ((ByteKey) obj).bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
