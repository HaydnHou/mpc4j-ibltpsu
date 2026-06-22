package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss.Rep5PrssMpSogsMpsuPartyRunner;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * REP5 PRSS packed MP-SOGS MPSU tests.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep5PrssMpSogsMpsuPartyRunnerTest {
    @Test
    public void testFivePartyOpenedFirstAllOutput() throws InterruptedException {
        testFivePartyAllOutput(MpSogsMpsuConfig.SecurePeelType.REP5_PRSS_OPENED_FIRST);
    }

    private void testFivePartyAllOutput(MpSogsMpsuConfig.SecurePeelType securePeelType)
        throws InterruptedException {
        List<Set<Long>> inputs = generateInputs(5, 8, 0.5);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(5, expectedUnion.size())
            .setAlpha(4.0)
            .setHashNum(3)
            .build();
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(securePeelType)
            .setMaxHashSeedRetries(8)
            .build();
        MemoryRpcManager rpcManager = new MemoryRpcManager(5);
        Rpc[] rpcs = IntStream.range(0, 5).mapToObj(rpcManager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        Rep5PrssThread[] threads = IntStream.range(0, 5)
            .mapToObj(partyIndex -> new Rep5PrssThread(
                rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, config
            ))
            .toArray(Rep5PrssThread[]::new);
        try {
            Arrays.stream(rpcs).forEach(Rpc::reset);
            Arrays.stream(threads).forEach(Thread::start);
            for (Rep5PrssThread thread : threads) {
                thread.join(30_000L);
            }
            for (Rep5PrssThread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                    Assert.fail("REP5 PRSS MP-SOGS thread timed out: " + thread.getName());
                }
                if (thread.throwable != null) {
                    throw new AssertionError("REP5 PRSS MP-SOGS party failed: " + thread.getName(),
                        thread.throwable);
                }
                Assert.assertTrue(thread.transcript.getFailureReason(), thread.transcript.isSuccess());
                Assert.assertEquals(expectedUnion, thread.transcript.getUnionOutput());
            }
        } finally {
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static List<Set<Long>> generateInputs(int parties, int n, double commonOverlap) {
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

    private static Set<Long> union(List<Set<Long>> inputs) {
        Set<Long> union = new HashSet<>();
        inputs.forEach(union::addAll);
        return union;
    }

    private static class Rep5PrssThread extends Thread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private MpSogsTranscript transcript;
        private Throwable throwable;

        private Rep5PrssThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion, MpSogsMpsuConfig config) {
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            setName("rep5-prss-mp-sogs-party-" + rpc.ownParty().getPartyId());
        }

        @Override
        public void run() {
            try {
                transcript = new Rep5PrssMpSogsMpsuPartyRunner(rpc, config, 9_100_000L)
                    .run(localInput, expectedUnion);
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }
}
