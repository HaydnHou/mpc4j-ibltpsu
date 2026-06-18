package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVectorFactory;

import java.util.Arrays;

/**
 * Utilities for keeping the row release selector hidden before the token-keyed SOGS tail.
 *
 * <p>The input is the secret-shared output of a private equality layer over an {@code alpha x rowNum} anonymous matrix.
 * For each row, the selector is {@code release = NOT(OR_j eq[j][row])}. The returned vector is still a Z2 share, so it
 * can drive the aggregate tail without opening per-row hit / miss bits.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsHiddenSelector {
    /**
     * Private constructor.
     */
    private TokenKeyedSogsHiddenSelector() {
        // empty
    }

    /**
     * Flattens an {@code alpha x rowNum} matrix in row-major order.
     *
     * @param matrix matrix.
     * @return flattened inputs.
     */
    public static byte[][] flatten(byte[][][] matrix) {
        if (matrix.length == 0 || matrix[0].length == 0) {
            throw new IllegalArgumentException("empty equality matrix");
        }
        int alpha = matrix.length;
        int rowNum = matrix[0].length;
        byte[][] flat = new byte[alpha * rowNum][];
        for (int j = 0; j < alpha; j++) {
            if (matrix[j].length != rowNum) {
                throw new IllegalArgumentException("ragged equality matrix");
            }
            for (int i = 0; i < rowNum; i++) {
                flat[j * rowNum + i] = Arrays.copyOf(matrix[j][i], matrix[j][i].length);
            }
        }
        return flat;
    }

    /**
     * Collapses equality shares to row release shares.
     *
     * @param z2cParty Z2 circuit party.
     * @param eqShare  secret share of flattened equality bits.
     * @param alpha    number of equality lanes per row.
     * @param rowNum   row number.
     * @return secret share of row release bits.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public static SquareZ2Vector collapseToReleaseShare(
        Z2cParty z2cParty, SquareZ2Vector eqShare, int alpha, int rowNum
    ) throws MpcAbortException {
        if (alpha <= 0 || rowNum <= 0) {
            throw new IllegalArgumentException("invalid matrix shape");
        }
        if (eqShare.getNum() != alpha * rowNum) {
            throw new IllegalArgumentException("equality share length does not match matrix shape");
        }
        SquareZ2Vector eqShareCopy = eqShare.copy();
        SquareZ2Vector[] eqRows = new SquareZ2Vector[alpha];
        for (int j = alpha - 1; j >= 0; j--) {
            eqRows[j] = eqShareCopy.split(rowNum);
        }
        SquareZ2Vector hitShare = eqRows[0];
        for (int j = 1; j < alpha; j++) {
            hitShare = z2cParty.or(hitShare, eqRows[j]);
        }
        BitVector releaseShare = hitShare.getBitVector().copy();
        if (z2cParty.ownParty().getPartyId() == 0) {
            releaseShare.xori(BitVectorFactory.createOnes(rowNum));
        }
        return SquareZ2Vector.create(releaseShare, false);
    }

    /**
     * Converts a local Z2 share vector to booleans for protocols that consume local choice/share bits.
     *
     * @param share share vector.
     * @return local share bits.
     */
    public static boolean[] toBooleanArray(SquareZ2Vector share) {
        boolean[] bits = new boolean[share.getNum()];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = share.getBitVector().get(i);
        }
        return bits;
    }
}
