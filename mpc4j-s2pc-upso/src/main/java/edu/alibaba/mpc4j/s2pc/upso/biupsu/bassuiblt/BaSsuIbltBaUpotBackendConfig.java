package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * BA-SSU-IBLT BA-UPOT backend contract.
 *
 * <p>The production secure endpoint requires an implementation that hides the selected bucket branch from the
 * receiver. Current benchmark and M14a fixed-shape masked transports deliberately return {@code false}.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public interface BaSsuIbltBaUpotBackendConfig extends MultiPartyPtoConfig {
    /**
     * Returns the backend name used in diagnostics.
     *
     * @return backend name.
     */
    String getBackendName();

    /**
     * Returns whether the backend implements full oblivious branch selection.
     *
     * @return true if the selected branch remains hidden.
     */
    boolean isObliviousBranchSelection();
}
