package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * MCRG-token SOGS conditional release protocol description.
 *
 * <p>The sender sends fixed-length records masked by pnMCRG pads. Tokens and SOGS cell material are hidden for failed
 * hit rows.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
class McrgTokenSogsReleasePtoDesc implements PtoDesc {
    /**
     * Protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 2026061704L);
    /**
     * Protocol name.
     */
    private static final String PTO_NAME = "MCRG_TOKEN_SOGS_RELEASE";

    /**
     * Protocol steps.
     */
    enum PtoStep {
        /**
         * Sender sends fixed-length pnMCRG-pad masked atom records.
         */
        SENDER_SEND_MASKED_RECORDS,
    }

    /**
     * Singleton.
     */
    private static final McrgTokenSogsReleasePtoDesc INSTANCE = new McrgTokenSogsReleasePtoDesc();

    private McrgTokenSogsReleasePtoDesc() {
        // empty
    }

    /**
     * Gets singleton instance.
     */
    static PtoDesc getInstance() {
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
