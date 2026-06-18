package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * TCL23 PS-OPRF based label-output PM-PEQT protocol description.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class Tcl23PsOprfLabelPmPeqtPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 2026061807L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "TCL23_PS_OPRF_LABEL_PMPEQT";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * receiver sends encrypted label pairs to sender.
         */
        RECEIVER_SEND_LABEL_CIPHERTEXTS,
    }

    /**
     * singleton mode.
     */
    private static final Tcl23PsOprfLabelPmPeqtPtoDesc INSTANCE = new Tcl23PsOprfLabelPmPeqtPtoDesc();

    /**
     * private constructor.
     */
    private Tcl23PsOprfLabelPmPeqtPtoDesc() {
        // empty
    }

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
