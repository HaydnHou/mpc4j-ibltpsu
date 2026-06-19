package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierFactory;

/**
 * Folded PM-PEQT row-share relation carrier config.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class FoldedPmPeqtRowShareCarrierConfig extends AbstractMultiPartyPtoConfig
    implements RowShareRelationCarrierConfig {
    /**
     * Folded share-output PM-PEQT config.
     */
    private final FoldedSharePmPeqtConfig foldedSharePmPeqtConfig;

    private FoldedPmPeqtRowShareCarrierConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.foldedSharePmPeqtConfig);
        foldedSharePmPeqtConfig = builder.foldedSharePmPeqtConfig;
    }

    @Override
    public RowShareRelationCarrierFactory.RowShareRelationCarrierType getPtoType() {
        return RowShareRelationCarrierFactory.RowShareRelationCarrierType.FOLDED_PMPEQT;
    }

    @Override
    public InputType getInputType() {
        return InputType.DIGEST;
    }

    @Override
    public NetworkShape getNetworkShape() {
        return NetworkShape.ALPHA_BY_BIN;
    }

    public FoldedSharePmPeqtConfig getFoldedSharePmPeqtConfig() {
        return foldedSharePmPeqtConfig;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<FoldedPmPeqtRowShareCarrierConfig> {
        /**
         * Folded share-output PM-PEQT config.
         */
        private FoldedSharePmPeqtConfig foldedSharePmPeqtConfig;

        public Builder() {
            foldedSharePmPeqtConfig = FoldedSharePmPeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setFoldedSharePmPeqtConfig(FoldedSharePmPeqtConfig foldedSharePmPeqtConfig) {
            this.foldedSharePmPeqtConfig = foldedSharePmPeqtConfig;
            return this;
        }

        @Override
        public FoldedPmPeqtRowShareCarrierConfig build() {
            return new FoldedPmPeqtRowShareCarrierConfig(this);
        }
    }
}
