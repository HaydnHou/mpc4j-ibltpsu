package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

/**
 * Abstract label-output PM-PEQT sender.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public abstract class AbstractLabelPmPeqtSender extends AbstractTwoPartyPto implements LabelPmPeqtSender {
    /**
     * max row num.
     */
    protected int maxRow;
    /**
     * max column num.
     */
    protected int maxColumn;
    /**
     * label byte length.
     */
    protected int labelByteLength;
    /**
     * input byte length.
     */
    protected int byteLength;
    /**
     * row num.
     */
    protected int row;
    /**
     * column num.
     */
    protected int column;

    protected AbstractLabelPmPeqtSender(PtoDesc ptoDesc, Rpc senderRpc, Party receiverParty,
                                        LabelPmPeqtConfig config) {
        super(ptoDesc, senderRpc, receiverParty, config);
    }

    protected void setInitInput(int maxRow, int maxColumn, int labelByteLength) {
        MathPreconditions.checkGreater("maxRow * maxColumn", maxRow * maxColumn, 1);
        MathPreconditions.checkEqual(
            "labelByteLength", "block byte length", labelByteLength, CommonConstants.BLOCK_BYTE_LENGTH
        );
        this.maxRow = maxRow;
        this.maxColumn = maxColumn;
        this.labelByteLength = labelByteLength;
        extraInfo++;
        initState();
    }

    protected void setPtoInput(byte[][][] inputMatrix, int[] rowPermutationMap, int[] columnPermutationMap,
                               int byteLength) {
        checkInitialized();
        MathPreconditions.checkGreaterOrEqual("byteLength", byteLength, CommonConstants.STATS_BYTE_LENGTH);
        this.byteLength = byteLength;
        MathPreconditions.checkGreaterOrEqual("row", rowPermutationMap.length, 1);
        MathPreconditions.checkLessOrEqual("row", rowPermutationMap.length, maxRow);
        this.row = rowPermutationMap.length;
        MathPreconditions.checkGreaterOrEqual("column", columnPermutationMap.length, 1);
        MathPreconditions.checkLessOrEqual("column", columnPermutationMap.length, maxColumn);
        this.column = columnPermutationMap.length;
        MathPreconditions.checkGreater("row * column", row * column, 1);
        MathPreconditions.checkEqual("expected matrix row", "matrix row", row, inputMatrix.length);
        for (int i = 0; i < row; i++) {
            MathPreconditions.checkEqual("expected matrix column", "matrix column", column, inputMatrix[i].length);
        }
        extraInfo++;
    }
}
