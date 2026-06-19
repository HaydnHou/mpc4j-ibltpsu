package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteMulEcc;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.AbstractFoldedSharePmPeqtSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.PtoStep;
import static edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.getInstance;

/**
 * TCL23 Byte-ECC-DDH based folded share-output PM-PEQT sender.
 *
 * <p>This keeps TCL23's cheap anonymous DDH carrier, but replaces receiver-local equality testing with share-output
 * PEQT. The sender holds the permuted receiver PRF digests, the receiver holds the permuted sender PRF digests, and
 * neither party receives a plaintext hit bit.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23ByteEccDdhFoldedSharePmPeqtSender extends AbstractFoldedSharePmPeqtSender {
    /**
     * ECC scalar beta.
     */
    private byte[] beta;
    /**
     * Byte ECC.
     */
    private ByteMulEcc ecc;
    /**
     * share-output PEQT sender.
     */
    private final PeqtParty peqtSender;
    /**
     * Z2 circuit sender for hidden OR folding.
     */
    private final Z2cParty z2cSender;
    /**
     * Whether to use compact PEQT digest byte length.
     */
    private final boolean compactPeqtByteLength;

    public Tcl23ByteEccDdhFoldedSharePmPeqtSender(Rpc senderRpc, Party receiverParty,
                                                  Tcl23ByteEccDdhFoldedSharePmPeqtConfig config) {
        super(getInstance(), senderRpc, receiverParty, config);
        peqtSender = PeqtFactory.createSender(senderRpc, receiverParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        z2cSender = Z2cFactory.createSender(senderRpc, receiverParty, config.getZ2cConfig());
        addSubPto(z2cSender);
        compactPeqtByteLength = config.isCompactPeqtByteLength();
    }

    @Override
    public void init(int maxRow, int maxColumn) throws MpcAbortException {
        setInitInput(maxRow, maxColumn);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int maxSize = maxRow * maxColumn;
        ecc = ByteEccFactory.createMulInstance(envType);
        beta = ecc.randomScalar(secureRandom);
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

        DataPacketHeader receiverPrfPayloadHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.RECEIVER_SEND_PRF.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> receiverPrfPayload = rpc.receive(receiverPrfPayloadHeader).getPayload();
        MpcAbortPreconditions.checkArgument(receiverPrfPayload.size() == row * column);

        stopWatch.start();
        byte[][] senderPrfs = computeSenderPrfs(inputMatrix);
        byte[][] receiverPrfDigests = computeReceiverPrfDigests(receiverPrfPayload);
        List<byte[]> permutedSenderPrfs = new ArrayList<>(row * column);
        byte[][] permutedReceiverPrfDigests = new byte[row * column][];
        int index = 0;
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                int source = rowPermutationMap[i] * column + columnPermutationMap[j];
                permutedSenderPrfs.add(senderPrfs[source]);
                permutedReceiverPrfDigests[index] = receiverPrfDigests[source];
                index++;
            }
        }
        DataPacketHeader senderPrfPayloadHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SENDER_SEND_PERMUTED_PRF.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(senderPrfPayloadHeader, permutedSenderPrfs));
        stopWatch.stop();
        long ddhTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 3, ddhTime, "sender computes anonymous DDH carrier");

        stopWatch.start();
        SquareZ2Vector hitShare = peqtSender.peqt(getPeqtBitLength(row * column), permutedReceiverPrfDigests);
        stopWatch.stop();
        long peqtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 3, peqtTime, "sender executes share-output PEQT");

        stopWatch.start();
        SquareZ2Vector foldedHitShare = foldHitShares(hitShare);
        stopWatch.stop();
        long foldTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 3, foldTime, "sender folds hit shares");

        logPhaseInfo(PtoState.PTO_END);
        return foldedHitShare;
    }

    private byte[][] computeSenderPrfs(byte[][][] inputMatrix) {
        List<byte[]> inputList = new ArrayList<>(row * column);
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < column; j++) {
                inputList.add(inputMatrix[i][j]);
            }
        }
        Stream<byte[]> inputStream = inputList.stream();
        inputStream = parallel ? inputStream.parallel() : inputStream;
        return inputStream
            .map(input -> ecc.hashToCurve(input))
            .map(hash -> ecc.mul(hash, beta))
            .toArray(byte[][]::new);
    }

    private byte[][] computeReceiverPrfDigests(List<byte[]> receiverPrfPayload) {
        Hash peqtHash = HashFactory.createInstance(envType, getPeqtByteLength(row * column));
        Stream<byte[]> receiverPrfsStream = receiverPrfPayload.stream();
        receiverPrfsStream = parallel ? receiverPrfsStream.parallel() : receiverPrfsStream;
        return receiverPrfsStream
            .map(hash -> ecc.mul(hash, beta))
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

    private int getPeqtByteLength(int size) {
        int bitLength = CommonConstants.STATS_BIT_LENGTH + 2 * LongUtils.ceilLog2((long) size);
        if (!compactPeqtByteLength) {
            bitLength += 7;
        }
        return CommonUtils.getByteLength(bitLength);
    }

    private int getPeqtBitLength(int size) {
        return getPeqtByteLength(size) * Byte.SIZE;
    }
}
