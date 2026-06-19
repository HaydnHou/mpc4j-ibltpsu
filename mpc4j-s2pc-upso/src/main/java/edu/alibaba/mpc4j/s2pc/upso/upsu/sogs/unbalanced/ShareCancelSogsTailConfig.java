package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;

/**
 * Share-cancel SOGS tail config.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsTailConfig extends AbstractMultiPartyPtoConfig {
    /**
     * Token-keyed tail config.
     */
    private final TokenKeyedSogsTailConfig tokenKeyedSogsTailConfig;

    private ShareCancelSogsTailConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.tokenKeyedSogsTailConfig);
        tokenKeyedSogsTailConfig = builder.tokenKeyedSogsTailConfig;
    }

    /**
     * Gets the token-keyed SOGS tail config.
     */
    public TokenKeyedSogsTailConfig getTokenKeyedSogsTailConfig() {
        return tokenKeyedSogsTailConfig;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<ShareCancelSogsTailConfig> {
        /**
         * Token-keyed tail config.
         */
        private TokenKeyedSogsTailConfig tokenKeyedSogsTailConfig;

        public Builder() {
            tokenKeyedSogsTailConfig = new TokenKeyedSogsTailConfig.Builder().build();
        }

        public Builder setTokenKeyedSogsTailConfig(TokenKeyedSogsTailConfig tokenKeyedSogsTailConfig) {
            this.tokenKeyedSogsTailConfig = tokenKeyedSogsTailConfig;
            return this;
        }

        @Override
        public ShareCancelSogsTailConfig build() {
            return new ShareCancelSogsTailConfig(this);
        }
    }
}
