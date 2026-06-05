package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Production UP-BA-UPOT queue-probe protocol description.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbePtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) 7642060509501739231L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_SSU_IBLT_PRODUCTION_UNION_PROBE";

    /**
     * protocol steps.
     */
    enum PtoStep {
        /**
         * offline COT / ROT mask material.
         */
        OFFLINE_COT,
        /**
         * exchange one fixed-size masked capsule for a public bucket probe.
         */
        ONLINE_PROBE_CAPSULE,
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
