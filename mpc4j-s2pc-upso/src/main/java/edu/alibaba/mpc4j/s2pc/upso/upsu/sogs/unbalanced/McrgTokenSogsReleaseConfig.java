package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;

/**
 * MCRG-token SOGS conditional release config.
 *
 * <p>This release profile assumes an ePSU / pnMCRG carrier has already produced pad pairs {@code (u_i, v_i)} where
 * miss rows satisfy {@code u_i = v_i} and hit rows satisfy {@code u_i != v_i}. It does not generate the carrier.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class McrgTokenSogsReleaseConfig extends AbstractMultiPartyPtoConfig {

    private McrgTokenSogsReleaseConfig() {
        super(SecurityModel.SEMI_HONEST);
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<McrgTokenSogsReleaseConfig> {
        @Override
        public McrgTokenSogsReleaseConfig build() {
            return new McrgTokenSogsReleaseConfig();
        }
    }
}
