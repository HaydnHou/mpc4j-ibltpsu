package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.Objects;

/**
 * MP-SOGS MPSU parameters for the clear union-peel prototype.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuParams {
    /**
     * Default SOGS expansion factor used by the current simulator gate.
     */
    public static final double DEFAULT_ALPHA = 1.40;
    /**
     * Default number of SOGS hash positions.
     */
    public static final int DEFAULT_HASH_NUM = 3;
    /**
     * Default hash seed.
     */
    public static final long DEFAULT_HASH_SEED = 0x534F47534D505355L;
    /**
     * Default auxiliary SOGS cell count.
     */
    public static final int DEFAULT_AUXILIARY_CELL_NUM = 4098;
    /**
     * Current prototype element bit length.
     */
    public static final int ELEMENT_BIT_LENGTH = Long.SIZE;

    /**
     * Number of parties.
     */
    private final int partyNum;
    /**
     * Public upper bound on the union size.
     */
    private final int tauMax;
    /**
     * Expansion factor.
     */
    private final double alpha;
    /**
     * Number of hash positions.
     */
    private final int hashNum;
    /**
     * Number of cells per hash row.
     */
    private final int rowCellNum;
    /**
     * Number of cells in the row-disjoint SOGS table.
     */
    private final int cellNum;
    /**
     * Whether the sketch has a fixed auxiliary tier.
     */
    private final boolean twoTier;
    /**
     * Auxiliary hash count.
     */
    private final int auxiliaryHashNum;
    /**
     * Auxiliary cells per hash row.
     */
    private final int auxiliaryRowCellNum;
    /**
     * Auxiliary cell count.
     */
    private final int auxiliaryCellNum;
    /**
     * Hash seed.
     */
    private final long hashSeed;
    /**
     * Maximum peel rounds.
     */
    private final int maxPeelRounds;

    private MpSogsMpsuParams(Builder builder) {
        partyNum = builder.partyNum;
        tauMax = builder.tauMax;
        alpha = builder.alpha;
        hashNum = builder.hashNum;
        twoTier = builder.twoTier;
        auxiliaryHashNum = builder.auxiliaryHashNum;
        hashSeed = builder.hashSeed;
        maxPeelRounds = builder.maxPeelRounds;
        if (partyNum < 2) {
            throw new IllegalArgumentException("partyNum must be at least 2: " + partyNum);
        }
        if (tauMax <= 0) {
            throw new IllegalArgumentException("tauMax must be positive: " + tauMax);
        }
        if (!Double.isFinite(alpha) || alpha <= 1.0) {
            throw new IllegalArgumentException("alpha must be finite and > 1: " + alpha);
        }
        if (hashNum < 2) {
            throw new IllegalArgumentException("hashNum must be at least 2: " + hashNum);
        }
        if (auxiliaryHashNum < 2) {
            throw new IllegalArgumentException("auxiliaryHashNum must be at least 2: " + auxiliaryHashNum);
        }
        if (maxPeelRounds <= 0) {
            throw new IllegalArgumentException("maxPeelRounds must be positive: " + maxPeelRounds);
        }
        int targetCellNum = Math.max(hashNum, (int) Math.ceil(alpha * tauMax));
        rowCellNum = Math.max(1, (targetCellNum + hashNum - 1) / hashNum);
        cellNum = Math.multiplyExact(rowCellNum, hashNum);
        if (cellNum <= 0) {
            throw new IllegalArgumentException("cellNum overflows or is non-positive: " + cellNum);
        }
        if (builder.auxiliaryCellNum <= 0) {
            throw new IllegalArgumentException("auxiliaryCellNum must be positive: " + builder.auxiliaryCellNum);
        }
        auxiliaryRowCellNum = Math.max(1, (builder.auxiliaryCellNum + auxiliaryHashNum - 1) / auxiliaryHashNum);
        auxiliaryCellNum = Math.multiplyExact(auxiliaryRowCellNum, auxiliaryHashNum);
    }

    public int getPartyNum() {
        return partyNum;
    }

    public int getTauMax() {
        return tauMax;
    }

    public double getAlpha() {
        return alpha;
    }

    public int getHashNum() {
        return hashNum;
    }

    public int getRowCellNum() {
        return rowCellNum;
    }

    public int getCellNum() {
        return cellNum;
    }

    public boolean isTwoTier() {
        return twoTier;
    }

    public int getAuxiliaryHashNum() {
        return auxiliaryHashNum;
    }

    public int getAuxiliaryRowCellNum() {
        return auxiliaryRowCellNum;
    }

    public int getAuxiliaryCellNum() {
        return auxiliaryCellNum;
    }

    public int getHashNum(MpSogsTier tier) {
        return tier == MpSogsTier.MAIN ? hashNum : auxiliaryHashNum;
    }

    public int getRowCellNum(MpSogsTier tier) {
        return tier == MpSogsTier.MAIN ? rowCellNum : auxiliaryRowCellNum;
    }

    public int getCellNum(MpSogsTier tier) {
        return tier == MpSogsTier.MAIN ? cellNum : auxiliaryCellNum;
    }

    public long getHashSeed() {
        return hashSeed;
    }

    public long getHashSeed(MpSogsTier tier) {
        return tier == MpSogsTier.MAIN ? hashSeed : MpSogsHashUtils.splitMix64(hashSeed ^ 0x4D50534F47534158L);
    }

    public int getMaxPeelRounds() {
        return maxPeelRounds;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MpSogsMpsuParams that)) {
            return false;
        }
        return partyNum == that.partyNum
            && tauMax == that.tauMax
            && Double.compare(alpha, that.alpha) == 0
            && hashNum == that.hashNum
            && rowCellNum == that.rowCellNum
            && cellNum == that.cellNum
            && twoTier == that.twoTier
            && auxiliaryHashNum == that.auxiliaryHashNum
            && auxiliaryRowCellNum == that.auxiliaryRowCellNum
            && auxiliaryCellNum == that.auxiliaryCellNum
            && hashSeed == that.hashSeed
            && maxPeelRounds == that.maxPeelRounds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            partyNum, tauMax, alpha, hashNum, rowCellNum, cellNum, twoTier, auxiliaryHashNum,
            auxiliaryRowCellNum, auxiliaryCellNum, hashSeed, maxPeelRounds
        );
    }

    /**
     * Builder.
     */
    public static class Builder {
        /**
         * Number of parties.
         */
        private final int partyNum;
        /**
         * Public union-size upper bound.
         */
        private final int tauMax;
        /**
         * Expansion factor.
         */
        private double alpha;
        /**
         * Hash count.
         */
        private int hashNum;
        /**
         * Whether to enable the auxiliary SOGS tier.
         */
        private boolean twoTier;
        /**
         * Auxiliary hash count.
         */
        private int auxiliaryHashNum;
        /**
         * Auxiliary cell count.
         */
        private int auxiliaryCellNum;
        /**
         * Hash seed.
         */
        private long hashSeed;
        /**
         * Maximum peel rounds.
         */
        private int maxPeelRounds;

        public Builder(int partyNum, int tauMax) {
            this.partyNum = partyNum;
            this.tauMax = tauMax;
            alpha = DEFAULT_ALPHA;
            hashNum = DEFAULT_HASH_NUM;
            twoTier = true;
            auxiliaryHashNum = DEFAULT_HASH_NUM;
            auxiliaryCellNum = DEFAULT_AUXILIARY_CELL_NUM;
            hashSeed = DEFAULT_HASH_SEED;
            maxPeelRounds = 10_000;
        }

        public Builder setAlpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder setHashNum(int hashNum) {
            this.hashNum = hashNum;
            return this;
        }

        public Builder setTwoTier(boolean twoTier) {
            this.twoTier = twoTier;
            return this;
        }

        public Builder setAuxiliaryHashNum(int auxiliaryHashNum) {
            this.auxiliaryHashNum = auxiliaryHashNum;
            return this;
        }

        public Builder setAuxiliaryCellNum(int auxiliaryCellNum) {
            this.auxiliaryCellNum = auxiliaryCellNum;
            return this;
        }

        public Builder setHashSeed(long hashSeed) {
            this.hashSeed = hashSeed;
            return this;
        }

        public Builder setMaxPeelRounds(int maxPeelRounds) {
            this.maxPeelRounds = maxPeelRounds;
            return this;
        }

        public MpSogsMpsuParams build() {
            return new MpSogsMpsuParams(this);
        }
    }
}
