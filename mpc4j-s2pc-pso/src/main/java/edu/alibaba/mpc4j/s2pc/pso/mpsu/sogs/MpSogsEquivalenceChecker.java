package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks clear MP-SOGS against direct Encode(U) peeling.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsEquivalenceChecker {
    private MpSogsEquivalenceChecker() {
        // empty
    }

    public static Result check(List<Set<Long>> partyInputs, MpSogsMpsuParams params) {
        Set<Long> union = ClearMpSogsMpsu.unionOf(partyInputs);
        List<MpSogsSketch> mpSketches = partyInputs.stream()
            .map(input -> MpSogsSketch.encode(input, params))
            .collect(Collectors.toCollection(ArrayList::new));
        MpSogsSketch directSketch = MpSogsSketch.encode(union, params);
        Set<Long> unionOutput = new LinkedHashSet<>();
        MpSogsTier tier = MpSogsTier.MAIN;
        Set<Integer> queue = ClearMpSogsMpsu.allCells(params, tier);
        for (int round = 0; !queue.isEmpty(); round++) {
            if (round >= params.getMaxPeelRounds()) {
                return Result.fail("exceeds max peel rounds");
            }
            List<Integer> batchCells = new ArrayList<>(queue);
            List<MpSogsPeelResult> mpResults = new ArrayList<>(batchCells.size());
            List<MpSogsPeelResult> directResults = new ArrayList<>(batchCells.size());
            for (int cellIndex : batchCells) {
                mpResults.add(ClearMpSogsUnionPeel.uPeel(mpSketches, tier, cellIndex));
                directResults.add(directSketch.localPeel(tier, cellIndex));
            }
            if (!mpResults.equals(directResults)) {
                for (int index = 0; index < batchCells.size(); index++) {
                    if (!mpResults.get(index).equals(directResults.get(index))) {
                        return Result.fail("cell output mismatch at round " + round + ", cell "
                            + tier + "/" + batchCells.get(index) + ": mp=" + mpResults.get(index)
                            + ", direct=" + directResults.get(index));
                    }
                }
            }
            Set<Long> distinctOpened = mpResults.stream()
                .filter(result -> !result.isBottom())
                .map(MpSogsPeelResult::getValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> newlyOpened = new LinkedHashSet<>(distinctOpened);
            newlyOpened.removeAll(unionOutput);
            if (newlyOpened.isEmpty()) {
                if (params.isTwoTier() && tier == MpSogsTier.MAIN && ClearMpSogsMpsu.hasResidual(mpSketches)) {
                    tier = MpSogsTier.AUXILIARY;
                    queue = ClearMpSogsMpsu.allCells(params, tier);
                    continue;
                }
                break;
            }
            for (long value : newlyOpened) {
                for (MpSogsSketch sketch : mpSketches) {
                    sketch.deleteIfPresentOnce(value);
                }
                directSketch.deleteIfPresentOnce(value);
            }
            unionOutput.addAll(newlyOpened);
            queue = ClearMpSogsMpsu.nextQueue(newlyOpened, params, tier);
        }
        if (!unionOutput.equals(union)) {
            return Result.fail("stalled before full union output");
        }
        return Result.success(ClearMpSogsMpsu.run(partyInputs, params));
    }

    /**
     * Equivalence check result.
     */
    public static class Result {
        private final boolean success;
        private final String message;
        private final MpSogsTranscript transcript;

        private Result(boolean success, String message, MpSogsTranscript transcript) {
            this.success = success;
            this.message = message;
            this.transcript = transcript;
        }

        static Result success(MpSogsTranscript transcript) {
            return new Result(true, "", transcript);
        }

        static Result fail(String message) {
            return new Result(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public MpSogsTranscript getTranscript() {
            return transcript;
        }
    }
}
