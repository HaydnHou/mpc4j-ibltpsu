package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierSender;

/**
 * Row-level share relation carrier factory.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class RowShareRelationCarrierFactory implements PtoFactory {
    /**
     * private constructor.
     */
    private RowShareRelationCarrierFactory() {
        // empty
    }

    /**
     * Row-level share relation carrier type.
     */
    public enum RowShareRelationCarrierType {
        /**
         * Adapter over the current folded share-output PM-PEQT route.
         */
        FOLDED_PMPEQT,
    }

    /**
     * Creates a sender.
     *
     * @param senderRpc     sender RPC.
     * @param receiverParty receiver party.
     * @param config        config.
     * @return sender.
     */
    public static RowShareRelationCarrierSender createSender(Rpc senderRpc, Party receiverParty,
                                                             RowShareRelationCarrierConfig config) {
        RowShareRelationCarrierType type = config.getPtoType();
        switch (type) {
            case FOLDED_PMPEQT:
                return new FoldedPmPeqtRowShareCarrierSender(
                    senderRpc, receiverParty, (FoldedPmPeqtRowShareCarrierConfig) config
                );
            default:
                throw new IllegalArgumentException(
                    "Invalid " + RowShareRelationCarrierType.class.getSimpleName() + ": " + type.name()
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
    public static RowShareRelationCarrierReceiver createReceiver(Rpc receiverRpc, Party senderParty,
                                                                 RowShareRelationCarrierConfig config) {
        RowShareRelationCarrierType type = config.getPtoType();
        switch (type) {
            case FOLDED_PMPEQT:
                return new FoldedPmPeqtRowShareCarrierReceiver(
                    receiverRpc, senderParty, (FoldedPmPeqtRowShareCarrierConfig) config
                );
            default:
                throw new IllegalArgumentException(
                    "Invalid " + RowShareRelationCarrierType.class.getSimpleName() + ": " + type.name()
                );
        }
    }

    /**
     * Creates the default semi-honest config.
     *
     * @param securityModel security model.
     * @return default config.
     */
    public static RowShareRelationCarrierConfig createDefaultConfig(SecurityModel securityModel) {
        switch (securityModel) {
            case IDEAL:
            case SEMI_HONEST:
                return new FoldedPmPeqtRowShareCarrierConfig.Builder().build();
            case MALICIOUS:
            default:
                throw new IllegalArgumentException(
                    "Invalid " + SecurityModel.class.getSimpleName() + ": " + securityModel.name()
                );
        }
    }
}
