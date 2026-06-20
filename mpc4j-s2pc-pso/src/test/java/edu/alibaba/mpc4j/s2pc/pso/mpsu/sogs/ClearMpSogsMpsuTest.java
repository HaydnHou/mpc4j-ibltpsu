package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Clear all-output MP-SOGS MPSU tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class ClearMpSogsMpsuTest {
    @Test
    public void testThreePartyAllOutputDisjoint() {
        List<Set<Long>> inputs = generateInputs(3, 64, 0.0);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, union(inputs).size())
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        MpSogsTranscript transcript = ClearMpSogsMpsu.run(inputs, params);
        Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
        Assert.assertEquals(union(inputs), transcript.getUnionOutput());
        for (int partyIndex = 0; partyIndex < inputs.size(); partyIndex++) {
            MpSogsMpsuParticipant participant = new MpSogsMpsuParticipant(partyIndex);
            Assert.assertEquals(transcript.getUnionOutput(), participant.clearMpsu(inputs, params));
        }
    }

    @Test
    public void testThreePartyAllOutputHighOverlap() {
        List<Set<Long>> inputs = generateInputs(3, 128, 0.9);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, union(inputs).size())
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        MpSogsTranscript transcript = ClearMpSogsMpsu.run(inputs, params);
        Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
        Assert.assertEquals(union(inputs), transcript.getUnionOutput());
        Assert.assertTrue(transcript.getUpeelCalls() > 0);
    }

    @Test
    public void testEquivalenceChecker() {
        List<Set<Long>> inputs = generateInputs(3, 128, 0.5);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, union(inputs).size())
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        MpSogsEquivalenceChecker.Result result = MpSogsEquivalenceChecker.check(inputs, params);
        Assert.assertTrue(result.getMessage(), result.isSuccess());
        Assert.assertEquals(union(inputs), result.getTranscript().getUnionOutput());
    }

    @Test
    public void testAllParticipantLocalOutput() {
        List<Set<Long>> inputs = generateInputs(3, 96, 0.25);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, union(inputs).size())
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(params).build();
        for (int partyIndex = 0; partyIndex < inputs.size(); partyIndex++) {
            MpSogsMpsuParticipant participant = new MpSogsMpsuParticipant(partyIndex);
            Assert.assertEquals(union(inputs), participant.localMpsu(inputs, config));
        }
    }

    private List<Set<Long>> generateInputs(int parties, int n, double commonOverlap) {
        int commonCount = (int) Math.round(n * commonOverlap);
        int uniqueCount = n - commonCount;
        List<Set<Long>> inputs = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < parties; partyIndex++) {
            Set<Long> input = new HashSet<>();
            for (long value = 1; value <= commonCount; value++) {
                input.add(value);
            }
            long start = commonCount + (long) partyIndex * uniqueCount + 1;
            for (long value = start; value < start + uniqueCount; value++) {
                input.add(value);
            }
            inputs.add(input);
        }
        return inputs;
    }

    private Set<Long> union(List<Set<Long>> inputs) {
        Set<Long> union = new HashSet<>();
        inputs.forEach(union::addAll);
        return union;
    }
}
