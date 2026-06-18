package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.crypto.crhf.CrhfFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnPartyOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.AbstractLabelPmPeqtSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.RotReceiverOutput;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtPtoDesc.PtoStep;
import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtPtoDesc.getInstance;

/**
 * TCL23 PS-OPRF based label-output PM-PEQT sender.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class Tcl23PsOprfLabelPmPeqtSender extends AbstractLabelPmPeqtSender {
    /**
     * OSN receiver.
     */
    private final DosnReceiver dosnReceiver;
    /**
     * OPRF sender.
     */
    private final OprfSender oprfSender;
    /**
     * share-output PEQT sender.
     */
    private final PeqtParty peqtSender;
    /**
     * COT receiver for label adapter.
     */
    private final CotReceiver cotReceiver;

    public Tcl23PsOprfLabelPmPeqtSender(Rpc senderRpc, Party receiverParty, Tcl23PsOprfLabelPmPeqtConfig config) {
        super(getInstance(), senderRpc, receiverParty, config);
        dosnReceiver = DosnFactory.createReceiver(senderRpc, receiverParty, config.getOsnConfig());
        addSubPto(dosnReceiver);
        oprfSender = OprfFactory.createOprfSender(senderRpc, receiverParty, config.getOprfConfig());
        addSubPto(oprfSender);
        peqtSender = PeqtFactory.createSender(senderRpc, receiverParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        cotReceiver = CotFactory.createReceiver(senderRpc, receiverParty, config.getCotConfig());
        addSubPto(cotReceiver);
    }

    @Override
    public void init(int maxRow, int maxColumn, int labelByteLength) throws MpcAbortException {
        setInitInput(maxRow, maxColumn, labelByteLength);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int maxSize = maxRow * maxColumn;
        dosnReceiver.init();
        oprfSender.init(maxSize);
        peqtSender.init(getPeqtBitLength(maxSize), maxSize);
        cotReceiver.init(maxSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public byte[][] labelPmPeqt(byte[][][] inputMatrix, int[] rowPermutationMap, int[] columnPermutationMap,
                                int byteLength) throws MpcAbortException {
        setPtoInput(inputMatrix, rowPermutationMap, columnPermutationMap, byteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        int[] permutationMap = generatePermutationMap(rowPermutationMap, columnPermutationMap);
        DosnPartyOutput dosnPartyOutput = dosnReceiver.dosn(permutationMap, byteLength);
        byte[][] shareMatrix = handleOsnOutput(inputMatrix, dosnPartyOutput, rowPermutationMap, columnPermutationMap);
        stopWatch.stop();
        long osnTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 4, osnTime, "sender executes OSN");

        stopWatch.start();
        OprfSenderOutput oprfSenderOutput = oprfSender.oprf(row * column);
        byte[][] tags = computePrf(shareMatrix, oprfSenderOutput);
        stopWatch.stop();
        long oprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 4, oprfTime, "sender computes OPRF tags");

        stopWatch.start();
        SquareZ2Vector hitShare = peqtSender.peqt(getPeqtBitLength(row * column), tags);
        stopWatch.stop();
        long peqtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 4, peqtTime, "sender executes share-output PEQT");

        stopWatch.start();
        byte[][] labels = receiveLabels(hitShare);
        stopWatch.stop();
        long labelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 4, labelTime, "sender receives labels");

        logPhaseInfo(PtoState.PTO_END);
        return labels;
    }

    private int[] generatePermutationMap(int[] rowPermutationMap, int[] columnPermutationMap) {
        int[] permutationMap = new int[row * column];
        for (int i = 0; i < row; ++i) {
            for (int j = 0; j < column; j++) {
                permutationMap[i * column + j] = rowPermutationMap[i] * column + columnPermutationMap[j];
            }
        }
        return permutationMap;
    }

    private byte[][] handleOsnOutput(byte[][][] inputMatrix, DosnPartyOutput dosnPartyOutput, int[] rowPermutationMap,
                                     int[] columnPermutationMap) {
        byte[][] shareMatrix = new byte[row * column][];
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                int index = i * column + j;
                shareMatrix[index] = BytesUtils.xor(
                    dosnPartyOutput.getShare(index), inputMatrix[rowPermutationMap[i]][columnPermutationMap[j]]
                );
            }
        }
        return shareMatrix;
    }

    private byte[][] computePrf(byte[][] itemArray, OprfSenderOutput oprfSenderOutput) {
        Hash peqtHash = HashFactory.createInstance(envType, getPeqtByteLength(row * column));
        IntStream intStream = IntStream.range(0, oprfSenderOutput.getBatchSize());
        intStream = parallel ? intStream.parallel() : intStream;
        return intStream
            .mapToObj(i -> oprfSenderOutput.getPrf(i, itemArray[i]))
            .map(peqtHash::digestToBytes)
            .toArray(byte[][]::new);
    }

    private byte[][] receiveLabels(SquareZ2Vector hitShare) throws MpcAbortException {
        int num = hitShare.getNum();
        BitVector hC = hitShare.getBitVector();
        boolean[] choices = new boolean[num];
        for (int i = 0; i < num; i++) {
            choices[i] = hC.get(i);
        }
        RotReceiverOutput rotReceiverOutput = new RotReceiverOutput(
            envType, CrhfFactory.CrhfType.MMO, cotReceiver.receive(choices)
        );
        List<byte[]> ciphertexts = receiveOtherPartyPayload(PtoStep.RECEIVER_SEND_LABEL_CIPHERTEXTS.ordinal());
        MpcAbortPreconditions.checkArgument(ciphertexts.size() == 2 * num);
        byte[][] labels = new byte[num][labelByteLength];
        for (int i = 0; i < num; i++) {
            byte[] selected = choices[i] ? ciphertexts.get(2 * i + 1) : ciphertexts.get(2 * i);
            labels[i] = BytesUtils.xor(selected, Arrays.copyOf(rotReceiverOutput.getRb(i), labelByteLength));
        }
        return labels;
    }

    private static int getPeqtByteLength(int size) {
        int bitLength = CommonConstants.STATS_BIT_LENGTH + 2 * LongUtils.ceilLog2((long) size) + 7;
        return CommonUtils.getByteLength(bitLength);
    }

    private static int getPeqtBitLength(int size) {
        return getPeqtByteLength(size) * Byte.SIZE;
    }
}
