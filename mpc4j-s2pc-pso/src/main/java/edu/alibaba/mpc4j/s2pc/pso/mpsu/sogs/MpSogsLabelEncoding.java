package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Element-label representation used inside secure MP-SOGS union peeling.
 *
 * @author donghai hou
 * @date 2026/07/18
 */
public enum MpSogsLabelEncoding {
    /**
     * Shares and compares the complete 64-bit element.
     */
    FULL_VALUE {
        @Override
        public int bitLength(MpSogsMpsuParams params, MpSogsTier tier) {
            return MpSogsMpsuParams.ELEMENT_BIT_LENGTH;
        }

        @Override
        public long encode(long value, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
            return value;
        }

        @Override
        public long decode(long label, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
            return label;
        }
    },
    /**
     * Shares only the exact quotient left after the public row-cell remainder is removed.
     */
    EXACT_QUOTIENT {
        @Override
        public int bitLength(MpSogsMpsuParams params, MpSogsTier tier) {
            return MpSogsQuotientLabelCodec.bitLength(params, tier);
        }

        @Override
        public long encode(long value, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
            return MpSogsQuotientLabelCodec.encode(value, params, tier, cellIndex);
        }

        @Override
        public long decode(long label, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
            return MpSogsQuotientLabelCodec.decode(label, params, tier, cellIndex);
        }
    };

    public abstract int bitLength(MpSogsMpsuParams params, MpSogsTier tier);

    public abstract long encode(long value, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex);

    public abstract long decode(long label, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex);
}
