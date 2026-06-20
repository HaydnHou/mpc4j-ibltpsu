package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Hash utilities for the MP-SOGS clear prototype.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsHashUtils {
    /**
     * Check-hash domain separator.
     */
    private static final long CHECK_DOMAIN = 0xC13FA9A902A6328FL;
    private MpSogsHashUtils() {
        // empty
    }

    /**
     * Returns one row-disjoint cell per hash row for one element.
     *
     * @param value element.
     * @param params parameters.
     * @return k row-disjoint cell indexes.
     */
    public static int[] cells(long value, MpSogsMpsuParams params) {
        int hashNum = params.getHashNum();
        int rowCellNum = params.getRowCellNum();
        int[] cells = new int[hashNum];
        for (int hashIndex = 0; hashIndex < hashNum; hashIndex++) {
            long z = splitMix64(value ^ params.getHashSeed() ^ rowSeed(hashIndex));
            cells[hashIndex] = hashIndex * rowCellNum + toIndex(z, rowCellNum);
        }
        return cells;
    }

    /**
     * Returns a 64-bit check value.
     *
     * @param value element.
     * @return check value.
     */
    public static long check(long value) {
        return splitMix64(value ^ CHECK_DOMAIN);
    }

    static long splitMix64(long x) {
        long z = x + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long rowSeed(int hashIndex) {
        return splitMix64(0xD6E8FEB86659FD93L * (hashIndex + 1));
    }

    private static int toIndex(long z, int bound) {
        return (int) Long.remainderUnsigned(z, bound);
    }
}
