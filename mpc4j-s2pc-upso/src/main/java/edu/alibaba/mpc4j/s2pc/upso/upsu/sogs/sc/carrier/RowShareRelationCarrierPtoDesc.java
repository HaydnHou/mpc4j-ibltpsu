package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Row-level share relation carrier protocol description.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class RowShareRelationCarrierPtoDesc implements PtoDesc {
    /**
     * protocol id.
     */
    private static final int PTO_ID = Math.abs((int) -6042192689478061467L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "ROW_SHARE_RELATION_CARRIER";
    /**
     * singleton mode.
     */
    private static final RowShareRelationCarrierPtoDesc INSTANCE = new RowShareRelationCarrierPtoDesc();

    static {
        PtoDescManager.registerPtoDesc(INSTANCE);
    }

    private RowShareRelationCarrierPtoDesc() {
        // empty
    }

    public static RowShareRelationCarrierPtoDesc getInstance() {
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
