package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

/**
 * Specialized BA-UPOT standalone benchmark config.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotConfig extends AbstractMultiPartyPtoConfig implements BaSsuIbltBaUpotBackendConfig {
    /**
     * default case count.
     */
    public static final int DEFAULT_CASE_NUM = 4;
    /**
     * default COT count per bucket.
     */
    public static final int DEFAULT_COT_NUM_PER_BUCKET = 2;
    /**
     * default online batch size.
     */
    public static final int DEFAULT_ONLINE_BATCH_SIZE = 1 << 14;

    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * check bits.
     */
    private final int checkBits;
    /**
     * tag bits.
     */
    private final int tagBits;
    /**
     * fixed case count.
     */
    private final int caseNum;
    /**
     * COT count per bucket.
     */
    private final int cotNumPerBucket;
    /**
     * online send batch size.
     */
    private final int onlineBatchSize;
    /**
     * COT config.
     */
    private final CoreCotConfig coreCotConfig;

    private BaUpotConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.coreCotConfig);
        elementByteLength = builder.elementByteLength;
        checkBits = builder.checkBits;
        tagBits = builder.tagBits;
        caseNum = builder.caseNum;
        cotNumPerBucket = builder.cotNumPerBucket;
        onlineBatchSize = builder.onlineBatchSize;
        coreCotConfig = builder.coreCotConfig;
        validate();
    }

    private void validate() {
        if (elementByteLength <= 0 || elementByteLength > 32) {
            throw new IllegalArgumentException("elementByteLength must be in range [1, 32]");
        }
        if (checkBits <= 0 || checkByteLength() > 32) {
            throw new IllegalArgumentException("checkBits must fit one SHA-256 digest");
        }
        if (tagBits <= 0 || tagByteLength() > 32) {
            throw new IllegalArgumentException("tagBits must fit one SHA-256 digest");
        }
        if (tagBits < checkBits) {
            throw new IllegalArgumentException("tagBits must be at least checkBits");
        }
        if (caseNum <= 0) {
            throw new IllegalArgumentException("caseNum must be positive");
        }
        if (cotNumPerBucket <= 0) {
            throw new IllegalArgumentException("cotNumPerBucket must be positive");
        }
        if (onlineBatchSize <= 0) {
            throw new IllegalArgumentException("onlineBatchSize must be positive");
        }
    }

    public int getElementByteLength() {
        return elementByteLength;
    }

    public int getCheckBits() {
        return checkBits;
    }

    public int getTagBits() {
        return tagBits;
    }

    public int getCaseNum() {
        return caseNum;
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

    public int checkByteLength() {
        return (checkBits + Byte.SIZE - 1) / Byte.SIZE;
    }

    public int tagByteLength() {
        return (tagBits + Byte.SIZE - 1) / Byte.SIZE;
    }

    public int onlinePayloadByteLength() {
        return 2 * elementByteLength + 2 * checkByteLength() + 2 * BaUpotMicroBenchmark.BLOCK_BYTE_LENGTH;
    }

    public int cotNum(int bucketNum) {
        return Math.multiplyExact(bucketNum, cotNumPerBucket);
    }

    @Override
    public String getBackendName() {
        return "BA-UPOT benchmark/plain transport";
    }

    @Override
    public boolean isObliviousBranchSelection() {
        return false;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<BaUpotConfig> {
        /**
         * element byte length.
         */
        private int elementByteLength;
        /**
         * check bits.
         */
        private int checkBits;
        /**
         * tag bits.
         */
        private int tagBits;
        /**
         * case count.
         */
        private int caseNum;
        /**
         * COT count per bucket.
         */
        private int cotNumPerBucket;
        /**
         * online batch size.
         */
        private int onlineBatchSize;
        /**
         * COT config.
         */
        private CoreCotConfig coreCotConfig;

        public Builder() {
            elementByteLength = Long.BYTES;
            checkBits = 182;
            tagBits = 182;
            caseNum = DEFAULT_CASE_NUM;
            cotNumPerBucket = DEFAULT_COT_NUM_PER_BUCKET;
            onlineBatchSize = DEFAULT_ONLINE_BATCH_SIZE;
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setElementByteLength(int elementByteLength) {
            this.elementByteLength = elementByteLength;
            return this;
        }

        public Builder setCheckBits(int checkBits) {
            this.checkBits = checkBits;
            return this;
        }

        public Builder setTagBits(int tagBits) {
            this.tagBits = tagBits;
            return this;
        }

        public Builder setCaseNum(int caseNum) {
            this.caseNum = caseNum;
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
            this.coreCotConfig = coreCotConfig;
            return this;
        }

        @Override
        public BaUpotConfig build() {
            return new BaUpotConfig(this);
        }
    }
}
