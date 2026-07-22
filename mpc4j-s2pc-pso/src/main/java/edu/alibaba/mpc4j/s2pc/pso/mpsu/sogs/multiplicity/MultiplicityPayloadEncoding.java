package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsQuotientLabelCodec;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

/**
 * Payload representation inside a multiplicity SOGS Cell.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public enum MultiplicityPayloadEncoding {
    /** Two unsigned 32-bit limbs carrying the complete element. */
    FULL_LIMBS(2),
    /** One exact row/cell quotient. */
    EXACT_QUOTIENT(1);

    private final int payloadWordNum;

    MultiplicityPayloadEncoding(int payloadWordNum) {
        this.payloadWordNum = payloadWordNum;
    }

    public int getPayloadWordNum() {
        return payloadWordNum;
    }

    public int getCellWordNum() {
        return MultiplicitySogsCell.BASE_WORD_NUM + payloadWordNum;
    }

    /**
     * Rejects quotient parameters whose largest exact label is not a Mersenne-61 field element.
     */
    public void validate(MpSogsMpsuParams params) {
        if (this != EXACT_QUOTIENT) {
            return;
        }
        validateTier(params, MpSogsTier.MAIN);
        if (params.isTwoTier()) {
            validateTier(params, MpSogsTier.AUXILIARY);
        }
    }

    private static void validateTier(MpSogsMpsuParams params, MpSogsTier tier) {
        int rowCellNum = params.getRowCellNum(tier);
        long maxQuotient = Long.divideUnsigned(-1L, rowCellNum);
        if (Long.compareUnsigned(maxQuotient, Mersenne61Field.PRIME) >= 0) {
            throw new IllegalArgumentException(
                tier + " exact quotient does not fit F_(2^61-1): rowCellNum=" + rowCellNum
                    + ", labelBits=" + MpSogsQuotientLabelCodec.bitLength(params, tier)
            );
        }
    }
}
