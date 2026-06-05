package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BA-SSU-IBLT bi-output UPSU parameters.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltBiUpsuParams {
    /**
     * default statistical security parameter.
     */
    public static final int DEFAULT_LAMBDA = 128;
    /**
     * default check margin bits.
     */
    public static final int DEFAULT_MARGIN_BITS = 32;
    /**
     * default checks per bucket.
     */
    public static final int DEFAULT_CHECKS_PER_BUCKET = 3;
    /**
     * large set size.
     */
    private final int nLarge;
    /**
     * shadow set size.
     */
    private final int nShadow;
    /**
     * anchor table multiplier.
     */
    private final double alphaAnchor;
    /**
     * anchor table length.
     */
    private final int tableLength;
    /**
     * common visible degree.
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
     * check bits.
     */
    private final int checkBits;
    /**
     * tag bits.
     */
    private final int tagBits;
    /**
     * margin bits for union bound.
     */
    private final int marginBits;
    /**
     * per-bucket check count.
     */
    private final int checksPerBucket;
    /**
     * public placement seed.
     */
    private final byte[] publicPlaceSeed;
    /**
     * profile id.
     */
    private final String profileId;

    private BaSsuIbltBiUpsuParams(Builder builder) {
        nLarge = builder.nLarge;
        nShadow = builder.nShadow;
        alphaAnchor = builder.alphaAnchor;
        tableLength = builder.tableLength > 0 ? builder.tableLength : (int) Math.ceil(alphaAnchor * nLarge);
        degree = builder.degree;
        retryCount = builder.retryCount;
        lambda = builder.lambda;
        marginBits = builder.marginBits;
        checksPerBucket = builder.checksPerBucket;
        int minCheckBits = minCheckBits(lambda, builder.nTests, marginBits);
        checkBits = builder.checkBits > 0 ? builder.checkBits : minCheckBits;
        tagBits = builder.tagBits > 0 ? builder.tagBits : Math.max(checkBits, minCheckBits);
        publicPlaceSeed = Arrays.copyOf(builder.publicPlaceSeed, builder.publicPlaceSeed.length);
        profileId = builder.profileId == null
            ? "BA_SSU_L" + tableLength + "_D" + degree + "_R" + retryCount
            : builder.profileId;
        validate();
    }

    private void validate() {
        if (nLarge <= 0) {
            throw new IllegalArgumentException("nLarge must be positive");
        }
        if (nShadow < 0) {
            throw new IllegalArgumentException("nShadow must be non-negative");
        }
        if (tableLength <= 0) {
            throw new IllegalArgumentException("tableLength must be positive");
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
        if (checkBits < lambda) {
            throw new IllegalArgumentException("checkBits must be at least lambda");
        }
        if (tagBits < checkBits) {
            throw new IllegalArgumentException("tagBits must be at least checkBits");
        }
        if (checksPerBucket <= 0) {
            throw new IllegalArgumentException("checksPerBucket must be positive");
        }
    }

    /**
     * Computes the minimum check bits for a union-bound budget.
     *
     * @param lambda security parameter.
     * @param nTests number of false singleton / match tests.
     * @param marginBits margin bits.
     * @return minimum check bits.
     */
    public static int minCheckBits(int lambda, long nTests, int marginBits) {
        return lambda + ceilLog2(Math.max(1L, nTests)) + Math.max(0, marginBits);
    }

    /**
     * Returns ceil(log2(x)).
     *
     * @param x positive value.
     * @return ceil(log2(x)).
     */
    public static int ceilLog2(long x) {
        if (x <= 1) {
            return 0;
        }
        return Long.SIZE - Long.numberOfLeadingZeros(x - 1);
    }

    public int getNLarge() {
        return nLarge;
    }

    public int getNShadow() {
        return nShadow;
    }

    public double getAlphaAnchor() {
        return alphaAnchor;
    }

    public int getTableLength() {
        return tableLength;
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

    public int getCheckBits() {
        return checkBits;
    }

    public int getTagBits() {
        return tagBits;
    }

    public int getMarginBits() {
        return marginBits;
    }

    public int getChecksPerBucket() {
        return checksPerBucket;
    }

    public byte[] getPublicPlaceSeed() {
        return Arrays.copyOf(publicPlaceSeed, publicPlaceSeed.length);
    }

    byte[] getPublicPlaceSeedReference() {
        return publicPlaceSeed;
    }

    public String getProfileId() {
        return profileId;
    }

    /**
     * Builder.
     */
    public static class Builder {
        /**
         * large set size.
         */
        private final int nLarge;
        /**
         * shadow set size.
         */
        private final int nShadow;
        /**
         * anchor multiplier.
         */
        private double alphaAnchor;
        /**
         * table length.
         */
        private int tableLength;
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
         * check bits.
         */
        private int checkBits;
        /**
         * tag bits.
         */
        private int tagBits;
        /**
         * margin bits.
         */
        private int marginBits;
        /**
         * checks per bucket.
         */
        private int checksPerBucket;
        /**
         * estimated tests.
         */
        private long nTests;
        /**
         * public placement seed.
         */
        private byte[] publicPlaceSeed;
        /**
         * profile id.
         */
        private String profileId;

        public Builder(int nLarge, int nShadow) {
            this.nLarge = nLarge;
            this.nShadow = nShadow;
            alphaAnchor = 1.55;
            degree = 3;
            retryCount = 1;
            lambda = DEFAULT_LAMBDA;
            marginBits = DEFAULT_MARGIN_BITS;
            checksPerBucket = DEFAULT_CHECKS_PER_BUCKET;
            nTests = Math.max(1L, (long) retryCount * Math.max(1, nLarge) * checksPerBucket);
            publicPlaceSeed = defaultSeed(nLarge, nShadow, 0L);
        }

        public Builder setAlphaAnchor(double alphaAnchor) {
            this.alphaAnchor = alphaAnchor;
            return this;
        }

        public Builder setTableLength(int tableLength) {
            this.tableLength = tableLength;
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

        public Builder setCheckBits(int checkBits) {
            this.checkBits = checkBits;
            return this;
        }

        public Builder setTagBits(int tagBits) {
            this.tagBits = tagBits;
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

        public Builder setNTests(long nTests) {
            this.nTests = nTests;
            return this;
        }

        public Builder setPublicPlaceSeed(byte[] publicPlaceSeed) {
            this.publicPlaceSeed = Arrays.copyOf(publicPlaceSeed, publicPlaceSeed.length);
            return this;
        }

        public Builder setPublicPlaceSeed(long seed) {
            publicPlaceSeed = defaultSeed(nLarge, nShadow, seed);
            return this;
        }

        public Builder setProfileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public BaSsuIbltBiUpsuParams build() {
            if (tableLength <= 0) {
                tableLength = (int) Math.ceil(alphaAnchor * nLarge);
            }
            nTests = Math.max(nTests, (long) retryCount * tableLength * checksPerBucket);
            return new BaSsuIbltBiUpsuParams(this);
        }

        private static byte[] defaultSeed(int nLarge, int nShadow, long seed) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 2 + Long.BYTES);
            byteBuffer.putInt(nLarge);
            byteBuffer.putInt(nShadow);
            byteBuffer.putLong(seed);
            return byteBuffer.array();
        }
    }
}
