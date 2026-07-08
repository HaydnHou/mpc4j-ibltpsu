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
     * Main-tier cells.
     */
    private final MpSogsCell[] mainCells;
    /**
     * Auxiliary-tier cells, present only when two-tier SOGS is enabled.
     */
    private final MpSogsCell[] auxiliaryCells;
    /**
     * Remaining local set. Used only for local delete/no-op in the clear prototype.
     */
    private final Set<Long> remainingElements;

    public MpSogsSketch(MpSogsMpsuParams params) {
        this.params = params;
        mainCells = createCells(params.getCellNum(MpSogsTier.MAIN));
        auxiliaryCells = params.isTwoTier() ? createCells(params.getCellNum(MpSogsTier.AUXILIARY)) : null;
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
        add(value, MpSogsTier.MAIN);
        if (params.isTwoTier()) {
            add(value, MpSogsTier.AUXILIARY);
        }
    }

    public boolean deleteIfPresentOnce(long value) {
        if (!remainingElements.remove(value)) {
            return false;
        }
        remove(value, MpSogsTier.MAIN);
        if (params.isTwoTier()) {
            remove(value, MpSogsTier.AUXILIARY);
        }
        return true;
    }

    public MpSogsCellState state(int cellIndex) {
        return state(MpSogsTier.MAIN, cellIndex);
    }

    public MpSogsCellState state(MpSogsTier tier, int cellIndex) {
        checkCellIndex(tier, cellIndex);
        return cells(tier)[cellIndex].state();
    }

    public long singletonValue(int cellIndex) {
        return singletonValue(MpSogsTier.MAIN, cellIndex);
    }

    public long singletonValue(MpSogsTier tier, int cellIndex) {
        checkCellIndex(tier, cellIndex);
        return cells(tier)[cellIndex].singletonValue();
    }

    public MpSogsPeelResult localPeel(int cellIndex) {
        return localPeel(MpSogsTier.MAIN, cellIndex);
    }

    public MpSogsPeelResult localPeel(MpSogsTier tier, int cellIndex) {
        return state(tier, cellIndex) == MpSogsCellState.SINGLETON
            ? MpSogsPeelResult.element(singletonValue(tier, cellIndex))
            : MpSogsPeelResult.bottom();
    }

    public MpSogsLocalCellView localCellView(int cellIndex) {
        return localCellView(MpSogsTier.MAIN, cellIndex);
    }

    public MpSogsLocalCellView localCellView(MpSogsTier tier, int cellIndex) {
        MpSogsCellState cellState = state(tier, cellIndex);
        switch (cellState) {
            case EMPTY:
                return MpSogsLocalCellView.empty();
            case SINGLETON:
                return MpSogsLocalCellView.singleton(singletonValue(tier, cellIndex));
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
        return getCellNum(MpSogsTier.MAIN);
    }

    public int getCellNum(MpSogsTier tier) {
        return cells(tier).length;
    }

    public MpSogsMpsuParams getParams() {
        return params;
    }

    int cellCount(int cellIndex) {
        return cellCount(MpSogsTier.MAIN, cellIndex);
    }

    int cellCount(MpSogsTier tier, int cellIndex) {
        checkCellIndex(tier, cellIndex);
        return cells(tier)[cellIndex].count();
    }

    private void add(long value, MpSogsTier tier) {
        for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
            cells(tier)[cellIndex].add(value);
        }
    }

    private void remove(long value, MpSogsTier tier) {
        for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
            cells(tier)[cellIndex].remove(value);
        }
    }

    private static MpSogsCell[] createCells(int cellNum) {
        MpSogsCell[] cells = new MpSogsCell[cellNum];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = new MpSogsCell();
        }
        return cells;
    }

    private MpSogsCell[] cells(MpSogsTier tier) {
        if (tier == MpSogsTier.MAIN) {
            return mainCells;
        }
        if (auxiliaryCells == null) {
            throw new IllegalStateException("auxiliary tier is disabled");
        }
        return auxiliaryCells;
    }

    private void checkCellIndex(int cellIndex) {
        checkCellIndex(MpSogsTier.MAIN, cellIndex);
    }

    private void checkCellIndex(MpSogsTier tier, int cellIndex) {
        int cellNum = getCellNum(tier);
        if (cellIndex < 0 || cellIndex >= cellNum) {
            throw new IndexOutOfBoundsException("invalid " + tier + " cell index: " + cellIndex);
        }
    }
}
