package edu.alibaba.mpc4j.common.structure.sogs;

/**
 * Hash utilities for source-oblivious graph sketch.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
final class SogsHashUtils {
    /**
     * Golden-ratio constant.
     */
    private static final long PHI = 0x9E3779B97F4A7C15L;

    private SogsHashUtils() {
        // empty
    }

    /**
     * SplitMix64 finalizer.
     *
     * @param z input.
     * @return output.
     */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Computes a non-zero label.
     *
     * @param itemId item.
     * @param seed   seed.
     * @return label.
     */
    static long label(long itemId, long seed) {
        long label = mix64(itemId + PHI + seed);
        return label == 0L ? PHI : label;
    }

    /**
     * Computes check value.
     *
     * @param label     label.
     * @param checkSeed check seed.
     * @return check.
     */
    static long check(long label, long checkSeed) {
        long check = mix64(label ^ checkSeed ^ 0xD1B54A32D192ED03L);
        return check == 0L ? 0xA24BAED4963EE407L : check;
    }

    /**
     * Computes graph positions.
     *
     * @param label  label.
     * @param params parameters.
     * @return positions.
     */
    static int[] positions(long label, SogsGraphParams params) {
        int degree = params.getDegree();
        int subTableLength = params.getSubTableLength();
        int[] positions = new int[degree];
        long seed = params.getSeed();
        for (int i = 0; i < degree; i++) {
            long hash = mix64(label ^ seed ^ (PHI * (i + 1)));
            positions[i] = i * subTableLength + Math.floorMod(hash, subTableLength);
        }
        return positions;
    }
}
