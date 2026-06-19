package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Share-cancel SOGS tail protocol description.
 *
 * <p>This protocol is a semantic wrapper around the token-keyed aggregate SOGS tail. Its input is a hidden row hit
 * share, not an opened hit / miss bit. The sender locally converts its hit share into a release share by XORing the
 * private real-row bit, so real miss rows enter the SOGS sketch and real hit rows cancel before peel.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
class ShareCancelSogsTailPtoDesc implements PtoDesc {
    /**
     * Protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 2026061901L);
    /**
     * Protocol name.
     */
    private static final String PTO_NAME = "SHARE_CANCEL_SOGS_TAIL";

    /**
     * Singleton.
     */
    private static final ShareCancelSogsTailPtoDesc INSTANCE = new ShareCancelSogsTailPtoDesc();

    private ShareCancelSogsTailPtoDesc() {
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
