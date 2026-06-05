package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * BA-SSU-IBLT local source-split anchor table.
 *
 * <p>The table stores the two source layers in the common public coordinate system. It is a local structure only; a
 * secure realization must feed masked or secret-shared equivalents of {@link BaUpotBucketInput} into BA-UPOT.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltAnchorTable {
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
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * cells.
     */
    private final Cell[] cells;

    public BaSsuIbltAnchorTable(BaSsuIbltBiUpsuParams params, int retryIndex, int elementByteLength) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        if (retryIndex < 0 || retryIndex >= params.getRetryCount()) {
            throw new IllegalArgumentException("retryIndex must be in [0, retryCount)");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        int checkBytes = BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits());
        this.params = params;
        this.retryIndex = retryIndex;
        this.elementByteLength = elementByteLength;
        checkByteLength = checkBytes;
        cells = new Cell[params.getTableLength()];
    }

    /**
     * Inserts a batch of anchor elements.
     *
     * @param elements elements.
     */
    public void insertAnchors(Set<ByteBuffer> elements) {
        if (elements == null) {
            throw new IllegalArgumentException("elements must be non-null");
        }
        for (ByteBuffer element : elements) {
            insertAnchor(element);
        }
    }

    /**
     * Inserts a batch of shadow elements.
     *
     * @param elements elements.
     */
    public void insertShadows(Set<ByteBuffer> elements) {
        if (elements == null) {
            throw new IllegalArgumentException("elements must be non-null");
        }
        for (ByteBuffer element : elements) {
            insertShadow(element);
        }
    }

    /**
     * Inserts one anchor element.
     *
     * @param element element.
     */
    public void insertAnchor(ByteBuffer element) {
        insertAnchor(toElementBytes(element));
    }

    /**
     * Inserts one anchor element.
     *
     * @param element element.
     */
    public void insertAnchor(byte[] element) {
        update(element, true, true);
    }

    /**
     * Inserts one shadow element.
     *
     * @param element element.
     */
    public void insertShadow(ByteBuffer element) {
        insertShadow(toElementBytes(element));
    }

    /**
     * Inserts one shadow element.
     *
     * @param element element.
     */
    public void insertShadow(byte[] element) {
        update(element, false, true);
    }

    /**
     * Deletes one anchor element.
     *
     * @param element element.
     */
    public void deleteAnchor(ByteBuffer element) {
        deleteAnchor(toElementBytes(element));
    }

    /**
     * Deletes one anchor element.
     *
     * @param element element.
     */
    public void deleteAnchor(byte[] element) {
        update(element, true, false);
    }

    /**
     * Deletes one shadow element.
     *
     * @param element element.
     */
    public void deleteShadow(ByteBuffer element) {
        deleteShadow(toElementBytes(element));
    }

    /**
     * Deletes one shadow element.
     *
     * @param element element.
     */
    public void deleteShadow(byte[] element) {
        update(element, false, false);
    }

    /**
     * Gets one bucket input.
     *
     * @param bucketIndex bucket index.
     * @return bucket input.
     */
    BaUpotBucketInput getBucketInput(int bucketIndex) {
        if (bucketIndex < 0 || bucketIndex >= cells.length) {
            throw new IllegalArgumentException("bucketIndex out of range");
        }
        return cells[bucketIndex] == null
            ? BaUpotBucketInput.empty(bucketIndex, elementByteLength, checkByteLength)
            : cells[bucketIndex].toBucketInput(bucketIndex);
    }

    /**
     * Gets all bucket inputs.
     *
     * @return bucket inputs.
     */
    BaUpotBucketInput[] getBucketInputs() {
        BaUpotBucketInput[] inputs = new BaUpotBucketInput[cells.length];
        for (int bucketIndex = 0; bucketIndex < cells.length; bucketIndex++) {
            inputs[bucketIndex] = getBucketInput(bucketIndex);
        }
        return inputs;
    }

    /**
     * Gets touched positions for an element.
     *
     * @param element element.
     * @return touched positions.
     */
    public int[] positions(ByteBuffer element) {
        return positions(toElementBytes(element));
    }

    /**
     * Gets touched positions for an element.
     *
     * @param element element.
     * @return touched positions.
     */
    public int[] positions(byte[] element) {
        return BaSsuIbltPlacement.positions(params, retryIndex, normalizeElementBytes(element));
    }

    public int getTableLength() {
        return cells.length;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public int getCheckByteLength() {
        return checkByteLength;
    }

    private void update(byte[] element, boolean anchor, boolean insert) {
        byte[] elementBytes = normalizeElementBytes(element);
        byte[] check = BaSsuIbltOprfTagPipeline.referenceCheck(elementBytes, checkByteLength);
        for (int position : BaSsuIbltPlacement.positions(params, retryIndex, elementBytes)) {
            if (cells[position] == null) {
                cells[position] = new Cell(elementByteLength, checkByteLength);
            }
            cells[position].update(elementBytes, check, anchor, insert);
        }
    }

    private byte[] toElementBytes(ByteBuffer element) {
        if (element == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        ByteBuffer duplicate = element.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        if (bytes.length != elementByteLength) {
            throw new IllegalArgumentException("element byte length must be " + elementByteLength);
        }
        return bytes;
    }

    private byte[] normalizeElementBytes(byte[] element) {
        if (element == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        if (element.length != elementByteLength) {
            throw new IllegalArgumentException("element byte length must be " + elementByteLength);
        }
        return element.clone();
    }

    /**
     * source-split cell.
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

        Cell(int elementByteLength, int checkByteLength) {
            anchorKeyXor = new byte[elementByteLength];
            shadowKeyXor = new byte[elementByteLength];
            anchorCheckXor = new byte[checkByteLength];
            shadowCheckXor = new byte[checkByteLength];
        }

        void update(byte[] element, byte[] check, boolean anchor, boolean insert) {
            if (anchor) {
                if (!insert && anchorCount == 0) {
                    throw new IllegalStateException("anchor count became negative");
                }
                anchorCount += insert ? 1 : -1;
                xori(anchorKeyXor, element);
                xori(anchorCheckXor, check);
            } else {
                if (!insert && shadowCount == 0) {
                    throw new IllegalStateException("shadow count became negative");
                }
                shadowCount += insert ? 1 : -1;
                xori(shadowKeyXor, element);
                xori(shadowCheckXor, check);
            }
        }

        BaUpotBucketInput toBucketInput(int bucketIndex) {
            return BaUpotBucketInput.of(bucketIndex, anchorCount, shadowCount, anchorKeyXor, shadowKeyXor,
                anchorCheckXor, shadowCheckXor);
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
