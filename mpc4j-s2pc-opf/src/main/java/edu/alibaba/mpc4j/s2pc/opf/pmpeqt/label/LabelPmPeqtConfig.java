package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * Label-output permuted matrix private equality test config.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public interface LabelPmPeqtConfig extends MultiPartyPtoConfig {

    /**
     * Gets protocol type.
     *
     * @return protocol type.
     */
    LabelPmPeqtFactory.LabelPmPeqtType getPtoType();
}
