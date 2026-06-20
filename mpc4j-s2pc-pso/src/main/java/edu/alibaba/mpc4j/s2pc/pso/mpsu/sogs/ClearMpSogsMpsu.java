package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Clear all-output MP-SOGS MPSU evaluator. This is not a secure protocol.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class ClearMpSogsMpsu {
    private ClearMpSogsMpsu() {
        // empty
    }

    public static MpSogsTranscript run(List<Set<Long>> partyInputs, MpSogsMpsuParams params) {
        if (partyInputs.size() != params.getPartyNum()) {
            throw new IllegalArgumentException("partyInputs size must equal partyNum");
        }
        List<MpSogsSketch> sketches = partyInputs.stream()
            .map(input -> MpSogsSketch.encode(input, params))
            .collect(Collectors.toList());
        return runWithSketches(sketches, params, unionOf(partyInputs));
    }

    static MpSogsTranscript runWithSketches(List<MpSogsSketch> sketches, MpSogsMpsuParams params,
                                            Set<Long> expectedUnion) {
        Set<Long> unionOutput = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        Set<Integer> queue = IntStream.range(0, params.getCellNum()).boxed().collect(Collectors.toCollection(TreeSet::new));
        String failureReason = "";
        for (int round = 0; !queue.isEmpty(); round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            List<Long> openedBatch = new ArrayList<>();
            long calls = 0L;
            for (int cellIndex : queue) {
                MpSogsPeelResult result = ClearMpSogsUnionPeel.uPeel(sketches, cellIndex);
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
                for (MpSogsSketch sketch : sketches) {
                    sketch.deleteIfPresentOnce(value);
                }
            }
            unionOutput.addAll(newlyOpened);
            queue = nextQueue(newlyOpened, params);
        }
        boolean success = unionOutput.equals(expectedUnion);
        if (!success && failureReason.isEmpty()) {
            failureReason = "stalled before full union output";
        }
        return new MpSogsTranscript(unionOutput, stats, success, failureReason);
    }

    static Set<Long> unionOf(Collection<? extends Set<Long>> sets) {
        Set<Long> union = new HashSet<>();
        sets.forEach(union::addAll);
        return union;
    }

    public static Set<Integer> nextQueue(Collection<Long> newlyOpened, MpSogsMpsuParams params) {
        Set<Integer> nextQueue = new TreeSet<>();
        for (long value : newlyOpened) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params)) {
                nextQueue.add(cellIndex);
            }
        }
        return nextQueue;
    }
}
