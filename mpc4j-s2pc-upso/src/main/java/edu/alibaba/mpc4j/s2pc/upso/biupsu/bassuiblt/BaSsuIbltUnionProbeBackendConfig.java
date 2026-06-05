package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * Queue-peel union-probe BA-UPOT backend contract.
 *
 * <p>This contract is intentionally separate from the historical fixed-loop BA-UPOT backend. A queue-peel production
 * backend must be a specialized public-bucket probe gadget, not a generic OT/MPC/PEQT/OKVS/PIR route.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public interface BaSsuIbltUnionProbeBackendConfig extends MultiPartyPtoConfig {
    /**
     * Returns the backend name used in diagnostics.
     *
     * @return backend name.
     */
    String getUnionProbeBackendName();

    /**
     * Returns whether this backend is the specialized bucket-probe gadget required by the fast queue-peel endpoint.
     *
     * @return true if the backend is a specialized bucket-probe gadget.
     */
    boolean isSpecializedBucketProbe();

    /**
     * Returns whether this backend is production-ready for QUEUE_PEEL_ALIGNED.
     *
     * @return true if production-ready.
     */
    boolean isQueuePeelProductionReady();
}
