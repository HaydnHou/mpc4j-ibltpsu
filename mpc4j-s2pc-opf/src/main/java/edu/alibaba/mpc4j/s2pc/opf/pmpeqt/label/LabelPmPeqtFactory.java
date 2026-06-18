package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtSender;

/**
 * Label-output permuted matrix private equality test factory.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class LabelPmPeqtFactory implements PtoFactory {

    /**
     * private constructor.
     */
    private LabelPmPeqtFactory() {
        // empty
    }

    /**
     * Label PM-PEQT type.
     */
    public enum LabelPmPeqtType {
        /**
         * TCL23 permute-share OPRF prefix + share-output PEQT + label adapter.
         */
        TCL23_PS_OPRF,
    }

    /**
     * Creates a sender.
     *
     * @param senderRpc     sender RPC.
     * @param receiverParty receiver party.
     * @param config        config.
     * @return sender.
     */
    public static LabelPmPeqtSender createSender(Rpc senderRpc, Party receiverParty, LabelPmPeqtConfig config) {
        LabelPmPeqtType type = config.getPtoType();
        switch (type) {
            case TCL23_PS_OPRF:
                return new Tcl23PsOprfLabelPmPeqtSender(
                    senderRpc, receiverParty, (Tcl23PsOprfLabelPmPeqtConfig) config
                );
            default:
                throw new IllegalArgumentException(
                    "Invalid " + LabelPmPeqtType.class.getSimpleName() + ": " + type.name()
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
    public static LabelPmPeqtReceiver createReceiver(Rpc receiverRpc, Party senderParty, LabelPmPeqtConfig config) {
        LabelPmPeqtType type = config.getPtoType();
        switch (type) {
            case TCL23_PS_OPRF:
                return new Tcl23PsOprfLabelPmPeqtReceiver(
                    receiverRpc, senderParty, (Tcl23PsOprfLabelPmPeqtConfig) config
                );
            default:
                throw new IllegalArgumentException(
                    "Invalid " + LabelPmPeqtType.class.getSimpleName() + ": " + type.name()
                );
        }
    }

    /**
     * Creates the default semi-honest config.
     *
     * @param securityModel security model.
     * @return default config.
     */
    public static LabelPmPeqtConfig createDefaultConfig(SecurityModel securityModel) {
        switch (securityModel) {
            case IDEAL:
            case SEMI_HONEST:
                return new Tcl23PsOprfLabelPmPeqtConfig.Builder().build();
            case MALICIOUS:
            default:
                throw new IllegalArgumentException(
                    "Invalid " + SecurityModel.class.getSimpleName() + ": " + securityModel.name()
                );
        }
    }
}
