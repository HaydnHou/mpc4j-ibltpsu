package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PrssPhase;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * REP4 PRSS packed Boolean backend tests.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep4PrssPackedBooleanBackendTest {
    @Test
    public void testLinearAndAndOperations() throws InterruptedException {
        runBackendTest(1);
        runBackendTest(65);
        runBackendTest(130);
    }

    @Test
    public void testActualSizeBackendSequence() throws InterruptedException {
        MemoryRpcManager rpcManager = new MemoryRpcManager(Rep4PrssPackedBooleanBackend.PARTY_NUM);
        Rpc[] rpcs = IntStream.range(0, Rep4PrssPackedBooleanBackend.PARTY_NUM)
            .mapToObj(rpcManager::getRpc)
            .toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = IntStream.range(0, Rep4PrssPackedBooleanBackend.PARTY_NUM)
            .mapToObj(partyIndex -> new Thread(() -> {
                try {
                    runActualSizeSequence(rpcs[partyIndex]);
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            },
                "rep4-prss-actual-size-party-" + partyIndex))
            .toArray(Thread[]::new);
        try {
            Arrays.stream(threads).forEach(Thread::start);
            for (Thread thread : threads) {
                thread.join(30_000L);
                Assert.assertFalse("REP4 actual-size sequence timed out", thread.isAlive());
            }
            if (failure.get() != null) {
                throw new AssertionError("REP4 actual-size sequence failed", failure.get());
            }
        } finally {
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static void runActualSizeSequence(Rpc rpc) {
        Rep4PrssSession session = new Rep4PrssSession(rpc, 8_200_000L);
        for (int batchSize : new int[]{130, 1, 65}) {
            Rep4PrssPackedBooleanBackend backend = session.createBackend(batchSize, PrssPhase.FULL);
            int blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
            Assert.assertEquals(blockNum, backend.blockNum());
            long[] ownBits = new long[blockNum];
            Arrays.fill(ownBits, rpc.ownParty().getPartyId() + 1L);
            ownBits[blockNum - 1] &= lastMask(batchSize);
            Rep4PrssPackedBooleanShare[] shares = backend.shareOwnAndReceiveAll(ownBits);
            for (int dealerId = 0; dealerId < Rep4PrssPackedBooleanBackend.PARTY_NUM; dealerId++) {
                long[] expected = new long[blockNum];
                Arrays.fill(expected, dealerId + 1L);
                expected[blockNum - 1] &= lastMask(batchSize);
                Assert.assertArrayEquals(expected, backend.open(shares[dealerId]));
            }
        }
    }

    private void runBackendTest(int batchSize) throws InterruptedException {
        int blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
        long lastMask = lastMask(batchSize);
        long[][] inputs = randomInputs(batchSize, blockNum, lastMask);
        MemoryRpcManager rpcManager = new MemoryRpcManager(Rep4PrssPackedBooleanBackend.PARTY_NUM);
        Rpc[] rpcs = IntStream.range(0, Rep4PrssPackedBooleanBackend.PARTY_NUM)
            .mapToObj(rpcManager::getRpc)
            .toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        Rep4PrssThread[] threads = IntStream.range(0, Rep4PrssPackedBooleanBackend.PARTY_NUM)
            .mapToObj(partyIndex -> new Rep4PrssThread(rpcs[partyIndex], batchSize, inputs, lastMask))
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
                    Assert.fail("REP4 PRSS backend thread timed out: " + thread.getName());
                }
                if (thread.throwable != null) {
                    throw new AssertionError("REP4 PRSS backend thread failed: " + thread.getName(),
                        thread.throwable);
                }
            }
        } finally {
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static long[][] randomInputs(int batchSize, int blockNum, long lastMask) {
        Random random = new Random(20260622L + 17L * batchSize);
        long[][] inputs = new long[Rep4PrssPackedBooleanBackend.PARTY_NUM][blockNum];
        for (int partyIndex = 0; partyIndex < Rep4PrssPackedBooleanBackend.PARTY_NUM; partyIndex++) {
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

    private static class Rep4PrssThread extends Thread {
        private final Rpc rpc;
        private final int batchSize;
        private final long[][] inputs;
        private final long lastMask;
        private Throwable throwable;

        private Rep4PrssThread(Rpc rpc, int batchSize, long[][] inputs, long lastMask) {
            this.rpc = rpc;
            this.batchSize = batchSize;
            this.inputs = inputs;
            this.lastMask = lastMask;
            setName("rep4-prss-packed-backend-party-" + rpc.ownParty().getPartyId());
        }

        @Override
        public void run() {
            try {
                int ownPartyId = rpc.ownParty().getPartyId();
                Rep4PrssPackedBooleanBackend backend = new Rep4PrssPackedBooleanBackend(
                    rpc, batchSize, 8_100_000L
                );
                Rep4PrssPackedBooleanShare[] shares = backend.shareOwnAndReceiveAll(inputs[ownPartyId]);
                for (int dealerId = 0; dealerId < Rep4PrssPackedBooleanBackend.PARTY_NUM; dealerId++) {
                    Assert.assertArrayEquals(inputs[dealerId], backend.open(shares[dealerId]));
                }
                PackedBooleanShare x = shares[0];
                PackedBooleanShare y = shares[1];
                assertArrayEquals(xor(inputs[0], inputs[1], lastMask), backend.open(backend.xor(x, y)));
                assertArrayEquals(not(inputs[0], lastMask), backend.open(backend.not(x)));
                assertArrayEquals(and(inputs[0], inputs[1], lastMask), backend.open(backend.and(x, y)));
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
