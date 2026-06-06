package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;

/**
 * SECURE_SEMI_HONEST endpoint production gate.
 *
 * <p>The endpoint gate is intentionally stricter than the primitive capability checks. Even when the RPC/Core-COT
 * queue-probe candidate endpoint exists, the public endpoint stays closed until Section 8 / P50-P56 production
 * certification, no-reference-fallback certification, measured-endpoint wiring, and the readiness certificate path are
 * complete.</p>
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
        MpcAbortPreconditions.checkArgument(
            config.getProtocolMode() == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST,
            config.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            config.getScheduleShape() == BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED,
            config.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            config.getUnionProbeBackendConfig() instanceof BaSsuIbltProductionUnionProbeBackendConfig,
            config.getProductionReadinessReason()
        );
        BaSsuIbltProductionUnionProbeBackendConfig backendConfig =
            (BaSsuIbltProductionUnionProbeBackendConfig) config.getUnionProbeBackendConfig();
        MpcAbortPreconditions.checkArgument(
            backendConfig.isLocalRemoteDecodeFree(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasFixedShapeCapsules(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.opensOnlySourceAgnosticOutput(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasRemoteStateHidingEvaluator(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasAcceptedAdaptiveQueueTranscriptLeakage(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasQueuePeelEndpointIntegration(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasProductionAuditPassed(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasNoReferenceFallbackCertificate(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(
            backendConfig.hasMeasuredEndpointWired(), backendConfig.getProductionReadinessReason()
        );
        MpcAbortPreconditions.checkArgument(config.isProductionReady(), config.getProductionReadinessReason());
    }
}
