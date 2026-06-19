package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Two-party tests for the share-cancel SOGS tail.
 *
 * <p>The test gives the parties only local shares of the row hit bits. The sender privately folds its real-row bits
 * into its share so that the final aggregate sketch contains exactly real miss rows.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsTailTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Sender size.
     */
    private static final int M = 1024;
    /**
     * Anonymous padded row count for the target m = 2^10 setting.
     */
    private static final int ROW_NUM = 1639;
    /**
     * Cell count at gamma = 1.45.
     */
    private static final int CELL_NUM = 1485;
    /**
     * SOGS degree.
     */
    private static final int DEGREE = 3;

    public ShareCancelSogsTailTest() {
        super(ShareCancelSogsTailTest.class.getSimpleName());
    }

    @Test
    public void testShareCancelTail() throws Exception {
        runCase(16, M / 2, 0);
        runCase(16, 0, 1);
        runCase(64, M / 2, 2);
    }

    private void runCase(int payloadByteLength, int intersectionSize, int trial) throws Exception {
        CaseData data = createCase(payloadByteLength, intersectionSize, trial);
        SecureRandom secureRandom = seededSecureRandom("share-cancel-token", payloadByteLength, intersectionSize, trial);
        TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
            data.payloads, data.realRowBits, CELL_NUM, DEGREE, secureRandom, 100
        );

        ShareCancelSogsTailConfig config = new ShareCancelSogsTailConfig.Builder().build();
        ShareCancelSogsTailSender sender = new ShareCancelSogsTailSender(firstRpc, secondRpc.ownParty(), config);
        ShareCancelSogsTailReceiver receiver = new ShareCancelSogsTailReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        sender.setParallel(true);
        receiver.setParallel(true);

        InitSenderThread initSenderThread = new InitSenderThread(sender);
        InitReceiverThread initReceiverThread = new InitReceiverThread(receiver);
        initSenderThread.start();
        initReceiverThread.start();
        initSenderThread.join();
        initReceiverThread.join();
        initSenderThread.check();
        initReceiverThread.check();

        firstRpc.reset();
        secondRpc.reset();
        SenderThread senderThread = new SenderThread(
            sender, data.senderHitShares, data.realRowBits, sample.tokens, data.payloads
        );
        ReceiverThread receiverThread = new ReceiverThread(receiver, data.receiverHitShares, payloadByteLength);
        long start = System.nanoTime();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        long timeMs = (System.nanoTime() - start) / 1_000_000;
        senderThread.check();
        receiverThread.check();

        Assert.assertTrue(receiverThread.peelResult.success);
        Assert.assertEquals(0, receiverThread.peelResult.residualCells);
        Assert.assertEquals(data.expectedMisses, receiverThread.peelResult.recovered);
        printAndResetRpc(timeMs);

        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
    }

    private static CaseData createCase(int payloadByteLength, int intersectionSize, int trial) {
        Random random = new Random(2026061901L + payloadByteLength * 1000L + intersectionSize * 17L + trial);
        List<Integer> rowIndexes = new ArrayList<>(ROW_NUM);
        for (int i = 0; i < ROW_NUM; i++) {
            rowIndexes.add(i);
        }
        Collections.shuffle(rowIndexes, random);
        List<Integer> realRows = rowIndexes.subList(0, M);
        Set<Integer> hitRows = new HashSet<>(realRows.subList(0, intersectionSize));
        byte[][] payloads = new byte[ROW_NUM][payloadByteLength];
        boolean[] realRowBits = new boolean[ROW_NUM];
        boolean[] senderHitShares = new boolean[ROW_NUM];
        boolean[] receiverHitShares = new boolean[ROW_NUM];
        Set<ByteBuffer> expectedMisses = new HashSet<>(M - intersectionSize);
        for (int row = 0; row < ROW_NUM; row++) {
            payloads[row] = payload(payloadByteLength, trial, row);
            receiverHitShares[row] = random.nextBoolean();
            senderHitShares[row] = receiverHitShares[row] ^ random.nextBoolean();
        }
        for (int itemIndex = 0; itemIndex < M; itemIndex++) {
            int row = realRows.get(itemIndex);
            byte[] payload = payload(payloadByteLength, trial, ROW_NUM + itemIndex);
            payloads[row] = payload;
            realRowBits[row] = true;
            boolean hit = hitRows.contains(row);
            receiverHitShares[row] = random.nextBoolean();
            senderHitShares[row] = receiverHitShares[row] ^ hit;
            if (!hit) {
                expectedMisses.add(ByteBuffer.wrap(payload));
            }
        }
        return new CaseData(payloads, realRowBits, senderHitShares, receiverHitShares, expectedMisses);
    }

    private static byte[] payload(int payloadByteLength, int trial, int itemIndex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] output = new byte[payloadByteLength];
            int offset = 0;
            int counter = 0;
            while (offset < payloadByteLength) {
                digest.update("share-cancel-payload".getBytes(StandardCharsets.UTF_8));
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(trial).array());
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(itemIndex).array());
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(counter).array());
                byte[] block = digest.digest();
                int copyLength = Math.min(block.length, payloadByteLength - offset);
                System.arraycopy(block, 0, output, offset, copyLength);
                offset += copyLength;
                counter++;
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecureRandom seededSecureRandom(String domain, int payloadByteLength, int intersectionSize, int trial) {
        byte[] seed = (domain + ":" + payloadByteLength + ":" + intersectionSize + ":" + trial)
            .getBytes(StandardCharsets.UTF_8);
        return new SecureRandom(seed);
    }

    private static class InitSenderThread extends Thread {
        private final ShareCancelSogsTailSender sender;
        private Exception exception;

        private InitSenderThread(ShareCancelSogsTailSender sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            try {
                sender.init();
            } catch (MpcAbortException e) {
                exception = e;
            }
        }

        private void check() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static class InitReceiverThread extends Thread {
        private final ShareCancelSogsTailReceiver receiver;
        private Exception exception;

        private InitReceiverThread(ShareCancelSogsTailReceiver receiver) {
            this.receiver = receiver;
        }

        @Override
        public void run() {
            try {
                receiver.init();
            } catch (MpcAbortException e) {
                exception = e;
            }
        }

        private void check() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static class SenderThread extends Thread {
        private final ShareCancelSogsTailSender sender;
        private final boolean[] senderHitShares;
        private final boolean[] realRowBits;
        private final byte[][] tokens;
        private final byte[][] payloads;
        private Exception exception;

        private SenderThread(
            ShareCancelSogsTailSender sender, boolean[] senderHitShares, boolean[] realRowBits, byte[][] tokens,
            byte[][] payloads
        ) {
            this.sender = sender;
            this.senderHitShares = senderHitShares;
            this.realRowBits = realRowBits;
            this.tokens = tokens;
            this.payloads = payloads;
        }

        @Override
        public void run() {
            try {
                sender.send(senderHitShares, realRowBits, tokens, payloads, CELL_NUM, DEGREE);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        private void check() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static class ReceiverThread extends Thread {
        private final ShareCancelSogsTailReceiver receiver;
        private final boolean[] receiverHitShares;
        private final int payloadByteLength;
        private TokenKeyedSogsSketch.PeelResult peelResult;
        private Exception exception;

        private ReceiverThread(
            ShareCancelSogsTailReceiver receiver, boolean[] receiverHitShares, int payloadByteLength
        ) {
            this.receiver = receiver;
            this.receiverHitShares = receiverHitShares;
            this.payloadByteLength = payloadByteLength;
        }

        @Override
        public void run() {
            try {
                peelResult = receiver.receive(receiverHitShares, payloadByteLength, CELL_NUM, DEGREE);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        private void check() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }
    }

    private static class CaseData {
        private final byte[][] payloads;
        private final boolean[] realRowBits;
        private final boolean[] senderHitShares;
        private final boolean[] receiverHitShares;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(
            byte[][] payloads, boolean[] realRowBits, boolean[] senderHitShares, boolean[] receiverHitShares,
            Set<ByteBuffer> expectedMisses
        ) {
            this.payloads = payloads;
            this.realRowBits = realRowBits;
            this.senderHitShares = senderHitShares;
            this.receiverHitShares = receiverHitShares;
            this.expectedMisses = expectedMisses;
        }
    }
}
