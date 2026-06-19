package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

/**
 * Abstract folded share-output PM-PEQT sender.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public abstract class AbstractFoldedSharePmPeqtSender extends AbstractTwoPartyPto
    implements FoldedSharePmPeqtSender {
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

    protected AbstractFoldedSharePmPeqtSender(PtoDesc ptoDesc, Rpc senderRpc, Party receiverParty,
                                              FoldedSharePmPeqtConfig config) {
        super(ptoDesc, senderRpc, receiverParty, config);
    }

    protected void setInitInput(int maxRow, int maxColumn) {
        MathPreconditions.checkGreater("maxRow * maxColumn", maxRow * maxColumn, 1);
        this.maxRow = maxRow;
        this.maxColumn = maxColumn;
        extraInfo++;
        initState();
    }

    protected void setPtoInput(byte[][][] inputMatrix, int[] rowPermutationMap, int[] columnPermutationMap,
                               int byteLength) {
        checkInitialized();
        MathPreconditions.checkGreaterOrEqual("byteLength", byteLength, CommonConstants.STATS_BYTE_LENGTH);
        this.byteLength = byteLength;
        int rowNum = rowPermutationMap.length;
        MathPreconditions.checkGreaterOrEqual("row", rowNum, 1);
        MathPreconditions.checkLessOrEqual("row", rowNum, maxRow);
        int columnNum = columnPermutationMap.length;
        MathPreconditions.checkGreaterOrEqual("column", columnNum, 1);
        MathPreconditions.checkLessOrEqual("column", columnNum, maxColumn);
        checkPermutation("rowPermutationMap", rowPermutationMap, rowNum);
        checkPermutation("columnPermutationMap", columnPermutationMap, columnNum);
        row = rowNum;
        column = columnNum;
        MathPreconditions.checkGreater("row * column", row * column, 1);
        MathPreconditions.checkEqual("expected matrix row", "matrix row", row, inputMatrix.length);
        for (int i = 0; i < row; i++) {
            MathPreconditions.checkEqual("expected matrix column", "matrix column", column, inputMatrix[i].length);
        }
        extraInfo++;
    }

    private static void checkPermutation(String name, int[] permutationMap, int size) {
        boolean[] seen = new boolean[size];
        for (int index : permutationMap) {
            if (index < 0 || index >= size) {
                throw new IllegalArgumentException(name + " must contain values in [0, " + size + "): " + index);
            }
            if (seen[index]) {
                throw new IllegalArgumentException(name + " must not contain duplicate value: " + index);
            }
            seen[index] = true;
        }
    }
}
