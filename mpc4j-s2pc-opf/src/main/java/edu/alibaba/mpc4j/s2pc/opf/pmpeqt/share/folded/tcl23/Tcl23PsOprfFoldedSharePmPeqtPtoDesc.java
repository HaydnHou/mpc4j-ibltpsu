package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * TCL23 PS-OPRF based folded share-output PM-PEQT protocol description.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23PsOprfFoldedSharePmPeqtPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 3180674011420190601L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "TCL23_PS_OPRF_FOLDED_SHARE_PMPEQT";

    /**
     * singleton mode.
     */
    private static final Tcl23PsOprfFoldedSharePmPeqtPtoDesc INSTANCE =
        new Tcl23PsOprfFoldedSharePmPeqtPtoDesc();

    /**
     * private constructor.
     */
    private Tcl23PsOprfFoldedSharePmPeqtPtoDesc() {
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
