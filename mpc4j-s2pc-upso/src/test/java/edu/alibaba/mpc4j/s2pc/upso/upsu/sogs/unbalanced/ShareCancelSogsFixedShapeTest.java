package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.rpc.utils.PayloadType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fixed-shape tests for the share-cancel SOGS tail.
 *
 * <p>The online transcript size must be determined only by public parameters: row count, payload byte length,
 * cell count, and degree. It must not change with real/dummy placement or hit/miss distribution.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsFixedShapeTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Row num.
     */
    private static final int ROW_NUM = 256;
    /**
     * Payload byte length.
     */
    private static final int PAYLOAD_BYTE_LENGTH = 16;
    /**
     * Cell num.
     */
    private static final int CELL_NUM = 1024;
    /**
     * SOGS degree.
     */
    private static final int DEGREE = 3;

    public ShareCancelSogsFixedShapeTest() {
        super(ShareCancelSogsFixedShapeTest.class.getSimpleName());
    }

    @Test
    public void testFixedShapeAcrossHitMissDummyPatterns() throws Exception {
        ShapeStats allMissStats = runCase(Pattern.ALL_REAL_MISS, 1);
        ShapeStats allHitStats = runCase(Pattern.ALL_REAL_HIT, 2);
        ShapeStats mixedStats = runCase(Pattern.MIXED_REAL_DUMMY_HIT_MISS, 3);

        Assert.assertEquals(allMissStats.firstPayloadBytes, allHitStats.firstPayloadBytes);
        Assert.assertEquals(allMissStats.secondPayloadBytes, allHitStats.secondPayloadBytes);
        Assert.assertEquals(allMissStats.firstPacketNum, allHitStats.firstPacketNum);
        Assert.assertEquals(allMissStats.secondPacketNum, allHitStats.secondPacketNum);

        Assert.assertEquals(allMissStats.firstPayloadBytes, mixedStats.firstPayloadBytes);
        Assert.assertEquals(allMissStats.secondPayloadBytes, mixedStats.secondPayloadBytes);
        Assert.assertEquals(allMissStats.firstPacketNum, mixedStats.firstPacketNum);
        Assert.assertEquals(allMissStats.secondPacketNum, mixedStats.secondPacketNum);
    }

    @Test
    public void testTailPacketShapeUsesOnlyAggregateStepsAcrossPatterns() throws Exception {
        List<PacketRecord> allMissPackets = runCaseAndRecordTailPackets(Pattern.ALL_REAL_MISS, 11);
        List<PacketRecord> allHitPackets = runCaseAndRecordTailPackets(Pattern.ALL_REAL_HIT, 12);
        List<PacketRecord> mixedPackets = runCaseAndRecordTailPackets(Pattern.MIXED_REAL_DUMMY_HIT_MISS, 13);

        assertTailPacketShape(allMissPackets);
        Assert.assertEquals(allMissPackets, allHitPackets);
        Assert.assertEquals(allMissPackets, mixedPackets);
    }

    @Test
    public void testTailAggregatePacketsDoNotDirectlyExposeRawRealAtoms() throws Exception {
        RecordedTailRun run = runCaseAndRecordTail(Pattern.MIXED_REAL_DUMMY_HIT_MISS, 14);
        PacketRecord otMaskPacket = onlyPacketWithStep(
            run.packetRecords, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_OT_MASKS.ordinal()
        );
        PacketRecord cellSharePacket = onlyPacketWithStep(
            run.packetRecords, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_CELL_SHARE.ordinal()
        );
        Set<ByteBuffer> aggregatePayloads = new HashSet<>();
        otMaskPacket.payloads.forEach(payload -> aggregatePayloads.add(ByteBuffer.wrap(payload)));
        cellSharePacket.payloads.forEach(payload -> aggregatePayloads.add(ByteBuffer.wrap(payload)));

        TokenKeyedSogsSketch helper = new TokenKeyedSogsSketch(CELL_NUM, PAYLOAD_BYTE_LENGTH, DEGREE);
        for (int row = 0; row < ROW_NUM; row++) {
            if (run.data.realRowBits[row]) {
                byte[] rawAtom = helper.createAtomBytes(run.tokens[row], run.data.payloads[row]);
                Assert.assertFalse(
                    "tail aggregate wire payload must not contain a direct raw SOGS atom",
                    aggregatePayloads.contains(ByteBuffer.wrap(rawAtom))
                );
            }
        }
    }

    private ShapeStats runCase(Pattern pattern, int taskOffset) throws Exception {
        CaseData data = createCase(pattern);
        SecureRandom tokenRandom = SecureRandom.getInstance("SHA1PRNG");
        tokenRandom.setSeed(("fixed-shape:" + pattern.name()).getBytes());
        TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
            data.payloads, data.realRowBits, CELL_NUM, DEGREE, tokenRandom, 100
        );

        ShareCancelSogsTailConfig config = new ShareCancelSogsTailConfig.Builder().build();
        ShareCancelSogsTailSender sender = new ShareCancelSogsTailSender(firstRpc, secondRpc.ownParty(), config);
        ShareCancelSogsTailReceiver receiver = new ShareCancelSogsTailReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(2026061900 + taskOffset);
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

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
        ReceiverThread receiverThread = new ReceiverThread(receiver, data.receiverHitShares);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        senderThread.check();
        receiverThread.check();

        Assert.assertTrue(receiverThread.peelResult.success);
        Assert.assertEquals(data.expectedMisses, receiverThread.peelResult.recovered);
        ShapeStats stats = new ShapeStats(
            firstRpc.getPayloadByteLength(), secondRpc.getPayloadByteLength(),
            firstRpc.getSendDataPacketNum(), secondRpc.getSendDataPacketNum()
        );
        firstRpc.reset();
        secondRpc.reset();
        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
        return stats;
    }

    private List<PacketRecord> runCaseAndRecordTailPackets(Pattern pattern, int taskOffset) throws Exception {
        return runCaseAndRecordTail(pattern, taskOffset).packetRecords;
    }

    private RecordedTailRun runCaseAndRecordTail(Pattern pattern, int taskOffset) throws Exception {
        CaseData data = createCase(pattern);
        SecureRandom tokenRandom = SecureRandom.getInstance("SHA1PRNG");
        tokenRandom.setSeed(("fixed-shape-record:" + pattern.name()).getBytes());
        TokenKeyedSogsSketch.TokenSample sample = TokenKeyedSogsSketch.samplePeelableTokens(
            data.payloads, data.realRowBits, CELL_NUM, DEGREE, tokenRandom, 100
        );

        RecordingRpc recordingFirstRpc = new RecordingRpc(firstRpc);
        RecordingRpc recordingSecondRpc = new RecordingRpc(secondRpc);
        ShareCancelSogsTailConfig config = new ShareCancelSogsTailConfig.Builder().build();
        ShareCancelSogsTailSender sender = new ShareCancelSogsTailSender(
            recordingFirstRpc, recordingSecondRpc.ownParty(), config
        );
        ShareCancelSogsTailReceiver receiver = new ShareCancelSogsTailReceiver(
            recordingSecondRpc, recordingFirstRpc.ownParty(), config
        );
        int taskId = Math.abs(2026061950 + taskOffset);
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

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
        recordingFirstRpc.clear();
        recordingSecondRpc.clear();
        SenderThread senderThread = new SenderThread(
            sender, data.senderHitShares, data.realRowBits, sample.tokens, data.payloads
        );
        ReceiverThread receiverThread = new ReceiverThread(receiver, data.receiverHitShares);
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        senderThread.check();
        receiverThread.check();

        Assert.assertTrue(receiverThread.peelResult.success);
        Assert.assertEquals(data.expectedMisses, receiverThread.peelResult.recovered);
        List<PacketRecord> packetRecords = recordingFirstRpc.snapshot().stream()
            .filter(packetRecord -> packetRecord.ptoId == TokenKeyedSogsTailPtoDesc.getInstance().getPtoId())
            .collect(Collectors.toList());

        firstRpc.reset();
        secondRpc.reset();
        Thread senderDestroyThread = new Thread(sender::destroy);
        Thread receiverDestroyThread = new Thread(receiver::destroy);
        senderDestroyThread.start();
        receiverDestroyThread.start();
        senderDestroyThread.join();
        receiverDestroyThread.join();
        return new RecordedTailRun(data, sample.tokens, packetRecords);
    }

    private static void assertTailPacketShape(List<PacketRecord> packetRecords) {
        Assert.assertEquals("share-cancel tail must expose exactly three aggregate sender packets", 3, packetRecords.size());
        PacketRecord tokenPacket = onlyPacketWithStep(
            packetRecords, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal()
        );
        PacketRecord otMaskPacket = onlyPacketWithStep(
            packetRecords, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_OT_MASKS.ordinal()
        );
        PacketRecord cellSharePacket = onlyPacketWithStep(
            packetRecords, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_CELL_SHARE.ordinal()
        );
        Assert.assertEquals(PayloadType.EQUAL_SIZE, tokenPacket.payloadType);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, otMaskPacket.payloadType);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, cellSharePacket.payloadType);
        Assert.assertEquals(ROW_NUM, tokenPacket.payloadNum);
        Assert.assertEquals(TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH, tokenPacket.equalLength);
        Assert.assertEquals(ROW_NUM * 2, otMaskPacket.payloadNum);
        Assert.assertEquals(TokenKeyedSogsSketch.atomByteLength(PAYLOAD_BYTE_LENGTH), otMaskPacket.equalLength);
        Assert.assertEquals(CELL_NUM, cellSharePacket.payloadNum);
        Assert.assertEquals(TokenKeyedSogsSketch.atomByteLength(PAYLOAD_BYTE_LENGTH), cellSharePacket.equalLength);
    }

    private static PacketRecord onlyPacketWithStep(List<PacketRecord> packetRecords, int stepId) {
        List<PacketRecord> matches = packetRecords.stream()
            .filter(packetRecord -> packetRecord.stepId == stepId)
            .collect(Collectors.toList());
        Assert.assertEquals("expected exactly one tail packet with step " + stepId, 1, matches.size());
        return matches.get(0);
    }

    private static CaseData createCase(Pattern pattern) {
        Random random = new Random(2026061902L + pattern.ordinal());
        byte[][] payloads = new byte[ROW_NUM][PAYLOAD_BYTE_LENGTH];
        boolean[] realRowBits = new boolean[ROW_NUM];
        boolean[] senderHitShares = new boolean[ROW_NUM];
        boolean[] receiverHitShares = new boolean[ROW_NUM];
        Set<ByteBuffer> expectedMisses = new HashSet<>();
        for (int i = 0; i < ROW_NUM; i++) {
            payloads[i] = payload(i);
            boolean real;
            boolean hit;
            switch (pattern) {
                case ALL_REAL_MISS:
                    real = true;
                    hit = false;
                    break;
                case ALL_REAL_HIT:
                    real = true;
                    hit = true;
                    break;
                case MIXED_REAL_DUMMY_HIT_MISS:
                    real = (i % 4) != 0;
                    hit = real && (i % 3 == 0);
                    break;
                default:
                    throw new IllegalStateException("unknown pattern: " + pattern);
            }
            realRowBits[i] = real;
            receiverHitShares[i] = random.nextBoolean();
            senderHitShares[i] = receiverHitShares[i] ^ hit;
            if (real && !hit) {
                expectedMisses.add(ByteBuffer.wrap(payloads[i]));
            }
        }
        return new CaseData(payloads, realRowBits, senderHitShares, receiverHitShares, expectedMisses);
    }

    private static byte[] payload(int index) {
        byte[] payload = new byte[PAYLOAD_BYTE_LENGTH];
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).putInt(index);
        byte[] indexBytes = buffer.array();
        for (int offset = 0; offset < payload.length; offset += indexBytes.length) {
            System.arraycopy(indexBytes, 0, payload, offset, Math.min(indexBytes.length, payload.length - offset));
        }
        return payload;
    }

    private enum Pattern {
        /**
         * Every row is a real miss.
         */
        ALL_REAL_MISS,
        /**
         * Every row is a real hit.
         */
        ALL_REAL_HIT,
        /**
         * Mixed real miss, real hit, and dummy rows.
         */
        MIXED_REAL_DUMMY_HIT_MISS,
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
            this.senderHitShares = Arrays.copyOf(senderHitShares, senderHitShares.length);
            this.realRowBits = Arrays.copyOf(realRowBits, realRowBits.length);
            this.tokens = copy(tokens);
            this.payloads = copy(payloads);
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
        private TokenKeyedSogsSketch.PeelResult peelResult;
        private Exception exception;

        private ReceiverThread(ShareCancelSogsTailReceiver receiver, boolean[] receiverHitShares) {
            this.receiver = receiver;
            this.receiverHitShares = Arrays.copyOf(receiverHitShares, receiverHitShares.length);
        }

        @Override
        public void run() {
            try {
                peelResult = receiver.receive(receiverHitShares, PAYLOAD_BYTE_LENGTH, CELL_NUM, DEGREE);
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

    private static byte[][] copy(byte[][] input) {
        byte[][] output = new byte[input.length][];
        for (int i = 0; i < input.length; i++) {
            output[i] = Arrays.copyOf(input[i], input[i].length);
        }
        return output;
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

    private static class ShapeStats {
        private final long firstPayloadBytes;
        private final long secondPayloadBytes;
        private final long firstPacketNum;
        private final long secondPacketNum;

        private ShapeStats(long firstPayloadBytes, long secondPayloadBytes, long firstPacketNum, long secondPacketNum) {
            this.firstPayloadBytes = firstPayloadBytes;
            this.secondPayloadBytes = secondPayloadBytes;
            this.firstPacketNum = firstPacketNum;
            this.secondPacketNum = secondPacketNum;
        }
    }

    private static class RecordedTailRun {
        private final CaseData data;
        private final byte[][] tokens;
        private final List<PacketRecord> packetRecords;

        private RecordedTailRun(CaseData data, byte[][] tokens, List<PacketRecord> packetRecords) {
            this.data = data;
            this.tokens = copy(tokens);
            this.packetRecords = packetRecords;
        }
    }

    private static class RecordingRpc implements Rpc {
        private final Rpc delegate;
        private final List<PacketRecord> packetRecords;

        private RecordingRpc(Rpc delegate) {
            this.delegate = delegate;
            packetRecords = Collections.synchronizedList(new ArrayList<>());
        }

        @Override
        public Party ownParty() {
            return delegate.ownParty();
        }

        @Override
        public Set<Party> getPartySet() {
            return delegate.getPartySet();
        }

        @Override
        public Party getParty(int partyId) {
            return delegate.getParty(partyId);
        }

        @Override
        public void connect() {
            delegate.connect();
        }

        @Override
        public void send(DataPacket dataPacket) {
            packetRecords.add(new PacketRecord(dataPacket));
            delegate.send(dataPacket);
        }

        @Override
        public DataPacket receive(DataPacketHeader header) {
            return delegate.receive(header);
        }

        @Override
        public DataPacket receiveAny(int ptoId) {
            return delegate.receiveAny(ptoId);
        }

        @Override
        public long getPayloadByteLength() {
            return delegate.getPayloadByteLength();
        }

        @Override
        public long getSendByteLength() {
            return delegate.getSendByteLength();
        }

        @Override
        public long getSendDataPacketNum() {
            return delegate.getSendDataPacketNum();
        }

        @Override
        public void synchronize() {
            delegate.synchronize();
        }

        @Override
        public void reset() {
            delegate.reset();
            clear();
        }

        @Override
        public void disconnect() {
            delegate.disconnect();
        }

        private void clear() {
            packetRecords.clear();
        }

        private List<PacketRecord> snapshot() {
            synchronized (packetRecords) {
                return List.copyOf(packetRecords);
            }
        }
    }

    private static class PacketRecord {
        private final int ptoId;
        private final int stepId;
        private final PayloadType payloadType;
        private final int payloadNum;
        private final int equalLength;
        private final List<byte[]> payloads;

        private PacketRecord(DataPacket dataPacket) {
            DataPacketHeader header = dataPacket.getHeader();
            ptoId = header.getPtoId();
            stepId = header.getStepId();
            payloadType = dataPacket.getPayloadType();
            payloadNum = dataPacket.getPayload().size();
            equalLength = dataPacket.getEqualLength();
            payloads = dataPacket.getPayload().stream()
                .map(payload -> Arrays.copyOf(payload, payload.length))
                .collect(Collectors.toUnmodifiableList());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PacketRecord that)) {
                return false;
            }
            return ptoId == that.ptoId
                && stepId == that.stepId
                && payloadType == that.payloadType
                && payloadNum == that.payloadNum
                && equalLength == that.equalLength;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptoId, stepId, payloadType, payloadNum, equalLength);
        }
    }
}
