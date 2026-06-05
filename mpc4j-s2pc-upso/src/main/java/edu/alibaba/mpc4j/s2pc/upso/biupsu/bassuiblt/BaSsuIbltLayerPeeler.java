package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Peels fixed source-layer BA-SSU-IBLT payloads.
 *
 * <p>This is the endpoint bridge for Milestone 3. It consumes fixed-shape layer cells instead of raw element sets. The
 * evaluator is still local and reference-only; later milestones replace this bridge with protocol-level BA-UPOT.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltLayerPeeler {
    /**
     * private constructor.
     */
    private BaSsuIbltLayerPeeler() {
        // empty
    }

    /**
     * Peels anchor/shadow layer payloads.
     *
     * @param anchorCells anchor layer cells.
     * @param shadowCells shadow layer cells.
     * @param params parameters.
     * @param elementByteLength element byte length.
     * @return peel result.
     * @throws MpcAbortException the protocol aborts.
     */
    static PeelResult peel(BaSsuIbltLayerPayloadCodec.LayerCell[] anchorCells,
                           BaSsuIbltLayerPayloadCodec.LayerCell[] shadowCells,
                           BaSsuIbltBiUpsuParams params, int elementByteLength) throws MpcAbortException {
        int expectedCellCount = Math.toIntExact((long) params.getRetryCount() * params.getTableLength());
        MpcAbortPreconditions.checkArgument(anchorCells.length == expectedCellCount, "invalid anchor cell count");
        MpcAbortPreconditions.checkArgument(shadowCells.length == expectedCellCount, "invalid shadow cell count");
        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            MutableCombinedTable table = new MutableCombinedTable(anchorCells, shadowCells, params, retryIndex,
                elementByteLength);
            AttemptResult attempt = peelAttempt(table, params, retryIndex);
            if (attempt.success) {
                return new PeelResult(true, retryIndex, attempt.anchorOnlyElements, attempt.shadowOnlyElements,
                    attempt.sharedCount);
            }
        }
        return new PeelResult(false, -1, new TreeSet<>(), new TreeSet<>(), 0);
    }

    private static AttemptResult peelAttempt(MutableCombinedTable table, BaSsuIbltBiUpsuParams params,
                                             int retryIndex) throws MpcAbortException {
        TreeSet<ByteKey> peeledElements = new TreeSet<>();
        TreeSet<ByteKey> anchorOnlyElements = new TreeSet<>();
        TreeSet<ByteKey> shadowOnlyElements = new TreeSet<>();
        int sharedCount = 0;
        int[] schedule = null;
        int maxRounds = params.getNLarge() + params.getNShadow() + 1;
        for (int roundIndex = 0; roundIndex < maxRounds; roundIndex++) {
            int scheduleSize = schedule == null ? params.getTableLength() : schedule.length;
            if (scheduleSize == 0) {
                break;
            }
            List<PeelCandidate> roundNew = new ArrayList<>();
            if (schedule == null) {
                for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
                    evaluateBucket(table, bucketIndex, peeledElements, anchorOnlyElements, shadowOnlyElements,
                        roundNew);
                }
            } else {
                for (int bucketIndex : schedule) {
                    evaluateBucket(table, bucketIndex, peeledElements, anchorOnlyElements, shadowOnlyElements,
                        roundNew);
                }
            }
            if (roundNew.isEmpty()) {
                break;
            }
            for (PeelCandidate candidate : roundNew) {
                switch (candidate.caseType) {
                    case ANCHOR_SINGLETON:
                        table.delete(candidate.element.bytes, true);
                        break;
                    case SHADOW_SINGLETON:
                        table.delete(candidate.element.bytes, false);
                        break;
                    case SHARED_SINGLETON:
                        table.delete(candidate.element.bytes, true);
                        table.delete(candidate.element.bytes, false);
                        sharedCount++;
                        break;
                    default:
                        throw new IllegalStateException("fresh candidate must be a singleton");
                }
            }
            schedule = touchedPositions(params, retryIndex, roundNew);
            if (table.isEmpty()) {
                break;
            }
        }
        return new AttemptResult(table.isEmpty(), anchorOnlyElements, shadowOnlyElements, sharedCount);
    }

    private static void evaluateBucket(MutableCombinedTable table, int bucketIndex, TreeSet<ByteKey> peeledElements,
                                       TreeSet<ByteKey> anchorOnlyElements, TreeSet<ByteKey> shadowOnlyElements,
                                       List<PeelCandidate> roundNew) {
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(bucketIndex));
        if (!output.isSingleton()) {
            return;
        }
        ByteKey outputKey = new ByteKey(output.getElementReference());
        if (!peeledElements.add(outputKey)) {
            return;
        }
        switch (output.getCaseType()) {
            case ANCHOR_SINGLETON:
                anchorOnlyElements.add(outputKey);
                break;
            case SHADOW_SINGLETON:
                shadowOnlyElements.add(outputKey);
                break;
            case SHARED_SINGLETON:
                break;
            default:
                throw new IllegalStateException("singleton output has non-singleton case");
        }
        roundNew.add(new PeelCandidate(outputKey, output.getCaseType()));
    }

    private static int[] touchedPositions(BaSsuIbltBiUpsuParams params, int retryIndex, List<PeelCandidate> roundNew) {
        BitSet touched = new BitSet(params.getTableLength());
        for (PeelCandidate candidate : roundNew) {
            for (int position : BaSsuIbltPlacement.positions(params, retryIndex, candidate.element.bytes)) {
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

    /**
     * Attempt result.
     */
    private static class AttemptResult {
        /**
         * success.
         */
        private final boolean success;
        /**
         * anchor-only elements.
         */
        private final TreeSet<ByteKey> anchorOnlyElements;
        /**
         * shadow-only elements.
         */
        private final TreeSet<ByteKey> shadowOnlyElements;
        /**
         * shared singleton count.
         */
        private final int sharedCount;

        AttemptResult(boolean success, TreeSet<ByteKey> anchorOnlyElements, TreeSet<ByteKey> shadowOnlyElements,
                      int sharedCount) {
            this.success = success;
            this.anchorOnlyElements = new TreeSet<>(anchorOnlyElements);
            this.shadowOnlyElements = new TreeSet<>(shadowOnlyElements);
            this.sharedCount = sharedCount;
        }
    }

    /**
     * Peel result.
     */
    static class PeelResult {
        /**
         * success.
         */
        private final boolean success;
        /**
         * selected retry index.
         */
        private final int selectedRetryIndex;
        /**
         * anchor-only elements.
         */
        private final TreeSet<ByteKey> anchorOnlyElements;
        /**
         * shadow-only elements.
         */
        private final TreeSet<ByteKey> shadowOnlyElements;
        /**
         * shared singleton count.
         */
        private final int sharedCount;

        PeelResult(boolean success, int selectedRetryIndex, TreeSet<ByteKey> anchorOnlyElements,
                   TreeSet<ByteKey> shadowOnlyElements, int sharedCount) {
            this.success = success;
            this.selectedRetryIndex = selectedRetryIndex;
            this.anchorOnlyElements = new TreeSet<>(anchorOnlyElements);
            this.shadowOnlyElements = new TreeSet<>(shadowOnlyElements);
            this.sharedCount = sharedCount;
        }

        boolean isSuccess() {
            return success;
        }

        int getSelectedRetryIndex() {
            return selectedRetryIndex;
        }

        Set<byte[]> getAnchorOnlyElements() {
            return toByteArraySet(anchorOnlyElements);
        }

        Set<byte[]> getShadowOnlyElements() {
            return toByteArraySet(shadowOnlyElements);
        }

        int getSharedCount() {
            return sharedCount;
        }
    }

    /**
     * Mutable combined source-split table.
     */
    private static class MutableCombinedTable {
        /**
         * params.
         */
        private final BaSsuIbltBiUpsuParams params;
        /**
         * retry index.
         */
        private final int retryIndex;
        /**
         * check byte length.
         */
        private final int checkByteLength;
        /**
         * cells.
         */
        private final Cell[] cells;

        MutableCombinedTable(BaSsuIbltLayerPayloadCodec.LayerCell[] anchorCells,
                             BaSsuIbltLayerPayloadCodec.LayerCell[] shadowCells,
                             BaSsuIbltBiUpsuParams params, int retryIndex, int elementByteLength) {
            this.params = params;
            this.retryIndex = retryIndex;
            checkByteLength = BaSsuIbltLayerPayloadCodec.checkByteLength(params);
            cells = new Cell[params.getTableLength()];
            int offset = retryIndex * params.getTableLength();
            for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
                cells[bucketIndex] = new Cell(anchorCells[offset + bucketIndex], shadowCells[offset + bucketIndex],
                    elementByteLength, checkByteLength);
            }
        }

        BaUpotBucketInput getBucketInput(int bucketIndex) {
            return cells[bucketIndex].toBucketInput(bucketIndex);
        }

        void delete(byte[] element, boolean anchor) throws MpcAbortException {
            byte[] check = BaUpotIdeal.digest(element, checkByteLength);
            for (int position : BaSsuIbltPlacement.positions(params, retryIndex, element)) {
                cells[position].delete(element, check, anchor);
            }
        }

        boolean isEmpty() {
            for (Cell cell : cells) {
                if (!cell.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Mutable source-split cell.
     */
    private static class Cell {
        /**
         * anchor count.
         */
        private int anchorCount;
        /**
         * shadow count.
         */
        private int shadowCount;
        /**
         * anchor key xor.
         */
        private final byte[] anchorKeyXor;
        /**
         * shadow key xor.
         */
        private final byte[] shadowKeyXor;
        /**
         * anchor check xor.
         */
        private final byte[] anchorCheckXor;
        /**
         * shadow check xor.
         */
        private final byte[] shadowCheckXor;

        Cell(BaSsuIbltLayerPayloadCodec.LayerCell anchorCell, BaSsuIbltLayerPayloadCodec.LayerCell shadowCell,
             int elementByteLength, int checkByteLength) {
            anchorCount = anchorCell.getCount();
            shadowCount = shadowCell.getCount();
            anchorKeyXor = anchorCell.getKeyXor();
            shadowKeyXor = shadowCell.getKeyXor();
            anchorCheckXor = anchorCell.getCheckXor();
            shadowCheckXor = shadowCell.getCheckXor();
            if (anchorKeyXor.length != elementByteLength || shadowKeyXor.length != elementByteLength
                || anchorCheckXor.length != checkByteLength || shadowCheckXor.length != checkByteLength) {
                throw new IllegalArgumentException("invalid source-layer cell length");
            }
        }

        BaUpotBucketInput toBucketInput(int bucketIndex) {
            return BaUpotBucketInput.of(bucketIndex, anchorCount, shadowCount, anchorKeyXor, shadowKeyXor,
                anchorCheckXor, shadowCheckXor);
        }

        void delete(byte[] element, byte[] check, boolean anchor) throws MpcAbortException {
            if (anchor) {
                MpcAbortPreconditions.checkArgument(anchorCount > 0, "anchor count became negative");
                anchorCount--;
                xori(anchorKeyXor, element);
                xori(anchorCheckXor, check);
            } else {
                MpcAbortPreconditions.checkArgument(shadowCount > 0, "shadow count became negative");
                shadowCount--;
                xori(shadowKeyXor, element);
                xori(shadowCheckXor, check);
            }
        }

        boolean isEmpty() {
            return anchorCount == 0 && shadowCount == 0
                && isZero(anchorKeyXor) && isZero(shadowKeyXor)
                && isZero(anchorCheckXor) && isZero(shadowCheckXor);
        }
    }

    /**
     * Peel candidate.
     */
    private static class PeelCandidate {
        /**
         * element.
         */
        private final ByteKey element;
        /**
         * case type.
         */
        private final BaUpotBucketOutput.CaseType caseType;

        PeelCandidate(ByteKey element, BaUpotBucketOutput.CaseType caseType) {
            this.element = element;
            this.caseType = caseType;
        }
    }

    /**
     * Immutable byte-array key.
     */
    static class ByteKey implements Comparable<ByteKey> {
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

    private static Set<byte[]> toByteArraySet(Set<ByteKey> byteKeys) {
        Set<byte[]> elements = new java.util.LinkedHashSet<>(byteKeys.size());
        for (ByteKey byteKey : byteKeys) {
            elements.add(Arrays.copyOf(byteKey.bytes, byteKey.bytes.length));
        }
        return elements;
    }

    private static void xori(byte[] target, byte[] other) {
        if (target.length != other.length) {
            throw new IllegalArgumentException("xor inputs must have equal length");
        }
        for (int i = 0; i < target.length; i++) {
            target[i] ^= other[i];
        }
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
