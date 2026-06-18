package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.sqoprf.SqOprfFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced.McrgTokenSogsReleaseConfig;

/**
 * MC-SOGS UPSU config.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuConfig extends AbstractMultiPartyPtoConfig implements UpsuConfig {
    /**
     * Single-query OPRF config for the TCL23 FHE prefix.
     */
    private final SqOprfConfig sqOprfConfig;
    /**
     * Label-output PM-PEQT config.
     */
    private final LabelPmPeqtConfig labelPmPeqtConfig;
    /**
     * MCRG-token SOGS release config.
     */
    private final McrgTokenSogsReleaseConfig releaseConfig;
    /**
     * SOGS degree.
     */
    private final int degree;
    /**
     * Optional fixed SOGS cell number. Non-positive means derive from max sender size.
     */
    private final int cellNum;

    private McSogsUpsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.sqOprfConfig, builder.labelPmPeqtConfig, builder.releaseConfig);
        sqOprfConfig = builder.sqOprfConfig;
        labelPmPeqtConfig = builder.labelPmPeqtConfig;
        releaseConfig = builder.releaseConfig;
        degree = builder.degree;
        cellNum = builder.cellNum;
    }

    @Override
    public UpsuFactory.UpsuType getPtoType() {
        return UpsuFactory.UpsuType.MC_SOGS;
    }

    public SqOprfConfig getSqOprfConfig() {
        return sqOprfConfig;
    }

    public LabelPmPeqtConfig getLabelPmPeqtConfig() {
        return labelPmPeqtConfig;
    }

    public McrgTokenSogsReleaseConfig getReleaseConfig() {
        return releaseConfig;
    }

    public int getDegree() {
        return degree;
    }

    public int getCellNum(int maxSenderElementSize) {
        return cellNum > 0 ? cellNum : Math.max(2, maxSenderElementSize * 2);
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<McSogsUpsuConfig> {
        /**
         * Single-query OPRF config.
         */
        private SqOprfConfig sqOprfConfig;
        /**
         * Label-output PM-PEQT config.
         */
        private LabelPmPeqtConfig labelPmPeqtConfig;
        /**
         * Release config.
         */
        private McrgTokenSogsReleaseConfig releaseConfig;
        /**
         * SOGS degree.
         */
        private int degree;
        /**
         * Fixed SOGS cell number.
         */
        private int cellNum;

        public Builder() {
            sqOprfConfig = SqOprfFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
            labelPmPeqtConfig = LabelPmPeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
            releaseConfig = new McrgTokenSogsReleaseConfig.Builder().build();
            degree = 3;
            cellNum = 0;
        }

        public Builder setSqOprfConfig(SqOprfConfig sqOprfConfig) {
            this.sqOprfConfig = sqOprfConfig;
            return this;
        }

        public Builder setLabelPmPeqtConfig(LabelPmPeqtConfig labelPmPeqtConfig) {
            this.labelPmPeqtConfig = labelPmPeqtConfig;
            return this;
        }

        public Builder setReleaseConfig(McrgTokenSogsReleaseConfig releaseConfig) {
            this.releaseConfig = releaseConfig;
            return this;
        }

        public Builder setDegree(int degree) {
            this.degree = degree;
            return this;
        }

        public Builder setCellNum(int cellNum) {
            this.cellNum = cellNum;
            return this;
        }

        @Override
        public McSogsUpsuConfig build() {
            return new McSogsUpsuConfig(this);
        }
    }
}
