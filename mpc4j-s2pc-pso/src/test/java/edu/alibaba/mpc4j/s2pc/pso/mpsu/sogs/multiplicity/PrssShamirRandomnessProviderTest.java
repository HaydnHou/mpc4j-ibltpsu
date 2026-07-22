package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Subset-seed Shamir PRSS tests.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class PrssShamirRandomnessProviderTest {
    @Test
    public void testDegreeTReconstructionThreeToFiveParties() throws InterruptedException {
        for (int partyNum = 3; partyNum <= 5; partyNum++) {
            RunResult result = run(partyNum, 7_000_000L + partyNum, 0);
            int threshold = (partyNum - 1) / 2;
            long[] expected = reconstruct(result.firstShares, firstCombination(partyNum, threshold + 1));
            for (int[] subset : combinations(partyNum, threshold + 1)) {
                Assert.assertArrayEquals(expected, reconstruct(result.firstShares, subset));
            }
            Assert.assertFalse(Arrays.equals(expected,
                reconstruct(result.secondShares, firstCombination(partyNum, threshold + 1))));
            Assert.assertTrue(result.minSetupSendBytes > 0L);
            Assert.assertEquals(4L, result.minRequestCount);
            Assert.assertEquals(131L, result.minGeneratedElementCount);
        }
    }

    @Test
    public void testCorrelatedDoubleSharesThreeToTenParties() throws InterruptedException {
        for (int partyNum = 3; partyNum <= 10; partyNum++) {
            RunResult result = run(partyNum, 7_050_000L + partyNum, 0);
            int threshold = (partyNum - 1) / 2;
            long[] degreeTSecret = reconstruct(
                result.doubleDegreeTShares, firstCombination(partyNum, threshold + 1)
            );
            long[] degree2TSecret = reconstruct(
                result.doubleDegree2TShares, firstCombination(partyNum, 2 * threshold + 1)
            );
            Assert.assertArrayEquals(degreeTSecret, degree2TSecret);
            for (int[] subset : combinations(partyNum, threshold + 1)) {
                Assert.assertArrayEquals(degreeTSecret, reconstruct(result.doubleDegreeTShares, subset));
            }
            for (int[] subset : combinations(partyNum, 2 * threshold + 1)) {
                Assert.assertArrayEquals(degree2TSecret, reconstruct(result.doubleDegree2TShares, subset));
            }
        }
    }

    @Test
    public void testDegree2TZeroSharesThreeToTenParties() throws InterruptedException {
        for (int partyNum = 3; partyNum <= 10; partyNum++) {
            RunResult result = run(partyNum, 7_075_000L + partyNum, 0);
            int threshold = (partyNum - 1) / 2;
            boolean observedNonzeroShare = false;
            for (long[] partyShares : result.degree2TZeroShares) {
                for (long share : partyShares) {
                    observedNonzeroShare |= share != 0L;
                }
            }
            Assert.assertTrue("zero sharing must randomize nonconstant coefficients", observedNonzeroShare);
            for (int[] subset : combinations(partyNum, 2 * threshold + 1)) {
                long[] opened = reconstruct(result.degree2TZeroShares, subset);
                Assert.assertArrayEquals(new long[opened.length], opened);
            }
        }
    }

    @Test
    public void testCoalitionComplementTermIsHiddenAndZeroAtCoalitionPoints() {
        int partyNum = 5;
        int threshold = 2;
        for (int[] coalition : combinations(partyNum, threshold)) {
            for (int partyIndex : coalition) {
                Assert.assertEquals(0L, basisAtPoint(coalition, partyIndex + 1L));
            }
            Assert.assertNotEquals(0L, basisAtPoint(coalition, 0L));
        }
    }

    @Test
    public void testCrossAttemptIndependence() throws InterruptedException {
        RunResult first = run(5, 7_100_000L, 0);
        RunResult second = run(5, 7_200_000L, 1);
        int[] subset = firstCombination(5, 3);
        Assert.assertFalse(Arrays.equals(
            reconstruct(first.firstShares, subset), reconstruct(second.firstShares, subset)
        ));
    }

    private static RunResult run(int partyNum, long taskId, int attemptId) throws InterruptedException {
        MemoryRpcManager manager = new MemoryRpcManager(partyNum);
        Rpc[] rpcs = IntStream.range(0, partyNum).mapToObj(manager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        ProviderThread[] threads = IntStream.range(0, partyNum)
            .mapToObj(index -> new ProviderThread(rpcs[index], taskId, attemptId))
            .toArray(ProviderThread[]::new);
        try {
            Arrays.stream(threads).forEach(Thread::start);
            for (ProviderThread thread : threads) {
                thread.join(30_000L);
            }
            long[][] first = new long[partyNum][];
            long[][] second = new long[partyNum][];
            long[][] doubleDegreeT = new long[partyNum][];
            long[][] doubleDegree2T = new long[partyNum][];
            long[][] degree2TZero = new long[partyNum][];
            long minSetup = Long.MAX_VALUE;
            long minRequests = Long.MAX_VALUE;
            long minGenerated = Long.MAX_VALUE;
            for (int index = 0; index < partyNum; index++) {
                ProviderThread thread = threads[index];
                Assert.assertFalse("PRSS thread timed out", thread.isAlive());
                if (thread.throwable != null) {
                    throw new AssertionError("PRSS party failed", thread.throwable);
                }
                first[index] = thread.first;
                second[index] = thread.second;
                doubleDegreeT[index] = thread.doubleShare.degreeT();
                doubleDegree2T[index] = thread.doubleShare.degree2T();
                degree2TZero[index] = thread.degree2TZero;
                minSetup = Math.min(minSetup, thread.provider.getSetupSendBytes());
                minRequests = Math.min(minRequests, thread.provider.getRequestCount());
                minGenerated = Math.min(minGenerated, thread.provider.getGeneratedElementCount());
            }
            return new RunResult(
                first, second, doubleDegreeT, doubleDegree2T, degree2TZero,
                minSetup, minRequests, minGenerated
            );
        } finally {
            Arrays.stream(threads).filter(Thread::isAlive).forEach(Thread::interrupt);
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static long[] reconstruct(long[][] shares, int[] subset) {
        int length = shares[0].length;
        long[] result = new long[length];
        for (int subsetIndex = 0; subsetIndex < subset.length; subsetIndex++) {
            int partyIndex = subset[subsetIndex];
            long xi = partyIndex + 1L;
            long numerator = 1L;
            long denominator = 1L;
            for (int otherIndex = 0; otherIndex < subset.length; otherIndex++) {
                if (subsetIndex == otherIndex) {
                    continue;
                }
                long xj = subset[otherIndex] + 1L;
                numerator = Mersenne61Field.mul(numerator, Mersenne61Field.neg(xj));
                denominator = Mersenne61Field.mul(denominator, Mersenne61Field.sub(xi, xj));
            }
            long lambda = Mersenne61Field.div(numerator, denominator);
            for (int valueIndex = 0; valueIndex < length; valueIndex++) {
                result[valueIndex] = Mersenne61Field.add(
                    result[valueIndex], Mersenne61Field.mul(lambda, shares[partyIndex][valueIndex])
                );
            }
        }
        return result;
    }

    private static long basisAtPoint(int[] roots, long point) {
        long result = 1L;
        for (int root : roots) {
            result = Mersenne61Field.mul(result, Mersenne61Field.sub(point, root + 1L));
        }
        return result;
    }

    private static int[] firstCombination(int universeSize, int selectedSize) {
        return combinations(universeSize, selectedSize).get(0);
    }

    private static List<int[]> combinations(int universeSize, int selectedSize) {
        List<int[]> result = new ArrayList<>();
        collect(result, new int[selectedSize], 0, 0, universeSize);
        return result;
    }

    private static void collect(List<int[]> output, int[] current, int depth, int next, int universeSize) {
        if (depth == current.length) {
            output.add(Arrays.copyOf(current, current.length));
            return;
        }
        for (int value = next; value <= universeSize - (current.length - depth); value++) {
            current[depth] = value;
            collect(output, current, depth + 1, value + 1, universeSize);
        }
    }

    private static final class ProviderThread extends Thread {
        private final Rpc rpc;
        private final long taskId;
        private final int attemptId;
        private PrssShamirRandomnessProvider provider;
        private long[] first;
        private long[] second;
        private ShamirDoubleShare doubleShare;
        private long[] degree2TZero;
        private Throwable throwable;

        private ProviderThread(Rpc rpc, long taskId, int attemptId) {
            this.rpc = rpc;
            this.taskId = taskId;
            this.attemptId = attemptId;
        }

        @Override
        public void run() {
            try {
                provider = new PrssShamirRandomnessProvider(rpc, taskId, attemptId);
                ShamirRandomnessDomain firstDomain = new ShamirRandomnessDomain(
                    MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.PREDICATE, 0, 0L,
                    0L, 0, 0, 32
                );
                first = provider.randomDegreeT(firstDomain);
                second = provider.randomDegreeT(new ShamirRandomnessDomain(
                    MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.RECOVERY, 0, 0L,
                    0L, 0, 0, 32
                ));
                ShamirRandomnessDomain doubleDomain = new ShamirRandomnessDomain(
                    MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.DEGREE_REDUCTION, 0, 0L,
                    0L, 0, 0, 33
                );
                doubleShare = provider.randomDoubleShare(doubleDomain);
                ShamirRandomnessDomain zeroDomain = new ShamirRandomnessDomain(
                    MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.TERMINAL_ZERO, 0, 0L,
                    1L, 0, 0, 34
                );
                degree2TZero = provider.randomDegree2TZero(zeroDomain);
                try {
                    provider.randomDegreeT(firstDomain);
                    Assert.fail("reused PRSS domain was accepted");
                } catch (IllegalStateException expected) {
                    // expected
                }
                try {
                    provider.randomDegree2TZero(zeroDomain);
                    Assert.fail("reused degree-2t zero-share PRSS domain was accepted");
                } catch (IllegalStateException expected) {
                    // expected
                }
                try {
                    provider.randomDoubleShare(doubleDomain);
                    Assert.fail("reused double-share PRSS domain was accepted");
                } catch (IllegalStateException expected) {
                    // expected
                }
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }

    private record RunResult(long[][] firstShares, long[][] secondShares, long[][] doubleDegreeTShares,
                             long[][] doubleDegree2TShares, long[][] degree2TZeroShares,
                             long minSetupSendBytes, long minRequestCount, long minGeneratedElementCount) {
        // empty
    }
}
