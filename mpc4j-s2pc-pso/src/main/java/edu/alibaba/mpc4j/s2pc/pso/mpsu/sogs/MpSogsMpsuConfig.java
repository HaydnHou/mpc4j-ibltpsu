package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import com.google.common.base.Preconditions;

/**
 * MP-SOGS MPSU configuration for the prototype line.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuConfig {
    /**
     * Party number supported by the current concrete ABB3 backend.
     */
    public static final int ABB3_PARTY_NUM = 3;
    /**
     * Default maximum cells per secure-uPeel batch.
     */
    public static final int DEFAULT_MAX_BATCH_CELLS = 1 << 20;
    /**
     * Secure peel implementation type.
     */
    public enum SecurePeelType {
        /**
         * Dummy clear adapter, not secure.
         */
        DUMMY_CLEAR,
        /**
         * ABB3 three-party semi-honest backend.
         */
        ABB3,
        /**
         * Generic honest-majority Shamir backend for 4/5-party semi-honest experiments.
         */
        SHAMIR,
        /**
         * Planned 4-party packed replicated Z2 backend.
         */
        REP4_PACKED,
        /**
         * 4-party packed replicated Z2 backend with PRSS-compressed share/open.
         */
        REP4_PRSS_PACKED,
        /**
         * 4-party PRSS packed backend with opened-first selected-lane candidate recovery.
         */
        REP4_PRSS_OPENED_FIRST,
        /**
         * Planned 5-party packed replicated Z2 backend.
         */
        REP5_PACKED,
        /**
         * Placeholder for a future generic n-party semi-honest MPC implementation.
         */
        GENERIC_MPC,
        /**
         * Placeholder for a future optimized batched implementation.
         */
        OPT_BATCH,
    }

    private final MpSogsMpsuParams params;
    private final SecurePeelType securePeelType;
    private final MpSogsMpsuFactory.MpSogsMpsuType ptoType;
    private final int maxHashSeedRetries;
    private final int maxBatchCells;

    private MpSogsMpsuConfig(Builder builder) {
        params = Preconditions.checkNotNull(builder.params);
        securePeelType = Preconditions.checkNotNull(builder.securePeelType);
        ptoType = MpSogsMpsuFactory.MpSogsMpsuType.MP_SOGS;
        if (securePeelType == SecurePeelType.ABB3) {
            Preconditions.checkArgument(params.getPartyNum() == ABB3_PARTY_NUM,
                "ABB3 MP-SOGS backend requires exactly %s parties: %s", ABB3_PARTY_NUM, params.getPartyNum());
        }
        if (securePeelType == SecurePeelType.SHAMIR) {
            Preconditions.checkArgument(params.getPartyNum() >= 3,
                "Shamir MP-SOGS backend requires at least 3 parties: %s", params.getPartyNum());
        }
        if (securePeelType == SecurePeelType.REP4_PACKED) {
            Preconditions.checkArgument(params.getPartyNum() == 4,
                "REP4 packed MP-SOGS backend requires exactly 4 parties: %s", params.getPartyNum());
        }
        if (securePeelType == SecurePeelType.REP4_PRSS_PACKED
            || securePeelType == SecurePeelType.REP4_PRSS_OPENED_FIRST) {
            Preconditions.checkArgument(params.getPartyNum() == 4,
                "REP4 PRSS packed MP-SOGS backend requires exactly 4 parties: %s", params.getPartyNum());
        }
        if (securePeelType == SecurePeelType.REP5_PACKED) {
            Preconditions.checkArgument(params.getPartyNum() == 5,
                "REP5 packed MP-SOGS backend requires exactly 5 parties: %s", params.getPartyNum());
        }
        Preconditions.checkArgument(builder.maxHashSeedRetries > 0,
            "maxHashSeedRetries must be positive: %s", builder.maxHashSeedRetries);
        maxHashSeedRetries = builder.maxHashSeedRetries;
        Preconditions.checkArgument(builder.maxBatchCells > 0,
            "maxBatchCells must be positive: %s", builder.maxBatchCells);
        maxBatchCells = builder.maxBatchCells;
    }

    public MpSogsMpsuParams getParams() {
        return params;
    }

    public SecurePeelType getSecurePeelType() {
        return securePeelType;
    }

    public MpSogsMpsuFactory.MpSogsMpsuType getPtoType() {
        return ptoType;
    }

    public int getMaxHashSeedRetries() {
        return maxHashSeedRetries;
    }

    public int getMaxBatchCells() {
        return maxBatchCells;
    }

    /**
     * Builder.
     */
    public static class Builder {
        private final MpSogsMpsuParams params;
        private SecurePeelType securePeelType;
        private int maxHashSeedRetries;
        private int maxBatchCells;

        public Builder(MpSogsMpsuParams params) {
            this.params = Preconditions.checkNotNull(params);
            securePeelType = SecurePeelType.DUMMY_CLEAR;
            maxHashSeedRetries = 4;
            maxBatchCells = DEFAULT_MAX_BATCH_CELLS;
        }

        public Builder setSecurePeelType(SecurePeelType securePeelType) {
            this.securePeelType = Preconditions.checkNotNull(securePeelType);
            return this;
        }

        public Builder setMaxHashSeedRetries(int maxHashSeedRetries) {
            this.maxHashSeedRetries = maxHashSeedRetries;
            return this;
        }

        public Builder setMaxBatchCells(int maxBatchCells) {
            this.maxBatchCells = maxBatchCells;
            return this;
        }

        public MpSogsMpsuConfig build() {
            return new MpSogsMpsuConfig(this);
        }
    }
}
