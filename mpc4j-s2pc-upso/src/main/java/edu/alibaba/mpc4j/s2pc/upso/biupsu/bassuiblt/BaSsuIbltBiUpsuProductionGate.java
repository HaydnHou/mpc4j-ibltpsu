package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;

/**
 * SECURE_SEMI_HONEST endpoint production gate.
 *
 * <p>The endpoint gate is intentionally stricter than the backend gate. Even after a future UP-BA-UPOT primitive
 * reports backend readiness, the public endpoint must stay closed until the P36 backend, P37 queue-peel execution, and
 * P38 endpoint wiring are all complete and tested.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltBiUpsuProductionGate {
    /**
     * private constructor.
     */
    private BaSsuIbltBiUpsuProductionGate() {
        // empty
    }

    static void checkEndpointReady(BaSsuIbltBiUpsuConfig config) throws MpcAbortException {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        MpcAbortPreconditions.checkArgument(config.isProductionReady(), config.getProductionReadinessReason());
    }
}
