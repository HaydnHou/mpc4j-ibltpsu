package edu.alibaba.mpc4j.s2pc.pso.psu.sogs;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory.PsuType;

/**
 * SOGS-PSU config.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * MP-OPRF config.
     */
    private final MpOprfConfig mpOprfConfig;
    /**
     * core COT config.
     */
    private final CoreCotConfig coreCotConfig;
    /**
     * SOGS vertex multiplier.
     */
    private final double sogsAlpha;
    /**
     * SOGS graph degree.
     */
    private final int sogsDegree;
    /**
     * SOGS-PSU execution profile.
     */
    private final SogsPsuProfile profile;
    /**
     * Whether to enable two-tier SOGS. This is intended for all-output PSU only.
     */
    private final boolean twoTier;
    /**
     * Auxiliary graph degree.
     */
    private final int auxiliaryDegree;
    /**
     * Fixed auxiliary vertex count.
     */
    private final int auxiliaryVertexCount;

    private SogsPsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.mpOprfConfig, builder.coreCotConfig);
        mpOprfConfig = builder.mpOprfConfig;
        coreCotConfig = builder.coreCotConfig;
        sogsAlpha = builder.sogsAlpha;
        sogsDegree = builder.sogsDegree;
        profile = builder.profile;
        twoTier = builder.twoTier;
        auxiliaryDegree = builder.auxiliaryDegree;
        auxiliaryVertexCount = builder.auxiliaryVertexCount;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.SOGS;
    }

    public MpOprfConfig getMpOprfConfig() {
        return mpOprfConfig;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public double getSogsAlpha() {
        return sogsAlpha;
    }

    public int getSogsDegree() {
        return sogsDegree;
    }

    public SogsPsuProfile getProfile() {
        return profile;
    }

    public boolean isTwoTier() {
        return twoTier;
    }

    public int getAuxiliaryDegree() {
        return auxiliaryDegree;
    }

    public int getAuxiliaryVertexCount() {
        return auxiliaryVertexCount;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<SogsPsuConfig> {
        /**
         * MP-OPRF config.
         */
        private MpOprfConfig mpOprfConfig;
        /**
         * core COT config.
         */
        private CoreCotConfig coreCotConfig;
        /**
         * SOGS vertex multiplier.
         */
        private double sogsAlpha;
        /**
         * SOGS graph degree.
         */
        private int sogsDegree;
        /**
         * SOGS-PSU execution profile.
         */
        private SogsPsuProfile profile;
        /**
         * Whether to enable two-tier SOGS.
         */
        private boolean twoTier;
        /**
         * Auxiliary graph degree.
         */
        private int auxiliaryDegree;
        /**
         * Fixed auxiliary vertex count.
         */
        private int auxiliaryVertexCount;

        public Builder() {
            mpOprfConfig = new Rs21MpOprfConfig.Builder(SecurityModel.SEMI_HONEST).build();
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
            sogsAlpha = 1.25;
            sogsDegree = 3;
            profile = SogsPsuProfile.BALANCED;
            twoTier = false;
            auxiliaryDegree = 3;
            auxiliaryVertexCount = 4098;
        }

        public Builder setMpOprfConfig(MpOprfConfig mpOprfConfig) {
            this.mpOprfConfig = Preconditions.checkNotNull(mpOprfConfig);
            return this;
        }

        public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
            this.coreCotConfig = Preconditions.checkNotNull(coreCotConfig);
            return this;
        }

        public Builder setSogsAlpha(double sogsAlpha) {
            Preconditions.checkArgument(Double.isFinite(sogsAlpha));
            Preconditions.checkArgument(sogsAlpha > 0);
            this.sogsAlpha = sogsAlpha;
            return this;
        }

        public Builder setSogsDegree(int sogsDegree) {
            Preconditions.checkArgument(sogsDegree >= 2);
            this.sogsDegree = sogsDegree;
            return this;
        }

        public Builder setProfile(SogsPsuProfile profile) {
            this.profile = Preconditions.checkNotNull(profile);
            return this;
        }

        public Builder setTwoTier(boolean twoTier) {
            this.twoTier = twoTier;
            return this;
        }

        public Builder setAuxiliaryDegree(int auxiliaryDegree) {
            Preconditions.checkArgument(auxiliaryDegree >= 2);
            this.auxiliaryDegree = auxiliaryDegree;
            return this;
        }

        public Builder setAuxiliaryVertexCount(int auxiliaryVertexCount) {
            Preconditions.checkArgument(auxiliaryVertexCount > 0);
            this.auxiliaryVertexCount = auxiliaryVertexCount;
            return this;
        }

        @Override
        public SogsPsuConfig build() {
            Preconditions.checkArgument(auxiliaryVertexCount % auxiliaryDegree == 0);
            return new SogsPsuConfig(this);
        }
    }
}
