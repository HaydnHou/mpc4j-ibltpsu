package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Token-keyed aggregate SOGS tail protocol description.
 *
 * <p>This tail takes secret-shared row release bits and opens only the aggregate token-keyed SOGS sketch.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
class TokenKeyedSogsTailPtoDesc implements PtoDesc {
    /**
     * Protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 2026061703L);
    /**
     * Protocol name.
     */
    private static final String PTO_NAME = "TOKEN_KEYED_SOGS_TAIL";

    /**
     * Protocol steps.
     */
    enum PtoStep {
        /**
         * Sender sends public row tokens.
         */
        SENDER_SEND_TOKENS,
        /**
         * Sender sends OT ciphertexts for row correction.
         */
        SENDER_SEND_OT_MASKS,
        /**
         * Sender opens its aggregate cell share.
         */
        SENDER_SEND_CELL_SHARE,
    }

    /**
     * Singleton.
     */
    private static final TokenKeyedSogsTailPtoDesc INSTANCE = new TokenKeyedSogsTailPtoDesc();

    private TokenKeyedSogsTailPtoDesc() {
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
