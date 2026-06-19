package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtSender;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtSender;

/**
 * Folded share-output PM-PEQT factory.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class FoldedSharePmPeqtFactory implements PtoFactory {
    /**
     * private constructor.
     */
    private FoldedSharePmPeqtFactory() {
        // empty
    }

    /**
     * Folded share-output PM-PEQT type.
     */
    public enum FoldedSharePmPeqtType {
        /**
         * TCL23 permute-share OPRF prefix + share-output PEQT + hidden OR fold.
         */
        TCL23_PS_OPRF,
        /**
         * TCL23 Byte-ECC-DDH prefix + share-output PEQT + hidden OR fold.
         */
        TCL23_BYTE_ECC_DDH,
    }

    /**
     * Creates a sender.
     *
     * @param senderRpc     sender RPC.
     * @param receiverParty receiver party.
     * @param config        config.
     * @return sender.
     */
    public static FoldedSharePmPeqtSender createSender(Rpc senderRpc, Party receiverParty,
                                                       FoldedSharePmPeqtConfig config) {
        FoldedSharePmPeqtType type = config.getPtoType();
        switch (type) {
            case TCL23_PS_OPRF:
                return new Tcl23PsOprfFoldedSharePmPeqtSender(
                    senderRpc, receiverParty, (Tcl23PsOprfFoldedSharePmPeqtConfig) config
                );
            case TCL23_BYTE_ECC_DDH:
                return new Tcl23ByteEccDdhFoldedSharePmPeqtSender(
                    senderRpc, receiverParty, (Tcl23ByteEccDdhFoldedSharePmPeqtConfig) config
                );
            default:
                throw new IllegalArgumentException(
                    "Invalid " + FoldedSharePmPeqtType.class.getSimpleName() + ": " + type.name()
                );
        }
    }

    /**
     * Creates a receiver.
     *
     * @param receiverRpc receiver RPC.
     * @param senderParty sender party.
     * @param config      config.
     * @return receiver.
     */
    public static FoldedSharePmPeqtReceiver createReceiver(Rpc receiverRpc, Party senderParty,
                                                           FoldedSharePmPeqtConfig config) {
        FoldedSharePmPeqtType type = config.getPtoType();
        switch (type) {
            case TCL23_PS_OPRF:
                return new Tcl23PsOprfFoldedSharePmPeqtReceiver(
                    receiverRpc, senderParty, (Tcl23PsOprfFoldedSharePmPeqtConfig) config
                );
            case TCL23_BYTE_ECC_DDH:
                return new Tcl23ByteEccDdhFoldedSharePmPeqtReceiver(
                    receiverRpc, senderParty, (Tcl23ByteEccDdhFoldedSharePmPeqtConfig) config
                );
            default:
                throw new IllegalArgumentException(
                    "Invalid " + FoldedSharePmPeqtType.class.getSimpleName() + ": " + type.name()
                );
        }
    }

    /**
     * Creates the default semi-honest config.
     *
     * @param securityModel security model.
     * @return default config.
     */
    public static FoldedSharePmPeqtConfig createDefaultConfig(SecurityModel securityModel) {
        switch (securityModel) {
            case IDEAL:
            case SEMI_HONEST:
                return new Tcl23ByteEccDdhFoldedSharePmPeqtConfig.Builder().build();
            case MALICIOUS:
            default:
                throw new IllegalArgumentException(
                    "Invalid " + SecurityModel.class.getSimpleName() + ": " + securityModel.name()
                );
        }
    }
}
