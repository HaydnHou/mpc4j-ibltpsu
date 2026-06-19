package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

/**
 * Abstract folded share-output PM-PEQT receiver.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public abstract class AbstractFoldedSharePmPeqtReceiver extends AbstractTwoPartyPto
    implements FoldedSharePmPeqtReceiver {
    /**
     * max row num.
     */
    protected int maxRow;
    /**
     * max column num.
     */
    protected int maxColumn;
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

    protected AbstractFoldedSharePmPeqtReceiver(PtoDesc ptoDesc, Rpc receiverRpc, Party senderParty,
                                                FoldedSharePmPeqtConfig config) {
        super(ptoDesc, receiverRpc, senderParty, config);
    }

    protected void setInitInput(int maxRow, int maxColumn) {
        MathPreconditions.checkGreater("maxRow * maxColumn", maxRow * maxColumn, 1);
        this.maxRow = maxRow;
        this.maxColumn = maxColumn;
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
