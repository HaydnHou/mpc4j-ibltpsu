package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtFactory;

/**
 * TCL23 PS-OPRF based folded share-output PM-PEQT config.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23PsOprfFoldedSharePmPeqtConfig extends AbstractMultiPartyPtoConfig
    implements FoldedSharePmPeqtConfig {
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
     * Z2 circuit config for hidden OR folding.
     */
    private final Z2cConfig z2cConfig;

    private Tcl23PsOprfFoldedSharePmPeqtConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.dosnConfig, builder.oprfConfig, builder.peqtConfig,
            builder.z2cConfig);
        dosnConfig = builder.dosnConfig;
        oprfConfig = builder.oprfConfig;
        peqtConfig = builder.peqtConfig;
        z2cConfig = builder.z2cConfig;
    }

    @Override
    public FoldedSharePmPeqtFactory.FoldedSharePmPeqtType getPtoType() {
        return FoldedSharePmPeqtFactory.FoldedSharePmPeqtType.TCL23_PS_OPRF;
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

    public Z2cConfig getZ2cConfig() {
        return z2cConfig;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tcl23PsOprfFoldedSharePmPeqtConfig> {
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
         * Z2 circuit config.
         */
        private Z2cConfig z2cConfig;

        public Builder() {
            dosnConfig = DosnFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
            oprfConfig = OprfFactory.createOprfDefaultConfig(SecurityModel.SEMI_HONEST);
            peqtConfig = PeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
            z2cConfig = Z2cFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
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

        public Builder setZ2cConfig(Z2cConfig z2cConfig) {
            this.z2cConfig = z2cConfig;
            return this;
        }

        @Override
        public Tcl23PsOprfFoldedSharePmPeqtConfig build() {
            return new Tcl23PsOprfFoldedSharePmPeqtConfig(this);
        }
    }
}
