package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * REP5 PRSS packed Boolean backend tests.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep5PrssPackedBooleanBackendTest {
    @Test
    public void testLinearAndAndOperations() throws InterruptedException {
        runBackendTest(1);
        runBackendTest(65);
        runBackendTest(130);
    }

    private void runBackendTest(int batchSize) throws InterruptedException {
        int blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
        long lastMask = lastMask(batchSize);
        long[][] inputs = randomInputs(batchSize, blockNum, lastMask);
        MemoryRpcManager rpcManager = new MemoryRpcManager(Rep5PrssPackedBooleanBackend.PARTY_NUM);
        Rpc[] rpcs = IntStream.range(0, Rep5PrssPackedBooleanBackend.PARTY_NUM)
            .mapToObj(rpcManager::getRpc)
            .toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        Rep5PrssThread[] threads = IntStream.range(0, Rep5PrssPackedBooleanBackend.PARTY_NUM)
            .mapToObj(partyIndex -> new Rep5PrssThread(rpcs[partyIndex], batchSize, inputs, lastMask))
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
                    Assert.fail("REP5 PRSS backend thread timed out: " + thread.getName());
                }
                if (thread.throwable != null) {
                    throw new AssertionError("REP5 PRSS backend thread failed: " + thread.getName(),
                        thread.throwable);
                }
            }
        } finally {
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static long[][] randomInputs(int batchSize, int blockNum, long lastMask) {
        Random random = new Random(20260622L + 17L * batchSize);
        long[][] inputs = new long[Rep5PrssPackedBooleanBackend.PARTY_NUM][blockNum];
        for (int partyIndex = 0; partyIndex < Rep5PrssPackedBooleanBackend.PARTY_NUM; partyIndex++) {
            for (int blockIndex = 0; blockIndex < blockNum; blockIndex++) {
                inputs[partyIndex][blockIndex] = random.nextLong();
            }
            inputs[partyIndex][blockNum - 1] &= lastMask;
        }
        return inputs;
    }

    private static long lastMask(int batchSize) {
        int lastBits = batchSize & (Long.SIZE - 1);
        return lastBits == 0 ? -1L : (1L << lastBits) - 1L;
    }

    private static class Rep5PrssThread extends Thread {
        private final Rpc rpc;
        private final int batchSize;
        private final long[][] inputs;
        private final long lastMask;
        private Throwable throwable;

        private Rep5PrssThread(Rpc rpc, int batchSize, long[][] inputs, long lastMask) {
            this.rpc = rpc;
            this.batchSize = batchSize;
            this.inputs = inputs;
            this.lastMask = lastMask;
            setName("rep5-prss-packed-backend-party-" + rpc.ownParty().getPartyId());
        }

        @Override
        public void run() {
            try {
                int ownPartyId = rpc.ownParty().getPartyId();
                Rep5PrssPackedBooleanBackend backend = new Rep5PrssPackedBooleanBackend(
                    rpc, batchSize, 8_100_000L
                );
                Rep5PrssPackedBooleanShare[] shares = backend.shareOwnAndReceiveAll(inputs[ownPartyId]);
                for (int dealerId = 0; dealerId < Rep5PrssPackedBooleanBackend.PARTY_NUM; dealerId++) {
                    Assert.assertArrayEquals(inputs[dealerId], backend.open(shares[dealerId]));
                }
                PackedBooleanShare x = shares[0];
                PackedBooleanShare y = shares[1];
                assertArrayEquals(xor(inputs[0], inputs[1], lastMask), backend.open(backend.xor(x, y)));
                assertArrayEquals(not(inputs[0], lastMask), backend.open(backend.not(x)));
                assertArrayEquals(and(inputs[0], inputs[1], lastMask), backend.open(backend.and(x, y)));
                PackedBooleanShare[] andMany = backend.andMany(
                    new PackedBooleanShare[]{x, y},
                    new PackedBooleanShare[]{y, x}
                );
                assertArrayEquals(and(inputs[0], inputs[1], lastMask), backend.open(andMany[0]));
                assertArrayEquals(and(inputs[1], inputs[0], lastMask), backend.open(andMany[1]));
                assertArrayEquals(or(inputs[0], inputs[1], lastMask), backend.open(backend.or(x, y)));
                int[] selectedIndexes = selectedIndexes(batchSize);
                assertArrayEquals(select(inputs[0], selectedIndexes), backend.openSelected(x, selectedIndexes));
            } catch (Throwable t) {
                throwable = t;
            }
        }

        private static void assertArrayEquals(long[] expected, long[] actual) {
            Assert.assertArrayEquals(expected, actual);
        }

        private static long[] xor(long[] x, long[] y, long lastMask) {
            long[] z = new long[x.length];
            for (int i = 0; i < z.length; i++) {
                z[i] = x[i] ^ y[i];
            }
            z[z.length - 1] &= lastMask;
            return z;
        }

        private static long[] not(long[] x, long lastMask) {
            long[] z = new long[x.length];
            for (int i = 0; i < z.length; i++) {
                z[i] = ~x[i];
            }
            z[z.length - 1] &= lastMask;
            return z;
        }

        private static long[] and(long[] x, long[] y, long lastMask) {
            long[] z = new long[x.length];
            for (int i = 0; i < z.length; i++) {
                z[i] = x[i] & y[i];
            }
            z[z.length - 1] &= lastMask;
            return z;
        }

        private static long[] or(long[] x, long[] y, long lastMask) {
            long[] z = new long[x.length];
            for (int i = 0; i < z.length; i++) {
                z[i] = x[i] | y[i];
            }
            z[z.length - 1] &= lastMask;
            return z;
        }

        private static int[] selectedIndexes(int batchSize) {
            return IntStream.range(0, batchSize)
                .filter(index -> index % 3 == 0 || index + 1 == batchSize)
                .toArray();
        }

        private static long[] select(long[] blocks, int[] selectedIndexes) {
            int compactBlockNum = (selectedIndexes.length + Long.SIZE - 1) / Long.SIZE;
            long[] selected = new long[compactBlockNum];
            for (int selectedIndex = 0; selectedIndex < selectedIndexes.length; selectedIndex++) {
                int laneIndex = selectedIndexes[selectedIndex];
                if (((blocks[laneIndex >>> 6] >>> (laneIndex & (Long.SIZE - 1))) & 1L) != 0L) {
                    selected[selectedIndex >>> 6] |= 1L << (selectedIndex & (Long.SIZE - 1));
                }
            }
            int lastBits = selectedIndexes.length & (Long.SIZE - 1);
            long compactLastMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
            selected[compactBlockNum - 1] &= compactLastMask;
            return selected;
        }
    }
}
