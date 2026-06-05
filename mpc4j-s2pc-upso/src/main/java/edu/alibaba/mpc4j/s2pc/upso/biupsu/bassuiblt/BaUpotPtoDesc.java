package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Specialized BA-UPOT standalone benchmark protocol description.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) 7318457842660182473L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_UPOT_STANDALONE_BENCH";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * sender sends fixed-shape capsules.
         */
        SENDER_SEND_CAPSULE,
    }

    /**
     * singleton instance.
     */
    private static final BaUpotPtoDesc INSTANCE = new BaUpotPtoDesc();

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private BaUpotPtoDesc() {
        // empty
    }

    static BaUpotPtoDesc getInstance() {
        return INSTANCE;
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
