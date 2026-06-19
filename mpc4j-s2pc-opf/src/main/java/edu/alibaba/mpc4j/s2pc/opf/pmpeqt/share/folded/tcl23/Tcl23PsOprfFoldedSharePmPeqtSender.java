package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnPartyOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.AbstractFoldedSharePmPeqtSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtPtoDesc.getInstance;

/**
 * TCL23 PS-OPRF based folded share-output PM-PEQT sender.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23PsOprfFoldedSharePmPeqtSender extends AbstractFoldedSharePmPeqtSender {
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
     * Z2 circuit sender for hidden OR folding.
     */
    private final Z2cParty z2cSender;

    public Tcl23PsOprfFoldedSharePmPeqtSender(Rpc senderRpc, Party receiverParty,
                                              Tcl23PsOprfFoldedSharePmPeqtConfig config) {
        super(getInstance(), senderRpc, receiverParty, config);
        dosnReceiver = DosnFactory.createReceiver(senderRpc, receiverParty, config.getOsnConfig());
        addSubPto(dosnReceiver);
        oprfSender = OprfFactory.createOprfSender(senderRpc, receiverParty, config.getOprfConfig());
        addSubPto(oprfSender);
        peqtSender = PeqtFactory.createSender(senderRpc, receiverParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        z2cSender = Z2cFactory.createSender(senderRpc, receiverParty, config.getZ2cConfig());
        addSubPto(z2cSender);
    }

    @Override
    public void init(int maxRow, int maxColumn) throws MpcAbortException {
        setInitInput(maxRow, maxColumn);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int maxSize = maxRow * maxColumn;
        dosnReceiver.init();
        oprfSender.init(maxSize);
        peqtSender.init(getPeqtBitLength(maxSize), maxSize);
        z2cSender.init(Math.max(1, (maxRow - 1) * maxColumn));
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public SquareZ2Vector foldedSharePmPeqt(byte[][][] inputMatrix, int[] rowPermutationMap,
                                            int[] columnPermutationMap, int byteLength)
        throws MpcAbortException {
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
        SquareZ2Vector foldedHitShare = foldHitShares(hitShare);
        stopWatch.stop();
        long foldTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 4, foldTime, "sender folds hit shares");

        logPhaseInfo(PtoState.PTO_END);
        return foldedHitShare;
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
            SquareZ2Vector[] foldedPairs = z2cSender.or(left, right);
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
