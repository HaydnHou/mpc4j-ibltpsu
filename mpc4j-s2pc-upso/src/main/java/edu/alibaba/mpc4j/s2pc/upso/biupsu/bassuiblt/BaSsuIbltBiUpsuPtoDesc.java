package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * BA-SSU-IBLT bi-output UPSU protocol description.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaSsuIbltBiUpsuPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = 1469367217;
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "BA_SSU_IBLT_BI_UPSU";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * Queue-peel secure path initializes MP-OPRF / SS-OTag.
         */
        INIT_OPRF_TAGS,
        /**
         * Queue-peel secure path probes one public bucket or a public batch of buckets.
         */
        QUEUE_PROBE,
        /**
         * Queue-peel secure path publishes retry success/failure and probe count.
         */
        RETRY_STATUS,
        /**
         * Queue-peel secure path publishes the final union or public failure.
         */
        FINAL_UNION,
        /**
         * Sender sends fixed source-layer bucket payload.
         */
        SENDER_SEND_FIXED_LAYER,
        /**
         * Receiver sends fixed source-layer bucket payload.
         */
        RECEIVER_SEND_FIXED_LAYER,
    }

    /**
     * singleton instance.
     */
    private static final BaSsuIbltBiUpsuPtoDesc INSTANCE = new BaSsuIbltBiUpsuPtoDesc();

    static BaSsuIbltBiUpsuPtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private BaSsuIbltBiUpsuPtoDesc() {
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
