package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * SC-SOGS UPSU protocol description.
 *
 * <p>SC-SOGS is the share-cancel SOGS profile: TCL-style relation prefix, folded share-output relation carrier, and
 * aggregate SOGS tail. It is intentionally exposed as a separate UPSU protocol instead of an MC-SOGS config
 * variant.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ScSogsUpsuPtoDesc implements PtoDesc {
    /**
     * Protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 2026061902L);
    /**
     * Protocol name.
     */
    private static final String PTO_NAME = "SC_SOGS_UPSU";

    /**
     * Protocol step.
     */
    public enum PtoStep {
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
    private static final ScSogsUpsuPtoDesc INSTANCE = new ScSogsUpsuPtoDesc();

    /**
     * Private constructor.
     */
    private ScSogsUpsuPtoDesc() {
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
