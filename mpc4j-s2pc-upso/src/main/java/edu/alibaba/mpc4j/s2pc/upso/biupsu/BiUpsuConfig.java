package edu.alibaba.mpc4j.s2pc.upso.biupsu;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * Bi-output UPSU config interface.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public interface BiUpsuConfig extends MultiPartyPtoConfig {
    /**
     * Gets protocol type.
     *
     * @return protocol type.
     */
    BiUpsuFactory.BiUpsuType getPtoType();
}
