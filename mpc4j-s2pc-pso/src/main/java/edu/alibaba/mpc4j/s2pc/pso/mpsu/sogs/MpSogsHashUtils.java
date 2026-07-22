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
        return cells(value, params, MpSogsTier.MAIN);
    }

    /**
     * Returns one row-disjoint cell per hash row for one element in a SOGS tier.
     *
     * @param value element.
     * @param params parameters.
     * @param tier SOGS tier.
     * @return k row-disjoint cell indexes.
     */
    public static int[] cells(long value, MpSogsMpsuParams params, MpSogsTier tier) {
        int hashNum = params.getHashNum(tier);
        int rowCellNum = params.getRowCellNum(tier);
        int[] cells = new int[hashNum];
        for (int hashIndex = 0; hashIndex < hashNum; hashIndex++) {
            long z = rowHash(value, params, tier, hashIndex);
            cells[hashIndex] = hashIndex * rowCellNum + toIndex(z, rowCellNum);
        }
        return cells;
    }

    /**
     * Returns the 64-bit permutation word used by one row hash.
     */
    static long rowHash(long value, MpSogsMpsuParams params, MpSogsTier tier, int hashIndex) {
        if (hashIndex < 0 || hashIndex >= params.getHashNum(tier)) {
            throw new IllegalArgumentException("invalid " + tier + " hash row: " + hashIndex);
        }
        return splitMix64(value ^ params.getHashSeed(tier) ^ rowSeed(hashIndex));
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

    /**
     * Inverts {@link #splitMix64(long)} exactly over 64-bit words.
     */
    static long inverseSplitMix64(long z) {
        z = (z ^ (z >>> 31) ^ (z >>> 62)) * 0x319642B2D24D8EC3L;
        z = (z ^ (z >>> 27) ^ (z >>> 54)) * 0x96DE1B173F119089L;
        z = z ^ (z >>> 30) ^ (z >>> 60);
        return z - 0x9E3779B97F4A7C15L;
    }

    static long rowSeed(int hashIndex) {
        return splitMix64(0xD6E8FEB86659FD93L * (hashIndex + 1));
    }

    private static int toIndex(long z, int bound) {
        return (int) Long.remainderUnsigned(z, bound);
    }
}
