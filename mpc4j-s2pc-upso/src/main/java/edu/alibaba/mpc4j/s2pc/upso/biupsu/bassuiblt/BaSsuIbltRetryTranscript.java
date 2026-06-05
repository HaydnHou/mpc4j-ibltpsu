package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fixed-schedule transcript for one BA-SSU-IBLT retry.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltRetryTranscript {
    /**
     * retry index.
     */
    private final int retryIndex;
    /**
     * bucket transcripts.
     */
    private final List<BaSsuIbltBucketTranscript> bucketTranscripts;

    BaSsuIbltRetryTranscript(BaSsuIbltFixedBucketSchedule schedule, int retryIndex,
                             List<BaSsuIbltBucketTranscript> bucketTranscripts) {
        if (retryIndex < 0) {
            throw new IllegalArgumentException("retryIndex must be non-negative");
        }
        int[] expectedBucketIndices = schedule.getBucketIndices(retryIndex);
        if (bucketTranscripts.size() != expectedBucketIndices.length) {
            throw new IllegalArgumentException("bucket transcript count must equal fixed schedule length");
        }
        for (int offset = 0; offset < bucketTranscripts.size(); offset++) {
            BaSsuIbltBucketTranscript transcript = bucketTranscripts.get(offset);
            if (transcript.getRetryIndex() != retryIndex) {
                throw new IllegalArgumentException("all bucket transcripts must use the same retry index");
            }
            if (transcript.getBucketIndex() != expectedBucketIndices[offset]) {
                throw new IllegalArgumentException("bucket transcript index does not match fixed schedule");
            }
            if (transcript.getGlobalOrdinal() != schedule.globalOrdinal(retryIndex, offset)) {
                throw new IllegalArgumentException("bucket transcript ordinal does not match fixed schedule");
            }
        }
        this.retryIndex = retryIndex;
        this.bucketTranscripts = Collections.unmodifiableList(new ArrayList<>(bucketTranscripts));
    }

    int getRetryIndex() {
        return retryIndex;
    }

    int size() {
        return bucketTranscripts.size();
    }

    List<BaSsuIbltBucketTranscript> getBucketTranscripts() {
        return bucketTranscripts;
    }

    List<BaUpotBucketInput> getInputs() {
        List<BaUpotBucketInput> inputs = new ArrayList<>(bucketTranscripts.size());
        for (BaSsuIbltBucketTranscript transcript : bucketTranscripts) {
            inputs.add(transcript.getDebugInput());
        }
        return inputs;
    }

    List<BaUpotBucketOutput> getOutputs() {
        List<BaUpotBucketOutput> outputs = new ArrayList<>(bucketTranscripts.size());
        for (BaSsuIbltBucketTranscript transcript : bucketTranscripts) {
            outputs.add(transcript.getDebugOutput());
        }
        return outputs;
    }

    int[] getBucketIndices() {
        int[] bucketIndices = new int[bucketTranscripts.size()];
        for (int index = 0; index < bucketTranscripts.size(); index++) {
            bucketIndices[index] = bucketTranscripts.get(index).getBucketIndex();
        }
        return bucketIndices;
    }
}
