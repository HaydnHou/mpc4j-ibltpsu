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
        if (maxPeelRounds <= 0) {
            throw new IllegalArgumentException("maxPeelRounds must be positive: " + maxPeelRounds);
        }
        int targetCellNum = Math.max(hashNum, (int) Math.ceil(alpha * tauMax));
        rowCellNum = Math.max(1, (targetCellNum + hashNum - 1) / hashNum);
        cellNum = Math.multiplyExact(rowCellNum, hashNum);
        if (cellNum <= 0) {
            throw new IllegalArgumentException("cellNum overflows or is non-positive: " + cellNum);
        }
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

    public long getHashSeed() {
        return hashSeed;
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
            && hashSeed == that.hashSeed
            && maxPeelRounds == that.maxPeelRounds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(partyNum, tauMax, alpha, hashNum, rowCellNum, cellNum, hashSeed, maxPeelRounds);
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
