package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuParams;

/**
 * Public fixed profile for PISF-IBLT enhanced UPSU.
 *
 * <p>The profile follows the final-candidate document: only fixed public capacities are exposed. The true sender size
 * {@code m}, peel progress, repetition success, frontier size, and output count are not protocol messages.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltUpsuParams implements UpsuParams {
    /**
     * supported sender capacity profiles.
     */
    private static final int[] SUPPORTED_SENDER_CAPACITIES = new int[] {
        1 << 5, 1 << 10, 1 << 12, 1 << 14, 1 << 16,
        1 << 18,
    };
    /**
     * sender fixed capacity M.
     */
    private final int senderCapacity;
    /**
     * receiver fixed capacity N.
     */
    private final int receiverCapacity;
    /**
     * flattened PISF-IBLT address space L_all.
     */
    private final int tableLength;
    /**
     * max degree d_max.
     */
    private final int dMax;
    /**
     * fixed repetition count R_rep.
     */
    private final int repetitionNum;
    /**
     * fixed peel round count P.
     */
    private final int peelRound;
    /**
     * security parameter.
     */
    private final int securityParameter;
    /**
     * key byte length.
     */
    private final int keyByteLength;
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * Conservative evidence profile.
     */
    private final boolean conservative;

    private PisIbltUpsuParams(Builder builder) {
        senderCapacity = builder.senderCapacity;
        receiverCapacity = builder.receiverCapacity;
        tableLength = builder.tableLength;
        dMax = builder.dMax;
        repetitionNum = builder.repetitionNum;
        peelRound = builder.peelRound;
        securityParameter = builder.securityParameter;
        keyByteLength = builder.keyByteLength;
        tagByteLength = builder.tagByteLength;
        conservative = builder.conservative;
    }

    /**
     * Creates the strict fixed profile from runtime capacities.
     *
     * @param maxSenderElementSize max sender element size.
     * @param receiverElementSize  receiver element size.
     * @return fixed profile.
     */
    public static PisIbltUpsuParams createStrict(int maxSenderElementSize, int receiverElementSize) {
        MathPreconditions.checkPositive("maxSenderElementSize", maxSenderElementSize);
        MathPreconditions.checkPositive("receiverElementSize", receiverElementSize);
        int senderCapacity = selectSenderCapacity(maxSenderElementSize);
        int receiverCapacity = nextPowerOfTwo(receiverElementSize);
        int dMax = 5;
        int tableLength = nextPowerOfTwo((int) Math.ceil(1.50 * (senderCapacity + receiverCapacity)));
        int repetitionNum = 3;
        int peelRound = Math.max(tableLength, 4 * senderCapacity);
        return new Builder(senderCapacity, receiverCapacity)
            .setTableLength(tableLength)
            .setDMax(dMax)
            .setRepetitionNum(repetitionNum)
            .setPeelRound(peelRound)
            .setSecurityParameter(128)
            .setKeyByteLength(16)
            .setTagByteLength(16)
            .setConservative(true)
            .build();
    }

    /**
     * Creates the fast source-split profile from public real sizes.
     *
     * @param senderElementSize   sender element size.
     * @param receiverElementSize receiver element size.
     * @param alpha               table length multiplier.
     * @param dMax                max degree.
     * @param repetitionNum       repetition count.
     * @param keyByteLength       key byte length.
     * @param tagByteLength       tag byte length.
     * @return fixed profile.
     */
    public static PisIbltUpsuParams createFast(int senderElementSize, int receiverElementSize, double alpha, int dMax,
                                               int repetitionNum, int keyByteLength, int tagByteLength) {
        MathPreconditions.checkPositive("senderElementSize", senderElementSize);
        MathPreconditions.checkPositive("receiverElementSize", receiverElementSize);
        if (!Double.isFinite(alpha) || alpha <= 1.0) {
            throw new IllegalArgumentException("alpha must be finite and greater than 1.0: " + alpha);
        }
        long totalSize = Math.addExact((long) senderElementSize, receiverElementSize);
        long tableLength = (long) Math.ceil(alpha * totalSize);
        if (tableLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("PISF-IBLT table is too large: " + tableLength);
        }
        return new Builder(senderElementSize, receiverElementSize)
            .setTableLength((int) tableLength)
            .setDMax(dMax)
            .setRepetitionNum(repetitionNum)
            .setPeelRound((int) tableLength)
            .setSecurityParameter(128)
            .setKeyByteLength(keyByteLength)
            .setTagByteLength(tagByteLength)
            .setConservative(true)
            .build();
    }

    /**
     * Selects public sender capacity M.
     *
     * @param maxSenderElementSize max sender element size.
     * @return public capacity.
     */
    public static int selectSenderCapacity(int maxSenderElementSize) {
        MathPreconditions.checkPositive("maxSenderElementSize", maxSenderElementSize);
        for (int senderCapacity : SUPPORTED_SENDER_CAPACITIES) {
            if (maxSenderElementSize <= senderCapacity) {
                return senderCapacity;
            }
        }
        throw new IllegalArgumentException("maxSenderElementSize exceeds largest PISF-IBLT profile: "
            + maxSenderElementSize);
    }

    /**
     * Returns the next power of two.
     *
     * @param value value.
     * @return next power of two.
     */
    public static int nextPowerOfTwo(int value) {
        MathPreconditions.checkPositive("value", value);
        if (value >= (1 << 30)) {
            throw new IllegalArgumentException("value is too large for an int power-of-two profile: " + value);
        }
        return 1 << LongUtils.ceilLog2(value);
    }

    public int getSenderCapacity() {
        return senderCapacity;
    }

    public int getReceiverCapacity() {
        return receiverCapacity;
    }

    public int getTableLength() {
        return tableLength;
    }

    public int getDMax() {
        return dMax;
    }

    public int getRepetitionNum() {
        return repetitionNum;
    }

    public int getPeelRound() {
        return peelRound;
    }

    public int getSecurityParameter() {
        return securityParameter;
    }

    public int getKeyByteLength() {
        return keyByteLength;
    }

    public int getTagByteLength() {
        return tagByteLength;
    }

    public boolean isConservative() {
        return conservative;
    }

    @Override
    public int maxSenderElementSize() {
        return senderCapacity;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<PisIbltUpsuParams> {
        /**
         * sender capacity.
         */
        private final int senderCapacity;
        /**
         * receiver capacity.
         */
        private final int receiverCapacity;
        /**
         * table length.
         */
        private int tableLength;
        /**
         * max degree.
         */
        private int dMax;
        /**
         * repetition count.
         */
        private int repetitionNum;
        /**
         * fixed peel rounds.
         */
        private int peelRound;
        /**
         * security parameter.
         */
        private int securityParameter;
        /**
         * key byte length.
         */
        private int keyByteLength;
        /**
         * tag byte length.
         */
        private int tagByteLength;
        /**
         * conservative evidence mode.
         */
        private boolean conservative;

        public Builder(int senderCapacity, int receiverCapacity) {
            MathPreconditions.checkPositive("senderCapacity", senderCapacity);
            MathPreconditions.checkPositive("receiverCapacity", receiverCapacity);
            this.senderCapacity = senderCapacity;
            this.receiverCapacity = receiverCapacity;
            tableLength = nextPowerOfTwo((int) Math.ceil(1.50 * (senderCapacity + receiverCapacity)));
            dMax = 5;
            repetitionNum = 3;
            peelRound = Math.max(tableLength, 4 * senderCapacity);
            securityParameter = 128;
            keyByteLength = 16;
            tagByteLength = 16;
            conservative = true;
        }

        public Builder setTableLength(int tableLength) {
            this.tableLength = tableLength;
            return this;
        }

        public Builder setDMax(int dMax) {
            this.dMax = dMax;
            return this;
        }

        public Builder setRepetitionNum(int repetitionNum) {
            this.repetitionNum = repetitionNum;
            return this;
        }

        public Builder setPeelRound(int peelRound) {
            this.peelRound = peelRound;
            return this;
        }

        public Builder setSecurityParameter(int securityParameter) {
            this.securityParameter = securityParameter;
            return this;
        }

        public Builder setKeyByteLength(int keyByteLength) {
            this.keyByteLength = keyByteLength;
            return this;
        }

        public Builder setTagByteLength(int tagByteLength) {
            this.tagByteLength = tagByteLength;
            return this;
        }

        public Builder setConservative(boolean conservative) {
            this.conservative = conservative;
            return this;
        }

        @Override
        public PisIbltUpsuParams build() {
            MathPreconditions.checkPositive("senderCapacity", senderCapacity);
            MathPreconditions.checkGreaterOrEqual("receiverCapacity", receiverCapacity, 1);
            MathPreconditions.checkGreaterOrEqual("tableLength", tableLength, senderCapacity + receiverCapacity);
            MathPreconditions.checkInRangeClosed("dMax", dMax, 2, 8);
            MathPreconditions.checkPositive("repetitionNum", repetitionNum);
            MathPreconditions.checkGreaterOrEqual("peelRound", peelRound, senderCapacity);
            MathPreconditions.checkPositive("securityParameter", securityParameter);
            MathPreconditions.checkPositive("keyByteLength", keyByteLength);
            MathPreconditions.checkPositive("tagByteLength", tagByteLength);
            return new PisIbltUpsuParams(this);
        }
    }
}
