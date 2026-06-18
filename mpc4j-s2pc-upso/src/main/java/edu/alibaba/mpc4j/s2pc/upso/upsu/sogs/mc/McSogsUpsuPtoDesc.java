package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * MC-SOGS UPSU protocol description.
 *
 * <p>This profile keeps the TCL23 FHE relation prefix, replaces receiver-output PM-PEQT/COT release with
 * label-output PM-PEQT, and uses MCRG-token SOGS release for receiver-output {@code X \ Y} recovery.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuPtoDesc implements PtoDesc {
    /**
     * Protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) -6582148370642795132L);
    /**
     * Protocol name.
     */
    private static final String PTO_NAME = "MC_SOGS_UPSU";

    /**
     * Protocol step.
     */
    enum PtoStep {
        /**
         * Receiver sends cuckoo hash keys.
         */
        RECEIVER_SEND_CUCKOO_HASH_KEYS,
        /**
         * Sender sends FHE public keys.
         */
        SENDER_SEND_PUBLIC_KEYS,
        /**
         * Sender sends FHE query.
         */
        SENDER_SEND_QUERY,
        /**
         * Receiver sends FHE response.
         */
        RECEIVER_SEND_RESPONSE,
    }

    /**
     * Singleton instance.
     */
    private static final McSogsUpsuPtoDesc INSTANCE = new McSogsUpsuPtoDesc();

    /**
     * Private constructor.
     */
    private McSogsUpsuPtoDesc() {
        // empty
    }

    /**
     * Gets singleton instance.
     *
     * @return singleton instance.
     */
    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(getInstance());
    }

    @Override
    public int getPtoId() {
        return PTO_ID;
    }

    @Override
    public String getPtoName() {
        return PTO_NAME;
    }
}
