package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsRoundStats;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * End-to-end clear oracle for secret-shared multiplicity SOGS. This class is not secure.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class ClearMultiplicityMpSogsMpsu {
    private ClearMultiplicityMpSogsMpsu() {
        // empty
    }

    public static MpSogsTranscript run(List<Set<Long>> partyInputs, MpSogsMpsuParams params, long sessionSeed) {
        if (partyInputs.size() != params.getPartyNum()) {
            throw new IllegalArgumentException("input count must equal party count");
        }
        List<MultiplicitySogsSketch> sketches = new ArrayList<>(partyInputs.size());
        Set<Long> expectedUnion = new LinkedHashSet<>();
        for (Set<Long> input : partyInputs) {
            sketches.add(MultiplicitySogsSketch.encode(input, params, sessionSeed));
            expectedUnion.addAll(input);
        }
        ClearMultiplicityUnionPeel peel = new ClearMultiplicityUnionPeel(sketches, params);
        Set<Long> outputUnion = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        MpSogsTier tier = MpSogsTier.MAIN;
        int[] queue = allCells(params.getCellNum(tier));
        String failureReason = "";
        for (int round = 0; queue.length > 0; round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            BatchMpSogsPeelOutput batch = peel.peelBatch(new BatchMpSogsPeelInput(
                round, tier, toList(queue)
            ));
            Set<Long> newlyOpened = new LinkedHashSet<>();
            int opened = 0;
            for (MpSogsPeelResult result : batch.getResults()) {
                if (!result.isBottom()) {
                    opened++;
                    if (outputUnion.add(result.getValue())) {
                        newlyOpened.add(result.getValue());
                    }
                }
            }
            stats.add(new MpSogsRoundStats(
                round, queue.length, opened, newlyOpened.size(), opened - newlyOpened.size(), queue.length,
                0L, 0L, 0
            ));
            if (newlyOpened.isEmpty()) {
                boolean residual = sketches.stream().anyMatch(MultiplicitySogsSketch::hasRemainingElements);
                if (params.isTwoTier() && tier == MpSogsTier.MAIN && residual) {
                    tier = MpSogsTier.AUXILIARY;
                    queue = allCells(params.getCellNum(tier));
                    continue;
                }
                if (residual) {
                    failureReason = "stalled before all residual elements were peeled";
                }
                break;
            }
            for (long value : newlyOpened) {
                sketches.forEach(sketch -> sketch.deleteIfPresentOnce(value));
            }
            queue = nextQueue(newlyOpened, params, tier);
        }
        boolean success = failureReason.isEmpty() && outputUnion.equals(expectedUnion);
        if (!success && failureReason.isEmpty()) {
            failureReason = "union output does not match expected union";
        }
        return new MpSogsTranscript(outputUnion, stats, success, failureReason);
    }

    private static int[] allCells(int cellNum) {
        int[] cells = new int[cellNum];
        for (int cellIndex = 0; cellIndex < cellNum; cellIndex++) {
            cells[cellIndex] = cellIndex;
        }
        return cells;
    }

    private static List<Integer> toList(int[] cells) {
        List<Integer> result = new ArrayList<>(cells.length);
        for (int cell : cells) {
            result.add(cell);
        }
        return result;
    }

    private static int[] nextQueue(Set<Long> opened, MpSogsMpsuParams params, MpSogsTier tier) {
        int[] cells = new int[Math.multiplyExact(opened.size(), params.getHashNum(tier))];
        int size = 0;
        for (long value : opened) {
            for (int cell : MpSogsHashUtils.cells(value, params, tier)) {
                cells[size++] = cell;
            }
        }
        Arrays.sort(cells, 0, size);
        int unique = 0;
        int previous = -1;
        for (int index = 0; index < size; index++) {
            if (unique == 0 || cells[index] != previous) {
                cells[unique++] = cells[index];
                previous = cells[index];
            }
        }
        return unique == cells.length ? cells : Arrays.copyOf(cells, unique);
    }
}
