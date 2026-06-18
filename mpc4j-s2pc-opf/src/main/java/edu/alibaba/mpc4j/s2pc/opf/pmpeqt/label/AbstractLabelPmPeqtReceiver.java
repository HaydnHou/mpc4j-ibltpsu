package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

/**
 * Abstract label-output PM-PEQT receiver.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public abstract class AbstractLabelPmPeqtReceiver extends AbstractTwoPartyPto implements LabelPmPeqtReceiver {
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

    protected AbstractLabelPmPeqtReceiver(PtoDesc ptoDesc, Rpc receiverRpc, Party senderParty,
                                          LabelPmPeqtConfig config) {
        super(ptoDesc, receiverRpc, senderParty, config);
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

    protected void setPtoInput(byte[][][] inputMatrix, int byteLength, int row, int column) {
        checkInitialized();
        MathPreconditions.checkGreaterOrEqual("byteLength", byteLength, CommonConstants.STATS_BYTE_LENGTH);
        this.byteLength = byteLength;
        MathPreconditions.checkGreaterOrEqual("row", row, 1);
        MathPreconditions.checkLessOrEqual("row", row, maxRow);
        this.row = row;
        MathPreconditions.checkGreaterOrEqual("column", column, 1);
        MathPreconditions.checkLessOrEqual("column", column, maxColumn);
        this.column = column;
        MathPreconditions.checkGreater("row * column", row * column, 1);
        MathPreconditions.checkEqual("expected matrix row", "matrix row", row, inputMatrix.length);
        for (int i = 0; i < row; i++) {
            MathPreconditions.checkEqual("expected matrix column", "matrix column", column, inputMatrix[i].length);
        }
        extraInfo++;
    }
}
