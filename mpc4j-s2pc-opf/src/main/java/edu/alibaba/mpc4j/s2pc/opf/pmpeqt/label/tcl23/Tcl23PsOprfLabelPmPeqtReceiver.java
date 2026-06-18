package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.crypto.crhf.CrhfFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnPartyOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.AbstractLabelPmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.RotSenderOutput;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtPtoDesc.PtoStep;
import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtPtoDesc.getInstance;

/**
 * TCL23 PS-OPRF based label-output PM-PEQT receiver.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class Tcl23PsOprfLabelPmPeqtReceiver extends AbstractLabelPmPeqtReceiver {
    /**
     * OSN sender.
     */
    private final DosnSender dosnSender;
    /**
     * OPRF receiver.
     */
    private final OprfReceiver oprfReceiver;
    /**
     * share-output PEQT receiver.
     */
    private final PeqtParty peqtReceiver;
    /**
     * COT sender for label adapter.
     */
    private final CotSender cotSender;

    public Tcl23PsOprfLabelPmPeqtReceiver(Rpc receiverRpc, Party senderParty, Tcl23PsOprfLabelPmPeqtConfig config) {
        super(getInstance(), receiverRpc, senderParty, config);
        dosnSender = DosnFactory.createSender(receiverRpc, senderParty, config.getOsnConfig());
        addSubPto(dosnSender);
        oprfReceiver = OprfFactory.createOprfReceiver(receiverRpc, senderParty, config.getOprfConfig());
        addSubPto(oprfReceiver);
        peqtReceiver = PeqtFactory.createReceiver(receiverRpc, senderParty, config.getPeqtConfig());
        addSubPto(peqtReceiver);
        cotSender = CotFactory.createSender(receiverRpc, senderParty, config.getCotConfig());
        addSubPto(cotSender);
    }

    @Override
    public void init(int maxRow, int maxColumn, int labelByteLength) throws MpcAbortException {
        setInitInput(maxRow, maxColumn, labelByteLength);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int maxSize = maxRow * maxColumn;
        dosnSender.init();
        oprfReceiver.init(maxSize);
        peqtReceiver.init(getPeqtBitLength(maxSize), maxSize);
        cotSender.init(BlockUtils.randomBlock(secureRandom), maxSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public byte[][] labelPmPeqt(byte[][][] inputMatrix, int byteLength, int row, int column)
        throws MpcAbortException {
        setPtoInput(inputMatrix, byteLength, row, column);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        byte[][] osnInputVector = generateOsnInputVector(inputMatrix);
        DosnPartyOutput dosnPartyOutput = dosnSender.dosn(osnInputVector, byteLength);
        byte[][] oprfInputs = handleOsnOutput(dosnPartyOutput);
        stopWatch.stop();
        long osnTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 4, osnTime, "receiver executes OSN");

        stopWatch.start();
        OprfReceiverOutput oprfReceiverOutput = oprfReceiver.oprf(oprfInputs);
        byte[][] tags = computePrf(oprfReceiverOutput);
        stopWatch.stop();
        long oprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 4, oprfTime, "receiver computes OPRF tags");

        stopWatch.start();
        SquareZ2Vector hitShare = peqtReceiver.peqt(getPeqtBitLength(row * column), tags);
        stopWatch.stop();
        long peqtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 4, peqtTime, "receiver executes share-output PEQT");

        stopWatch.start();
        byte[][] labels = sendLabels(hitShare);
        stopWatch.stop();
        long labelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 4, labelTime, "receiver sends labels");

        logPhaseInfo(PtoState.PTO_END);
        return labels;
    }

    private byte[][] generateOsnInputVector(byte[][][] inputMatrix) {
        byte[][] payload = new byte[row * column][];
        int index = 0;
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                payload[index] = inputMatrix[i][j];
                index++;
            }
        }
        return payload;
    }

    private byte[][] handleOsnOutput(DosnPartyOutput dosnPartyOutput) {
        return IntStream.range(0, row * column)
            .mapToObj(dosnPartyOutput::getShare)
            .toArray(byte[][]::new);
    }

    private byte[][] computePrf(OprfReceiverOutput oprfReceiverOutput) {
        Hash peqtHash = HashFactory.createInstance(envType, getPeqtByteLength(row * column));
        IntStream intStream = IntStream.range(0, row * column);
        intStream = parallel ? intStream.parallel() : intStream;
        return intStream
            .mapToObj(oprfReceiverOutput::getPrf)
            .map(peqtHash::digestToBytes)
            .toArray(byte[][]::new);
    }

    private byte[][] sendLabels(SquareZ2Vector hitShare) throws MpcAbortException {
        int num = hitShare.getNum();
        RotSenderOutput rotSenderOutput = new RotSenderOutput(
            envType, CrhfFactory.CrhfType.MMO, cotSender.send(num)
        );
        BitVector hS = hitShare.getBitVector();
        byte[][] labels = new byte[num][labelByteLength];
        List<byte[]> ciphertexts = IntStream.range(0, num)
            .mapToObj(i -> {
                boolean missShare = hS.get(i) ^ true;
                byte[] l0 = randomLabel();
                byte[] l1 = randomLabel();
                labels[i] = BytesUtils.clone(l1);
                byte[] msg0 = missShare ? l1 : l0;
                byte[] msg1 = missShare ? l0 : l1;
                byte[] c0 = BytesUtils.xor(msg0, Arrays.copyOf(rotSenderOutput.getR0(i), labelByteLength));
                byte[] c1 = BytesUtils.xor(msg1, Arrays.copyOf(rotSenderOutput.getR1(i), labelByteLength));
                return new byte[][]{c0, c1};
            })
            .flatMap(Arrays::stream)
            .collect(Collectors.toList());
        sendOtherPartyPayload(PtoStep.RECEIVER_SEND_LABEL_CIPHERTEXTS.ordinal(), ciphertexts);
        return labels;
    }

    private byte[] randomLabel() {
        byte[] label = new byte[labelByteLength];
        secureRandom.nextBytes(label);
        return label;
    }

    private static int getPeqtByteLength(int size) {
        int bitLength = CommonConstants.STATS_BIT_LENGTH + 2 * LongUtils.ceilLog2((long) size) + 7;
        return CommonUtils.getByteLength(bitLength);
    }

    private static int getPeqtBitLength(int size) {
        return getPeqtByteLength(size) * Byte.SIZE;
    }
}
