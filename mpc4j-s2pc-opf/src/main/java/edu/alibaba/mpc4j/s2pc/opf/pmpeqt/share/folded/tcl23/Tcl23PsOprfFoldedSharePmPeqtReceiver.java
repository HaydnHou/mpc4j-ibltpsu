package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnPartyOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.AbstractFoldedSharePmPeqtReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtPtoDesc.getInstance;

/**
 * TCL23 PS-OPRF based folded share-output PM-PEQT receiver.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23PsOprfFoldedSharePmPeqtReceiver extends AbstractFoldedSharePmPeqtReceiver {
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
     * Z2 circuit receiver for hidden OR folding.
     */
    private final Z2cParty z2cReceiver;

    public Tcl23PsOprfFoldedSharePmPeqtReceiver(Rpc receiverRpc, Party senderParty,
                                                Tcl23PsOprfFoldedSharePmPeqtConfig config) {
        super(getInstance(), receiverRpc, senderParty, config);
        dosnSender = DosnFactory.createSender(receiverRpc, senderParty, config.getOsnConfig());
        addSubPto(dosnSender);
        oprfReceiver = OprfFactory.createOprfReceiver(receiverRpc, senderParty, config.getOprfConfig());
        addSubPto(oprfReceiver);
        peqtReceiver = PeqtFactory.createReceiver(receiverRpc, senderParty, config.getPeqtConfig());
        addSubPto(peqtReceiver);
        z2cReceiver = Z2cFactory.createReceiver(receiverRpc, senderParty, config.getZ2cConfig());
        addSubPto(z2cReceiver);
    }

    @Override
    public void init(int maxRow, int maxColumn) throws MpcAbortException {
        setInitInput(maxRow, maxColumn);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int maxSize = maxRow * maxColumn;
        dosnSender.init();
        oprfReceiver.init(maxSize);
        peqtReceiver.init(getPeqtBitLength(maxSize), maxSize);
        z2cReceiver.init(Math.max(1, (maxRow - 1) * maxColumn));
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public SquareZ2Vector foldedSharePmPeqt(byte[][][] inputMatrix, int byteLength, int row, int column)
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
        SquareZ2Vector foldedHitShare = foldHitShares(hitShare);
        stopWatch.stop();
        long foldTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 4, foldTime, "receiver folds hit shares");

        logPhaseInfo(PtoState.PTO_END);
        return foldedHitShare;
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

    private SquareZ2Vector foldHitShares(SquareZ2Vector hitShare) throws MpcAbortException {
        List<SquareZ2Vector> level = new ArrayList<>(row);
        for (int i = 0; i < row; i++) {
            level.add(hitShare.getPointsWithFixedSpace(i * column, column, 1));
        }
        while (level.size() > 1) {
            int pairNum = level.size() / 2;
            SquareZ2Vector[] left = new SquareZ2Vector[pairNum];
            SquareZ2Vector[] right = new SquareZ2Vector[pairNum];
            for (int i = 0; i < pairNum; i++) {
                left[i] = level.get(2 * i);
                right[i] = level.get(2 * i + 1);
            }
            SquareZ2Vector[] foldedPairs = z2cReceiver.or(left, right);
            List<SquareZ2Vector> next = new ArrayList<>((level.size() + 1) / 2);
            next.addAll(Arrays.asList(foldedPairs));
            if ((level.size() & 1) == 1) {
                next.add(level.get(level.size() - 1));
            }
            level = next;
        }
        return level.get(0);
    }

    private static int getPeqtByteLength(int size) {
        int bitLength = CommonConstants.STATS_BIT_LENGTH + 2 * LongUtils.ceilLog2((long) size) + 7;
        return CommonUtils.getByteLength(bitLength);
    }

    private static int getPeqtBitLength(int size) {
        return getPeqtByteLength(size) * Byte.SIZE;
    }
}
