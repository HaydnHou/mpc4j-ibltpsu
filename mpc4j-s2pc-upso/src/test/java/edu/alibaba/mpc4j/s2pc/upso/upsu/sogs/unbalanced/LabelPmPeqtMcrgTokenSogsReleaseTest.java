package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtSender;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * End-to-end gate from Label-PM-PEQT labels to MCRG-token SOGS release.
 *
 * <p>This is not a full UPSU protocol. It checks the concrete composition boundary:
 * Label-PM-PEQT creates pads satisfying miss: u_i = v_i, hit: u_i != v_i; the existing fixed-record SOGS release then
 * peels exactly the sender-side miss payloads.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class LabelPmPeqtMcrgTokenSogsReleaseTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(LabelPmPeqtMcrgTokenSogsReleaseTest.class);
    /**
     * 39 * 42 = 1638.
     */
    private static final int ROW = 39;
    /**
     * 39 * 42 = 1638.
     */
    private static final int COLUMN = 42;
    /**
     * Padded row count.
     */
    private static final int ROW_NUM = ROW * COLUMN;
    /**
     * Sender real rows.
     */
    private static final int M = 1024;
    /**
     * Input and payload byte length.
     */
    private static final int BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * Label byte length.
     */
    private static final int LABEL_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * SOGS cell number.
     */
    private static final int CELL_NUM = 4096;
    /**
     * SOGS degree.
     */
    private static final int DEGREE = 3;

    public LabelPmPeqtMcrgTokenSogsReleaseTest() {
        super(LabelPmPeqtMcrgTokenSogsReleaseTest.class.getSimpleName());
    }

    @Test
    public void testLabelPmPeqtToSogsRelease() throws Exception {
        CaseData data = createCase();
        Tcl23PsOprfLabelPmPeqtConfig labelConfig = new Tcl23PsOprfLabelPmPeqtConfig.Builder()
            .setPeqtConfig(new Cgs22PeqtConfig.Builder(SecurityModel.SEMI_HONEST, false).setM(4).build())
            .build();
        LabelPmPeqtSender labelSender = LabelPmPeqtFactory.createSender(firstRpc, secondRpc.ownParty(), labelConfig);
        LabelPmPeqtReceiver labelReceiver = LabelPmPeqtFactory.createReceiver(secondRpc, firstRpc.ownParty(), labelConfig);
        int labelTaskId = Math.abs(SECURE_RANDOM.nextInt());
        labelSender.setTaskId(labelTaskId);
        labelReceiver.setTaskId(labelTaskId);

        LabelSenderThread labelSenderThread = new LabelSenderThread(labelSender, data);
        LabelReceiverThread labelReceiverThread = new LabelReceiverThread(labelReceiver, data.receiverMatrix);
        firstRpc.reset();
        secondRpc.reset();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        labelSenderThread.start();
        labelReceiverThread.start();
        labelSenderThread.join();
        labelReceiverThread.join();
        stopWatch.stop();
        long labelTimeMs = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        long labelBytes = firstRpc.getPayloadByteLength() + secondRpc.getPayloadByteLength();
        printAndResetRpc(labelTimeMs);
        labelSenderThread.check();
        labelReceiverThread.check();

        McrgTokenSogsPadCarrierSenderOutput senderOutput = toSenderOutput(data, labelSenderThread.labels);
        McrgTokenSogsPadCarrierReceiverOutput receiverOutput =
            new McrgTokenSogsPadCarrierReceiverOutput(labelReceiverThread.labels);
        McrgTokenSogsReleaseConfig releaseConfig = new McrgTokenSogsReleaseConfig.Builder().build();
        McrgTokenSogsReleaseSender releaseSender =
            new McrgTokenSogsReleaseSender(firstRpc, secondRpc.ownParty(), releaseConfig);
        McrgTokenSogsReleaseReceiver releaseReceiver =
            new McrgTokenSogsReleaseReceiver(secondRpc, firstRpc.ownParty(), releaseConfig);
        int releaseTaskId = Math.abs(SECURE_RANDOM.nextInt());
        releaseSender.setTaskId(releaseTaskId);
        releaseReceiver.setTaskId(releaseTaskId);
        releaseSender.init();
        releaseReceiver.init();

        ReleaseSenderThread releaseSenderThread = new ReleaseSenderThread(releaseSender, senderOutput);
        ReleaseReceiverThread releaseReceiverThread = new ReleaseReceiverThread(releaseReceiver, receiverOutput);
        firstRpc.reset();
        secondRpc.reset();
        stopWatch.start();
        releaseSenderThread.start();
        releaseReceiverThread.start();
        releaseSenderThread.join();
        releaseReceiverThread.join();
        stopWatch.stop();
        long releaseTimeMs = stopWatch.getTime(TimeUnit.MILLISECONDS);
        long releaseBytes = firstRpc.getPayloadByteLength() + secondRpc.getPayloadByteLength();
        printAndResetRpc(releaseTimeMs);
        releaseSenderThread.check();
        releaseReceiverThread.check();

        Assert.assertTrue(releaseReceiverThread.peelResult.success);
        Assert.assertEquals(0, releaseReceiverThread.peelResult.residualCells);
        Assert.assertEquals(data.expectedMisses, releaseReceiverThread.peelResult.recovered);
        Assert.assertEquals(M / 2, data.expectedMisses.size());

        long totalBytes = labelBytes + releaseBytes;
        long totalTimeMs = labelTimeMs + releaseTimeMs;
        LOGGER.info(
            "Label-PM-PEQT -> MCRG-token SOGS: label={}ms/{}B, release={}ms/{}B, total={}ms/{}B",
            labelTimeMs, labelBytes, releaseTimeMs, releaseBytes, totalTimeMs, totalBytes
        );

        new Thread(labelSender::destroy).start();
        new Thread(labelReceiver::destroy).start();
        new Thread(releaseSender::destroy).start();
        new Thread(releaseReceiver::destroy).start();
    }

    private static McrgTokenSogsPadCarrierSenderOutput toSenderOutput(CaseData data, byte[][] uLabels) {
        byte[][] payloads = new byte[ROW_NUM][BYTE_LENGTH];
        boolean[] realRowBits = new boolean[ROW_NUM];
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COLUMN; j++) {
                int labelIndex = i * COLUMN + j;
                int sourceIndex = data.rowPermutationMap[i] * COLUMN + data.columnPermutationMap[j];
                payloads[labelIndex] = BytesUtils.clone(data.senderFlat[sourceIndex]);
                realRowBits[labelIndex] = data.realFlat[sourceIndex];
            }
        }
        return new McrgTokenSogsPadCarrierSenderOutput(uLabels, realRowBits, payloads);
    }

    private static CaseData createCase() {
        SecureRandom secureRandom = new SecureRandom();
        byte[][] senderFlat = new byte[ROW_NUM][BYTE_LENGTH];
        byte[][] receiverFlat = new byte[ROW_NUM][BYTE_LENGTH];
        boolean[] realFlat = new boolean[ROW_NUM];
        boolean[] hitFlat = new boolean[ROW_NUM];
        for (int i = 0; i < ROW_NUM; i++) {
            secureRandom.nextBytes(senderFlat[i]);
            secureRandom.nextBytes(receiverFlat[i]);
        }
        List<Integer> rows = new ArrayList<>(ROW_NUM);
        for (int i = 0; i < ROW_NUM; i++) {
            rows.add(i);
        }
        Collections.shuffle(rows, secureRandom);
        Set<ByteBuffer> expectedMisses = new HashSet<>();
        for (int itemIndex = 0; itemIndex < M; itemIndex++) {
            int flatIndex = rows.get(itemIndex);
            realFlat[flatIndex] = true;
            secureRandom.nextBytes(senderFlat[flatIndex]);
            if (itemIndex < M / 2) {
                receiverFlat[flatIndex] = BytesUtils.clone(senderFlat[flatIndex]);
                hitFlat[flatIndex] = true;
            } else {
                do {
                    secureRandom.nextBytes(receiverFlat[flatIndex]);
                } while (BytesUtils.equals(senderFlat[flatIndex], receiverFlat[flatIndex]));
                expectedMisses.add(ByteBuffer.wrap(BytesUtils.clone(senderFlat[flatIndex])));
            }
        }
        List<Integer> rowPermutation = IntStream.range(0, ROW).boxed().collect(Collectors.toList());
        Collections.shuffle(rowPermutation, secureRandom);
        int[] rowPermutationMap = rowPermutation.stream().mapToInt(Integer::intValue).toArray();
        List<Integer> columnPermutation = IntStream.range(0, COLUMN).boxed().collect(Collectors.toList());
        Collections.shuffle(columnPermutation, secureRandom);
        int[] columnPermutationMap = columnPermutation.stream().mapToInt(Integer::intValue).toArray();
        byte[][][] senderMatrix = matrix(senderFlat);
        byte[][][] receiverMatrix = matrix(receiverFlat);
        return new CaseData(
            senderFlat, receiverFlat, realFlat, hitFlat, senderMatrix, receiverMatrix,
            rowPermutationMap, columnPermutationMap, expectedMisses
        );
    }

    private static byte[][][] matrix(byte[][] flat) {
        byte[][][] matrix = new byte[ROW][COLUMN][];
        for (int i = 0; i < ROW; i++) {
            for (int j = 0; j < COLUMN; j++) {
                matrix[i][j] = BytesUtils.clone(flat[i * COLUMN + j]);
            }
        }
        return matrix;
    }

    private static class LabelSenderThread extends Thread {
        private final LabelPmPeqtSender sender;
        private final CaseData data;
        private byte[][] labels;
        private Exception exception;

        private LabelSenderThread(LabelPmPeqtSender sender, CaseData data) {
            this.sender = sender;
            this.data = data;
        }

        @Override
        public void run() {
            try {
                sender.init(ROW, COLUMN, LABEL_BYTE_LENGTH);
                labels = sender.labelPmPeqt(
                    data.senderMatrix, data.rowPermutationMap, data.columnPermutationMap, BYTE_LENGTH
                );
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

    private static class LabelReceiverThread extends Thread {
        private final LabelPmPeqtReceiver receiver;
        private final byte[][][] receiverMatrix;
        private byte[][] labels;
        private Exception exception;

        private LabelReceiverThread(LabelPmPeqtReceiver receiver, byte[][][] receiverMatrix) {
            this.receiver = receiver;
            this.receiverMatrix = receiverMatrix;
        }

        @Override
        public void run() {
            try {
                receiver.init(ROW, COLUMN, LABEL_BYTE_LENGTH);
                labels = receiver.labelPmPeqt(receiverMatrix, BYTE_LENGTH, ROW, COLUMN);
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

    private static class ReleaseSenderThread extends Thread {
        private final McrgTokenSogsReleaseSender sender;
        private final McrgTokenSogsPadCarrierSenderOutput output;
        private Exception exception;

        private ReleaseSenderThread(McrgTokenSogsReleaseSender sender, McrgTokenSogsPadCarrierSenderOutput output) {
            this.sender = sender;
            this.output = output;
        }

        @Override
        public void run() {
            try {
                sender.send(output);
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

    private static class ReleaseReceiverThread extends Thread {
        private final McrgTokenSogsReleaseReceiver receiver;
        private final McrgTokenSogsPadCarrierReceiverOutput output;
        private TokenKeyedSogsSketch.PeelResult peelResult;
        private Exception exception;

        private ReleaseReceiverThread(McrgTokenSogsReleaseReceiver receiver,
                                      McrgTokenSogsPadCarrierReceiverOutput output) {
            this.receiver = receiver;
            this.output = output;
        }

        @Override
        public void run() {
            try {
                peelResult = receiver.receive(output, BYTE_LENGTH, CELL_NUM, DEGREE);
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
        private final byte[][] senderFlat;
        @SuppressWarnings("unused")
        private final byte[][] receiverFlat;
        private final boolean[] realFlat;
        @SuppressWarnings("unused")
        private final boolean[] hitFlat;
        private final byte[][][] senderMatrix;
        private final byte[][][] receiverMatrix;
        private final int[] rowPermutationMap;
        private final int[] columnPermutationMap;
        private final Set<ByteBuffer> expectedMisses;

        private CaseData(
            byte[][] senderFlat, byte[][] receiverFlat, boolean[] realFlat, boolean[] hitFlat,
            byte[][][] senderMatrix, byte[][][] receiverMatrix, int[] rowPermutationMap, int[] columnPermutationMap,
            Set<ByteBuffer> expectedMisses
        ) {
            this.senderFlat = senderFlat;
            this.receiverFlat = receiverFlat;
            this.realFlat = realFlat;
            this.hitFlat = hitFlat;
            this.senderMatrix = senderMatrix;
            this.receiverMatrix = receiverMatrix;
            this.rowPermutationMap = rowPermutationMap;
            this.columnPermutationMap = columnPermutationMap;
            this.expectedMisses = expectedMisses;
        }
    }
}
