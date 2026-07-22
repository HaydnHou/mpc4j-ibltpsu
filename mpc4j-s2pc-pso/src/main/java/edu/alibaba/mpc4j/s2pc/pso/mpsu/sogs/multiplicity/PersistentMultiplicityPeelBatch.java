package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Party-local persistent multiplicity peel output. Count shares are never opened or serialized as public results.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class PersistentMultiplicityPeelBatch {
    private final List<MpSogsPeelResult> results;
    private final long[] countShares;
    private final int roundCount;

    public PersistentMultiplicityPeelBatch(List<MpSogsPeelResult> results, long[] countShares, int roundCount) {
        if (results.size() != countShares.length) {
            throw new IllegalArgumentException("persistent result and count-share sizes differ");
        }
        if (roundCount < 0) {
            throw new IllegalArgumentException("roundCount must be non-negative");
        }
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.countShares = Arrays.copyOf(countShares, countShares.length);
        this.roundCount = roundCount;
    }

    public List<MpSogsPeelResult> getResults() {
        return results;
    }

    public long getCountShare(int batchIndex) {
        return countShares[batchIndex];
    }

    public int getRoundCount() {
        return roundCount;
    }
}
