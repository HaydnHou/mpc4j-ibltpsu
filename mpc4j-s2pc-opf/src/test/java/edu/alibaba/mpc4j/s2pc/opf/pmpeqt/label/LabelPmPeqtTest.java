package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Label-output PM-PEQT tests.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class LabelPmPeqtTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(LabelPmPeqtTest.class);
    /**
     * 39 * 42 = 1638, matching the current m = 2^10 padded-row gate.
     */
    private static final int ROW = 39;
    /**
     * 39 * 42 = 1638.
     */
    private static final int COLUMN = 42;
    /**
     * input byte length.
     */
    private static final int BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * label byte length.
     */
    private static final int LABEL_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;

    public LabelPmPeqtTest() {
        super(LabelPmPeqtTest.class.getSimpleName());
    }

    @Test
    public void testTcl23PsOprfLabelPmPeqt() throws InterruptedException {
        TestInput input = generateInput();
        Tcl23PsOprfLabelPmPeqtConfig config = new Tcl23PsOprfLabelPmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .build();
        LabelPmPeqtSender sender = LabelPmPeqtFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        LabelPmPeqtReceiver receiver = LabelPmPeqtFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
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
        long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        printAndResetRpc(time);

        Assert.assertNull(senderThread.getError());
        Assert.assertNull(receiverThread.getError());
        assertLabels(input, senderThread.getLabels(), receiverThread.getLabels());

        new Thread(sender::destroy).start();
        new Thread(receiver::destroy).start();
    }

    private void assertLabels(TestInput input, byte[][] senderLabels, byte[][] receiverLabels) {
        Assert.assertEquals(ROW * COLUMN, senderLabels.length);
        Assert.assertEquals(ROW * COLUMN, receiverLabels.length);
        int hitCount = 0;
        int missCount = 0;
        Set<String> hitSenderLabels = new HashSet<>();
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COLUMN; j++) {
                int index = i * COLUMN + j;
                boolean hit = expectedHit(input, i, j);
                boolean equal = Arrays.equals(senderLabels[index], receiverLabels[index]);
                if (hit) {
                    hitCount++;
                    Assert.assertFalse("hit row must not open: " + index, equal);
                    hitSenderLabels.add(Base64.getEncoder().encodeToString(senderLabels[index]));
                } else {
                    missCount++;
                    Assert.assertTrue("miss row must open: " + index, equal);
                }
            }
        }
        Assert.assertEquals("client-visible hit labels must not share one common value", hitCount, hitSenderLabels.size());
        LOGGER.info("Label-PM-PEQT: hit = {}, miss = {}", hitCount, missCount);
    }

    private boolean expectedHit(TestInput input, int rowIndex, int columnIndex) {
        int permutedIndex = input.rowPermutationMap[rowIndex] * COLUMN + input.columnPermutationMap[columnIndex];
        byte[] sender = input.senderFlat[permutedIndex];
        byte[] receiver = input.receiverFlat[permutedIndex];
        return BytesUtils.equals(sender, receiver);
    }

    private TestInput generateInput() {
        byte[][] senderFlat = new byte[ROW * COLUMN][BYTE_LENGTH];
        byte[][] receiverFlat = new byte[ROW * COLUMN][BYTE_LENGTH];
        for (int i = 0; i < ROW * COLUMN / 2; i++) {
            SECURE_RANDOM.nextBytes(senderFlat[i]);
            SECURE_RANDOM.nextBytes(receiverFlat[i]);
        }
        for (int i = ROW * COLUMN / 2; i < ROW * COLUMN; i++) {
            SECURE_RANDOM.nextBytes(senderFlat[i]);
            receiverFlat[i] = BytesUtils.clone(senderFlat[i]);
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

    private static class SenderThread extends Thread {
        private final LabelPmPeqtSender sender;
        private final byte[][][] inputMatrix;
        private final int[] rowPermutationMap;
        private final int[] columnPermutationMap;
        private byte[][] labels;
        private Throwable error;

        private SenderThread(LabelPmPeqtSender sender, byte[][][] inputMatrix, int[] rowPermutationMap,
                             int[] columnPermutationMap) {
            this.sender = sender;
            this.inputMatrix = inputMatrix;
            this.rowPermutationMap = rowPermutationMap;
            this.columnPermutationMap = columnPermutationMap;
        }

        private byte[][] getLabels() {
            return labels;
        }

        private Throwable getError() {
            return error;
        }

        @Override
        public void run() {
            try {
                sender.init(ROW, COLUMN, LABEL_BYTE_LENGTH);
                labels = sender.labelPmPeqt(inputMatrix, rowPermutationMap, columnPermutationMap, BYTE_LENGTH);
            } catch (MpcAbortException | RuntimeException e) {
                error = e;
            }
        }
    }

    private static class ReceiverThread extends Thread {
        private final LabelPmPeqtReceiver receiver;
        private final byte[][][] inputMatrix;
        private byte[][] labels;
        private Throwable error;

        private ReceiverThread(LabelPmPeqtReceiver receiver, byte[][][] inputMatrix) {
            this.receiver = receiver;
            this.inputMatrix = inputMatrix;
        }

        private byte[][] getLabels() {
            return labels;
        }

        private Throwable getError() {
            return error;
        }

        @Override
        public void run() {
            try {
                receiver.init(ROW, COLUMN, LABEL_BYTE_LENGTH);
                labels = receiver.labelPmPeqt(inputMatrix, BYTE_LENGTH, ROW, COLUMN);
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
