package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

/**
 * Packed Boolean backend for MP-SOGS secure-uPeel.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public interface PackedBooleanBackend {
    /**
     * Returns block count.
     */
    int blockNum();

    /**
     * Shares/imports an own packed bit vector.
     */
    PackedBooleanShare shareOwn(long[] bits);

    /**
     * Creates a zero vector.
     */
    PackedBooleanShare zero();

    /**
     * Creates an all-ones vector over valid batch lanes.
     */
    PackedBooleanShare one();

    /**
     * XOR.
     */
    PackedBooleanShare xor(PackedBooleanShare x, PackedBooleanShare y);

    /**
     * NOT over valid batch lanes.
     */
    PackedBooleanShare not(PackedBooleanShare x);

    /**
     * AND.
     */
    PackedBooleanShare and(PackedBooleanShare x, PackedBooleanShare y);

    /**
     * Opens a packed vector.
     */
    long[] open(PackedBooleanShare x);

    /**
     * Opens selected lanes into compact packed blocks.
     *
     * @param x packed vector.
     * @param selectedIndexes public selected lane indexes.
     * @return compact opened blocks.
     */
    long[] openSelected(PackedBooleanShare x, int[] selectedIndexes);

    /**
     * OR.
     */
    default PackedBooleanShare or(PackedBooleanShare x, PackedBooleanShare y) {
        return xor(xor(x, y), and(x, y));
    }
}
