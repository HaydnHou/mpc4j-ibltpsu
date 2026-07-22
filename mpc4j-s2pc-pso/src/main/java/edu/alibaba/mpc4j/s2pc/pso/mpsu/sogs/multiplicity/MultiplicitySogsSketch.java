package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Party-local two-tier SOGS with multiplicity moment cells.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class MultiplicitySogsSketch {
    private final MpSogsMpsuParams params;
    private final long sessionSeed;
    private final MultiplicityPayloadEncoding payloadEncoding;
    private MultiplicitySogsCell[] mainCells;
    private MultiplicitySogsCell[] auxiliaryCells;
    private final Set<Long> remainingElements;

    public MultiplicitySogsSketch(MpSogsMpsuParams params, long sessionSeed) {
        this(params, sessionSeed, MultiplicityPayloadEncoding.FULL_LIMBS);
    }

    public MultiplicitySogsSketch(MpSogsMpsuParams params, long sessionSeed,
                                  MultiplicityPayloadEncoding payloadEncoding) {
        this.params = params;
        this.sessionSeed = sessionSeed;
        this.payloadEncoding = payloadEncoding;
        payloadEncoding.validate(params);
        mainCells = createCells(params, MpSogsTier.MAIN, payloadEncoding);
        auxiliaryCells = params.isTwoTier()
            ? createCells(params, MpSogsTier.AUXILIARY, payloadEncoding) : null;
        remainingElements = new HashSet<>();
    }

    public static MultiplicitySogsSketch encode(Collection<Long> input, MpSogsMpsuParams params, long sessionSeed) {
        return encode(input, params, sessionSeed, MultiplicityPayloadEncoding.FULL_LIMBS);
    }

    public static MultiplicitySogsSketch encode(Collection<Long> input, MpSogsMpsuParams params, long sessionSeed,
                                                 MultiplicityPayloadEncoding payloadEncoding) {
        MultiplicitySogsSketch sketch = new MultiplicitySogsSketch(params, sessionSeed, payloadEncoding);
        for (long value : new HashSet<>(input)) {
            sketch.insert(value);
        }
        return sketch;
    }

    public void insert(long value) {
        if (!remainingElements.add(value)) {
            return;
        }
        update(value, MpSogsTier.MAIN, true);
        if (params.isTwoTier()) {
            update(value, MpSogsTier.AUXILIARY, true);
        }
    }

    public boolean deleteIfPresentOnce(long value) {
        if (!remainingElements.remove(value)) {
            return false;
        }
        update(value, MpSogsTier.MAIN, false);
        if (params.isTwoTier()) {
            update(value, MpSogsTier.AUXILIARY, false);
        }
        return true;
    }

    /**
     * Removes one public output from the local residual set after the active tier has moved to persistent sharing.
     * While Main is active, the not-yet-shared Auxiliary tier must receive the same deletion.
     */
    public boolean deleteAfterPersistentOpen(long value, MpSogsTier activeTier) {
        if (!remainingElements.remove(value)) {
            return false;
        }
        if (activeTier == MpSogsTier.MAIN && params.isTwoTier()) {
            update(value, MpSogsTier.AUXILIARY, false);
        }
        return true;
    }

    /**
     * Releases a plaintext tier after its aggregate Shamir shares have been installed.
     */
    public void releaseTier(MpSogsTier tier) {
        if (tier == MpSogsTier.MAIN) {
            mainCells = null;
        } else {
            auxiliaryCells = null;
        }
    }

    public long[] getCellWords(MpSogsTier tier, int cellIndex) {
        checkCellIndex(tier, cellIndex);
        return cells(tier)[cellIndex].toArray();
    }

    public int getCellNum(MpSogsTier tier) {
        return params.getCellNum(tier);
    }

    public boolean hasRemainingElements() {
        return !remainingElements.isEmpty();
    }

    public Set<Long> getRemainingElements() {
        return new HashSet<>(remainingElements);
    }

    public MpSogsMpsuParams getParams() {
        return params;
    }

    public long getSessionSeed() {
        return sessionSeed;
    }

    public MultiplicityPayloadEncoding getPayloadEncoding() {
        return payloadEncoding;
    }

    public int getWordNum() {
        return payloadEncoding.getCellWordNum();
    }

    private void update(long value, MpSogsTier tier, boolean add) {
        for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
            if (add) {
                cells(tier)[cellIndex].add(value, sessionSeed, tier);
            } else {
                cells(tier)[cellIndex].remove(value, sessionSeed, tier);
            }
        }
    }

    private MultiplicitySogsCell[] cells(MpSogsTier tier) {
        if (tier == MpSogsTier.MAIN) {
            if (mainCells == null) {
                throw new IllegalStateException("main tier has been released");
            }
            return mainCells;
        }
        if (auxiliaryCells == null) {
            throw new IllegalStateException("auxiliary tier is disabled");
        }
        return auxiliaryCells;
    }

    private void checkCellIndex(MpSogsTier tier, int cellIndex) {
        if (cellIndex < 0 || cellIndex >= getCellNum(tier)) {
            throw new IndexOutOfBoundsException("invalid " + tier + " cell index: " + cellIndex);
        }
    }

    private static MultiplicitySogsCell[] createCells(MpSogsMpsuParams params, MpSogsTier tier,
                                                      MultiplicityPayloadEncoding payloadEncoding) {
        int cellNum = params.getCellNum(tier);
        MultiplicitySogsCell[] cells = new MultiplicitySogsCell[cellNum];
        for (int cellIndex = 0; cellIndex < cellNum; cellIndex++) {
            cells[cellIndex] = new MultiplicitySogsCell(params, tier, cellIndex, payloadEncoding);
        }
        return cells;
    }
}
