package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * One fixed-schedule BA-SSU-IBLT debug bucket transcript entry.
 *
 * <p>This is a local/debug transcript object. It binds a public retry index, public bucket index, and fixed global
 * ordinal to the payload-bound BA-UPOT input/output used by the current prototype. The raw input/output fields reveal
 * bucket counts, XORs, cases, and singleton payloads; they must never be serialized as protocol messages.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltBucketTranscript {
    /**
     * retry index.
     */
    private final int retryIndex;
    /**
     * fixed global ordinal.
     */
    private final long globalOrdinal;
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * bucket input.
     */
    private final BaUpotBucketInput input;
    /**
     * bucket output.
     */
    private final BaUpotBucketOutput output;

    private BaSsuIbltBucketTranscript(int retryIndex, long globalOrdinal, int bucketIndex, BaUpotBucketInput input,
                                      BaUpotBucketOutput output) {
        if (retryIndex < 0) {
            throw new IllegalArgumentException("retryIndex must be non-negative");
        }
        if (globalOrdinal < 0) {
            throw new IllegalArgumentException("globalOrdinal must be non-negative");
        }
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (input.getBucketIndex() != bucketIndex || output.getBucketIndex() != bucketIndex) {
            throw new IllegalArgumentException("bucket indices must match");
        }
        this.retryIndex = retryIndex;
        this.globalOrdinal = globalOrdinal;
        this.bucketIndex = bucketIndex;
        this.input = copyInput(input);
        this.output = copyOutput(output);
    }

    /**
     * Creates a transcript entry.
     *
     * @param retryIndex retry index.
     * @param globalOrdinal fixed global ordinal.
     * @param bucketIndex bucket index.
     * @param input bucket input.
     * @param output bucket output.
     * @return bucket transcript.
     */
    static BaSsuIbltBucketTranscript of(int retryIndex, long globalOrdinal, int bucketIndex,
                                        BaUpotBucketInput input, BaUpotBucketOutput output) {
        return new BaSsuIbltBucketTranscript(retryIndex, globalOrdinal, bucketIndex, input, output);
    }

    int getRetryIndex() {
        return retryIndex;
    }

    long getGlobalOrdinal() {
        return globalOrdinal;
    }

    int getBucketIndex() {
        return bucketIndex;
    }

    BaUpotBucketInput getDebugInput() {
        return copyInput(input);
    }

    BaUpotBucketOutput getDebugOutput() {
        return copyOutput(output);
    }

    private static BaUpotBucketInput copyInput(BaUpotBucketInput input) {
        return BaUpotBucketInput.of(
            input.getBucketIndex(),
            input.getAnchorCount(),
            input.getShadowCount(),
            input.getAnchorKeyXor(),
            input.getShadowKeyXor(),
            input.getAnchorCheckXor(),
            input.getShadowCheckXor()
        );
    }

    private static BaUpotBucketOutput copyOutput(BaUpotBucketOutput output) {
        if (output.isSingleton()) {
            return BaUpotBucketOutput.singleton(output.getBucketIndex(), output.getCaseType(), output.getElement());
        }
        return switch (output.getCaseType()) {
            case EMPTY -> BaUpotBucketOutput.empty(output.getBucketIndex());
            case BLOCKED -> BaUpotBucketOutput.blocked(output.getBucketIndex());
            default -> throw new IllegalArgumentException("singleton case must carry an element");
        };
    }
}
