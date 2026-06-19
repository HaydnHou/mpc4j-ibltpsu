package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;

/**
 * Folded share-output PM-PEQT sender.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public interface FoldedSharePmPeqtSender extends TwoPartyPto {
    /**
     * Initializes the protocol.
     *
     * @param maxRow    max row num.
     * @param maxColumn max column num.
     * @throws MpcAbortException the protocol failure aborts.
     */
    void init(int maxRow, int maxColumn) throws MpcAbortException;

    /**
     * Runs folded share-output PM-PEQT.
     *
     * @param inputMatrix          sender input matrix.
     * @param rowPermutationMap    anonymous row permutation map.
     * @param columnPermutationMap anonymous column permutation map.
     * @param byteLength           input byte length.
     * @return sender share of per-column OR-hit bits.
     * @throws MpcAbortException the protocol failure aborts.
     */
    SquareZ2Vector foldedSharePmPeqt(byte[][][] inputMatrix, int[] rowPermutationMap, int[] columnPermutationMap,
                                     int byteLength) throws MpcAbortException;
}
