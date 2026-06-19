package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;

/**
 * Row-level share relation carrier receiver.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public interface RowShareRelationCarrierReceiver extends TwoPartyPto {
    /**
     * Initializes the carrier.
     *
     * @param maxRow    max internal row num.
     * @param maxColumn max anonymous row / column num.
     * @throws MpcAbortException the protocol failure aborts.
     */
    void init(int maxRow, int maxColumn) throws MpcAbortException;

    /**
     * Runs the row-level share relation carrier.
     *
     * @param inputMatrix receiver relation input matrix.
     * @param byteLength  input byte length.
     * @param row         internal row num.
     * @param column      anonymous row num.
     * @return receiver share of folded hit bits, one bit per anonymous row.
     * @throws MpcAbortException the protocol failure aborts.
     */
    SquareZ2Vector rowShareRelation(byte[][][] inputMatrix, int byteLength, int row, int column)
        throws MpcAbortException;
}
