package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

/**
 * COT-backed BA-UnionPeel-OT fixed-shape masked transport config.
 *
 * <p>This milestone provides fixed Core-COT accounting and fixed-shape masked capsules. It is not yet a complete
 * oblivious branch-selection BA-UPOT backend because both parties derive a shared transport mask seed.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaUnionPeelOtSecureConfig extends AbstractMultiPartyPtoConfig
    implements BaSsuIbltBaUpotBackendConfig, BaSsuIbltUnionProbeBackendConfig {
    /**
     * default auth tag bytes.
     */
    private static final int DEFAULT_AUTH_TAG_BYTE_LENGTH = 16;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * auth tag byte length.
     */
    private final int authTagByteLength;
    /**
     * COT count per bucket.
     */
    private final int cotNumPerBucket;
    /**
     * online batch size.
     */
    private final int onlineBatchSize;
    /**
     * Core COT config.
     */
    private final CoreCotConfig coreCotConfig;

    private BaUnionPeelOtSecureConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.coreCotConfig);
        elementByteLength = builder.elementByteLength;
        authTagByteLength = builder.authTagByteLength;
        cotNumPerBucket = builder.cotNumPerBucket;
        onlineBatchSize = builder.onlineBatchSize;
        coreCotConfig = builder.coreCotConfig;
        validate();
    }

    private void validate() {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (authTagByteLength <= 0 || authTagByteLength > 32) {
            throw new IllegalArgumentException("authTagByteLength must be in range [1, 32]");
        }
        if (cotNumPerBucket <= 0) {
            throw new IllegalArgumentException("cotNumPerBucket must be positive");
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

    public int getAuthTagByteLength() {
        return authTagByteLength;
    }

    public int getCotNumPerBucket() {
        return cotNumPerBucket;
    }

    public int getOnlineBatchSize() {
        return onlineBatchSize;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public int cotNum(int bucketNum) {
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        return Math.multiplyExact(bucketNum, cotNumPerBucket);
    }

    @Override
    public String getBackendName() {
        return "M14a fixed-shape masked BA-UnionPeel-OT";
    }

    @Override
    public boolean isObliviousBranchSelection() {
        return false;
    }

    @Override
    public String getUnionProbeBackendName() {
        return "M14a fixed-shape masked BA-UnionPeel-OT";
    }

    @Override
    public boolean isSpecializedBucketProbe() {
        return false;
    }

    @Override
    public boolean isQueuePeelProductionReady() {
        return false;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<BaUnionPeelOtSecureConfig> {
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * auth tag byte length.
         */
        private int authTagByteLength;
        /**
         * COT count per bucket.
         */
        private int cotNumPerBucket;
        /**
         * online batch size.
         */
        private int onlineBatchSize;
        /**
         * Core COT config.
         */
        private CoreCotConfig coreCotConfig;

        public Builder() {
            elementByteLength = Long.BYTES;
            authTagByteLength = DEFAULT_AUTH_TAG_BYTE_LENGTH;
            cotNumPerBucket = BaUpotConfig.DEFAULT_COT_NUM_PER_BUCKET;
            onlineBatchSize = BaUpotConfig.DEFAULT_ONLINE_BATCH_SIZE;
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setElementByteLength(int elementByteLength) {
            this.elementByteLength = elementByteLength;
            return this;
        }

        public Builder setAuthTagByteLength(int authTagByteLength) {
            this.authTagByteLength = authTagByteLength;
            return this;
        }

        public Builder setCotNumPerBucket(int cotNumPerBucket) {
            this.cotNumPerBucket = cotNumPerBucket;
            return this;
        }

        public Builder setOnlineBatchSize(int onlineBatchSize) {
            this.onlineBatchSize = onlineBatchSize;
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
        public BaUnionPeelOtSecureConfig build() {
            return new BaUnionPeelOtSecureConfig(this);
        }
    }
}
