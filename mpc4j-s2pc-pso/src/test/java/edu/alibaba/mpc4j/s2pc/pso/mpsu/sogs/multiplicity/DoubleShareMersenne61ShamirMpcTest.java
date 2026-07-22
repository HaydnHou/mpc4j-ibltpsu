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
 * Correlated double-share multiplication and balanced-opening tests.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class DoubleShareMersenne61ShamirMpcTest {
    @Test
    public void testThreeToTenPartiesAndUnbalancedChunks() throws InterruptedException {
        for (Mersenne61WireFormat wireFormat : Mersenne61WireFormat.values()) {
            for (int partyNum = 3; partyNum <= 10; partyNum++) {
                runAndVerify(partyNum, 7_500_000L + 10_000L * wireFormat.ordinal() + 100L * partyNum, wireFormat);
            }
        }
    }

    private static void runAndVerify(int partyNum, long taskId, Mersenne61WireFormat wireFormat)
        throws InterruptedException {
        int[] lengths = new int[]{1, Math.max(1, partyNum - 1), partyNum, partyNum + 3};
        List<long[]> leftVectors = new ArrayList<>();
        List<long[]> rightVectors = new ArrayList<>();
        List<long[]> expectedProducts = new ArrayList<>();
        for (int vectorIndex = 0; vectorIndex < lengths.length; vectorIndex++) {
            long[] left = new long[lengths[vectorIndex]];
            long[] right = new long[lengths[vectorIndex]];
            long[] expected = new long[lengths[vectorIndex]];
            for (int index = 0; index < left.length; index++) {
                left[index] = index == 0 && vectorIndex == 0
                    ? 0L
                    : index == 1 ? Mersenne61Field.PRIME - 1L : 17L * (index + 1L) + vectorIndex;
                right[index] = index == 1
                    ? Mersenne61Field.PRIME - 1L : 29L * (index + 2L) + vectorIndex;
                expected[index] = Mersenne61Field.mul(left[index], right[index]);
            }
            leftVectors.add(left);
            rightVectors.add(right);
            expectedProducts.add(expected);
        }

        MemoryRpcManager manager = new MemoryRpcManager(partyNum);
        Rpc[] rpcs = IntStream.range(0, partyNum).mapToObj(manager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        MpcThread[] threads = IntStream.range(0, partyNum)
            .mapToObj(index -> new MpcThread(rpcs[index], taskId, leftVectors, rightVectors, wireFormat))
            .toArray(MpcThread[]::new);
        try {
            Arrays.stream(threads).forEach(Thread::start);
            for (MpcThread thread : threads) {
                thread.join(60_000L);
            }
            for (MpcThread thread : threads) {
                Assert.assertFalse("double-share MPC thread timed out", thread.isAlive());
                if (thread.throwable != null) {
                    throw new AssertionError("double-share MPC party failed", thread.throwable);
                }
                Assert.assertEquals(3 * expectedProducts.size(), thread.openedProducts.size());
                for (int vectorIndex = 0; vectorIndex < expectedProducts.size(); vectorIndex++) {
                    Assert.assertArrayEquals(
                        expectedProducts.get(vectorIndex), thread.openedProducts.get(3 * vectorIndex)
                    );
                    Assert.assertArrayEquals(
                        expectedProducts.get(vectorIndex), thread.openedProducts.get(3 * vectorIndex + 1)
                    );
                    Assert.assertArrayEquals(
                        expectedProducts.get(vectorIndex), thread.openedProducts.get(3 * vectorIndex + 2)
                    );
                }
            }
        } finally {
            Arrays.stream(threads).filter(Thread::isAlive).forEach(Thread::interrupt);
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static final class MpcThread extends Thread {
        private final Rpc rpc;
        private final long taskId;
        private final List<long[]> leftVectors;
        private final List<long[]> rightVectors;
        private final Mersenne61WireFormat wireFormat;
        private final List<long[]> openedProducts;
        private Throwable throwable;

        private MpcThread(Rpc rpc, long taskId, List<long[]> leftVectors, List<long[]> rightVectors,
                          Mersenne61WireFormat wireFormat) {
            this.rpc = rpc;
            this.taskId = taskId;
            this.leftVectors = leftVectors;
            this.rightVectors = rightVectors;
            this.wireFormat = wireFormat;
            openedProducts = new ArrayList<>();
        }

        @Override
        public void run() {
            try {
                Mersenne61ShamirMpc mpc = new Mersenne61ShamirMpc(rpc, taskId, wireFormat);
                PrssShamirRandomnessProvider provider = new PrssShamirRandomnessProvider(rpc, taskId + 10_000L, 0);
                int ownPartyId = rpc.ownParty().getPartyId();
                for (int vectorIndex = 0; vectorIndex < leftVectors.size(); vectorIndex++) {
                    long[] left = leftVectors.get(vectorIndex);
                    long[] right = rightVectors.get(vectorIndex);
                    long[] ownLeft = ownPartyId == 0 ? left : new long[left.length];
                    long[] ownRight = ownPartyId == 1 ? right : new long[right.length];
                    long[] leftShares = mpc.shareOwnAndAggregate(ownLeft);
                    long[] rightShares = mpc.shareOwnAndAggregate(ownRight);
                    ShamirDoubleShare doubleShare = provider.randomDoubleShare(new ShamirRandomnessDomain(
                        MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.DEGREE_REDUCTION,
                        0, vectorIndex, 0L, 0, 0, left.length
                    ));
                    long[] productShares = mpc.mulWithDoubleShare(leftShares, rightShares, doubleShare);
                    openedProducts.add(mpc.openBalanced(productShares));
                    long[] balancedZero = provider.randomDegree2TZero(new ShamirRandomnessDomain(
                        MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.TERMINAL_ZERO,
                        0, vectorIndex, 1L, 0, 0, left.length
                    ));
                    openedProducts.add(mpc.openTerminalProduct(
                        leftShares, rightShares, balancedZero, SsmOpeningMode.BALANCED_TWO_PHASE, 0.0, 0.0
                    ));
                    long[] allToAllZero = provider.randomDegree2TZero(new ShamirRandomnessDomain(
                        MpSogsTier.MAIN, ShamirRandomnessDomain.Phase.TERMINAL_ZERO,
                        0, vectorIndex, 2L, 0, 0, left.length
                    ));
                    openedProducts.add(mpc.openTerminalProduct(
                        leftShares, rightShares, allToAllZero, SsmOpeningMode.ALL_TO_ALL_ONE_PHASE, 0.0, 0.0
                    ));
                }
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }
}
