package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

/**
 * Label-output PM-PEQT receiver.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public interface LabelPmPeqtReceiver extends TwoPartyPto {

    /**
     * Initializes the protocol.
     *
     * @param maxRow          max row num.
     * @param maxColumn       max column num.
     * @param labelByteLength output label byte length.
     * @throws MpcAbortException the protocol failure aborts.
     */
    void init(int maxRow, int maxColumn, int labelByteLength) throws MpcAbortException;

    /**
     * Executes the protocol.
     *
     * @param inputMatrix receiver input matrix.
     * @param byteLength  element byte length.
     * @param row         row num.
     * @param column      column num.
     * @return receiver labels.
     * @throws MpcAbortException the protocol failure aborts.
     */
    byte[][] labelPmPeqt(byte[][][] inputMatrix, int byteLength, int row, int column) throws MpcAbortException;
}
