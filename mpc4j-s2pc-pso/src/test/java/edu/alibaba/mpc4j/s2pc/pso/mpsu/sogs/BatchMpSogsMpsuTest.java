package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Batch MP-SOGS MPSU driver tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class BatchMpSogsMpsuTest {
    @Test
    public void testDummyBatchMatchesClearPath() {
        List<Set<Long>> inputs = generateInputs(3, 96, 0.5);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, union(inputs).size())
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        MpSogsTranscript clearTranscript = ClearMpSogsMpsu.run(inputs, params);
        MpSogsTranscript batchTranscript = BatchMpSogsMpsu.runDummyClear(inputs, params);
        Assert.assertTrue(batchTranscript.getFailureReason(), batchTranscript.isSuccess());
        Assert.assertEquals(clearTranscript.getUnionOutput(), batchTranscript.getUnionOutput());
        Assert.assertEquals(clearTranscript.getRoundNum(), batchTranscript.getRoundNum());
        Assert.assertEquals(clearTranscript.getUpeelCalls(), batchTranscript.getUpeelCalls());
        Assert.assertEquals(0L, batchTranscript.getSendBytes());
        Assert.assertEquals(0L, batchTranscript.getReceiveBytes());
        Assert.assertEquals(0, batchTranscript.getNetworkRoundCount());
    }

    @Test
    public void testBatchAccountingPassesThroughTranscript() {
        List<Set<Long>> inputs = generateInputs(3, 64, 0.25);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, union(inputs).size())
            .setAlpha(1.4)
            .setHashNum(3)
            .build();
        List<MpSogsSketch> sketches = inputs.stream()
            .map(input -> MpSogsSketch.encode(input, params))
            .collect(Collectors.toCollection(ArrayList::new));
        MpSogsTranscript transcript = BatchMpSogsMpsu.runWithSketches(
            sketches,
            params,
            union(inputs),
            new AccountingClearUnionPeel(sketches, 11L, 13L, 2)
        );
        Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
        Assert.assertEquals(union(inputs), transcript.getUnionOutput());
        Assert.assertTrue(transcript.getRoundNum() > 0);
        Assert.assertEquals(11L * transcript.getRoundNum(), transcript.getSendBytes());
        Assert.assertEquals(13L * transcript.getRoundNum(), transcript.getReceiveBytes());
        Assert.assertEquals(2 * transcript.getRoundNum(), transcript.getNetworkRoundCount());
    }

    private static class AccountingClearUnionPeel implements SecureMpSogsUnionPeel {
        private final List<MpSogsSketch> sketches;
        private final long sendBytes;
        private final long receiveBytes;
        private final int roundCount;

        AccountingClearUnionPeel(List<MpSogsSketch> sketches, long sendBytes, long receiveBytes, int roundCount) {
            this.sketches = sketches;
            this.sendBytes = sendBytes;
            this.receiveBytes = receiveBytes;
            this.roundCount = roundCount;
        }

        @Override
        public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
            List<MpSogsPeelResult> results = input.getCellIndexes().stream()
                .map(cellIndex -> ClearMpSogsUnionPeel.uPeel(sketches, cellIndex))
                .collect(Collectors.toCollection(ArrayList::new));
            return new BatchMpSogsPeelOutput(results, sendBytes, receiveBytes, roundCount);
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
