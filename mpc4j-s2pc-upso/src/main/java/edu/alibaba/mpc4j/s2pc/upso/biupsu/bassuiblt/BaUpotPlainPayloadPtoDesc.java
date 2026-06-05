package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Plain payload-bound BA-UPOT transport protocol description.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadPtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) 3841082409238296781L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_UPOT_PLAIN_PAYLOAD";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * sender sends payload-bound output capsules.
         */
        SENDER_SEND_OUTPUT_CAPSULE,
    }

    /**
     * singleton instance.
     */
    private static final BaUpotPlainPayloadPtoDesc INSTANCE = new BaUpotPlainPayloadPtoDesc();

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private BaUpotPlainPayloadPtoDesc() {
        // empty
    }

    static BaUpotPlainPayloadPtoDesc getInstance() {
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
