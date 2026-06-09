package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.structure.iblt.LongIbltEntry;
import edu.alibaba.mpc4j.common.structure.iblt.LongIbltPeelResult;
import edu.alibaba.mpc4j.common.tool.EnvType;

import java.util.ArrayList;
import java.util.List;

/**
 * H5LongIblt-backed PSU sketch backend.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class H5LongIbltPsuSketchBackend implements SogsPsuSketchBackend {
    /**
     * H5 IBLT.
     */
    private final H5LongIblt iblt;

    /**
     * Creates a backend.
     *
     * @param envType           environment.
     * @param threshold         threshold.
     * @param multiplier        multiplier.
     * @param payloadByteLength payload byte length.
     * @param hashKey           hash key.
     */
    public H5LongIbltPsuSketchBackend(
        EnvType envType, int threshold, double multiplier, int payloadByteLength, byte[] hashKey
    ) {
        iblt = H5LongIblt.create(envType, threshold, multiplier, payloadByteLength, hashKey);
    }

    @Override
    public SogsPsuSketchBackendType type() {
        return SogsPsuSketchBackendType.H5_IBLT;
    }

    @Override
    public void add(long label, byte[] payload) {
        iblt.add(label, payload);
    }

    @Override
    public void remove(long label, byte[] payload) {
        iblt.remove(label, payload);
    }

    @Override
    public SogsPsuSketchPeelResult peel() {
        long start = System.nanoTime();
        LongIbltPeelResult result = iblt.copy().peel();
        long peelNanos = System.nanoTime() - start;
        List<SogsPsuSketchEntry> entries = new ArrayList<>(result.entries().size());
        for (LongIbltEntry entry : result.entries()) {
            entries.add(new SogsPsuSketchEntry(entry.key(), entry.value(), entry.sign()));
        }
        return new SogsPsuSketchPeelResult(result.success(), entries, result.success() ? 0 : -1, 0L, 0L, peelNanos);
    }

    @Override
    public int[] positions(long label) {
        return iblt.positions(label);
    }

    @Override
    public int[] uniquePositions(long[] labels, boolean[] excluded) {
        return iblt.uniquePositions(labels, excluded);
    }

    @Override
    public int[] counts() {
        return iblt.counts();
    }

    @Override
    public byte[][] valueSums() {
        return iblt.valueSums();
    }

    @Override
    public boolean[] pureSingletons() {
        return iblt.pureSingletons();
    }

    @Override
    public int tableSize() {
        return iblt.tableLength();
    }

    @Override
    public int hashNum() {
        return H5LongIblt.HASH_NUM;
    }

    @Override
    public int payloadByteLength() {
        return iblt.valueByteLength();
    }

    @Override
    public int itemCount() {
        return iblt.size();
    }
}
