package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Tests for ePSU / pnMCRG-shaped MCRG-token SOGS release.
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class McrgTokenSogsReleaseTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Sender size.
     */
    private static final int M = 1024;
    /**
     * Padded row count.
     */
    private static final int ROW_NUM = 1638;
    /**
     * Large enough cell count for direct random-token release tests.
     */
    private static final int CELL_NUM = 4096;
    /**
     * SOGS degree.
     */
    private static final int DEGREE = 3;
    /**
     * Pad byte length.
     */
    private static final int PAD_BYTE_LENGTH = 16;

    public McrgTokenSogsReleaseTest() {
        super(McrgTokenSogsReleaseTest.class.getSimpleName());
    }

    @Test
    public void testMcrgTokenSogsRelease() throws Exception {
        runCase(16, M / 2, 0);
        runCase(64, M / 2, 1);
        runCase(64, 0, 2);
    }

    private void runCase(int payloadByteLength, int intersectionSize, int trial) throws Exception {
        CaseData data = createCase(payloadByteLength, intersectionSize, trial);
        McrgTokenSogsReleaseConfig config = new McrgTokenSogsReleaseConfig.Builder().build();
        McrgTokenSogsReleaseSender sender =
            new McrgTokenSogsReleaseSender(firstRpc, secondRpc.ownParty(), config);
        McrgTokenSogsReleaseReceiver receiver =
            new McrgTokenSogsReleaseReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        sender.setParallel(true);
        receiver.setParallel(true);

        sender.init();
        receiver.init();
        firstRpc.reset();
        secondRpc.reset();

        Assert.assertEquals(ROW_NUM, data.senderOutput.getRowNum());
        Assert.assertEquals(ROW_NUM, data.receiverOutput.getRowNum());
        Assert.assertEquals(payloadByteLength, data.senderOutput.getPayloadByteLength());
        Assert.assertEquals(PAD_BYTE_LENGTH, data.senderOutput.getPadByteLength());
        Assert.assertEquals(PAD_BYTE_LENGTH, data.receiverOutput.getPadByteLength());
        SenderThread senderThread = new SenderThread(sender, data.senderOutput);
        ReceiverThread receiverThread = new ReceiverThread(receiver, data.receiverOutput, payloadByteLength);
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
        long rawPayloadBytes = (long) ROW_NUM * TokenKeyedSogsSketch.atomByteLength(payloadByteLength);
        // MemoryRpc accounts one 4-byte length field for the equal-size payload vector.
        long expectedPayloadBytes = rawPayloadBytes + Integer.BYTES;
        long payloadBytes = firstRpc.getPayloadByteLength() + secondRpc.getPayloadByteLength();
        long packetNum = firstRpc.getSendDataPacketNum() + secondRpc.getSendDataPacketNum();
        Assert.assertEquals(expectedPayloadBytes, payloadBytes);
        Assert.assertEquals(1L, packetNum);
        printAndResetRpc(timeMs);

        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
    }

    private static CaseData createCase(int payloadByteLength, int intersectionSize, int trial) {
        Random random = new Random(2026061704L + payloadByteLength * 1000L + intersectionSize * 17L + trial);
        List<Integer> rowIndexes = new ArrayList<>(ROW_NUM);
        for (int i = 0; i < ROW_NUM; i++) {
            rowIndexes.add(i);
        }
        Collections.shuffle(rowIndexes, random);
        List<Integer> realRows = rowIndexes.subList(0, M);
        Set<Integer> hitRows = new HashSet<>(realRows.subList(0, intersectionSize));
        byte[][] uPads = new byte[ROW_NUM][PAD_BYTE_LENGTH];
        byte[][] vPads = new byte[ROW_NUM][PAD_BYTE_LENGTH];
        byte[][] payloads = new byte[ROW_NUM][payloadByteLength];
        boolean[] realRowBits = new boolean[ROW_NUM];
        boolean[] openRowBits = new boolean[ROW_NUM];

        for (int row = 0; row < ROW_NUM; row++) {
            random.nextBytes(uPads[row]);
            random.nextBytes(vPads[row]);
            payloads[row] = payload(payloadByteLength, trial, row);
        }
        for (int itemIndex = 0; itemIndex < M; itemIndex++) {
            int row = realRows.get(itemIndex);
            byte[] payload = payload(payloadByteLength, trial, ROW_NUM + itemIndex);
            payloads[row] = payload;
            realRowBits[row] = true;
            if (!hitRows.contains(row)) {
                openRowBits[row] = true;
            }
        }
        SimulatedMcrgTokenSogsPadCarrier carrier =
            SimulatedMcrgTokenSogsPadCarrier.create(uPads, vPads, realRowBits, openRowBits, payloads);
        return new CaseData(carrier.getSenderOutput(), carrier.getReceiverOutput(), carrier.getExpectedMisses());
    }

    private static byte[] payload(int payloadByteLength, int trial, int itemIndex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("mcrg-release-payload".getBytes(StandardCharsets.UTF_8));
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

    private static class SenderThread extends Thread {
        private final McrgTokenSogsReleaseSender sender;
        private final McrgTokenSogsPadCarrierSenderOutput carrierOutput;
        private Exception exception;

        private SenderThread(McrgTokenSogsReleaseSender sender, McrgTokenSogsPadCarrierSenderOutput carrierOutput) {
            this.sender = sender;
            this.carrierOutput = carrierOutput;
        }

        @Override
        public void run() {
            try {
                sender.send(carrierOutput);
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

    private static class ReceiverThread extends Thread {
        private final McrgTokenSogsReleaseReceiver receiver;
        private final McrgTokenSogsPadCarrierReceiverOutput carrierOutput;
        private final int payloadByteLength;
        private TokenKeyedSogsSketch.PeelResult peelResult;
        private Exception exception;

        private ReceiverThread(
            McrgTokenSogsReleaseReceiver receiver,
            McrgTokenSogsPadCarrierReceiverOutput carrierOutput,
            int payloadByteLength
        ) {
            this.receiver = receiver;
            this.carrierOutput = carrierOutput;
            this.payloadByteLength = payloadByteLength;
        }

        @Override
        public void run() {
            try {
                peelResult = receiver.receive(carrierOutput, payloadByteLength, CELL_NUM, DEGREE);
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

    private static class CaseData {
        private final McrgTokenSogsPadCarrierSenderOutput senderOutput;
        private final McrgTokenSogsPadCarrierReceiverOutput receiverOutput;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(
            McrgTokenSogsPadCarrierSenderOutput senderOutput,
            McrgTokenSogsPadCarrierReceiverOutput receiverOutput,
            Set<ByteBuffer> expectedMisses
        ) {
            this.senderOutput = senderOutput;
            this.receiverOutput = receiverOutput;
            this.expectedMisses = expectedMisses;
        }
    }
}
