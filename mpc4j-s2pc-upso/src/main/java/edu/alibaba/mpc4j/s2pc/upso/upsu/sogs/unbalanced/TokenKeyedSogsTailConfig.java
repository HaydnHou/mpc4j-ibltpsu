package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

/**
 * Token-keyed aggregate SOGS tail config.
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsTailConfig extends AbstractMultiPartyPtoConfig {
    /**
     * Core COT config for row correction.
     */
    private final CoreCotConfig coreCotConfig;

    private TokenKeyedSogsTailConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.coreCotConfig);
        coreCotConfig = builder.coreCotConfig;
    }

    /**
     * Gets core COT config.
     */
    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<TokenKeyedSogsTailConfig> {
        /**
         * Core COT config.
         */
        private CoreCotConfig coreCotConfig;

        public Builder() {
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
            this.coreCotConfig = coreCotConfig;
            return this;
        }

        @Override
        public TokenKeyedSogsTailConfig build() {
            return new TokenKeyedSogsTailConfig(this);
        }
    }
}
