package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

/**
 * Label-output PM-PEQT sender.
 *
 * <p>The sender obtains one label per permuted matrix position. The labels are intended as relation-to-openable-pad
 * material: for a miss row the sender label equals the receiver label, and for a hit row the two labels differ.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public interface LabelPmPeqtSender extends TwoPartyPto {

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
     * @param inputMatrix          sender input matrix.
     * @param rowPermutationMap    row permutation map.
     * @param columnPermutationMap column permutation map.
     * @param byteLength           element byte length.
     * @return sender labels.
     * @throws MpcAbortException the protocol failure aborts.
     */
    byte[][] labelPmPeqt(byte[][][] inputMatrix, int[] rowPermutationMap, int[] columnPermutationMap, int byteLength)
        throws MpcAbortException;
}
