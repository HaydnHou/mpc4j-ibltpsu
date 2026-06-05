package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * COT-backed BA-UnionPeel-OT secure fixed-bucket protocol description.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtSecurePtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) 5178392715093347176L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_UNION_PEEL_OT_SECURE";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * sender sends fixed masked output capsules.
         */
        SENDER_SEND_MASKED_OUTPUT_CAPSULES,
    }

    /**
     * singleton instance.
     */
    private static final BaUnionPeelOtSecurePtoDesc INSTANCE = new BaUnionPeelOtSecurePtoDesc();

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private BaUnionPeelOtSecurePtoDesc() {
        // empty
    }

    static BaUnionPeelOtSecurePtoDesc getInstance() {
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
