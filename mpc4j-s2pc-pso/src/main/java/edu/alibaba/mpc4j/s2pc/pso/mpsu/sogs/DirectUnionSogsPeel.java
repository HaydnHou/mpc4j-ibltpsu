package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

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
        Set<Integer> queue = IntStream.range(0, params.getCellNum()).boxed().collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        String failureReason = "";
        for (int round = 0; !queue.isEmpty(); round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            List<Long> openedBatch = new ArrayList<>();
            long calls = 0L;
            for (int cellIndex : queue) {
                MpSogsPeelResult result = unionSketch.localPeel(cellIndex);
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
                break;
            }
            for (long value : newlyOpened) {
                unionSketch.deleteIfPresentOnce(value);
            }
            unionOutput.addAll(newlyOpened);
            queue = ClearMpSogsMpsu.nextQueue(newlyOpened, params);
        }
        boolean success = unionOutput.equals(union);
        if (!success && failureReason.isEmpty()) {
            failureReason = "stalled before full union output";
        }
        return new MpSogsTranscript(unionOutput, stats, success, failureReason);
    }
}
