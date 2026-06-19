package edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * Folded share-output PM-PEQT config.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public interface FoldedSharePmPeqtConfig extends MultiPartyPtoConfig {
    /**
     * Gets the protocol type.
     *
     * @return protocol type.
     */
    FoldedSharePmPeqtFactory.FoldedSharePmPeqtType getPtoType();
}
