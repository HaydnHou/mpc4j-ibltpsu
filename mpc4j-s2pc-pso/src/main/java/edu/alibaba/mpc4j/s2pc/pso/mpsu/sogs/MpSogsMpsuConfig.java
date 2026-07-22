package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity.MultiplicityPayloadEncoding;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity.SsmOpeningMode;

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
         * Honest-majority Shamir backend over secret-shared multiplicity SOGS cells.
         */
        SHAMIR_MULTIPLICITY,
        /**
         * Honest-majority Shamir backend with a persistent secret-shared multiplicity SOGS state.
         */
        SHAMIR_MULTIPLICITY_PERSISTENT,
        /**
         * Persistent multiplicity SOGS with one exact quotient payload word per Cell.
         */
        SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT,
        /**
         * Persistent exact-quotient multiplicity SOGS with subset-seed Shamir PRSS masks.
         */
        SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS,
        /**
         * PRSS exact-quotient backend with double-share degree reduction and balanced opening.
         */
        SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE,
        /**
         * RTT-aware Stage 4 with terminal product fusion and selectable collective opening.
         */
        SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE,
        /**
         * Cumulative Stage 5 backend with RTT-aware terminal fusion and canonical 61-bit wire packing.
         */
        SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED,
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
         * 5-party T1 PRSS packed backend with opened-first selected-lane candidate recovery.
         */
        REP5_PRSS_OPENED_FIRST,
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
    private final MpSogsLabelEncoding labelEncoding;
    private final MpSogsMpsuFactory.MpSogsMpsuType ptoType;
    private final int maxHashSeedRetries;
    private final int maxBatchCells;
    private final SsmOpeningMode ssmOpeningMode;
    private final double networkRttMillis;
    private final double networkBandwidthMbps;

    private MpSogsMpsuConfig(Builder builder) {
        params = Preconditions.checkNotNull(builder.params);
        securePeelType = Preconditions.checkNotNull(builder.securePeelType);
        labelEncoding = Preconditions.checkNotNull(builder.labelEncoding);
        ptoType = MpSogsMpsuFactory.MpSogsMpsuType.MP_SOGS;
        if (securePeelType == SecurePeelType.ABB3) {
            Preconditions.checkArgument(params.getPartyNum() == ABB3_PARTY_NUM,
                "ABB3 MP-SOGS backend requires exactly %s parties: %s", ABB3_PARTY_NUM, params.getPartyNum());
        }
        if (securePeelType == SecurePeelType.SHAMIR
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED) {
            Preconditions.checkArgument(params.getPartyNum() >= 3,
                "Shamir MP-SOGS backend requires at least 3 parties: %s", params.getPartyNum());
        }
        if (securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE
            || securePeelType == SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED) {
            MultiplicityPayloadEncoding.EXACT_QUOTIENT.validate(params);
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
        if (securePeelType == SecurePeelType.REP5_PRSS_OPENED_FIRST) {
            Preconditions.checkArgument(params.getPartyNum() == 5,
                "REP5 PRSS packed MP-SOGS backend requires exactly 5 parties: %s", params.getPartyNum());
        }
        Preconditions.checkArgument(builder.maxHashSeedRetries > 0,
            "maxHashSeedRetries must be positive: %s", builder.maxHashSeedRetries);
        maxHashSeedRetries = builder.maxHashSeedRetries;
        Preconditions.checkArgument(builder.maxBatchCells > 0,
            "maxBatchCells must be positive: %s", builder.maxBatchCells);
        maxBatchCells = builder.maxBatchCells;
        ssmOpeningMode = Preconditions.checkNotNull(builder.ssmOpeningMode);
        Preconditions.checkArgument(Double.isFinite(builder.networkRttMillis) && builder.networkRttMillis >= 0.0,
            "networkRttMillis must be finite and non-negative: %s", builder.networkRttMillis);
        networkRttMillis = builder.networkRttMillis;
        Preconditions.checkArgument(
            Double.isFinite(builder.networkBandwidthMbps) && builder.networkBandwidthMbps >= 0.0,
            "networkBandwidthMbps must be finite and non-negative: %s", builder.networkBandwidthMbps
        );
        networkBandwidthMbps = builder.networkBandwidthMbps;
    }

    public MpSogsMpsuParams getParams() {
        return params;
    }

    public SecurePeelType getSecurePeelType() {
        return securePeelType;
    }

    public MpSogsLabelEncoding getLabelEncoding() {
        return labelEncoding;
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

    public SsmOpeningMode getSsmOpeningMode() {
        return ssmOpeningMode;
    }

    public double getNetworkRttMillis() {
        return networkRttMillis;
    }

    public double getNetworkBandwidthMbps() {
        return networkBandwidthMbps;
    }

    /**
     * Builder.
     */
    public static class Builder {
        private final MpSogsMpsuParams params;
        private SecurePeelType securePeelType;
        private MpSogsLabelEncoding labelEncoding;
        private int maxHashSeedRetries;
        private int maxBatchCells;
        private SsmOpeningMode ssmOpeningMode;
        private double networkRttMillis;
        private double networkBandwidthMbps;

        public Builder(MpSogsMpsuParams params) {
            this.params = Preconditions.checkNotNull(params);
            securePeelType = SecurePeelType.DUMMY_CLEAR;
            labelEncoding = MpSogsLabelEncoding.EXACT_QUOTIENT;
            maxHashSeedRetries = 4;
            maxBatchCells = DEFAULT_MAX_BATCH_CELLS;
            ssmOpeningMode = SsmOpeningMode.BALANCED_TWO_PHASE;
            networkRttMillis = 0.0;
            networkBandwidthMbps = 0.0;
        }

        public Builder setSecurePeelType(SecurePeelType securePeelType) {
            this.securePeelType = Preconditions.checkNotNull(securePeelType);
            return this;
        }

        public Builder setLabelEncoding(MpSogsLabelEncoding labelEncoding) {
            this.labelEncoding = Preconditions.checkNotNull(labelEncoding);
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

        public Builder setSsmOpeningMode(SsmOpeningMode ssmOpeningMode) {
            this.ssmOpeningMode = Preconditions.checkNotNull(ssmOpeningMode);
            return this;
        }

        public Builder setNetworkRttMillis(double networkRttMillis) {
            this.networkRttMillis = networkRttMillis;
            return this;
        }

        public Builder setNetworkBandwidthMbps(double networkBandwidthMbps) {
            this.networkBandwidthMbps = networkBandwidthMbps;
            return this;
        }

        public MpSogsMpsuConfig build() {
            return new MpSogsMpsuConfig(this);
        }
    }
}
