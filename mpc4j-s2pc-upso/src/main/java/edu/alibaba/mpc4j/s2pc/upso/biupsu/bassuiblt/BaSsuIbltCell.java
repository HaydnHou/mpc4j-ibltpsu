package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-SSU-IBLT simulation cell.
 *
 * <p>The simulator uses integer element ids and XOR aggregates to model singleton recovery.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaSsuIbltCell {
    /**
     * anchor count.
     */
    private int anchorCount;
    /**
     * anchor xor.
     */
    private int anchorXor;
    /**
     * shadow count.
     */
    private int shadowCount;
    /**
     * shadow xor.
     */
    private int shadowXor;

    void insertAnchor(int element) {
        anchorCount++;
        anchorXor ^= element;
    }

    void insertShadow(int element) {
        shadowCount++;
        shadowXor ^= element;
    }

    void deleteAnchor(int element) {
        anchorCount--;
        anchorXor ^= element;
    }

    void deleteShadow(int element) {
        shadowCount--;
        shadowXor ^= element;
    }

    int getAnchorCount() {
        return anchorCount;
    }

    int getShadowCount() {
        return shadowCount;
    }

    boolean isCrossLayerBlocking() {
        return anchorCount == 1 && shadowCount == 1 && anchorXor != shadowXor;
    }

    /**
     * Evaluates the ideal BA-UPOT case table.
     *
     * @return recovered union singleton, or 0 for bottom.
     */
    int unionSingletonOrBottom() {
        if (anchorCount == 0 && shadowCount == 1) {
            return shadowXor;
        }
        if (anchorCount == 1 && shadowCount == 0) {
            return anchorXor;
        }
        if (anchorCount == 1 && shadowCount == 1 && anchorXor == shadowXor) {
            return anchorXor;
        }
        return 0;
    }
}
