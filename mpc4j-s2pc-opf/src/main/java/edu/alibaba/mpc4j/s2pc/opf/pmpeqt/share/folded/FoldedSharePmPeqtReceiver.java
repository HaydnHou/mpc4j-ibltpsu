package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;

/**
 * Folded share-output PM-PEQT receiver.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public interface FoldedSharePmPeqtReceiver extends TwoPartyPto {
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
     * @param inputMatrix receiver input matrix.
     * @param byteLength  input byte length.
     * @param row         row num.
     * @param column      column num.
     * @return receiver share of per-column OR-hit bits.
     * @throws MpcAbortException the protocol failure aborts.
     */
    SquareZ2Vector foldedSharePmPeqt(byte[][][] inputMatrix, int byteLength, int row, int column)
        throws MpcAbortException;
}
