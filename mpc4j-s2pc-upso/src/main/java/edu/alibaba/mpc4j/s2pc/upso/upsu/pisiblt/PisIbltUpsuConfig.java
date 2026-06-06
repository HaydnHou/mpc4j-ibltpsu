package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;

import static edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory.UpsuType;

/**
 * PISF-IBLT enhanced UPSU config.
 *
 * <p>The default mode is the final-candidate document's main safety profile: Conservative PISF-IBLT plus an always-run
 * deterministic fixed-capacity fallback. The current Java backend realizes the fallback with secret-shared equality and
 * fixed receiver-only capsules; the PISF source-split table and Conservative EGUP simulator are kept as the profile
 * structure and audit layer for replacing the fallback with a dedicated secret-address backend later.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltUpsuConfig extends AbstractMultiPartyPtoConfig implements UpsuConfig {
    /**
     * Z2 circuit config.
     */
    private final Z2cConfig z2cConfig;
    /**
     * MP-OPRF config.
     */
    private final MpOprfConfig mpOprfConfig;
    /**
     * max element byte length.
     */
    private final int maxElementByteLength;
    /**
     * execution mode.
     */
    private final PisIbltUpsuMode mode;
    /**
     * fixed PISF profile. If null, the parties derive a public profile from init capacities.
     */
    private final PisIbltUpsuParams params;
    /**
     * fast table multiplier.
     */
    private final double fastAlpha;
    /**
     * fast key byte length.
     */
    private final int keyByteLength;
    /**
     * fast tag byte length.
     */
    private final int tagByteLength;
    /**
     * fast degree.
     */
    private final int dMax;
    /**
     * fast repetition count.
     */
    private final int repetitionNum;

    private PisIbltUpsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.z2cConfig, builder.mpOprfConfig);
        z2cConfig = builder.z2cConfig;
        mpOprfConfig = builder.mpOprfConfig;
        maxElementByteLength = builder.maxElementByteLength;
        mode = builder.mode;
        params = builder.params;
        fastAlpha = builder.fastAlpha;
        keyByteLength = builder.keyByteLength;
        tagByteLength = builder.tagByteLength;
        dMax = builder.dMax;
        repetitionNum = builder.repetitionNum;
    }

    @Override
    public UpsuType getPtoType() {
        return UpsuType.PIS_IBLT;
    }

    public Z2cConfig getZ2cConfig() {
        return z2cConfig;
    }

    public MpOprfConfig getMpOprfConfig() {
        return mpOprfConfig;
    }

    public int getMaxElementByteLength() {
        return maxElementByteLength;
    }

    public PisIbltUpsuMode getMode() {
        return mode;
    }

    public PisIbltUpsuMode getCanonicalMode() {
        return mode.canonical();
    }

    public PisIbltUpsuParams getParams() {
        return params;
    }

    public PisIbltUpsuParams createParams(int maxSenderElementSize, int receiverElementSize) {
        if (params == null) {
            if (getCanonicalMode() == PisIbltUpsuMode.PISF_FAST) {
                return PisIbltUpsuParams.createFast(
                    maxSenderElementSize, receiverElementSize, fastAlpha, dMax, repetitionNum,
                    keyByteLength, tagByteLength
                );
            } else {
                return PisIbltUpsuParams.createStrict(maxSenderElementSize, receiverElementSize);
            }
        }
        MathPreconditions.checkLessOrEqual(
            "maxSenderElementSize", maxSenderElementSize, params.getSenderCapacity()
        );
        MathPreconditions.checkLessOrEqual(
            "receiverElementSize", receiverElementSize, params.getReceiverCapacity()
        );
        return params;
    }

    public double getFastAlpha() {
        return fastAlpha;
    }

    public int getKeyByteLength() {
        return keyByteLength;
    }

    public int getTagByteLength() {
        return tagByteLength;
    }

    public int getDMax() {
        return dMax;
    }

    public int getRepetitionNum() {
        return repetitionNum;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<PisIbltUpsuConfig> {
        /**
         * Z2 circuit config.
         */
        private Z2cConfig z2cConfig;
        /**
         * MP-OPRF config.
         */
        private MpOprfConfig mpOprfConfig;
        /**
         * max element byte length.
         */
        private int maxElementByteLength;
        /**
         * execution mode.
         */
        private PisIbltUpsuMode mode;
        /**
         * fixed profile.
         */
        private PisIbltUpsuParams params;
        /**
         * fast table multiplier.
         */
        private double fastAlpha;
        /**
         * fast key byte length.
         */
        private int keyByteLength;
        /**
         * fast tag byte length.
         */
        private int tagByteLength;
        /**
         * fast degree.
         */
        private int dMax;
        /**
         * fast repetition count.
         */
        private int repetitionNum;

        public Builder() {
            z2cConfig = Z2cFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, true);
            mpOprfConfig = new Rs21MpOprfConfig.Builder(SecurityModel.SEMI_HONEST).build();
            maxElementByteLength = 32;
            mode = PisIbltUpsuMode.PISF_FAST;
            params = null;
            fastAlpha = 1.5;
            keyByteLength = 16;
            tagByteLength = 16;
            dMax = 3;
            repetitionNum = 3;
        }

        public Builder setZ2cConfig(Z2cConfig z2cConfig) {
            this.z2cConfig = z2cConfig;
            return this;
        }

        public Builder setMpOprfConfig(MpOprfConfig mpOprfConfig) {
            this.mpOprfConfig = mpOprfConfig;
            return this;
        }

        public Builder setMaxElementByteLength(int maxElementByteLength) {
            this.maxElementByteLength = maxElementByteLength;
            return this;
        }

        public Builder setMode(PisIbltUpsuMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder setParams(PisIbltUpsuParams params) {
            this.params = params;
            return this;
        }

        public Builder setFastAlpha(double fastAlpha) {
            this.fastAlpha = fastAlpha;
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

        public Builder setDMax(int dMax) {
            this.dMax = dMax;
            return this;
        }

        public Builder setRepetitionNum(int repetitionNum) {
            this.repetitionNum = repetitionNum;
            return this;
        }

        @Override
        public PisIbltUpsuConfig build() {
            Preconditions.checkArgument(maxElementByteLength > 0);
            Preconditions.checkNotNull(mode);
            Preconditions.checkNotNull(z2cConfig);
            Preconditions.checkNotNull(mpOprfConfig);
            Preconditions.checkArgument(Double.isFinite(fastAlpha) && fastAlpha > 1.0);
            MathPreconditions.checkPositive("keyByteLength", keyByteLength);
            MathPreconditions.checkPositive("tagByteLength", tagByteLength);
            MathPreconditions.checkInRangeClosed("dMax", dMax, 2, 8);
            MathPreconditions.checkPositive("repetitionNum", repetitionNum);
            return new PisIbltUpsuConfig(this);
        }
    }
}
