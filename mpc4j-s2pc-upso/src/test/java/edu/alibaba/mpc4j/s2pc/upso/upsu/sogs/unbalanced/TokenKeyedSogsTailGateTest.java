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
 * Two-party tests for the token-keyed aggregate SOGS tail gate.
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsTailGateTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Sender size.
     */
    private static final int M = 1024;
    /**
     * Cuckoo row count at the target m = 2^10 setting.
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

    public TokenKeyedSogsTailGateTest() {
        super(TokenKeyedSogsTailGateTest.class.getSimpleName());
    }

    @Test
    public void testTwoPartyTailGate() throws Exception {
        runCase(16, M / 2, 0);
        runCase(64, M / 2, 1);
        runCase(64, 0, 2);
    }

    private void runCase(int payloadByteLength, int intersectionSize, int trial) throws Exception {
        CaseData data = createCase(payloadByteLength, intersectionSize, trial);
        SecureRandom secureRandom = seededSecureRandom("token", payloadByteLength, intersectionSize, trial);
        TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
            data.payloads, data.validBits, CELL_NUM, DEGREE, secureRandom, 100
        );

        TokenKeyedSogsTailConfig config = new TokenKeyedSogsTailConfig.Builder().build();
        TokenKeyedSogsTailSender sender = new TokenKeyedSogsTailSender(firstRpc, secondRpc.ownParty(), config);
        TokenKeyedSogsTailReceiver receiver = new TokenKeyedSogsTailReceiver(secondRpc, firstRpc.ownParty(), config);
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
            sender, data.clientSelectorShares, data.validBits, sample.tokens, data.payloads
        );
        ReceiverThread receiverThread = new ReceiverThread(receiver, data.serverSelectorShares, payloadByteLength);
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
        Random random = new Random(20260617L + payloadByteLength * 1000L + intersectionSize * 17L + trial);
        List<Integer> rowIndexes = new ArrayList<>(ROW_NUM);
        for (int i = 0; i < ROW_NUM; i++) {
            rowIndexes.add(i);
        }
        Collections.shuffle(rowIndexes, random);
        List<Integer> realRows = rowIndexes.subList(0, M);
        Set<Integer> hitRows = new HashSet<>(realRows.subList(0, intersectionSize));
        byte[][] payloads = new byte[ROW_NUM][payloadByteLength];
        boolean[] validBits = new boolean[ROW_NUM];
        boolean[] releaseBits = new boolean[ROW_NUM];
        boolean[] serverSelectorShares = new boolean[ROW_NUM];
        boolean[] clientSelectorShares = new boolean[ROW_NUM];
        Set<ByteBuffer> expectedMisses = new HashSet<>(M - intersectionSize);
        for (int row = 0; row < ROW_NUM; row++) {
            payloads[row] = payload(payloadByteLength, trial, row);
            serverSelectorShares[row] = random.nextBoolean();
        }
        for (int itemIndex = 0; itemIndex < M; itemIndex++) {
            int row = realRows.get(itemIndex);
            byte[] payload = payload(payloadByteLength, trial, ROW_NUM + itemIndex);
            payloads[row] = payload;
            validBits[row] = true;
            releaseBits[row] = !hitRows.contains(row);
            if (releaseBits[row]) {
                expectedMisses.add(ByteBuffer.wrap(payload));
            }
        }
        for (int row = 0; row < ROW_NUM; row++) {
            clientSelectorShares[row] = serverSelectorShares[row] ^ releaseBits[row];
        }
        return new CaseData(payloads, validBits, serverSelectorShares, clientSelectorShares, expectedMisses);
    }

    private static byte[] payload(int payloadByteLength, int trial, int itemIndex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("tail-payload".getBytes(StandardCharsets.UTF_8));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(trial).array());
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(itemIndex).array());
            byte[] first = digest.digest();
            if (payloadByteLength <= first.length) {
                return java.util.Arrays.copyOf(first, payloadByteLength);
            }
            byte[] output = new byte[payloadByteLength];
            System.arraycopy(first, 0, output, 0, first.length);
            int offset = first.length;
            int counter = 1;
            while (offset < payloadByteLength) {
                digest.reset();
                digest.update(first);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(counter).array());
                byte[] next = digest.digest();
                int copyLength = Math.min(next.length, payloadByteLength - offset);
                System.arraycopy(next, 0, output, offset, copyLength);
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
        private final TokenKeyedSogsTailSender sender;
        private Exception exception;

        private InitSenderThread(TokenKeyedSogsTailSender sender) {
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
        private final TokenKeyedSogsTailReceiver receiver;
        private Exception exception;

        private InitReceiverThread(TokenKeyedSogsTailReceiver receiver) {
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
        private final TokenKeyedSogsTailSender sender;
        private final boolean[] clientSelectorShares;
        private final boolean[] validBits;
        private final byte[][] tokens;
        private final byte[][] payloads;
        private Exception exception;

        private SenderThread(
            TokenKeyedSogsTailSender sender, boolean[] clientSelectorShares, boolean[] validBits, byte[][] tokens,
            byte[][] payloads
        ) {
            this.sender = sender;
            this.clientSelectorShares = clientSelectorShares;
            this.validBits = validBits;
            this.tokens = tokens;
            this.payloads = payloads;
        }

        @Override
        public void run() {
            try {
                sender.send(clientSelectorShares, validBits, tokens, payloads, CELL_NUM, DEGREE);
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
        private final TokenKeyedSogsTailReceiver receiver;
        private final boolean[] serverSelectorShares;
        private final int payloadByteLength;
        private TokenKeyedSogsSketch.PeelResult peelResult;
        private Exception exception;

        private ReceiverThread(
            TokenKeyedSogsTailReceiver receiver, boolean[] serverSelectorShares, int payloadByteLength
        ) {
            this.receiver = receiver;
            this.serverSelectorShares = serverSelectorShares;
            this.payloadByteLength = payloadByteLength;
        }

        @Override
        public void run() {
            try {
                peelResult = receiver.receive(serverSelectorShares, payloadByteLength, CELL_NUM, DEGREE);
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
        private final boolean[] validBits;
        private final boolean[] serverSelectorShares;
        private final boolean[] clientSelectorShares;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(
            byte[][] payloads, boolean[] validBits, boolean[] serverSelectorShares, boolean[] clientSelectorShares,
            Set<ByteBuffer> expectedMisses
        ) {
            this.payloads = payloads;
            this.validBits = validBits;
            this.serverSelectorShares = serverSelectorShares;
            this.clientSelectorShares = clientSelectorShares;
            this.expectedMisses = expectedMisses;
        }
    }
}
