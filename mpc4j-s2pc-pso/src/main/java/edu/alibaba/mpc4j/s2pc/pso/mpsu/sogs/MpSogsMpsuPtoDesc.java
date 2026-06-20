package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * MP-SOGS MPSU protocol description.
 *
 * <p>MP-SOGS is an all-output multi-party PSU line. Every participant keeps a local SOGS sketch and
 * jointly evaluates batched union-peel cells. The only public cell output is {@code x} or {@code bottom}.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) -6538722618946305129L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "MP_SOGS_MPSU";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * Participants agree on public sketch parameters.
         */
        PARTICIPANTS_AGREE_PARAMS,
        /**
         * Participants evaluate a public batch of secure union-peel cells.
         */
        PARTICIPANTS_RUN_BATCH_UPEEL,
        /**
         * Participants enter a public delete barrier for newly opened union elements.
         */
        PARTICIPANTS_DELETE_OPENED_ELEMENTS,
        /**
         * Participants finish after no new element opens.
         */
        PARTICIPANTS_FINISH,
    }

    /**
     * singleton.
     */
    private static final MpSogsMpsuPtoDesc INSTANCE = new MpSogsMpsuPtoDesc();

    /**
     * private constructor.
     */
    private MpSogsMpsuPtoDesc() {
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
