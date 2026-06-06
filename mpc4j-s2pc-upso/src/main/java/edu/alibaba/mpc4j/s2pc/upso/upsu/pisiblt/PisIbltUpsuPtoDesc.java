package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * PISF-IBLT enhanced UPSU protocol description.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class PisIbltUpsuPtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) 893721645732804121L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "PISF_IBLT_UPSU";
    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * sender sends retry salt.
         */
        SENDER_SEND_PISF_SALT,
        /**
         * sender sends source-split table.
         */
        SENDER_SEND_PISF_TABLE,
        /**
         * receiver sends retry result.
         */
        RECEIVER_SEND_RETRY_RESULT,
    }
    /**
     * singleton mode.
     */
    private static final PisIbltUpsuPtoDesc INSTANCE = new PisIbltUpsuPtoDesc();

    /**
     * Gets the singleton instance.
     *
     * @return the singleton instance.
     */
    static PisIbltUpsuPtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private PisIbltUpsuPtoDesc() {
        // empty
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
