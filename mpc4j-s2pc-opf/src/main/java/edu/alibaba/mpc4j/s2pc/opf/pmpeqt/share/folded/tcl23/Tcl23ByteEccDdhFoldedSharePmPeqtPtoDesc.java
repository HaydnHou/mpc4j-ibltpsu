package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * TCL23 Byte-ECC-DDH based folded share-output PM-PEQT protocol description.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) 5174453601938742457L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "TCL23_BYTE_ECC_DDH_FOLDED_SHARE_PMPEQT";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * Receiver sends Byte-ECC PRFs.
         */
        RECEIVER_SEND_PRF,
        /**
         * Sender sends permuted sender Byte-ECC PRFs.
         */
        SENDER_SEND_PERMUTED_PRF,
    }

    /**
     * singleton mode.
     */
    private static final Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc INSTANCE =
        new Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc();

    /**
     * private constructor.
     */
    private Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc() {
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
