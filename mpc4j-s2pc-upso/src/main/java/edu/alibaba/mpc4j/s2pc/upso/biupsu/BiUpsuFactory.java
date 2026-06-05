package edu.alibaba.mpc4j.s2pc.upso.biupsu;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt.BaSsuIbltBiUpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt.BaSsuIbltBiUpsuFactory;

/**
 * Bi-output UPSU factory.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BiUpsuFactory {
    /**
     * private constructor.
     */
    private BiUpsuFactory() {
        // empty
    }

    /**
     * Bi-output UPSU protocol type.
     */
    public enum BiUpsuType {
        /**
         * BA-SSU-IBLT bi-output UPSU.
         */
        BA_SSU_IBLT,
    }

    /**
     * Creates a default config.
     *
     * @param type protocol type.
     * @return default config.
     */
    public static BiUpsuConfig createDefaultConfig(BiUpsuType type) {
        switch (type) {
            case BA_SSU_IBLT:
                return BaSsuIbltBiUpsuFactory.createDefaultConfig();
            default:
                throw new IllegalArgumentException("Invalid bi-output UPSU type: " + type);
        }
    }

    /**
     * Creates a sender.
     *
     * @param senderRpc sender RPC.
     * @param receiverParty receiver party.
     * @param config config.
     * @return sender.
     */
    public static BiUpsuSender createSender(Rpc senderRpc, Party receiverParty, BiUpsuConfig config) {
        switch (config.getPtoType()) {
            case BA_SSU_IBLT:
                return BaSsuIbltBiUpsuFactory.createSender(
                    senderRpc, receiverParty, (BaSsuIbltBiUpsuConfig) config
                );
            default:
                throw new IllegalArgumentException("Invalid bi-output UPSU type: " + config.getPtoType());
        }
    }

    /**
     * Creates a receiver.
     *
     * @param receiverRpc receiver RPC.
     * @param senderParty sender party.
     * @param config config.
     * @return receiver.
     */
    public static BiUpsuReceiver createReceiver(Rpc receiverRpc, Party senderParty, BiUpsuConfig config) {
        switch (config.getPtoType()) {
            case BA_SSU_IBLT:
                return BaSsuIbltBiUpsuFactory.createReceiver(
                    receiverRpc, senderParty, (BaSsuIbltBiUpsuConfig) config
                );
            default:
                throw new IllegalArgumentException("Invalid bi-output UPSU type: " + config.getPtoType());
        }
    }
}
