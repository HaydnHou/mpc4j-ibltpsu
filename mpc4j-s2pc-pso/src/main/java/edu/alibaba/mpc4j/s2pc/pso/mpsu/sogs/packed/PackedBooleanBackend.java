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
     * Returns batch size.
     */
    int batchSize();

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
     * Batched AND. Implementations should override this when many independent ANDs can share one protocol layer.
     *
     * @param xs left inputs.
     * @param ys right inputs.
     * @return outputs.
     */
    default PackedBooleanShare[] andMany(PackedBooleanShare[] xs, PackedBooleanShare[] ys) {
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys length mismatch: " + xs.length + " != " + ys.length);
        }
        PackedBooleanShare[] result = new PackedBooleanShare[xs.length];
        for (int i = 0; i < xs.length; i++) {
            result[i] = and(xs[i], ys[i]);
        }
        return result;
    }

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
     * Locally compacts a secret shared vector to public selected lanes. This does not open the share.
     *
     * @param x packed vector.
     * @param selectedIndexes public selected lane indexes.
     * @return compact packed vector.
     */
    default PackedBooleanShare compact(PackedBooleanShare x, int[] selectedIndexes) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support compact");
    }

    /**
     * Derives a backend for compact shares.
     *
     * @param compactBatchSize compact batch size.
     * @return compact backend.
     */
    default PackedBooleanBackend derive(int compactBatchSize) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support derive");
    }

    /**
     * OR.
     */
    default PackedBooleanShare or(PackedBooleanShare x, PackedBooleanShare y) {
        return xor(xor(x, y), and(x, y));
    }

    /**
     * OR over many packed vectors, implemented as a batched tree so secure backends can reduce protocol overhead.
     */
    default PackedBooleanShare orMany(PackedBooleanShare[] shares) {
        if (shares.length == 0) {
            return zero();
        }
        PackedBooleanShare[] layer = shares;
        while (layer.length > 1) {
            int pairNum = layer.length / 2;
            PackedBooleanShare[] xs = new PackedBooleanShare[pairNum];
            PackedBooleanShare[] ys = new PackedBooleanShare[pairNum];
            for (int i = 0; i < pairNum; i++) {
                xs[i] = layer[i << 1];
                ys[i] = layer[(i << 1) + 1];
            }
            PackedBooleanShare[] ands = andMany(xs, ys);
            PackedBooleanShare[] next = new PackedBooleanShare[pairNum + (layer.length & 1)];
            for (int i = 0; i < pairNum; i++) {
                next[i] = xor(xor(xs[i], ys[i]), ands[i]);
            }
            if ((layer.length & 1) == 1) {
                next[next.length - 1] = layer[layer.length - 1];
            }
            layer = next;
        }
        return layer[0];
    }
}
