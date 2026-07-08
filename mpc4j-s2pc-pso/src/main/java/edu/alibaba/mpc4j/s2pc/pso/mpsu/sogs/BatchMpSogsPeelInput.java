package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public batch input for one MP-SOGS union-peel layer.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class BatchMpSogsPeelInput {
    /**
     * Public round index.
     */
    private final int roundIndex;
    /**
     * Public SOGS tier for this batch.
     */
    private final MpSogsTier tier;
    /**
     * Ordered public cell indexes.
     */
    private final List<Integer> cellIndexes;

    public BatchMpSogsPeelInput(int roundIndex, List<Integer> cellIndexes) {
        this(roundIndex, MpSogsTier.MAIN, cellIndexes);
    }

    public BatchMpSogsPeelInput(int roundIndex, MpSogsTier tier, List<Integer> cellIndexes) {
        if (roundIndex < 0) {
            throw new IllegalArgumentException("roundIndex must be non-negative");
        }
        if (tier == null) {
            throw new NullPointerException("tier");
        }
        this.roundIndex = roundIndex;
        this.tier = tier;
        this.cellIndexes = Collections.unmodifiableList(new ArrayList<>(cellIndexes));
    }

    public int getRoundIndex() {
        return roundIndex;
    }

    public MpSogsTier getTier() {
        return tier;
    }

    public List<Integer> getCellIndexes() {
        return cellIndexes;
    }

    public int size() {
        return cellIndexes.size();
    }

    public MpSogsUpeelCircuitShape circuitShape(MpSogsMpsuParams params) {
        return new MpSogsUpeelCircuitShape(
            params.getPartyNum(), MpSogsMpsuParams.ELEMENT_BIT_LENGTH, size()
        );
    }
}
