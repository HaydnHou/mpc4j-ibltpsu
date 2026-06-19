package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.ShareCancelSogsTailConfig;

/**
 * SC-SOGS UPSU config.
 *
 * <p>This config fixes the release layer to the share-cancel SOGS tail and exposes the relation layer as a
 * row-share relation carrier. The default carrier is a folded PM-PEQT adapter; a native row-level carrier can replace
 * it without changing the SC-SOGS protocol surface.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ScSogsUpsuConfig extends AbstractMultiPartyPtoConfig implements UpsuConfig {
    /**
     * Single-query OPRF config for the TCL23-style FHE prefix.
     */
    private final SqOprfConfig sqOprfConfig;
    /**
     * Row-level share relation carrier config.
     */
    private final RowShareRelationCarrierConfig rowShareRelationCarrierConfig;
    /**
     * Share-cancel SOGS tail config.
     */
    private final ShareCancelSogsTailConfig shareCancelTailConfig;
    /**
     * SOGS degree.
     */
    private final int degree;
    /**
     * FHE relation-prefix parameters.
     */
    private final ScSogsUpsuParams params;
    /**
     * Optional fixed SOGS cell number. Non-positive means derive from max sender size.
     */
    private final int cellNum;

    private ScSogsUpsuConfig(Builder builder) {
        super(
            SecurityModel.SEMI_HONEST, builder.sqOprfConfig, builder.rowShareRelationCarrierConfig,
            builder.shareCancelTailConfig
        );
        sqOprfConfig = builder.sqOprfConfig;
        rowShareRelationCarrierConfig = builder.rowShareRelationCarrierConfig;
        shareCancelTailConfig = builder.shareCancelTailConfig;
        degree = builder.degree;
        params = builder.params;
        cellNum = builder.cellNum;
    }

    @Override
    public UpsuFactory.UpsuType getPtoType() {
        return UpsuFactory.UpsuType.SC_SOGS;
    }

    public SqOprfConfig getSqOprfConfig() {
        return sqOprfConfig;
    }

    public RowShareRelationCarrierConfig getRowShareRelationCarrierConfig() {
        return rowShareRelationCarrierConfig;
    }

    public ShareCancelSogsTailConfig getShareCancelTailConfig() {
        return shareCancelTailConfig;
    }

    public int getDegree() {
        return degree;
    }

    public ScSogsUpsuParams getParams() {
        return params;
    }

    public int getCellNum(int maxSenderElementSize) {
        return cellNum > 0 ? cellNum : Math.max(2, maxSenderElementSize * 2);
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<ScSogsUpsuConfig> {
        /**
         * Single-query OPRF config.
         */
        private SqOprfConfig sqOprfConfig;
        /**
         * Row-level share relation carrier config.
         */
        private RowShareRelationCarrierConfig rowShareRelationCarrierConfig;
        /**
         * Share-cancel tail config.
         */
        private ShareCancelSogsTailConfig shareCancelTailConfig;
        /**
         * SOGS degree.
         */
        private int degree;
        /**
         * FHE relation-prefix parameters.
         */
        private ScSogsUpsuParams params;
        /**
         * Fixed SOGS cell number.
         */
        private int cellNum;

        public Builder() {
            sqOprfConfig = SqOprfFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
            rowShareRelationCarrierConfig = RowShareRelationCarrierFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
            shareCancelTailConfig = new ShareCancelSogsTailConfig.Builder().build();
            degree = 3;
            params = ScSogsUpsuParams.RECEIVER_16M_SENDER_MAX_1024;
            cellNum = 0;
        }

        public Builder setSqOprfConfig(SqOprfConfig sqOprfConfig) {
            this.sqOprfConfig = sqOprfConfig;
            return this;
        }

        public Builder setFoldedSharePmPeqtConfig(FoldedSharePmPeqtConfig foldedSharePmPeqtConfig) {
            rowShareRelationCarrierConfig = new FoldedPmPeqtRowShareCarrierConfig.Builder()
                .setFoldedSharePmPeqtConfig(foldedSharePmPeqtConfig)
                .build();
            return this;
        }

        public Builder setRowShareRelationCarrierConfig(RowShareRelationCarrierConfig rowShareRelationCarrierConfig) {
            this.rowShareRelationCarrierConfig = rowShareRelationCarrierConfig;
            return this;
        }

        public Builder setShareCancelTailConfig(ShareCancelSogsTailConfig shareCancelTailConfig) {
            this.shareCancelTailConfig = shareCancelTailConfig;
            return this;
        }

        public Builder setDegree(int degree) {
            this.degree = degree;
            return this;
        }

        public Builder setParams(ScSogsUpsuParams params) {
            this.params = params;
            return this;
        }

        public Builder setCellNum(int cellNum) {
            this.cellNum = cellNum;
            return this;
        }

        @Override
        public ScSogsUpsuConfig build() {
            return new ScSogsUpsuConfig(this);
        }
    }
}
