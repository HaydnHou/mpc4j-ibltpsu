package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * MP-SOGS MPSU driver over a batch union-peel primitive.
 *
 * <p>This class is the protocol-level skeleton. It only assumes that a batch secure uPeel implementation
 * reveals one public result per public cell: either {@code bottom} or an opened union element. The driver
 * never branches on hidden counts or party ownership.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class BatchMpSogsMpsu {
    private BatchMpSogsMpsu() {
        // empty
    }

    /**
     * Runs MP-SOGS with a caller-supplied batch union-peel primitive.
     *
     * @param sketches local sketches, one per party.
     * @param params MP-SOGS parameters.
     * @param expectedUnion expected union for success checking in tests / simulators.
     * @param unionPeel batch union-peel primitive.
     * @return public transcript.
     */
    public static MpSogsTranscript runWithSketches(
        List<MpSogsSketch> sketches, MpSogsMpsuParams params, Set<Long> expectedUnion,
        SecureMpSogsUnionPeel unionPeel
    ) {
        if (sketches.size() != params.getPartyNum()) {
            throw new IllegalArgumentException("sketches size must equal partyNum");
        }
        Set<Long> unionOutput = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        Set<Integer> queue = IntStream.range(0, params.getCellNum())
            .boxed()
            .collect(Collectors.toCollection(TreeSet::new));
        String failureReason = "";
        for (int round = 0; !queue.isEmpty(); round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            List<Integer> batchCells = new ArrayList<>(queue);
            BatchMpSogsPeelOutput output = unionPeel.peelBatch(new BatchMpSogsPeelInput(round, batchCells));
            if (output.getResults().size() != batchCells.size()) {
                throw new IllegalStateException("union-peel output size must match input size");
            }
            List<Long> openedBatch = output.getResults().stream()
                .filter(result -> !result.isBottom())
                .map(MpSogsPeelResult::getValue)
                .collect(Collectors.toCollection(ArrayList::new));
            Set<Long> distinctOpened = new LinkedHashSet<>(openedBatch);
            Set<Long> newlyOpened = new LinkedHashSet<>(distinctOpened);
            newlyOpened.removeAll(unionOutput);
            int duplicateOpenings = openedBatch.size() - distinctOpened.size();
            stats.add(new MpSogsRoundStats(
                round,
                queue.size(),
                openedBatch.size(),
                newlyOpened.size(),
                duplicateOpenings,
                batchCells.size(),
                output.getSendBytes(),
                output.getReceiveBytes(),
                output.getRoundCount()
            ));
            if (newlyOpened.isEmpty()) {
                break;
            }
            deleteOpenedValues(sketches, newlyOpened);
            unionOutput.addAll(newlyOpened);
            queue = ClearMpSogsMpsu.nextQueue(newlyOpened, params);
        }
        boolean success = unionOutput.equals(expectedUnion);
        if (!success && failureReason.isEmpty()) {
            failureReason = "stalled before full union output";
        }
        return new MpSogsTranscript(unionOutput, stats, success, failureReason);
    }

    /**
     * Runs the clear dummy batch path from raw party inputs. This is not secure.
     *
     * @param partyInputs party input sets.
     * @param params MP-SOGS parameters.
     * @return public transcript.
     */
    public static MpSogsTranscript runDummyClear(List<Set<Long>> partyInputs, MpSogsMpsuParams params) {
        if (partyInputs.size() != params.getPartyNum()) {
            throw new IllegalArgumentException("partyInputs size must equal partyNum");
        }
        List<MpSogsSketch> sketches = partyInputs.stream()
            .map(input -> MpSogsSketch.encode(input, params))
            .collect(Collectors.toCollection(ArrayList::new));
        return runWithSketches(
            sketches, params, ClearMpSogsMpsu.unionOf(partyInputs), new DummySecureMpSogsUnionPeel(sketches)
        );
    }

    private static void deleteOpenedValues(List<MpSogsSketch> sketches, Collection<Long> newlyOpened) {
        for (long value : newlyOpened) {
            for (MpSogsSketch sketch : sketches) {
                sketch.deleteIfPresentOnce(value);
            }
        }
    }
}
