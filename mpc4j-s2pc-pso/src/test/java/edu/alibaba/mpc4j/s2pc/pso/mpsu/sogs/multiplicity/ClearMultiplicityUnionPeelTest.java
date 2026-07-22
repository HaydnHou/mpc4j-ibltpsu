package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Clear multiplicity-uPeel oracle tests.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class ClearMultiplicityUnionPeelTest {
    @Test
    public void testSharedSingletonAndMultiDistinct() {
        int partyNum = 5;
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, 32)
            .setAlpha(4.0)
            .setTwoTier(false)
            .build();
        long seed = 20260722L;
        long shared = 0xFFFF_FFFF_8000_0000L;
        int cellIndex = MpSogsHashUtils.cells(shared, params, MpSogsTier.MAIN)[0];
        List<MultiplicitySogsSketch> sketches = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            sketches.add(MultiplicitySogsSketch.encode(Set.of(shared), params, seed));
        }
        ClearMultiplicityUnionPeel peel = new ClearMultiplicityUnionPeel(sketches, params);
        BatchMpSogsPeelOutput singleton = peel.peelBatch(new BatchMpSogsPeelInput(
            0, MpSogsTier.MAIN, List.of(cellIndex)
        ));
        Assert.assertFalse(singleton.getResults().get(0).isBottom());
        Assert.assertEquals(shared, singleton.getResults().get(0).getValue());

        long other = findSameCell(shared, params, cellIndex);
        sketches.set(0, MultiplicitySogsSketch.encode(Set.of(shared, other), params, seed));
        BatchMpSogsPeelOutput multiple = new ClearMultiplicityUnionPeel(sketches, params).peelBatch(
            new BatchMpSogsPeelInput(0, MpSogsTier.MAIN, List.of(cellIndex))
        );
        Assert.assertTrue(multiple.getResults().get(0).isBottom());
    }

    @Test
    public void testFivePartyEndToEndUnion() {
        int partyNum = 5;
        List<Set<Long>> inputs = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            inputs.add(Set.of(1L, 2L, 100L + partyIndex, Long.MIN_VALUE + partyIndex));
        }
        Set<Long> expected = new java.util.HashSet<>();
        inputs.forEach(expected::addAll);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expected.size())
            .setAlpha(4.0)
            .setTwoTier(true)
            .setAuxiliaryCellNum(96)
            .build();
        edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript transcript =
            ClearMultiplicityMpSogsMpsu.run(inputs, params, 20260722L);
        Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
        Assert.assertEquals(expected, transcript.getUnionOutput());
    }

    private static long findSameCell(long value, MpSogsMpsuParams params, int targetCell) {
        for (long candidate = value + 1; candidate != value; candidate++) {
            if (MpSogsHashUtils.cells(candidate, params, MpSogsTier.MAIN)[0] == targetCell) {
                return candidate;
            }
        }
        throw new AssertionError("failed to find same-row cell collision");
    }
}
