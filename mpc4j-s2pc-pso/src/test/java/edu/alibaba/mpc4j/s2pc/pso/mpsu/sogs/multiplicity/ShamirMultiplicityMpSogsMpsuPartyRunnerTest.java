package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * End-to-end tests for the secret-shared multiplicity Shamir backend.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class ShamirMultiplicityMpSogsMpsuPartyRunnerTest {
    @Test
    public void testThreePartyAllOutput() throws InterruptedException {
        runTest(3, 8, 4);
    }

    @Test
    public void testFourPartyAllOutput() throws InterruptedException {
        runTest(4, 8, 4);
    }

    @Test
    public void testFivePartyT2AllOutput() throws InterruptedException {
        runTest(5, 12, 6);
    }

    private static void runTest(int partyNum, int setSize, int commonSize) throws InterruptedException {
        List<Set<Long>> inputs = generateInputs(partyNum, setSize, commonSize);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(4.0)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(96)
            .setMaxPeelRounds(256)
            .build();
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY)
            .setMaxHashSeedRetries(2)
            .setMaxBatchCells(256)
            .build();
        MemoryRpcManager rpcManager = new MemoryRpcManager(partyNum);
        Rpc[] rpcs = IntStream.range(0, partyNum).mapToObj(rpcManager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        PartyThread[] threads = IntStream.range(0, partyNum)
            .mapToObj(partyIndex -> new PartyThread(
                rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, config
            ))
            .toArray(PartyThread[]::new);
        try {
            Arrays.stream(rpcs).forEach(Rpc::reset);
            Arrays.stream(threads).forEach(Thread::start);
            for (PartyThread thread : threads) {
                thread.join(60_000L);
            }
            for (PartyThread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                    Assert.fail("multiplicity MP-SOGS thread timed out: " + thread.getName());
                }
                if (thread.throwable != null) {
                    throw new AssertionError("multiplicity MP-SOGS party failed: " + thread.getName(), thread.throwable);
                }
                Assert.assertTrue(thread.transcript.getFailureReason(), thread.transcript.isSuccess());
                Assert.assertEquals(expectedUnion, thread.transcript.getUnionOutput());
            }
        } finally {
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static List<Set<Long>> generateInputs(int partyNum, int setSize, int commonSize) {
        List<Set<Long>> inputs = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            Set<Long> input = new HashSet<>();
            for (long value = 1; value <= commonSize; value++) {
                input.add(value);
            }
            int uniqueSize = setSize - commonSize;
            long start = 1_000L + (long) partyIndex * uniqueSize;
            for (long offset = 0; offset < uniqueSize; offset++) {
                input.add(start + offset);
            }
            if (partyIndex == 0) {
                input.add(Long.MIN_VALUE);
                input.add(-1L);
            }
            inputs.add(input);
        }
        return inputs;
    }

    private static Set<Long> union(List<Set<Long>> inputs) {
        Set<Long> union = new HashSet<>();
        inputs.forEach(union::addAll);
        return union;
    }

    private static final class PartyThread extends Thread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private MpSogsTranscript transcript;
        private Throwable throwable;

        private PartyThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion, MpSogsMpsuConfig config) {
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            setName("shamir-multiplicity-party-" + rpc.ownParty().getPartyId());
        }

        @Override
        public void run() {
            try {
                transcript = new ShamirMultiplicityMpSogsMpsuPartyRunner(rpc, config, 8_000_000L)
                    .run(localInput, expectedUnion);
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }
}
