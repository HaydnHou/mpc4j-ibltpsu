package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Direct peel over Encode(U), used as the clear oracle.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class DirectUnionSogsPeel {
    private DirectUnionSogsPeel() {
        // empty
    }

    public static MpSogsTranscript run(Set<Long> union, MpSogsMpsuParams params) {
        MpSogsSketch unionSketch = MpSogsSketch.encode(union, params);
        Set<Long> unionOutput = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        MpSogsTier tier = MpSogsTier.MAIN;
        Set<Integer> queue = ClearMpSogsMpsu.allCells(params, tier);
        String failureReason = "";
        for (int round = 0; !queue.isEmpty(); round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            List<Long> openedBatch = new ArrayList<>();
            long calls = 0L;
            for (int cellIndex : queue) {
                MpSogsPeelResult result = unionSketch.localPeel(tier, cellIndex);
                calls++;
                if (!result.isBottom()) {
                    openedBatch.add(result.getValue());
                }
            }
            Set<Long> distinctOpened = new LinkedHashSet<>(openedBatch);
            Set<Long> newlyOpened = new LinkedHashSet<>(distinctOpened);
            newlyOpened.removeAll(unionOutput);
            int duplicateOpenings = openedBatch.size() - distinctOpened.size();
            stats.add(new MpSogsRoundStats(
                round, queue.size(), openedBatch.size(), newlyOpened.size(), duplicateOpenings, calls
            ));
            if (newlyOpened.isEmpty()) {
                if (params.isTwoTier() && tier == MpSogsTier.MAIN && !unionSketch.getRemainingElements().isEmpty()) {
                    tier = MpSogsTier.AUXILIARY;
                    queue = ClearMpSogsMpsu.allCells(params, tier);
                    continue;
                }
                break;
            }
            for (long value : newlyOpened) {
                unionSketch.deleteIfPresentOnce(value);
            }
            unionOutput.addAll(newlyOpened);
            queue = ClearMpSogsMpsu.nextQueue(newlyOpened, params, tier);
        }
        boolean success = unionOutput.equals(union);
        if (!success && failureReason.isEmpty()) {
            failureReason = "stalled before full union output";
        }
        return new MpSogsTranscript(unionOutput, stats, success, failureReason);
    }
}
