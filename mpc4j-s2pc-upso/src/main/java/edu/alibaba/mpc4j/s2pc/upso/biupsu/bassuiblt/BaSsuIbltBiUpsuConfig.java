package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfConfig;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BA-SSU-IBLT bi-output UPSU config.
 *
 * <p>This config currently gates a reference/costed implementation only; it is not production-secure.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltBiUpsuConfig extends AbstractMultiPartyPtoConfig implements BiUpsuConfig {
    /**
     * secure mode fail-closed reason.
     */
    public static final String SECURE_SEMI_HONEST_NOT_READY_REASON =
        "SECURE_SEMI_HONEST is fail-closed until the selected schedule has a production backend: "
            + "CURRENT_FIXED_LOOP_M14A requires M14b full oblivious branch-selection BA-UPOT; "
            + "QUEUE_PEEL_ALIGNED requires specialized production union-probe BA-UPOT";
    /**
     * reference endpoint reason.
     */
    public static final String REFERENCE_ENDPOINT_NOT_READY_REASON =
        "REFERENCE_FIXED_LAYER is a correctness-only endpoint and reveals fixed source-layer bucket summaries";
    /**
     * benchmark mode reason.
     */
    public static final String COSTED_BENCHMARK_NOT_READY_REASON =
        "COSTED_BENCHMARK is for cost estimation only and is not a production protocol endpoint";
    /**
     * max element byte length.
     */
    private final int maxElementByteLength;
    /**
     * anchor table multiplier.
     */
    private final double alphaAnchor;
    /**
     * placement degree.
     */
    private final int degree;
    /**
     * fixed retry count.
     */
    private final int retryCount;
    /**
     * statistical security parameter.
     */
    private final int lambda;
    /**
     * margin bits.
     */
    private final int marginBits;
    /**
     * checks per bucket.
     */
    private final int checksPerBucket;
    /**
     * public placement seed override. If null, params derive a deterministic seed from capacities.
     */
    private final byte[] publicPlaceSeed;
    /**
     * bucket evaluator mode for the plain reference runner.
     */
    private final BaUpotBucketEvaluatorMode evaluatorMode;
    /**
     * bi-output delivery mode.
     */
    private final BaSsuIbltBiOutputDeliveryMode deliveryMode;
    /**
     * whether to collect all fixed-retry bucket traces.
     */
    private final boolean collectTrace;
    /**
     * whether the fixed-layer reference endpoint is enabled.
     */
    private final boolean enableFixedLayerReferenceEndpoint;
    /**
     * protocol mode.
     */
    private final BaSsuIbltProtocolMode protocolMode;
    /**
     * OPRF config for the future secure protocol path.
     */
    private final OprfConfig oprfConfig;
    /**
     * BA-UPOT backend config for the future secure protocol path.
     */
    private final BaSsuIbltBaUpotBackendConfig secureBaUpotConfig;
    /**
     * secure schedule shape.
     */
    private final BaSsuIbltProtocolSchedule.Shape scheduleShape;
    /**
     * queue-peel union-probe backend config.
     */
    private final BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig;

    private BaSsuIbltBiUpsuConfig(Builder builder) {
        super(defaultSecurityModel(builder), subPtoConfigs(builder));
        maxElementByteLength = builder.maxElementByteLength;
        alphaAnchor = builder.alphaAnchor;
        degree = builder.degree;
        retryCount = builder.retryCount;
        lambda = builder.lambda;
        marginBits = builder.marginBits;
        checksPerBucket = builder.checksPerBucket;
        publicPlaceSeed = builder.publicPlaceSeed == null
            ? null
            : Arrays.copyOf(builder.publicPlaceSeed, builder.publicPlaceSeed.length);
        evaluatorMode = builder.evaluatorMode;
        deliveryMode = builder.deliveryMode;
        collectTrace = builder.collectTrace;
        enableFixedLayerReferenceEndpoint = builder.enableFixedLayerReferenceEndpoint;
        protocolMode = builder.protocolMode;
        oprfConfig = builder.oprfConfig;
        secureBaUpotConfig = builder.secureBaUpotConfig;
        scheduleShape = builder.scheduleShape;
        unionProbeBackendConfig = builder.unionProbeBackendConfig;
        validate();
    }

    private static SecurityModel defaultSecurityModel(Builder builder) {
        return builder.protocolMode == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST
            ? SecurityModel.SEMI_HONEST
            : SecurityModel.IDEAL;
    }

    private static MultiPartyPtoConfig[] subPtoConfigs(Builder builder) {
        if (builder.protocolMode == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST) {
            List<MultiPartyPtoConfig> subConfigs = new ArrayList<>(3);
            if (builder.oprfConfig != null) {
                subConfigs.add(builder.oprfConfig);
            }
            if (builder.secureBaUpotConfig != null) {
                subConfigs.add(builder.secureBaUpotConfig);
            }
            if (builder.unionProbeBackendConfig != null) {
                subConfigs.add(builder.unionProbeBackendConfig);
            }
            return subConfigs.toArray(new MultiPartyPtoConfig[0]);
        }
        return new MultiPartyPtoConfig[0];
    }

    private void validate() {
        if (protocolMode == null) {
            throw new IllegalArgumentException("protocolMode must be non-null");
        }
        if (scheduleShape == null) {
            throw new IllegalArgumentException("scheduleShape must be non-null");
        }
        if (maxElementByteLength <= 0) {
            throw new IllegalArgumentException("maxElementByteLength must be positive");
        }
        if (!Double.isFinite(alphaAnchor) || alphaAnchor <= 1.0) {
            throw new IllegalArgumentException("alphaAnchor must be finite and greater than 1");
        }
        if (degree != 3 && degree != 4) {
            throw new IllegalArgumentException("degree must be 3 or 4");
        }
        if (retryCount <= 0) {
            throw new IllegalArgumentException("retryCount must be positive");
        }
        if (lambda <= 0) {
            throw new IllegalArgumentException("lambda must be positive");
        }
        if (marginBits < 0) {
            throw new IllegalArgumentException("marginBits must be non-negative");
        }
        if (checksPerBucket <= 0) {
            throw new IllegalArgumentException("checksPerBucket must be positive");
        }
        if (enableFixedLayerReferenceEndpoint && retryCount != 1) {
            throw new IllegalArgumentException(
                "fixed-layer reference endpoint supports retryCount = 1 until safe retry selection is implemented"
            );
        }
        if (enableFixedLayerReferenceEndpoint && protocolMode != BaSsuIbltProtocolMode.REFERENCE_FIXED_LAYER) {
            throw new IllegalArgumentException("fixed-layer reference endpoint requires REFERENCE_FIXED_LAYER mode");
        }
        if (protocolMode == BaSsuIbltProtocolMode.REFERENCE_FIXED_LAYER && !enableFixedLayerReferenceEndpoint) {
            throw new IllegalArgumentException("REFERENCE_FIXED_LAYER mode requires explicit endpoint enable flag");
        }
        if (protocolMode == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST) {
            if (enableFixedLayerReferenceEndpoint) {
                throw new IllegalArgumentException("SECURE_SEMI_HONEST must not enable the fixed-layer reference endpoint");
            }
            if (oprfConfig == null) {
                throw new IllegalArgumentException("SECURE_SEMI_HONEST requires a non-null OPRF config");
            }
            if (!(oprfConfig instanceof MpOprfConfig)) {
                throw new IllegalArgumentException("SECURE_SEMI_HONEST requires an MP-OPRF config");
            }
            if (scheduleShape == BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A) {
                checkStrictBaUpotBackend();
            } else if (scheduleShape == BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED) {
                checkQueuePeelBackend();
            } else {
                throw new IllegalArgumentException(
                    "SECURE_SEMI_HONEST does not support historical schedule shape " + scheduleShape
                );
            }
        }
    }

    private void checkStrictBaUpotBackend() {
        if (secureBaUpotConfig == null) {
            throw new IllegalArgumentException(
                "SECURE_SEMI_HONEST CURRENT_FIXED_LOOP_M14A requires a non-null BA-UPOT config"
            );
        }
        if (!secureBaUpotConfig.isObliviousBranchSelection()) {
            throw new IllegalArgumentException(
                "SECURE_SEMI_HONEST CURRENT_FIXED_LOOP_M14A requires a BA-UPOT backend with oblivious "
                    + "branch-selection; " + secureBaUpotConfig.getBackendName() + " is not sufficient"
            );
        }
    }

    private void checkQueuePeelBackend() {
        if (unionProbeBackendConfig == null) {
            throw new IllegalArgumentException(
                "SECURE_SEMI_HONEST QUEUE_PEEL_ALIGNED requires a non-null union-probe BA-UPOT config"
            );
        }
        if (!(unionProbeBackendConfig instanceof BaSsuIbltProductionUnionProbeBackendConfig)) {
            throw new IllegalArgumentException(
                "SECURE_SEMI_HONEST QUEUE_PEEL_ALIGNED requires the trusted production union-probe backend type; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is not trusted"
            );
        }
        if (!unionProbeBackendConfig.isSpecializedBucketProbe()) {
            throw new IllegalArgumentException(
                "SECURE_SEMI_HONEST QUEUE_PEEL_ALIGNED requires a specialized bucket-probe gadget; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is not sufficient"
            );
        }
        if (!unionProbeBackendConfig.isQueuePeelProductionReady()) {
            String reason = unionProbeBackendConfig instanceof BaSsuIbltProductionUnionProbeBackendConfig
                ? ((BaSsuIbltProductionUnionProbeBackendConfig) unionProbeBackendConfig).getProductionReadinessReason()
                : "not production ready";
            throw new IllegalArgumentException(
                "SECURE_SEMI_HONEST QUEUE_PEEL_ALIGNED requires a production-ready union-probe backend; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is not production ready: " + reason
            );
        }
    }

    @Override
    public BiUpsuFactory.BiUpsuType getPtoType() {
        return BiUpsuFactory.BiUpsuType.BA_SSU_IBLT;
    }

    public int getMaxElementByteLength() {
        return maxElementByteLength;
    }

    public double getAlphaAnchor() {
        return alphaAnchor;
    }

    public int getDegree() {
        return degree;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getLambda() {
        return lambda;
    }

    public int getMarginBits() {
        return marginBits;
    }

    public int getChecksPerBucket() {
        return checksPerBucket;
    }

    public byte[] getPublicPlaceSeed() {
        return publicPlaceSeed == null ? null : Arrays.copyOf(publicPlaceSeed, publicPlaceSeed.length);
    }

    public BaUpotBucketEvaluatorMode getEvaluatorMode() {
        return evaluatorMode;
    }

    public BaSsuIbltBiOutputDeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public boolean isCollectTrace() {
        return collectTrace;
    }

    public boolean isEnableFixedLayerReferenceEndpoint() {
        return enableFixedLayerReferenceEndpoint;
    }

    public BaSsuIbltProtocolMode getProtocolMode() {
        return protocolMode;
    }

    public OprfConfig getOprfConfig() {
        return oprfConfig;
    }

    public MpOprfConfig getMpOprfConfig() {
        if (!(oprfConfig instanceof MpOprfConfig)) {
            throw new IllegalStateException("OPRF config is not an MP-OPRF config");
        }
        return (MpOprfConfig) oprfConfig;
    }

    public BaSsuIbltBaUpotBackendConfig getSecureBaUpotConfig() {
        return secureBaUpotConfig;
    }

    public BaSsuIbltProtocolSchedule.Shape getScheduleShape() {
        return scheduleShape;
    }

    public BaSsuIbltUnionProbeBackendConfig getUnionProbeBackendConfig() {
        return unionProbeBackendConfig;
    }

    public boolean isProductionReady() {
        return false;
    }

    public String getProductionReadinessReason() {
        switch (protocolMode) {
            case SECURE_SEMI_HONEST:
                if (scheduleShape == BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED
                    && unionProbeBackendConfig instanceof BaSsuIbltProductionUnionProbeBackendConfig) {
                    return ((BaSsuIbltProductionUnionProbeBackendConfig) unionProbeBackendConfig)
                        .getProductionReadinessReason();
                }
                if (isSecureMaterialReady()) {
                    return "SECURE_SEMI_HONEST materials pass config checks, but the endpoint adapter remains fail-closed";
                }
                return SECURE_SEMI_HONEST_NOT_READY_REASON;
            case REFERENCE_FIXED_LAYER:
                return REFERENCE_ENDPOINT_NOT_READY_REASON;
            case COSTED_BENCHMARK:
                return COSTED_BENCHMARK_NOT_READY_REASON;
            default:
                throw new IllegalStateException("unknown protocol mode: " + protocolMode);
        }
    }

    private boolean isSecureMaterialReady() {
        if (protocolMode != BaSsuIbltProtocolMode.SECURE_SEMI_HONEST || !(oprfConfig instanceof MpOprfConfig)) {
            return false;
        }
        if (scheduleShape == BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A) {
            return secureBaUpotConfig != null && secureBaUpotConfig.isObliviousBranchSelection();
        }
        if (scheduleShape == BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED) {
            return unionProbeBackendConfig instanceof BaSsuIbltProductionUnionProbeBackendConfig
                && unionProbeBackendConfig.isSpecializedBucketProbe()
                && unionProbeBackendConfig.isQueuePeelProductionReady();
        }
        return false;
    }

    /**
     * Creates concrete BA-SSU parameters for the two public capacities.
     *
     * @param leftElementSize left capacity.
     * @param rightElementSize right capacity.
     * @return params.
     */
    public BaSsuIbltBiUpsuParams createParams(int leftElementSize, int rightElementSize) {
        int nLarge = Math.max(leftElementSize, rightElementSize);
        int nShadow = Math.min(leftElementSize, rightElementSize);
        BaSsuIbltBiUpsuParams.Builder builder = new BaSsuIbltBiUpsuParams.Builder(nLarge, nShadow)
            .setAlphaAnchor(alphaAnchor)
            .setDegree(degree)
            .setRetryCount(retryCount)
            .setLambda(lambda)
            .setMarginBits(marginBits)
            .setChecksPerBucket(checksPerBucket);
        if (publicPlaceSeed != null) {
            builder.setPublicPlaceSeed(publicPlaceSeed);
        }
        return builder.build();
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<BaSsuIbltBiUpsuConfig> {
        /**
         * max element byte length.
         */
        private int maxElementByteLength;
        /**
         * anchor multiplier.
         */
        private double alphaAnchor;
        /**
         * degree.
         */
        private int degree;
        /**
         * retry count.
         */
        private int retryCount;
        /**
         * lambda.
         */
        private int lambda;
        /**
         * margin bits.
         */
        private int marginBits;
        /**
         * checks per bucket.
         */
        private int checksPerBucket;
        /**
         * public placement seed.
         */
        private byte[] publicPlaceSeed;
        /**
         * evaluator mode.
         */
        private BaUpotBucketEvaluatorMode evaluatorMode;
        /**
         * delivery mode.
         */
        private BaSsuIbltBiOutputDeliveryMode deliveryMode;
        /**
         * collect trace.
         */
        private boolean collectTrace;
        /**
         * enable fixed-layer reference endpoint.
         */
        private boolean enableFixedLayerReferenceEndpoint;
        /**
         * protocol mode.
         */
        private BaSsuIbltProtocolMode protocolMode;
        /**
         * OPRF config for secure mode.
         */
        private OprfConfig oprfConfig;
        /**
         * BA-UPOT backend config for secure mode.
         */
        private BaSsuIbltBaUpotBackendConfig secureBaUpotConfig;
        /**
         * secure schedule shape.
         */
        private BaSsuIbltProtocolSchedule.Shape scheduleShape;
        /**
         * queue-peel union-probe backend config.
         */
        private BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig;

        public Builder() {
            maxElementByteLength = Long.BYTES;
            alphaAnchor = 1.55;
            degree = 3;
            retryCount = 1;
            lambda = BaSsuIbltBiUpsuParams.DEFAULT_LAMBDA;
            marginBits = BaSsuIbltBiUpsuParams.DEFAULT_MARGIN_BITS;
            checksPerBucket = BaSsuIbltBiUpsuParams.DEFAULT_CHECKS_PER_BUCKET;
            evaluatorMode = BaUpotBucketEvaluatorMode.IDEAL;
            deliveryMode = BaSsuIbltBiOutputDeliveryMode.SIGNED_SOURCE_SPLIT;
            collectTrace = false;
            enableFixedLayerReferenceEndpoint = false;
            protocolMode = BaSsuIbltProtocolMode.COSTED_BENCHMARK;
            oprfConfig = null;
            secureBaUpotConfig = null;
            scheduleShape = BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A;
            unionProbeBackendConfig = null;
        }

        public Builder setMaxElementByteLength(int maxElementByteLength) {
            this.maxElementByteLength = maxElementByteLength;
            return this;
        }

        public Builder setAlphaAnchor(double alphaAnchor) {
            this.alphaAnchor = alphaAnchor;
            return this;
        }

        public Builder setDegree(int degree) {
            this.degree = degree;
            return this;
        }

        public Builder setRetryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder setLambda(int lambda) {
            this.lambda = lambda;
            return this;
        }

        public Builder setMarginBits(int marginBits) {
            this.marginBits = marginBits;
            return this;
        }

        public Builder setChecksPerBucket(int checksPerBucket) {
            this.checksPerBucket = checksPerBucket;
            return this;
        }

        public Builder setPublicPlaceSeed(byte[] publicPlaceSeed) {
            if (publicPlaceSeed == null) {
                throw new IllegalArgumentException("publicPlaceSeed must be non-null");
            }
            this.publicPlaceSeed = Arrays.copyOf(publicPlaceSeed, publicPlaceSeed.length);
            return this;
        }

        public Builder setEvaluatorMode(BaUpotBucketEvaluatorMode evaluatorMode) {
            if (evaluatorMode == null) {
                throw new IllegalArgumentException("evaluatorMode must be non-null");
            }
            this.evaluatorMode = evaluatorMode;
            return this;
        }

        public Builder setDeliveryMode(BaSsuIbltBiOutputDeliveryMode deliveryMode) {
            if (deliveryMode == null) {
                throw new IllegalArgumentException("deliveryMode must be non-null");
            }
            this.deliveryMode = deliveryMode;
            return this;
        }

        public Builder setCollectTrace(boolean collectTrace) {
            this.collectTrace = collectTrace;
            return this;
        }

        public Builder setEnableFixedLayerReferenceEndpoint(boolean enableFixedLayerReferenceEndpoint) {
            this.enableFixedLayerReferenceEndpoint = enableFixedLayerReferenceEndpoint;
            if (enableFixedLayerReferenceEndpoint) {
                if (protocolMode == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST) {
                    throw new IllegalArgumentException(
                        "SECURE_SEMI_HONEST must not enable the fixed-layer reference endpoint"
                    );
                }
                protocolMode = BaSsuIbltProtocolMode.REFERENCE_FIXED_LAYER;
            }
            return this;
        }

        public Builder setProtocolMode(BaSsuIbltProtocolMode protocolMode) {
            if (protocolMode == null) {
                throw new IllegalArgumentException("protocolMode must be non-null");
            }
            this.protocolMode = protocolMode;
            return this;
        }

        public Builder setOprfConfig(OprfConfig oprfConfig) {
            if (oprfConfig == null) {
                throw new IllegalArgumentException("oprfConfig must be non-null");
            }
            this.oprfConfig = oprfConfig;
            return this;
        }

        public Builder setSecureBaUpotConfig(BaSsuIbltBaUpotBackendConfig secureBaUpotConfig) {
            if (secureBaUpotConfig == null) {
                throw new IllegalArgumentException("secureBaUpotConfig must be non-null");
            }
            this.secureBaUpotConfig = secureBaUpotConfig;
            return this;
        }

        public Builder setScheduleShape(BaSsuIbltProtocolSchedule.Shape scheduleShape) {
            if (scheduleShape == null) {
                throw new IllegalArgumentException("scheduleShape must be non-null");
            }
            this.scheduleShape = scheduleShape;
            return this;
        }

        public Builder setUnionProbeBackendConfig(BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
            if (unionProbeBackendConfig == null) {
                throw new IllegalArgumentException("unionProbeBackendConfig must be non-null");
            }
            this.unionProbeBackendConfig = unionProbeBackendConfig;
            return this;
        }

        @Override
        public BaSsuIbltBiUpsuConfig build() {
            return new BaSsuIbltBiUpsuConfig(this);
        }
    }
}
