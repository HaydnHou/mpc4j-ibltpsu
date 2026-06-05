package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Protocol-facing BA-UnionPeel-OT bridge config.
 *
 * <p>The config deliberately carries only fixed public shape parameters. The current bridge uses precomputed ideal
 * bucket outputs and is therefore a payload-delivery milestone, not a completed secure BA-UPOT evaluator.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaUnionPeelOtConfig {
    /**
     * default mode.
     */
    private static final BaUnionPeelOtMode DEFAULT_MODE = BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD;
    /**
     * delivery mode.
     */
    private final BaUnionPeelOtMode mode;
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
     * COT count per bucket for later secure backend accounting.
     */
    private final int cotNumPerBucket;
    /**
     * online batch size.
     */
    private final int onlineBatchSize;
    /**
     * wire-mask seed.
     */
    private final byte[] maskSeed;

    private BaUnionPeelOtConfig(Builder builder) {
        mode = builder.mode;
        elementByteLength = builder.elementByteLength;
        checkBits = builder.checkBits;
        tagBits = builder.tagBits;
        cotNumPerBucket = builder.cotNumPerBucket;
        onlineBatchSize = builder.onlineBatchSize;
        maskSeed = Arrays.copyOf(builder.maskSeed, builder.maskSeed.length);
    }

    /**
     * Creates a bridge config from an existing BA-UPOT benchmark config.
     *
     * @param benchmarkConfig benchmark config.
     * @param mode bridge mode.
     * @return bridge config.
     */
    public static BaUnionPeelOtConfig fromBenchmarkConfig(BaUpotTwoPartyBenchmark.BenchmarkConfig benchmarkConfig,
                                                          BaUnionPeelOtMode mode) {
        if (benchmarkConfig == null) {
            throw new IllegalArgumentException("benchmarkConfig must be non-null");
        }
        return new Builder()
            .setMode(mode)
            .setElementByteLength(benchmarkConfig.getElementByteLength())
            .setCheckBits(benchmarkConfig.getCheckBits())
            .setTagBits(benchmarkConfig.getTagBits())
            .setCotNumPerBucket(benchmarkConfig.getCotNumPerBucket())
            .setOnlineBatchSize(benchmarkConfig.getOnlineBatchSize())
            .build();
    }

    public BaUnionPeelOtMode getMode() {
        return mode;
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

    public int getCotNumPerBucket() {
        return cotNumPerBucket;
    }

    public int getOnlineBatchSize() {
        return onlineBatchSize;
    }

    public byte[] getMaskSeed() {
        return Arrays.copyOf(maskSeed, maskSeed.length);
    }

    /**
     * Creates a BA-UPOT config with a run-specific bucket count.
     *
     * @param bucketNum bucket count.
     * @return benchmark config.
     */
    BaUpotTwoPartyBenchmark.BenchmarkConfig toBenchmarkConfig(int bucketNum) {
        return new BaUpotTwoPartyBenchmark.BenchmarkConfig()
            .setBucketNum(bucketNum)
            .setElementByteLength(elementByteLength)
            .setCheckBits(checkBits)
            .setTagBits(tagBits)
            .setCotNumPerBucket(cotNumPerBucket)
            .setOnlineBatchSize(onlineBatchSize);
    }

    /**
     * Builder.
     */
    public static class Builder {
        /**
         * mode.
         */
        private BaUnionPeelOtMode mode;
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
         * COT count per bucket.
         */
        private int cotNumPerBucket;
        /**
         * online batch size.
         */
        private int onlineBatchSize;
        /**
         * mask seed.
         */
        private byte[] maskSeed;

        public Builder() {
            mode = DEFAULT_MODE;
            elementByteLength = Long.BYTES;
            checkBits = 182;
            tagBits = 182;
            cotNumPerBucket = BaUpotConfig.DEFAULT_COT_NUM_PER_BUCKET;
            onlineBatchSize = BaUpotConfig.DEFAULT_ONLINE_BATCH_SIZE;
            maskSeed = BaUpotWireMaskedPayloadTwoPartyBenchmark.DEFAULT_MASK_SEED;
        }

        public Builder setMode(BaUnionPeelOtMode mode) {
            this.mode = mode;
            return this;
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

        public Builder setCotNumPerBucket(int cotNumPerBucket) {
            this.cotNumPerBucket = cotNumPerBucket;
            return this;
        }

        public Builder setOnlineBatchSize(int onlineBatchSize) {
            this.onlineBatchSize = onlineBatchSize;
            return this;
        }

        public Builder setMaskSeed(byte[] maskSeed) {
            if (maskSeed == null) {
                throw new IllegalArgumentException("maskSeed must be non-null");
            }
            this.maskSeed = Arrays.copyOf(maskSeed, maskSeed.length);
            return this;
        }

        public BaUnionPeelOtConfig build() {
            if (mode == null) {
                throw new IllegalArgumentException("mode must be set");
            }
            if (elementByteLength <= 0) {
                throw new IllegalArgumentException("elementByteLength must be positive");
            }
            if (checkBits <= 0) {
                throw new IllegalArgumentException("checkBits must be positive");
            }
            if (tagBits <= 0) {
                throw new IllegalArgumentException("tagBits must be positive");
            }
            if (cotNumPerBucket <= 0) {
                throw new IllegalArgumentException("cotNumPerBucket must be positive");
            }
            if (onlineBatchSize <= 0) {
                throw new IllegalArgumentException("onlineBatchSize must be positive");
            }
            if (maskSeed == null || maskSeed.length == 0) {
                throw new IllegalArgumentException("maskSeed must be non-empty");
            }
            return new BaUnionPeelOtConfig(this);
        }
    }
}
