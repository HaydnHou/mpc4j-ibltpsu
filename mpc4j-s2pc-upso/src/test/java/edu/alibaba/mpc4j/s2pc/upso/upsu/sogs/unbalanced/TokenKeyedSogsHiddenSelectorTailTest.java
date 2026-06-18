package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
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
 * End-to-end prototype test:
 *
 * <pre>
 * secret-shared PEQT matrix -> hidden row release shares -> token-keyed aggregate SOGS tail -> peel X \ Y.
 * </pre>
 *
 * <p>This intentionally does not open the row hit / miss vector. The only revealed structured object is the final SOGS
 * aggregate sketch that the receiver peels.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsHiddenSelectorTailTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Sender size at the target m = 2^10 setting.
     */
    private static final int M = 1024;
    /**
     * Cuckoo row count used by the current token-tail prototype.
     */
    private static final int ROW_NUM = 1639;
    /**
     * Equality lanes for an n = 2^22-style unbalanced point.
     */
    private static final int ALPHA = 7;
    /**
     * PEQT input bit length.
     */
    private static final int PEQT_L = 80;
    /**
     * PEQT input byte length.
     */
    private static final int PEQT_BYTE_LENGTH = PEQT_L / Byte.SIZE;
    /**
     * Cell count at gamma = 1.45.
     */
    private static final int CELL_NUM = 1485;
    /**
     * SOGS degree.
     */
    private static final int DEGREE = 3;

    public TokenKeyedSogsHiddenSelectorTailTest() {
        super(TokenKeyedSogsHiddenSelectorTailTest.class.getSimpleName());
    }

    @Test
    public void testHiddenSelectorToTail() throws Exception {
        CaseData data = createCase(16, M / 2);
        SecureRandom secureRandom = seededSecureRandom("hidden-selector-token");
        TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
            data.payloads, data.validBits, CELL_NUM, DEGREE, secureRandom, 100
        );

        PeqtConfig peqtConfig = PeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, true);
        Z2cConfig z2cConfig = Z2cFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, true);
        TokenKeyedSogsTailConfig tailConfig = new TokenKeyedSogsTailConfig.Builder().build();
        PeqtParty peqtSender = PeqtFactory.createSender(firstRpc, secondRpc.ownParty(), peqtConfig);
        PeqtParty peqtReceiver = PeqtFactory.createReceiver(secondRpc, firstRpc.ownParty(), peqtConfig);
        Z2cParty z2cSender = Z2cFactory.createSender(firstRpc, secondRpc.ownParty(), z2cConfig);
        Z2cParty z2cReceiver = Z2cFactory.createReceiver(secondRpc, firstRpc.ownParty(), z2cConfig);
        TokenKeyedSogsTailSender tailSender = new TokenKeyedSogsTailSender(firstRpc, secondRpc.ownParty(), tailConfig);
        TokenKeyedSogsTailReceiver tailReceiver = new TokenKeyedSogsTailReceiver(secondRpc, firstRpc.ownParty(), tailConfig);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        peqtSender.setTaskId(taskId);
        peqtReceiver.setTaskId(taskId);
        z2cSender.setTaskId(taskId);
        z2cReceiver.setTaskId(taskId);
        tailSender.setTaskId(taskId);
        tailReceiver.setTaskId(taskId);
        peqtSender.setParallel(true);
        peqtReceiver.setParallel(true);
        z2cSender.setParallel(true);
        z2cReceiver.setParallel(true);
        tailSender.setParallel(true);
        tailReceiver.setParallel(true);

        firstRpc.reset();
        secondRpc.reset();
        SenderThread senderThread = new SenderThread(
            peqtSender, z2cSender, tailSender, data.senderPeqtInputs, data.validBits, sample.tokens, data.payloads
        );
        ReceiverThread receiverThread = new ReceiverThread(
            peqtReceiver, z2cReceiver, tailReceiver, data.receiverPeqtInputs
        );
        long start = System.nanoTime();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        long timeMs = (System.nanoTime() - start) / 1_000_000;
        senderThread.check();
        receiverThread.check();

        boolean[] senderReleaseShare = senderThread.releaseShare;
        boolean[] receiverReleaseShare = receiverThread.releaseShare;
        for (int i = 0; i < ROW_NUM; i++) {
            Assert.assertEquals(data.expectedReleaseBits[i], senderReleaseShare[i] ^ receiverReleaseShare[i]);
        }
        Assert.assertTrue(receiverThread.peelResult.success);
        Assert.assertEquals(0, receiverThread.peelResult.residualCells);
        Assert.assertEquals(data.expectedMisses, receiverThread.peelResult.recovered);
        printAndResetRpc(timeMs);

        new Thread(peqtSender::destroy).start();
        new Thread(peqtReceiver::destroy).start();
        new Thread(z2cSender::destroy).start();
        new Thread(z2cReceiver::destroy).start();
        new Thread(tailSender::destroy).start();
        new Thread(tailReceiver::destroy).start();
    }

    private static CaseData createCase(int payloadByteLength, int intersectionSize) {
        Random random = new Random(20260617L + payloadByteLength * 1000L + intersectionSize * 17L);
        List<Integer> rowIndexes = new ArrayList<>(ROW_NUM);
        for (int i = 0; i < ROW_NUM; i++) {
            rowIndexes.add(i);
        }
        Collections.shuffle(rowIndexes, random);
        List<Integer> realRows = rowIndexes.subList(0, M);
        Set<Integer> hitRows = new HashSet<>(realRows.subList(0, intersectionSize));
        byte[][][] senderPeqtInputs = new byte[ALPHA][ROW_NUM][];
        byte[][][] receiverPeqtInputs = new byte[ALPHA][ROW_NUM][];
        byte[][] payloads = new byte[ROW_NUM][payloadByteLength];
        boolean[] validBits = new boolean[ROW_NUM];
        boolean[] expectedReleaseBits = new boolean[ROW_NUM];
        Set<ByteBuffer> expectedMisses = new HashSet<>(M - intersectionSize);
        for (int row = 0; row < ROW_NUM; row++) {
            payloads[row] = digest("dummy-payload", row, payloadByteLength);
            for (int j = 0; j < ALPHA; j++) {
                senderPeqtInputs[j][row] = digest("sender-peqt", j, row, PEQT_BYTE_LENGTH);
                receiverPeqtInputs[j][row] = digest("receiver-peqt", j, row, PEQT_BYTE_LENGTH);
            }
            expectedReleaseBits[row] = true;
        }
        for (int itemIndex = 0; itemIndex < M; itemIndex++) {
            int row = realRows.get(itemIndex);
            byte[] payload = digest("real-payload", itemIndex, payloadByteLength);
            payloads[row] = payload;
            validBits[row] = true;
            if (hitRows.contains(row)) {
                int lane = Math.floorMod(row, ALPHA);
                byte[] equalInput = digest("hit-peqt", lane, row, PEQT_BYTE_LENGTH);
                senderPeqtInputs[lane][row] = equalInput;
                receiverPeqtInputs[lane][row] = equalInput.clone();
                expectedReleaseBits[row] = false;
            } else {
                expectedMisses.add(ByteBuffer.wrap(payload));
            }
        }
        return new CaseData(
            senderPeqtInputs, receiverPeqtInputs, payloads, validBits, expectedReleaseBits, expectedMisses
        );
    }

    private static byte[] digest(String domain, int value, int byteLength) {
        return digest(domain, 0, value, byteLength);
    }

    private static byte[] digest(String domain, int lane, int value, int byteLength) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] output = new byte[byteLength];
            int offset = 0;
            int counter = 0;
            while (offset < byteLength) {
                digest.update(domain.getBytes(StandardCharsets.UTF_8));
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(lane).array());
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(counter).array());
                byte[] block = digest.digest();
                int copyLength = Math.min(block.length, byteLength - offset);
                System.arraycopy(block, 0, output, offset, copyLength);
                offset += copyLength;
                counter++;
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecureRandom seededSecureRandom(String domain) {
        return new SecureRandom(domain.getBytes(StandardCharsets.UTF_8));
    }

    private static class SenderThread extends Thread {
        private final PeqtParty peqtParty;
        private final Z2cParty z2cParty;
        private final TokenKeyedSogsTailSender tailSender;
        private final byte[][][] peqtInputs;
        private final boolean[] validBits;
        private final byte[][] tokens;
        private final byte[][] payloads;
        private boolean[] releaseShare;
        private Exception exception;

        private SenderThread(
            PeqtParty peqtParty, Z2cParty z2cParty, TokenKeyedSogsTailSender tailSender, byte[][][] peqtInputs,
            boolean[] validBits, byte[][] tokens, byte[][] payloads
        ) {
            this.peqtParty = peqtParty;
            this.z2cParty = z2cParty;
            this.tailSender = tailSender;
            this.peqtInputs = peqtInputs;
            this.validBits = validBits;
            this.tokens = tokens;
            this.payloads = payloads;
        }

        @Override
        public void run() {
            try {
                byte[][] flatInputs = TokenKeyedSogsHiddenSelector.flatten(peqtInputs);
                peqtParty.init(PEQT_L, flatInputs.length);
                SquareZ2Vector eqShare = peqtParty.peqt(PEQT_L, flatInputs);
                z2cParty.init(ROW_NUM * ALPHA);
                SquareZ2Vector releaseShareVector = TokenKeyedSogsHiddenSelector.collapseToReleaseShare(
                    z2cParty, eqShare, ALPHA, ROW_NUM
                );
                releaseShare = TokenKeyedSogsHiddenSelector.toBooleanArray(releaseShareVector);
                tailSender.init();
                tailSender.send(releaseShare, validBits, tokens, payloads, CELL_NUM, DEGREE);
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
        private final PeqtParty peqtParty;
        private final Z2cParty z2cParty;
        private final TokenKeyedSogsTailReceiver tailReceiver;
        private final byte[][][] peqtInputs;
        private boolean[] releaseShare;
        private TokenKeyedSogsSketch.PeelResult peelResult;
        private Exception exception;

        private ReceiverThread(
            PeqtParty peqtParty, Z2cParty z2cParty, TokenKeyedSogsTailReceiver tailReceiver, byte[][][] peqtInputs
        ) {
            this.peqtParty = peqtParty;
            this.z2cParty = z2cParty;
            this.tailReceiver = tailReceiver;
            this.peqtInputs = peqtInputs;
        }

        @Override
        public void run() {
            try {
                byte[][] flatInputs = TokenKeyedSogsHiddenSelector.flatten(peqtInputs);
                peqtParty.init(PEQT_L, flatInputs.length);
                SquareZ2Vector eqShare = peqtParty.peqt(PEQT_L, flatInputs);
                z2cParty.init(ROW_NUM * ALPHA);
                SquareZ2Vector releaseShareVector = TokenKeyedSogsHiddenSelector.collapseToReleaseShare(
                    z2cParty, eqShare, ALPHA, ROW_NUM
                );
                releaseShare = TokenKeyedSogsHiddenSelector.toBooleanArray(releaseShareVector);
                tailReceiver.init();
                peelResult = tailReceiver.receive(releaseShare, 16, CELL_NUM, DEGREE);
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
        private final byte[][][] senderPeqtInputs;
        private final byte[][][] receiverPeqtInputs;
        private final byte[][] payloads;
        private final boolean[] validBits;
        private final boolean[] expectedReleaseBits;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(
            byte[][][] senderPeqtInputs, byte[][][] receiverPeqtInputs, byte[][] payloads, boolean[] validBits,
            boolean[] expectedReleaseBits, Set<ByteBuffer> expectedMisses
        ) {
            this.senderPeqtInputs = senderPeqtInputs;
            this.receiverPeqtInputs = receiverPeqtInputs;
            this.payloads = payloads;
            this.validBits = validBits;
            this.expectedReleaseBits = expectedReleaseBits;
            this.expectedMisses = expectedMisses;
        }
    }
}
