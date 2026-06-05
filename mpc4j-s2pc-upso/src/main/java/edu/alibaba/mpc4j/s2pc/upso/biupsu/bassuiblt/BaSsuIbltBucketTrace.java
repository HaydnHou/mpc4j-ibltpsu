package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * One scheduled BA-SSU-IBLT bucket trace entry.
 *
 * <p>The trace is a correctness/debug artifact for payload-bound implementation work. It must not be enabled for
 * large benchmarks or treated as a protocol message.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaSsuIbltBucketTrace {
    /**
     * retry index.
     */
    private final int retryIndex;
    /**
     * peel round index.
     */
    private final int roundIndex;
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * BA-UPOT input.
     */
    private final BaUpotBucketInput input;
    /**
     * BA-UPOT output.
     */
    private final BaUpotBucketOutput output;
    /**
     * true when the singleton was accepted as a fresh peel element.
     */
    private final boolean accepted;

    BaSsuIbltBucketTrace(int retryIndex, int roundIndex, int bucketIndex, BaUpotBucketInput input,
                         BaUpotBucketOutput output, boolean accepted) {
        this.retryIndex = retryIndex;
        this.roundIndex = roundIndex;
        this.bucketIndex = bucketIndex;
        this.input = input;
        this.output = output;
        this.accepted = accepted;
    }

    int getRetryIndex() {
        return retryIndex;
    }

    int getRoundIndex() {
        return roundIndex;
    }

    int getBucketIndex() {
        return bucketIndex;
    }

    BaUpotBucketInput getInput() {
        return input;
    }

    BaUpotBucketOutput getOutput() {
        return output;
    }

    boolean isAccepted() {
        return accepted;
    }
}
