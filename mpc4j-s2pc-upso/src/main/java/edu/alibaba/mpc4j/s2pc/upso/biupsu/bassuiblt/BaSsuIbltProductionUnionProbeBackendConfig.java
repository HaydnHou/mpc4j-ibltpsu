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
public class BaSsuIbltProductionUnionProbeBackendConfig extends AbstractMultiPartyPtoConfig
    implements BaSsuIbltUnionProbeBackendConfig {
    /**
     * fail-closed reason until the local decode scaffold is replaced with real UP-BA-UPOT.
     */
    public static final String NOT_PRODUCTION_READY_REASON =
        "specialized union-probe candidate still uses local capsule decoding; production requires true "
            + "COT/ROT-backed UP-BA-UPOT that never materializes the remote bucket state";
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
        if (cotNumPerProbe <= 0) {
            throw new IllegalArgumentException("cotNumPerProbe must be positive");
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
        return false;
    }

    public String getProductionReadinessReason() {
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
