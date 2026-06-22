package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss.Rep4PrssMpSogsMpsuPartyRunner;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * REP4 PRSS packed MP-SOGS MPSU tests.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep4PrssMpSogsMpsuPartyRunnerTest {
    @Test
    public void testFourPartyAllOutput() throws InterruptedException {
        List<Set<Long>> inputs = generateInputs(4, 8, 0.5);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(4, expectedUnion.size())
            .setAlpha(4.0)
            .setHashNum(3)
            .build();
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_PACKED)
            .setMaxHashSeedRetries(8)
            .build();
        MemoryRpcManager rpcManager = new MemoryRpcManager(4);
        Rpc[] rpcs = IntStream.range(0, 4).mapToObj(rpcManager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        Rep4PrssThread[] threads = IntStream.range(0, 4)
            .mapToObj(partyIndex -> new Rep4PrssThread(
                rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, config
            ))
            .toArray(Rep4PrssThread[]::new);
        try {
            Arrays.stream(rpcs).forEach(Rpc::reset);
            Arrays.stream(threads).forEach(Thread::start);
            for (Rep4PrssThread thread : threads) {
                thread.join(30_000L);
            }
            for (Rep4PrssThread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                    Assert.fail("REP4 PRSS MP-SOGS thread timed out: " + thread.getName());
                }
                if (thread.throwable != null) {
                    throw new AssertionError("REP4 PRSS MP-SOGS party failed: " + thread.getName(),
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

    private static class Rep4PrssThread extends Thread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private MpSogsTranscript transcript;
        private Throwable throwable;

        private Rep4PrssThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion, MpSogsMpsuConfig config) {
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            setName("rep4-prss-mp-sogs-party-" + rpc.ownParty().getPartyId());
        }

        @Override
        public void run() {
            try {
                transcript = new Rep4PrssMpSogsMpsuPartyRunner(rpc, config, 9_100_000L)
                    .run(localInput, expectedUnion);
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }
}
