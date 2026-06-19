package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtFactory;

/**
 * TCL23 Byte-ECC-DDH based folded share-output PM-PEQT config.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23ByteEccDdhFoldedSharePmPeqtConfig extends AbstractMultiPartyPtoConfig
    implements FoldedSharePmPeqtConfig {
    /**
     * share-output PEQT config.
     */
    private final PeqtConfig peqtConfig;
    /**
     * Z2 circuit config for hidden OR folding.
     */
    private final Z2cConfig z2cConfig;
    /**
     * Whether to use the compact PEQT digest byte length ceil((stats + 2log(size)) / 8).
     */
    private final boolean compactPeqtByteLength;

    private Tcl23ByteEccDdhFoldedSharePmPeqtConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.peqtConfig, builder.z2cConfig);
        peqtConfig = builder.peqtConfig;
        z2cConfig = builder.z2cConfig;
        compactPeqtByteLength = builder.compactPeqtByteLength;
    }

    @Override
    public FoldedSharePmPeqtFactory.FoldedSharePmPeqtType getPtoType() {
        return FoldedSharePmPeqtFactory.FoldedSharePmPeqtType.TCL23_BYTE_ECC_DDH;
    }

    public PeqtConfig getPeqtConfig() {
        return peqtConfig;
    }

    public Z2cConfig getZ2cConfig() {
        return z2cConfig;
    }

    public boolean isCompactPeqtByteLength() {
        return compactPeqtByteLength;
    }

    /**
     * Builder.
     */
    public static class Builder
        implements org.apache.commons.lang3.builder.Builder<Tcl23ByteEccDdhFoldedSharePmPeqtConfig> {
        /**
         * share-output PEQT config.
         */
        private PeqtConfig peqtConfig;
        /**
         * Z2 circuit config.
         */
        private Z2cConfig z2cConfig;
        /**
         * Whether to use compact PEQT digest byte length.
         */
        private boolean compactPeqtByteLength;

        public Builder() {
            peqtConfig = PeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
            z2cConfig = Z2cFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, false);
            compactPeqtByteLength = true;
        }

        public Builder setPeqtConfig(PeqtConfig peqtConfig) {
            this.peqtConfig = peqtConfig;
            return this;
        }

        public Builder setZ2cConfig(Z2cConfig z2cConfig) {
            this.z2cConfig = z2cConfig;
            return this;
        }

        public Builder setCompactPeqtByteLength(boolean compactPeqtByteLength) {
            this.compactPeqtByteLength = compactPeqtByteLength;
            return this;
        }

        @Override
        public Tcl23ByteEccDdhFoldedSharePmPeqtConfig build() {
            return new Tcl23ByteEccDdhFoldedSharePmPeqtConfig(this);
        }
    }
}
