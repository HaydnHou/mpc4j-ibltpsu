package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.naive.NaivePeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Folded share-output PM-PEQT tests.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class FoldedSharePmPeqtTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * row num.
     */
    private static final int ROW = 7;
    /**
     * column num.
     */
    private static final int COLUMN = 11;
    /**
     * input byte length.
     */
    private static final int BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * Step id for {@code Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.PtoStep.SENDER_SEND_PERMUTED_PRF}.
     */
    private static final int BYTE_ECC_SENDER_SEND_PERMUTED_PRF_STEP = 1;

    public FoldedSharePmPeqtTest() {
        super(FoldedSharePmPeqtTest.class.getSimpleName());
    }

    @Test
    public void testTcl23PsOprfFoldedSharePmPeqt() throws InterruptedException {
        Tcl23PsOprfFoldedSharePmPeqtConfig config = new Tcl23PsOprfFoldedSharePmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .build();
        testPto(config);
    }

    @Test
    public void testTcl23ByteEccDdhFoldedSharePmPeqt() throws InterruptedException {
        Tcl23ByteEccDdhFoldedSharePmPeqtConfig config = new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .build();
        testPto(config);
    }

    @Test
    public void testTcl23ByteEccDdhNaiveFoldedSharePmPeqt() throws InterruptedException {
        Tcl23ByteEccDdhFoldedSharePmPeqtConfig config = new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder()
            .setPeqtConfig(new NaivePeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).build())
            .build();
        testPto(config);
    }

    @Test
    public void testTcl23ByteEccDdhCompactFoldedSharePmPeqt() throws InterruptedException {
        Tcl23ByteEccDdhFoldedSharePmPeqtConfig config = new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .setCompactPeqtByteLength(true)
            .build();
        testPto(config);
    }

    @Test
    public void testTcl23ByteEccDdhOutputUsesAnonymousColumnOrder() throws InterruptedException {
        Tcl23ByteEccDdhFoldedSharePmPeqtConfig config = new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .build();
        TestInput input = generateSingleHitInput();
        FoldedSharePmPeqtSender sender = FoldedSharePmPeqtFactory.createSender(
            firstRpc, secondRpc.ownParty(), config
        );
        FoldedSharePmPeqtReceiver receiver = FoldedSharePmPeqtFactory.createReceiver(
            secondRpc, firstRpc.ownParty(), config
        );
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        SenderThread senderThread = new SenderThread(
            sender, input.senderMatrix, input.rowPermutationMap, input.columnPermutationMap
        );
        ReceiverThread receiverThread = new ReceiverThread(receiver, input.receiverMatrix);

        firstRpc.reset();
        secondRpc.reset();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();

        Assert.assertNull(senderThread.getError());
        Assert.assertNull(receiverThread.getError());
        BitVector hitVector = senderThread.getHitShare().getBitVector().xor(receiverThread.getHitShare().getBitVector());
        for (int anonymousColumn = 0; anonymousColumn < COLUMN; anonymousColumn++) {
            Assert.assertEquals(
                "hit must appear in anonymous column order, not original column order",
                anonymousColumn == 1, hitVector.get(anonymousColumn)
            );
        }

        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
    }

    @Test
    public void testTcl23ByteEccDdhSenderPrfPayloadUsesAnonymousOrder() throws Exception {
        TestInput input = generateInput();
        int[] identityRows = IntStream.range(0, ROW).toArray();
        int[] identityColumns = IntStream.range(0, COLUMN).toArray();
        int[] anonymousRows = new int[]{3, 0, 6, 1, 5, 2, 4};
        int[] anonymousColumns = new int[]{7, 0, 3, 10, 1, 8, 2, 9, 4, 6, 5};

        List<byte[]> identityPayload = runByteEccAndCaptureSenderPermutedPrfs(
            input, identityRows, identityColumns
        );
        List<byte[]> anonymousPayload = runByteEccAndCaptureSenderPermutedPrfs(
            input, anonymousRows, anonymousColumns
        );

        Assert.assertFalse(
            "sender PRF payload must not stay in original row/column order under a non-identity permutation",
            byteArrayListsEqual(identityPayload, anonymousPayload)
        );
        int anonymousIndex = 0;
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COLUMN; j++) {
                int source = anonymousRows[i] * COLUMN + anonymousColumns[j];
                Assert.assertArrayEquals(
                    "sender PRF payload must follow anonymous row/column order",
                    identityPayload.get(source), anonymousPayload.get(anonymousIndex)
                );
                anonymousIndex++;
            }
        }
    }

    @Test
    public void testSenderRejectsMalformedPermutationMaps() throws MpcAbortException {
        PermutationCheckSender sender = new PermutationCheckSender(firstRpc, secondRpc.ownParty());
        TestInput input = generateInput();
        sender.init(ROW, COLUMN);
        int[] validRows = IntStream.range(0, ROW).toArray();
        int[] validColumns = IntStream.range(0, COLUMN).toArray();
        int[] duplicateRows = validRows.clone();
        duplicateRows[1] = duplicateRows[0];
        int[] outOfRangeRows = validRows.clone();
        outOfRangeRows[ROW - 1] = ROW;
        int[] duplicateColumns = validColumns.clone();
        duplicateColumns[1] = duplicateColumns[0];
        int[] outOfRangeColumns = validColumns.clone();
        outOfRangeColumns[COLUMN - 1] = COLUMN;

        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.foldedSharePmPeqt(input.senderMatrix, duplicateRows, validColumns, BYTE_LENGTH)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.foldedSharePmPeqt(input.senderMatrix, outOfRangeRows, validColumns, BYTE_LENGTH)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.foldedSharePmPeqt(input.senderMatrix, validRows, duplicateColumns, BYTE_LENGTH)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            sender.foldedSharePmPeqt(input.senderMatrix, validRows, outOfRangeColumns, BYTE_LENGTH)
        );

        sender.destroy();
    }

    private void testPto(FoldedSharePmPeqtConfig config) throws InterruptedException {
        TestInput input = generateInput();
        FoldedSharePmPeqtSender sender = FoldedSharePmPeqtFactory.createSender(
            firstRpc, secondRpc.ownParty(), config
        );
        FoldedSharePmPeqtReceiver receiver = FoldedSharePmPeqtFactory.createReceiver(
            secondRpc, firstRpc.ownParty(), config
        );
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        SenderThread senderThread = new SenderThread(
            sender, input.senderMatrix, input.rowPermutationMap, input.columnPermutationMap
        );
        ReceiverThread receiverThread = new ReceiverThread(receiver, input.receiverMatrix);

        firstRpc.reset();
        secondRpc.reset();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        stopWatch.stop();
        printAndResetRpc(stopWatch.getTime(TimeUnit.MILLISECONDS));

        Assert.assertNull(senderThread.getError());
        Assert.assertNull(receiverThread.getError());
        assertFoldedHitShares(input, senderThread.getHitShare(), receiverThread.getHitShare());

        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
    }

    private List<byte[]> runByteEccAndCaptureSenderPermutedPrfs(
        TestInput input, int[] rowPermutationMap, int[] columnPermutationMap
    ) throws Exception {
        RecordingRpc recordingFirstRpc = new RecordingRpc(firstRpc);
        RecordingRpc recordingSecondRpc = new RecordingRpc(secondRpc);
        Tcl23ByteEccDdhFoldedSharePmPeqtConfig config = new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .build();
        FoldedSharePmPeqtSender sender = FoldedSharePmPeqtFactory.createSender(
            recordingFirstRpc, recordingSecondRpc.ownParty(), config
        );
        FoldedSharePmPeqtReceiver receiver = FoldedSharePmPeqtFactory.createReceiver(
            recordingSecondRpc, recordingFirstRpc.ownParty(), config
        );
        int taskId = 20260619;
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);
        sender.setSecureRandom(seededSecureRandom(1L));
        receiver.setSecureRandom(seededSecureRandom(2L));
        SenderThread senderThread = new SenderThread(
            sender, input.senderMatrix, rowPermutationMap, columnPermutationMap
        );
        ReceiverThread receiverThread = new ReceiverThread(receiver, input.receiverMatrix);

        recordingFirstRpc.reset();
        recordingSecondRpc.reset();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();

        Assert.assertNull(senderThread.getError());
        Assert.assertNull(receiverThread.getError());
        List<byte[]> payload = recordingFirstRpc.findPayload(
            Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.getInstance().getPtoId(),
            BYTE_ECC_SENDER_SEND_PERMUTED_PRF_STEP
        );

        Thread senderDestroyThread = new Thread(sender::destroy);
        Thread receiverDestroyThread = new Thread(receiver::destroy);
        senderDestroyThread.start();
        receiverDestroyThread.start();
        senderDestroyThread.join();
        receiverDestroyThread.join();
        return payload;
    }

    private void assertFoldedHitShares(TestInput input, SquareZ2Vector senderHitShare,
                                       SquareZ2Vector receiverHitShare) {
        Assert.assertEquals(COLUMN, senderHitShare.getNum());
        Assert.assertEquals(COLUMN, receiverHitShare.getNum());
        BitVector hitVector = senderHitShare.getBitVector().xor(receiverHitShare.getBitVector());
        int zeroHitColumnCount = 0;
        int oneHitColumnCount = 0;
        int twoHitColumnCount = 0;
        for (int j = 0; j < COLUMN; j++) {
            int hitCount = expectedColumnHitCount(input, j);
            Assert.assertEquals("folded hit mismatch at anonymous column " + j, hitCount > 0, hitVector.get(j));
            if (hitCount == 0) {
                zeroHitColumnCount++;
            } else if (hitCount == 1) {
                oneHitColumnCount++;
            } else {
                twoHitColumnCount++;
            }
        }
        Assert.assertTrue("test must contain zero-hit columns", zeroHitColumnCount > 0);
        Assert.assertTrue("test must contain one-hit columns", oneHitColumnCount > 0);
        Assert.assertTrue("test must contain multi-hit columns to rule out XOR/parity folding", twoHitColumnCount > 0);
    }

    private int expectedColumnHitCount(TestInput input, int anonymousColumn) {
        int hitCount = 0;
        for (int i = 0; i < ROW; i++) {
            int permutedIndex = input.rowPermutationMap[i] * COLUMN + input.columnPermutationMap[anonymousColumn];
            if (BytesUtils.equals(input.senderFlat[permutedIndex], input.receiverFlat[permutedIndex])) {
                hitCount++;
            }
        }
        return hitCount;
    }

    private TestInput generateInput() {
        byte[][] senderFlat = new byte[ROW * COLUMN][BYTE_LENGTH];
        byte[][] receiverFlat = new byte[ROW * COLUMN][BYTE_LENGTH];
        for (int i = 0; i < ROW * COLUMN; i++) {
            SECURE_RANDOM.nextBytes(senderFlat[i]);
            SECURE_RANDOM.nextBytes(receiverFlat[i]);
        }
        for (int c = 0; c < COLUMN; c++) {
            if (c % 3 == 0) {
                int r = c % ROW;
                receiverFlat[r * COLUMN + c] = BytesUtils.clone(senderFlat[r * COLUMN + c]);
            } else if (c % 3 == 1) {
                int r0 = c % ROW;
                int r1 = (c + 2) % ROW;
                receiverFlat[r0 * COLUMN + c] = BytesUtils.clone(senderFlat[r0 * COLUMN + c]);
                receiverFlat[r1 * COLUMN + c] = BytesUtils.clone(senderFlat[r1 * COLUMN + c]);
            }
        }
        List<Integer> rowPermutation = IntStream.range(0, ROW).boxed().collect(Collectors.toList());
        Collections.shuffle(rowPermutation, SECURE_RANDOM);
        int[] rowPermutationMap = rowPermutation.stream().mapToInt(Integer::intValue).toArray();
        List<Integer> columnPermutation = IntStream.range(0, COLUMN).boxed().collect(Collectors.toList());
        Collections.shuffle(columnPermutation, SECURE_RANDOM);
        int[] columnPermutationMap = columnPermutation.stream().mapToInt(Integer::intValue).toArray();
        byte[][][] senderMatrix = new byte[ROW][COLUMN][];
        byte[][][] receiverMatrix = new byte[ROW][COLUMN][];
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COLUMN; j++) {
                senderMatrix[i][j] = BytesUtils.clone(senderFlat[i * COLUMN + j]);
                receiverMatrix[i][j] = BytesUtils.clone(receiverFlat[i * COLUMN + j]);
            }
        }
        return new TestInput(senderFlat, receiverFlat, senderMatrix, receiverMatrix, rowPermutationMap, columnPermutationMap);
    }

    private static SecureRandom seededSecureRandom(long seed) throws Exception {
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(ByteBuffer.allocate(Long.BYTES).putLong(seed).array());
        return secureRandom;
    }

    private static boolean byteArrayListsEqual(List<byte[]> first, List<byte[]> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!BytesUtils.equals(first.get(i), second.get(i))) {
                return false;
            }
        }
        return true;
    }

    private TestInput generateSingleHitInput() {
        byte[][] senderFlat = new byte[ROW * COLUMN][BYTE_LENGTH];
        byte[][] receiverFlat = new byte[ROW * COLUMN][BYTE_LENGTH];
        for (int i = 0; i < ROW * COLUMN; i++) {
            SECURE_RANDOM.nextBytes(senderFlat[i]);
            SECURE_RANDOM.nextBytes(receiverFlat[i]);
        }
        receiverFlat[0] = BytesUtils.clone(senderFlat[0]);
        int[] rowPermutationMap = new int[]{3, 0, 6, 1, 5, 2, 4};
        int[] columnPermutationMap = new int[]{7, 0, 3, 10, 1, 8, 2, 9, 4, 6, 5};
        byte[][][] senderMatrix = new byte[ROW][COLUMN][];
        byte[][][] receiverMatrix = new byte[ROW][COLUMN][];
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COLUMN; j++) {
                senderMatrix[i][j] = BytesUtils.clone(senderFlat[i * COLUMN + j]);
                receiverMatrix[i][j] = BytesUtils.clone(receiverFlat[i * COLUMN + j]);
            }
        }
        return new TestInput(senderFlat, receiverFlat, senderMatrix, receiverMatrix, rowPermutationMap, columnPermutationMap);
    }

    private static class SenderThread extends Thread {
        private final FoldedSharePmPeqtSender sender;
        private final byte[][][] inputMatrix;
        private final int[] rowPermutationMap;
        private final int[] columnPermutationMap;
        private SquareZ2Vector hitShare;
        private Throwable error;

        private SenderThread(FoldedSharePmPeqtSender sender, byte[][][] inputMatrix, int[] rowPermutationMap,
                             int[] columnPermutationMap) {
            this.sender = sender;
            this.inputMatrix = inputMatrix;
            this.rowPermutationMap = rowPermutationMap;
            this.columnPermutationMap = columnPermutationMap;
        }

        private SquareZ2Vector getHitShare() {
            return hitShare;
        }

        private Throwable getError() {
            return error;
        }

        @Override
        public void run() {
            try {
                sender.init(ROW, COLUMN);
                hitShare = sender.foldedSharePmPeqt(inputMatrix, rowPermutationMap, columnPermutationMap, BYTE_LENGTH);
            } catch (MpcAbortException | RuntimeException e) {
                error = e;
            }
        }
    }

    private static class ReceiverThread extends Thread {
        private final FoldedSharePmPeqtReceiver receiver;
        private final byte[][][] inputMatrix;
        private SquareZ2Vector hitShare;
        private Throwable error;

        private ReceiverThread(FoldedSharePmPeqtReceiver receiver, byte[][][] inputMatrix) {
            this.receiver = receiver;
            this.inputMatrix = inputMatrix;
        }

        private SquareZ2Vector getHitShare() {
            return hitShare;
        }

        private Throwable getError() {
            return error;
        }

        @Override
        public void run() {
            try {
                receiver.init(ROW, COLUMN);
                hitShare = receiver.foldedSharePmPeqt(inputMatrix, BYTE_LENGTH, ROW, COLUMN);
            } catch (MpcAbortException | RuntimeException e) {
                error = e;
            }
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
            packetRecords.clear();
        }

        @Override
        public void disconnect() {
            delegate.disconnect();
        }

        private List<byte[]> findPayload(int ptoId, int stepId) {
            List<PacketRecord> matches = packetRecords.stream()
                .filter(packetRecord -> packetRecord.ptoId == ptoId && packetRecord.stepId == stepId)
                .collect(Collectors.toList());
            Assert.assertEquals("expected exactly one packet", 1, matches.size());
            return matches.get(0).payload.stream()
                .map(BytesUtils::clone)
                .collect(Collectors.toList());
        }
    }

    private static class PacketRecord {
        private final int ptoId;
        private final int stepId;
        private final List<byte[]> payload;

        private PacketRecord(DataPacket dataPacket) {
            DataPacketHeader header = dataPacket.getHeader();
            ptoId = header.getPtoId();
            stepId = header.getStepId();
            payload = dataPacket.getPayload().stream()
                .map(BytesUtils::clone)
                .collect(Collectors.toList());
        }
    }

    private static class TestInput {
        private final byte[][] senderFlat;
        private final byte[][] receiverFlat;
        private final byte[][][] senderMatrix;
        private final byte[][][] receiverMatrix;
        private final int[] rowPermutationMap;
        private final int[] columnPermutationMap;

        private TestInput(byte[][] senderFlat, byte[][] receiverFlat, byte[][][] senderMatrix,
                          byte[][][] receiverMatrix, int[] rowPermutationMap, int[] columnPermutationMap) {
            this.senderFlat = senderFlat;
            this.receiverFlat = receiverFlat;
            this.senderMatrix = senderMatrix;
            this.receiverMatrix = receiverMatrix;
            this.rowPermutationMap = rowPermutationMap;
            this.columnPermutationMap = columnPermutationMap;
        }
    }

    private static class PermutationCheckSender extends AbstractFoldedSharePmPeqtSender {
        private static final PtoDesc TEST_PTO_DESC = new PtoDesc() {
            @Override
            public int getPtoId() {
                return 2026061901;
            }

            @Override
            public String getPtoName() {
                return "FOLDED_SHARE_PMPEQT_PERMUTATION_CHECK";
            }
        };

        private PermutationCheckSender(Rpc senderRpc, Party receiverParty) {
            super(TEST_PTO_DESC, senderRpc, receiverParty, new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder().build());
        }

        @Override
        public void init(int maxRow, int maxColumn) {
            setInitInput(maxRow, maxColumn);
        }

        @Override
        public SquareZ2Vector foldedSharePmPeqt(byte[][][] inputMatrix, int[] rowPermutationMap,
                                                int[] columnPermutationMap, int byteLength) {
            setPtoInput(inputMatrix, rowPermutationMap, columnPermutationMap, byteLength);
            return SquareZ2Vector.createZeros(columnPermutationMap.length);
        }
    }
}
