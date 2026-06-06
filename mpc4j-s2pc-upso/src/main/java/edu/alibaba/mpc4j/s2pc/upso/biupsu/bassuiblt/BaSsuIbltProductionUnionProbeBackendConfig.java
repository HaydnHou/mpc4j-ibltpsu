package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

/**
 * Production queue-peel union-probe BA-UPOT backend config.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public final class BaSsuIbltProductionUnionProbeBackendConfig extends AbstractMultiPartyPtoConfig
    implements BaSsuIbltUnionProbeBackendConfig {
    /**
     * fail-closed reason until the queue-peel endpoint accepts its adaptive transcript leakage.
     */
    public static final String NOT_PRODUCTION_READY_REASON =
        "specialized union-probe fixed-shape COT-backed row evaluator and true RPC/Core-COT queue-peel endpoint "
            + "are present, but production requires explicit acceptance of the IBLT-PSU-aligned adaptive queue "
            + "transcript leakage policy";
    /**
     * fail-closed reason until the final production audit passes.
     */
    public static final String PRODUCTION_AUDIT_NOT_READY_REASON =
        "production audit has not passed";
    /**
     * fail-closed reason until the no-reference-fallback certificate is available.
     */
    public static final String NO_REFERENCE_FALLBACK_NOT_READY_REASON =
        "no-reference-fallback certificate is not available";
    /**
     * fail-closed reason until the measured production endpoint wiring is complete.
     */
    public static final String MEASURED_ENDPOINT_NOT_READY_REASON =
        "measured production endpoint is not wired";
    /**
     * default auth tag byte length.
     */
    private static final int DEFAULT_AUTH_TAG_BYTE_LENGTH = 16;
    /**
     * default online batch size.
     */
    private static final int DEFAULT_ONLINE_BATCH_SIZE = 1 << 14;
    /**
     * default COT count per public bucket probe.
     */
    private static final int DEFAULT_COT_NUM_PER_PROBE = 3;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * OPRF tag byte length.
     */
    private final int tagByteLength;
    /**
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * transport auth tag byte length.
     */
    private final int authTagByteLength;
    /**
     * COT count per public probe.
     */
    private final int cotNumPerProbe;
    /**
     * online batch size.
     */
    private final int onlineBatchSize;
    /**
     * whether the endpoint explicitly accepts the IBLT-PSU-aligned adaptive queue transcript.
     */
    private final boolean acceptAdaptiveQueueTranscriptLeakage;
    /**
     * package-private production readiness certificate.
     */
    private final BaSsuIbltProductionReadinessCertificate productionReadinessCertificate;
    /**
     * Core COT config.
     */
    private final CoreCotConfig coreCotConfig;

    private BaSsuIbltProductionUnionProbeBackendConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.coreCotConfig);
        elementByteLength = builder.elementByteLength;
        tagByteLength = builder.tagByteLength;
        checkByteLength = builder.checkByteLength;
        authTagByteLength = builder.authTagByteLength;
        cotNumPerProbe = builder.cotNumPerProbe;
        onlineBatchSize = builder.onlineBatchSize;
        acceptAdaptiveQueueTranscriptLeakage = builder.acceptAdaptiveQueueTranscriptLeakage;
        productionReadinessCertificate = builder.productionReadinessCertificate;
        coreCotConfig = builder.coreCotConfig;
        validate();
    }

    private void validate() {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (tagByteLength <= 0 || tagByteLength > 64) {
            throw new IllegalArgumentException("tagByteLength must be in range [1, 64]");
        }
        if (checkByteLength <= 0 || checkByteLength > 64) {
            throw new IllegalArgumentException("checkByteLength must be in range [1, 64]");
        }
        if (authTagByteLength <= 0 || authTagByteLength > 32) {
            throw new IllegalArgumentException("authTagByteLength must be in range [1, 32]");
        }
        if (cotNumPerProbe < 3) {
            throw new IllegalArgumentException("cotNumPerProbe must be at least three");
        }
        if (onlineBatchSize <= 0) {
            throw new IllegalArgumentException("onlineBatchSize must be positive");
        }
        if (coreCotConfig == null) {
            throw new IllegalArgumentException("coreCotConfig must be non-null");
        }
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public int getTagByteLength() {
        return tagByteLength;
    }

    public int getCheckByteLength() {
        return checkByteLength;
    }

    public int getAuthTagByteLength() {
        return authTagByteLength;
    }

    public int getCotNumPerProbe() {
        return cotNumPerProbe;
    }

    public int getOnlineBatchSize() {
        return onlineBatchSize;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public boolean isAcceptAdaptiveQueueTranscriptLeakage() {
        return acceptAdaptiveQueueTranscriptLeakage;
    }

    public int cotNum(int maxProbeNum) {
        if (maxProbeNum <= 0) {
            throw new IllegalArgumentException("maxProbeNum must be positive");
        }
        return Math.multiplyExact(maxProbeNum, cotNumPerProbe);
    }

    public int capsuleByteLength() {
        return BaSsuIbltProductionUnionProbeCodec.capsuleByteLength(
            elementByteLength, tagByteLength, checkByteLength, authTagByteLength
        );
    }

    /**
     * Returns whether the main production code contains no local opener for remote capsules.
     *
     * @return true if the production capsule boundary has no local remote decode path.
     */
    public boolean isLocalRemoteDecodeFree() {
        return true;
    }

    /**
     * Returns whether all external probe capsules have fixed public length.
     *
     * @return true if capsules are fixed-shape.
     */
    public boolean hasFixedShapeCapsules() {
        return true;
    }

    /**
     * Returns whether the public output type is restricted to bottom or source-agnostic singleton.
     *
     * @return true if only source-agnostic public outputs are exposed.
     */
    public boolean opensOnlySourceAgnosticOutput() {
        return true;
    }

    /**
     * Returns whether the current implementation has a true remote-state-hiding evaluator.
     *
     * @return true once the COT-backed fixed-row evaluator is present.
     */
    public boolean hasRemoteStateHidingEvaluator() {
        return true;
    }

    /**
     * Returns whether the adaptive queue bucket sequence has been explicitly accepted as IBLT-PSU-aligned leakage.
     *
     * <p>Queue peel gets its speed from probing newly touched buckets after each public singleton delete. That bucket
     * sequence can reveal frontier/order information. Keep production readiness closed until the proof and endpoint
     * claim freeze explicitly account for this transcript, or until a padded schedule replaces it.</p>
     *
     * @return false until the adaptive transcript policy is finalized.
     */
    public boolean hasAcceptedAdaptiveQueueTranscriptLeakage() {
        return acceptAdaptiveQueueTranscriptLeakage;
    }

    /**
     * Returns whether the public two-party endpoint has wired the queue-peel loop to real RPC/Core-COT probes.
     *
     * @return true since SECURE_SEMI_HONEST now uses MP-OPRF plus RPC/Core-COT queue probes.
     */
    public boolean hasQueuePeelEndpointIntegration() {
        return true;
    }

    /**
     * Returns whether the final production audit has passed.
     *
     * @return true if the final production audit has passed.
     */
    public boolean hasProductionAuditPassed() {
        return productionReadinessCertificate.hasProductionAuditPassed();
    }

    /**
     * Returns whether production code has a no-reference-fallback certificate.
     *
     * @return true if reference fallbacks have been excluded from production code.
     */
    public boolean hasNoReferenceFallbackCertificate() {
        return productionReadinessCertificate.hasNoReferenceFallbackCertificate();
    }

    /**
     * Returns whether the measured production endpoint is wired.
     *
     * @return true if the measured production endpoint is wired.
     */
    public boolean hasMeasuredEndpointWired() {
        return productionReadinessCertificate.hasMeasuredEndpointWired();
    }

    @Override
    public String getUnionProbeBackendName() {
        return "specialized UP-BA-UPOT bucket-probe candidate";
    }

    @Override
    public boolean isSpecializedBucketProbe() {
        return true;
    }

    @Override
    public boolean isQueuePeelProductionReady() {
        return isLocalRemoteDecodeFree() && hasFixedShapeCapsules() && opensOnlySourceAgnosticOutput()
            && hasRemoteStateHidingEvaluator() && hasAcceptedAdaptiveQueueTranscriptLeakage()
            && hasQueuePeelEndpointIntegration() && hasProductionAuditPassed()
            && hasNoReferenceFallbackCertificate() && hasMeasuredEndpointWired();
    }

    public String getProductionReadinessReason() {
        if (isQueuePeelProductionReady()) {
            return "production-ready queue-peel endpoint";
        }
        if (!hasProductionAuditPassed()) {
            return PRODUCTION_AUDIT_NOT_READY_REASON;
        }
        if (!hasNoReferenceFallbackCertificate()) {
            return NO_REFERENCE_FALLBACK_NOT_READY_REASON;
        }
        if (!hasMeasuredEndpointWired()) {
            return MEASURED_ENDPOINT_NOT_READY_REASON;
        }
        return NOT_PRODUCTION_READY_REASON;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<BaSsuIbltProductionUnionProbeBackendConfig> {
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * OPRF tag byte length.
         */
        private int tagByteLength;
        /**
         * check byte length.
         */
        private int checkByteLength;
        /**
         * auth tag byte length.
         */
        private int authTagByteLength;
        /**
         * COT count per probe.
         */
        private int cotNumPerProbe;
        /**
         * online batch size.
         */
        private int onlineBatchSize;
        /**
         * whether the endpoint explicitly accepts the IBLT-PSU-aligned adaptive queue transcript.
         */
        private boolean acceptAdaptiveQueueTranscriptLeakage;
        /**
         * package-private production readiness certificate.
         */
        private BaSsuIbltProductionReadinessCertificate productionReadinessCertificate;
        /**
         * Core COT config.
         */
        private CoreCotConfig coreCotConfig;

        public Builder() {
            elementByteLength = Long.BYTES;
            tagByteLength = 24;
            checkByteLength = 16;
            authTagByteLength = DEFAULT_AUTH_TAG_BYTE_LENGTH;
            cotNumPerProbe = DEFAULT_COT_NUM_PER_PROBE;
            onlineBatchSize = DEFAULT_ONLINE_BATCH_SIZE;
            acceptAdaptiveQueueTranscriptLeakage = false;
            productionReadinessCertificate = BaSsuIbltProductionReadinessCertificate.none();
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setElementByteLength(int elementByteLength) {
            this.elementByteLength = elementByteLength;
            return this;
        }

        public Builder setTagByteLength(int tagByteLength) {
            this.tagByteLength = tagByteLength;
            return this;
        }

        public Builder setCheckByteLength(int checkByteLength) {
            this.checkByteLength = checkByteLength;
            return this;
        }

        public Builder setAuthTagByteLength(int authTagByteLength) {
            this.authTagByteLength = authTagByteLength;
            return this;
        }

        public Builder setCotNumPerProbe(int cotNumPerProbe) {
            this.cotNumPerProbe = cotNumPerProbe;
            return this;
        }

        public Builder setOnlineBatchSize(int onlineBatchSize) {
            this.onlineBatchSize = onlineBatchSize;
            return this;
        }

        public Builder setAcceptAdaptiveQueueTranscriptLeakage(boolean acceptAdaptiveQueueTranscriptLeakage) {
            this.acceptAdaptiveQueueTranscriptLeakage = acceptAdaptiveQueueTranscriptLeakage;
            return this;
        }

        Builder setProductionReadinessCertificate(
            BaSsuIbltProductionReadinessCertificate productionReadinessCertificate) {
            if (productionReadinessCertificate == null) {
                throw new IllegalArgumentException("productionReadinessCertificate must be non-null");
            }
            this.productionReadinessCertificate = productionReadinessCertificate;
            return this;
        }

        public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
            if (coreCotConfig == null) {
                throw new IllegalArgumentException("coreCotConfig must be non-null");
            }
            this.coreCotConfig = coreCotConfig;
            return this;
        }

        @Override
        public BaSsuIbltProductionUnionProbeBackendConfig build() {
            return new BaSsuIbltProductionUnionProbeBackendConfig(this);
        }
    }
}
