package edu.alibaba.mpc4j.s2pc.pso.psu.sogs;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * SOGS-PSU protocol description.
 *
 * <p>SOGS-PSU uses a source-oblivious graph sketch and union-peel interaction.</p>
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 7961344052376602173L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "SOGS_PSU";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * server sends sketch hash key.
         */
        SERVER_SEND_SKETCH_KEY,
        /**
         * client sends OT-encrypted singleton elements.
         */
        CLIENT_SEND_SINGLETON_ELEMENTS,
        /**
         * server sends OT-encrypted union-peel messages.
         */
        SERVER_SEND_UNION_PEEL_MESSAGES,
        /**
         * client sends peeled union elements.
         */
        CLIENT_SEND_PEELED_ELEMENTS,
        /**
         * client sends whether its remaining set is empty at a two-tier phase boundary.
         */
        CLIENT_SEND_PHASE_STATUS,
        /**
         * server sends whether to finish or enter auxiliary phase.
         */
        SERVER_SEND_PHASE_DECISION,
        /**
         * server sends finish status.
         */
        SERVER_SEND_FINISH,
    }

    /**
     * singleton.
     */
    private static final SogsPsuPtoDesc INSTANCE = new SogsPsuPtoDesc();

    /**
     * private constructor.
     */
    private SogsPsuPtoDesc() {
        // empty
    }

    /**
     * Gets the singleton instance.
     *
     * @return the singleton instance.
     */
    public static PtoDesc getInstance() {
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
