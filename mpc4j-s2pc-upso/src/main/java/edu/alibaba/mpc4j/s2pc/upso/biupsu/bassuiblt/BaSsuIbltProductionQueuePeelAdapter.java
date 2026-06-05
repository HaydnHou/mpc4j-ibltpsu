package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Production queue-peel secure-core adapter.
 *
 * <p>This adapter is the only entry point from the secure core into the future P36 remote-state-hiding UP-BA-UPOT
 * backend. It is intentionally fail-closed while the backend exposes only an opaque placeholder capsule boundary.
 * Reference queue-peel code must stay on {@code runQueuePeelAlignedReference} and must never be called from this
 * adapter.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionQueuePeelAdapter {
    /**
     * private constructor.
     */
    private BaSsuIbltProductionQueuePeelAdapter() {
        // empty
    }

    static BaSsuIbltSecureProtocolResult run(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        byte[][] leftFixedInputs, boolean[] leftActiveFlags, BaSsuIbltOprfTagOutput leftTagOutput,
        byte[][] rightFixedInputs, boolean[] rightActiveFlags, BaSsuIbltOprfTagOutput rightTagOutput,
        BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        requireProductionBackend(unionProbeBackendConfig);
        throw new UnsupportedOperationException(
            "P37 production queue-peel execution requires a true remote-state-hiding UP-BA-UPOT evaluator; "
                + "reference queue-peel execution is not permitted through the production adapter"
        );
    }

    static BaSsuIbltProductionUnionProbeBackendConfig requireProductionBackend(
        BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        if (unionProbeBackendConfig == null) {
            throw new IllegalArgumentException("unionProbeBackendConfig must be non-null");
        }
        if (unionProbeBackendConfig.getClass() != BaSsuIbltProductionUnionProbeBackendConfig.class) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires the exact trusted production union-probe backend type; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is not trusted"
            );
        }
        BaSsuIbltProductionUnionProbeBackendConfig productionConfig =
            (BaSsuIbltProductionUnionProbeBackendConfig) unionProbeBackendConfig;
        if (!productionConfig.isSpecializedBucketProbe()) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires a specialized bucket-probe backend"
            );
        }
        if (!productionConfig.isQueuePeelProductionReady()) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires a production-ready backend; "
                    + productionConfig.getUnionProbeBackendName() + " is fail-closed: "
                    + productionConfig.getProductionReadinessReason()
            );
        }
        return productionConfig;
    }
}
