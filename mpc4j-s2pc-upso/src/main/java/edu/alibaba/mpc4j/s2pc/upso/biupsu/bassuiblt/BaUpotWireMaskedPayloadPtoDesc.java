package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Wire-masked payload-bound BA-UPOT transport protocol description.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotWireMaskedPayloadPtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) 8274109187736281406L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_UPOT_WIRE_MASKED_PAYLOAD";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * sender sends wire-masked output capsules.
         */
        SENDER_SEND_MASKED_OUTPUT_CAPSULE,
    }

    /**
     * singleton instance.
     */
    private static final BaUpotWireMaskedPayloadPtoDesc INSTANCE = new BaUpotWireMaskedPayloadPtoDesc();

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private BaUpotWireMaskedPayloadPtoDesc() {
        // empty
    }

    static BaUpotWireMaskedPayloadPtoDesc getInstance() {
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
