package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * BA-SSU-IBLT production UP-BA-UPOT bucket-probe protocol description.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbePtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = 1469367218;
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_SSU_IBLT_PRODUCTION_UP_BA_UPOT";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * fixed offline COT / ROT material slot.
         */
        OFFLINE_COT,
        /**
         * receiver sends fixed-shape choice correction for the next precomputed COT slice.
         */
        ONLINE_CHOICE_CORRECTION,
        /**
         * sender sends one fixed-shape opaque probe capsule.
         */
        ONLINE_PROBE_CAPSULE,
        /**
         * receiver sends one fixed-shape public probe result.
         */
        ONLINE_FIXED_RESULT,
        /**
         * receiver sends fixed-shape choice corrections for a public probe batch.
         */
        ONLINE_CHOICE_CORRECTION_BATCH,
        /**
         * sender sends fixed-shape opaque probe capsules for a public probe batch.
         */
        ONLINE_PROBE_CAPSULE_BATCH,
        /**
         * receiver sends fixed-shape public probe results for a public probe batch.
         */
        ONLINE_FIXED_RESULT_BATCH,
    }

    /**
     * singleton instance.
     */
    private static final BaSsuIbltProductionUnionProbePtoDesc INSTANCE =
        new BaSsuIbltProductionUnionProbePtoDesc();

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private BaSsuIbltProductionUnionProbePtoDesc() {
        // empty
    }

    static BaSsuIbltProductionUnionProbePtoDesc getInstance() {
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
