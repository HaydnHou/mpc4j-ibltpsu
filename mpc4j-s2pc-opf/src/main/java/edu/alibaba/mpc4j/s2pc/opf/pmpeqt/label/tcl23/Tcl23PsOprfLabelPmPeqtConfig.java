package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.LabelPmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotFactory;

/**
 * TCL23 PS-OPRF based label-output PM-PEQT config.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class Tcl23PsOprfLabelPmPeqtConfig extends AbstractMultiPartyPtoConfig implements LabelPmPeqtConfig {
    /**
     * OSN config.
     */
    private final DosnConfig dosnConfig;
    /**
     * OPRF config.
     */
    private final OprfConfig oprfConfig;
    /**
     * share-output PEQT config.
     */
    private final PeqtConfig peqtConfig;
    /**
     * COT config for the label adapter.
     */
    private final CotConfig cotConfig;

    private Tcl23PsOprfLabelPmPeqtConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.dosnConfig, builder.oprfConfig, builder.peqtConfig, builder.cotConfig);
        dosnConfig = builder.dosnConfig;
        oprfConfig = builder.oprfConfig;
        peqtConfig = builder.peqtConfig;
        cotConfig = builder.cotConfig;
    }

    @Override
    public LabelPmPeqtFactory.LabelPmPeqtType getPtoType() {
        return LabelPmPeqtFactory.LabelPmPeqtType.TCL23_PS_OPRF;
    }

    public DosnConfig getOsnConfig() {
        return dosnConfig;
    }

    public OprfConfig getOprfConfig() {
        return oprfConfig;
    }

    public PeqtConfig getPeqtConfig() {
        return peqtConfig;
    }

    public CotConfig getCotConfig() {
        return cotConfig;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tcl23PsOprfLabelPmPeqtConfig> {
        /**
         * OSN config.
         */
        private DosnConfig dosnConfig;
        /**
         * OPRF config.
         */
        private OprfConfig oprfConfig;
        /**
         * share-output PEQT config.
         */
        private PeqtConfig peqtConfig;
        /**
         * COT config.
         */
        private CotConfig cotConfig;

        public Builder() {
            dosnConfig = DosnFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
            oprfConfig = OprfFactory.createOprfDefaultConfig(SecurityModel.SEMI_HONEST);
            peqtConfig = PeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
            cotConfig = CotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
        }

        public Builder setOsnConfig(DosnConfig dosnConfig) {
            this.dosnConfig = dosnConfig;
            return this;
        }

        public Builder setOprfConfig(OprfConfig oprfConfig) {
            this.oprfConfig = oprfConfig;
            return this;
        }

        public Builder setPeqtConfig(PeqtConfig peqtConfig) {
            this.peqtConfig = peqtConfig;
            return this;
        }

        public Builder setCotConfig(CotConfig cotConfig) {
            this.cotConfig = cotConfig;
            return this;
        }

        @Override
        public Tcl23PsOprfLabelPmPeqtConfig build() {
            return new Tcl23PsOprfLabelPmPeqtConfig(this);
        }
    }
}
