package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * BA-SSU-IBLT secure source-layer builder.
 *
 * <p>This builder is local-only. It prepares fixed source-split bucket inputs from real MP-OPRF tag outputs; it does
 * not send source-layer cells as protocol messages.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltSecureLayerBuilder {
    /**
     * params.
     */
    private final BaSsuIbltBiUpsuParams params;
    /**
     * retry index.
     */
    private final int retryIndex;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * mutable cells.
     */
    private final Cell[] cells;

    BaSsuIbltSecureLayerBuilder(BaSsuIbltBiUpsuParams params, int retryIndex, int elementByteLength,
                                int tagByteLength, int checkByteLength) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        if (retryIndex < 0 || retryIndex >= params.getRetryCount()) {
            throw new IllegalArgumentException("retryIndex must be in [0, retryCount)");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (tagByteLength <= 0) {
            throw new IllegalArgumentException("tagByteLength must be positive");
        }
        if (checkByteLength <= 0) {
            throw new IllegalArgumentException("checkByteLength must be positive");
        }
        if (tagByteLength < checkByteLength) {
            throw new IllegalArgumentException("tagByteLength must be at least checkByteLength");
        }
        this.params = params;
        this.retryIndex = retryIndex;
        this.elementByteLength = elementByteLength;
        this.tagByteLength = tagByteLength;
        this.checkByteLength = checkByteLength;
        cells = new Cell[params.getTableLength()];
    }

    static BaSsuIbltSecureLayerBuilder fromParams(BaSsuIbltBiUpsuParams params, int retryIndex,
                                                  int elementByteLength) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return new BaSsuIbltSecureLayerBuilder(
            params,
            retryIndex,
            elementByteLength,
            BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()),
            BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits())
        );
    }

    void insertAnchors(byte[][] fixedInputs, BaSsuIbltOprfTagOutput tagOutput) {
        insertAnchors(fixedInputs, allActive(fixedInputs), tagOutput);
    }

    void insertAnchors(byte[][] fixedInputs, boolean[] activeFlags, BaSsuIbltOprfTagOutput tagOutput) {
        updateBatch(fixedInputs, activeFlags, tagOutput, true, true);
    }

    void insertShadows(byte[][] fixedInputs, BaSsuIbltOprfTagOutput tagOutput) {
        insertShadows(fixedInputs, allActive(fixedInputs), tagOutput);
    }

    void insertShadows(byte[][] fixedInputs, boolean[] activeFlags, BaSsuIbltOprfTagOutput tagOutput) {
        updateBatch(fixedInputs, activeFlags, tagOutput, false, true);
    }

    public void deleteAnchor(byte[] element, byte[] tag, byte[] check) {
        update(element, tag, check, true, false);
    }

    void deleteAnchor(byte[] element, byte[] tag, byte[] check, int[] positions) {
        update(element, tag, check, true, false, positions);
    }

    public void deleteShadow(byte[] element, byte[] tag, byte[] check) {
        update(element, tag, check, false, false);
    }

    void deleteShadow(byte[] element, byte[] tag, byte[] check, int[] positions) {
        update(element, tag, check, false, false, positions);
    }

    public BaSsuIbltSecureCellView getAnchorCellView(int bucketIndex) {
        if (bucketIndex < 0 || bucketIndex >= cells.length) {
            throw new IllegalArgumentException("bucketIndex out of range");
        }
        return cells[bucketIndex] == null
            ? BaSsuIbltSecureCellView.empty(elementByteLength, tagByteLength, checkByteLength)
            : cells[bucketIndex].anchorView();
    }

    public BaSsuIbltSecureCellView getShadowCellView(int bucketIndex) {
        if (bucketIndex < 0 || bucketIndex >= cells.length) {
            throw new IllegalArgumentException("bucketIndex out of range");
        }
        return cells[bucketIndex] == null
            ? BaSsuIbltSecureCellView.empty(elementByteLength, tagByteLength, checkByteLength)
            : cells[bucketIndex].shadowView();
    }

    public int[] positions(byte[] element) {
        return BaSsuIbltPlacement.positions(params, retryIndex, normalizeElement(element));
    }

    public int getTableLength() {
        return cells.length;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public int getTagByteLength() {
        return tagByteLength;
    }

    public int getCheckByteLength() {
        return checkByteLength;
    }

    private void updateBatch(byte[][] fixedInputs, boolean[] activeFlags, BaSsuIbltOprfTagOutput tagOutput,
                             boolean anchor, boolean insert) {
        checkBatch(fixedInputs, activeFlags, tagOutput);
        for (int index = 0; index < fixedInputs.length; index++) {
            if (activeFlags[index]) {
                update(fixedInputs[index], tagOutput.getTag(index), tagOutput.getCheck(index), anchor, insert);
            }
        }
    }

    private void update(byte[] element, byte[] tag, byte[] check, boolean anchor, boolean insert) {
        update(element, tag, check, anchor, insert, null);
    }

    private void update(byte[] element, byte[] tag, byte[] check, boolean anchor, boolean insert,
                        int[] knownPositions) {
        byte[] elementBytes = normalizeElement(element);
        byte[] tagBytes = normalizeTag(tag);
        byte[] checkBytes = normalizeCheck(check);
        int[] positions = knownPositions == null
            ? BaSsuIbltPlacement.positions(params, retryIndex, elementBytes)
            : normalizePositions(knownPositions);
        if (!insert) {
            checkDeletePossible(positions, anchor);
        }
        for (int position : positions) {
            if (cells[position] == null) {
                if (!insert) {
                    throw new IllegalStateException("cannot delete from an empty source bucket");
                }
                cells[position] = new Cell(elementByteLength, tagByteLength, checkByteLength);
            }
            cells[position].update(elementBytes, tagBytes, checkBytes, anchor, insert);
        }
    }

    private int[] normalizePositions(int[] positions) {
        if (positions == null || positions.length != params.getDegree()) {
            throw new IllegalArgumentException("known positions must match the public degree");
        }
        int[] copy = Arrays.copyOf(positions, positions.length);
        for (int position : copy) {
            if (position < 0 || position >= cells.length) {
                throw new IllegalArgumentException("known position out of range");
            }
        }
        return copy;
    }

    private void checkDeletePossible(int[] positions, boolean anchor) {
        for (int position : positions) {
            if (cells[position] == null || !cells[position].canDelete(anchor)) {
                throw new IllegalStateException("cannot delete from an empty source bucket");
            }
        }
    }

    private void checkBatch(byte[][] fixedInputs, boolean[] activeFlags, BaSsuIbltOprfTagOutput tagOutput) {
        if (fixedInputs == null) {
            throw new IllegalArgumentException("fixedInputs must be non-null");
        }
        if (activeFlags == null) {
            throw new IllegalArgumentException("activeFlags must be non-null");
        }
        if (tagOutput == null) {
            throw new IllegalArgumentException("tagOutput must be non-null");
        }
        if (fixedInputs.length != activeFlags.length) {
            throw new IllegalArgumentException("fixedInputs and activeFlags must have equal length");
        }
        if (fixedInputs.length != tagOutput.getBatchSize()) {
            throw new IllegalArgumentException("fixedInputs length must equal tagOutput batch size");
        }
        if (tagOutput.getTagByteLength() != tagByteLength || tagOutput.getCheckByteLength() != checkByteLength) {
            throw new IllegalArgumentException("tagOutput byte lengths do not match builder");
        }
        for (byte[] input : fixedInputs) {
            normalizeElement(input);
        }
    }

    private byte[] normalizeElement(byte[] element) {
        if (element == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        if (element.length != elementByteLength) {
            throw new IllegalArgumentException("element byte length must be " + elementByteLength);
        }
        return Arrays.copyOf(element, element.length);
    }

    private byte[] normalizeTag(byte[] tag) {
        if (tag == null) {
            throw new IllegalArgumentException("tag must be non-null");
        }
        if (tag.length != tagByteLength) {
            throw new IllegalArgumentException("tag byte length must be " + tagByteLength);
        }
        return Arrays.copyOf(tag, tag.length);
    }

    private byte[] normalizeCheck(byte[] check) {
        if (check == null) {
            throw new IllegalArgumentException("check must be non-null");
        }
        if (check.length != checkByteLength) {
            throw new IllegalArgumentException("check byte length must be " + checkByteLength);
        }
        return Arrays.copyOf(check, check.length);
    }

    private static boolean[] allActive(byte[][] fixedInputs) {
        if (fixedInputs == null) {
            throw new IllegalArgumentException("fixedInputs must be non-null");
        }
        boolean[] activeFlags = new boolean[fixedInputs.length];
        Arrays.fill(activeFlags, true);
        return activeFlags;
    }

    /**
     * mutable source-split cell.
     */
    private static class Cell {
        /**
         * anchor source.
         */
        private final SourceCell anchor;
        /**
         * shadow source.
         */
        private final SourceCell shadow;

        Cell(int elementByteLength, int tagByteLength, int checkByteLength) {
            anchor = new SourceCell(elementByteLength, tagByteLength, checkByteLength);
            shadow = new SourceCell(elementByteLength, tagByteLength, checkByteLength);
        }

        void update(byte[] element, byte[] tag, byte[] check, boolean anchorSource, boolean insert) {
            if (anchorSource) {
                anchor.update(element, tag, check, insert);
            } else {
                shadow.update(element, tag, check, insert);
            }
        }

        boolean canDelete(boolean anchorSource) {
            return anchorSource ? anchor.canDelete() : shadow.canDelete();
        }

        BaSsuIbltSecureCellView anchorView() {
            return anchor.toView();
        }

        BaSsuIbltSecureCellView shadowView() {
            return shadow.toView();
        }
    }

    /**
     * mutable one-source aggregate.
     */
    private static class SourceCell {
        /**
         * count.
         */
        private int count;
        /**
         * key xor.
         */
        private final byte[] keyXor;
        /**
         * tag xor.
         */
        private final byte[] tagXor;
        /**
         * check xor.
         */
        private final byte[] checkXor;

        SourceCell(int elementByteLength, int tagByteLength, int checkByteLength) {
            keyXor = new byte[elementByteLength];
            tagXor = new byte[tagByteLength];
            checkXor = new byte[checkByteLength];
        }

        void update(byte[] element, byte[] tag, byte[] check, boolean insert) {
            if (!insert && count == 0) {
                throw new IllegalStateException("source count became negative");
            }
            count += insert ? 1 : -1;
            xori(keyXor, element);
            xori(tagXor, tag);
            xori(checkXor, check);
        }

        boolean canDelete() {
            return count > 0;
        }

        BaSsuIbltSecureCellView toView() {
            return BaSsuIbltSecureCellView.of(count, keyXor, tagXor, checkXor);
        }

        private static void xori(byte[] target, byte[] other) {
            if (target.length != other.length) {
                throw new IllegalArgumentException("xor inputs must have equal length");
            }
            for (int i = 0; i < target.length; i++) {
                target[i] ^= other[i];
            }
        }
    }
}
