package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Party-local MP-SOGS sketch for the clear prototype.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsSketch {
    /**
     * Parameters.
     */
    private final MpSogsMpsuParams params;
    /**
     * Cells.
     */
    private final MpSogsCell[] cells;
    /**
     * Remaining local set. Used only for local delete/no-op in the clear prototype.
     */
    private final Set<Long> remainingElements;

    public MpSogsSketch(MpSogsMpsuParams params) {
        this.params = params;
        cells = new MpSogsCell[params.getCellNum()];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = new MpSogsCell();
        }
        remainingElements = new HashSet<>();
    }

    public static MpSogsSketch encode(Collection<Long> input, MpSogsMpsuParams params) {
        MpSogsSketch sketch = new MpSogsSketch(params);
        Set<Long> deduplicated = new HashSet<>(input);
        for (long value : deduplicated) {
            sketch.insert(value);
        }
        return sketch;
    }

    public void insert(long value) {
        if (!remainingElements.add(value)) {
            return;
        }
        for (int cellIndex : MpSogsHashUtils.cells(value, params)) {
            cells[cellIndex].add(value);
        }
    }

    public boolean deleteIfPresentOnce(long value) {
        if (!remainingElements.remove(value)) {
            return false;
        }
        for (int cellIndex : MpSogsHashUtils.cells(value, params)) {
            cells[cellIndex].remove(value);
        }
        return true;
    }

    public MpSogsCellState state(int cellIndex) {
        checkCellIndex(cellIndex);
        return cells[cellIndex].state();
    }

    public long singletonValue(int cellIndex) {
        checkCellIndex(cellIndex);
        return cells[cellIndex].singletonValue();
    }

    public MpSogsPeelResult localPeel(int cellIndex) {
        return state(cellIndex) == MpSogsCellState.SINGLETON
            ? MpSogsPeelResult.element(singletonValue(cellIndex))
            : MpSogsPeelResult.bottom();
    }

    public MpSogsLocalCellView localCellView(int cellIndex) {
        MpSogsCellState cellState = state(cellIndex);
        switch (cellState) {
            case EMPTY:
                return MpSogsLocalCellView.empty();
            case SINGLETON:
                return MpSogsLocalCellView.singleton(singletonValue(cellIndex));
            case HEAVY:
                return MpSogsLocalCellView.heavy();
            default:
                throw new IllegalStateException("unknown cell state: " + cellState);
        }
    }

    public boolean containsRemaining(long value) {
        return remainingElements.contains(value);
    }

    public Set<Long> getRemainingElements() {
        return new HashSet<>(remainingElements);
    }

    public int getCellNum() {
        return cells.length;
    }

    public MpSogsMpsuParams getParams() {
        return params;
    }

    int cellCount(int cellIndex) {
        checkCellIndex(cellIndex);
        return cells[cellIndex].count();
    }

    private void checkCellIndex(int cellIndex) {
        if (cellIndex < 0 || cellIndex >= cells.length) {
            throw new IndexOutOfBoundsException("invalid cell index: " + cellIndex);
        }
    }
}
