package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierConfig;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Row-level share relation carrier tests.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class RowShareRelationCarrierTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * internal row num.
     */
    private static final int ROW = 5;
    /**
     * anonymous row / column num.
     */
    private static final int COLUMN = 7;
    /**
     * input byte length.
     */
    private static final int BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * secure random.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public RowShareRelationCarrierTest() {
        super(RowShareRelationCarrierTest.class.getSimpleName());
    }

    @Test
    public void testFoldedPmPeqtAdapterOutputsFoldedHitShares() throws InterruptedException {
        FoldedPmPeqtRowShareCarrierConfig config = new FoldedPmPeqtRowShareCarrierConfig.Builder().build();
        Assert.assertEquals(RowShareRelationCarrierConfig.InputType.DIGEST, config.getInputType());
        Assert.assertEquals(RowShareRelationCarrierConfig.NetworkShape.ALPHA_BY_BIN, config.getNetworkShape());
        RowShareRelationCarrierSender sender = RowShareRelationCarrierFactory.createSender(
            firstRpc, secondRpc.ownParty(), config
        );
        RowShareRelationCarrierReceiver receiver = RowShareRelationCarrierFactory.createReceiver(
            secondRpc, firstRpc.ownParty(), config
        );
        TestInput input = generateInput();
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
        assertFoldedHitShares(input, senderThread.getHitShare(), receiverThread.getHitShare());

        Thread senderDestroyThread = new Thread(sender::destroy);
        Thread receiverDestroyThread = new Thread(receiver::destroy);
        senderDestroyThread.start();
        receiverDestroyThread.start();
        senderDestroyThread.join();
        receiverDestroyThread.join();
    }

    private void assertFoldedHitShares(TestInput input, SquareZ2Vector senderHitShare,
                                       SquareZ2Vector receiverHitShare) {
        Assert.assertEquals(COLUMN, senderHitShare.getNum());
        Assert.assertEquals(COLUMN, receiverHitShare.getNum());
        BitVector hitVector = senderHitShare.getBitVector().xor(receiverHitShare.getBitVector());
        for (int anonymousColumn = 0; anonymousColumn < COLUMN; anonymousColumn++) {
            Assert.assertEquals(
                "folded hit mismatch at anonymous column " + anonymousColumn,
                expectedColumnHitCount(input, anonymousColumn) > 0,
                hitVector.get(anonymousColumn)
            );
        }
    }

    private int expectedColumnHitCount(TestInput input, int anonymousColumn) {
        int hitCount = 0;
        int rowNum = input.senderMatrix.length;
        int columnNum = input.senderMatrix[0].length;
        for (int row = 0; row < rowNum; row++) {
            int permutedIndex = input.rowPermutationMap[row] * columnNum + input.columnPermutationMap[anonymousColumn];
            if (BytesUtils.equals(input.senderFlat[permutedIndex], input.receiverFlat[permutedIndex])) {
                hitCount++;
            }
        }
        return hitCount;
    }

    private TestInput generateInput() {
        return generateInput(ROW, COLUMN);
    }

    private TestInput generateInput(int rowNum, int columnNum) {
        byte[][] senderFlat = new byte[rowNum * columnNum][BYTE_LENGTH];
        byte[][] receiverFlat = new byte[rowNum * columnNum][BYTE_LENGTH];
        for (int i = 0; i < rowNum * columnNum; i++) {
            SECURE_RANDOM.nextBytes(senderFlat[i]);
            SECURE_RANDOM.nextBytes(receiverFlat[i]);
        }
        for (int column = 0; column < columnNum; column++) {
            if (column % 3 == 0) {
                int row = column % rowNum;
                receiverFlat[row * columnNum + column] = BytesUtils.clone(senderFlat[row * columnNum + column]);
            } else if (column % 3 == 1) {
                int firstRow = column % rowNum;
                int secondRow = (column + 2) % rowNum;
                receiverFlat[firstRow * columnNum + column] = BytesUtils.clone(
                    senderFlat[firstRow * columnNum + column]
                );
                receiverFlat[secondRow * columnNum + column] = BytesUtils.clone(
                    senderFlat[secondRow * columnNum + column]
                );
            }
        }
        List<Integer> rows = IntStream.range(0, rowNum).boxed().collect(Collectors.toList());
        Collections.shuffle(rows, SECURE_RANDOM);
        int[] rowPermutationMap = rows.stream().mapToInt(Integer::intValue).toArray();
        List<Integer> columns = IntStream.range(0, columnNum).boxed().collect(Collectors.toList());
        Collections.shuffle(columns, SECURE_RANDOM);
        int[] columnPermutationMap = columns.stream().mapToInt(Integer::intValue).toArray();
        byte[][][] senderMatrix = new byte[rowNum][columnNum][];
        byte[][][] receiverMatrix = new byte[rowNum][columnNum][];
        for (int i = 0; i < rowNum; i++) {
            for (int j = 0; j < columnNum; j++) {
                senderMatrix[i][j] = BytesUtils.clone(senderFlat[i * columnNum + j]);
                receiverMatrix[i][j] = BytesUtils.clone(receiverFlat[i * columnNum + j]);
            }
        }
        return new TestInput(senderFlat, receiverFlat, senderMatrix, receiverMatrix, rowPermutationMap, columnPermutationMap);
    }

    private static class SenderThread extends Thread {
        private final RowShareRelationCarrierSender sender;
        private final byte[][][] inputMatrix;
        private final int[] rowPermutationMap;
        private final int[] columnPermutationMap;
        private SquareZ2Vector hitShare;
        private Throwable error;

        private SenderThread(RowShareRelationCarrierSender sender, byte[][][] inputMatrix, int[] rowPermutationMap,
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
                sender.init(inputMatrix.length, inputMatrix[0].length);
                hitShare = sender.rowShareRelation(inputMatrix, rowPermutationMap, columnPermutationMap, BYTE_LENGTH);
            } catch (MpcAbortException | RuntimeException e) {
                error = e;
            }
        }
    }

    private static class ReceiverThread extends Thread {
        private final RowShareRelationCarrierReceiver receiver;
        private final byte[][][] inputMatrix;
        private SquareZ2Vector hitShare;
        private Throwable error;

        private ReceiverThread(RowShareRelationCarrierReceiver receiver, byte[][][] inputMatrix) {
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
                receiver.init(inputMatrix.length, inputMatrix[0].length);
                hitShare = receiver.rowShareRelation(inputMatrix, BYTE_LENGTH, inputMatrix.length, inputMatrix[0].length);
            } catch (MpcAbortException | RuntimeException e) {
                error = e;
            }
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

}
