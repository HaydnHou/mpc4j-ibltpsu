package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT output paired with a fixed direct-all-buckets debug transcript.
 *
 * <p>The fixed transcript is a payload-bound implementation scaffold. The final union output is still produced by the
 * plain peel driver; the transcript fixes the public bucket schedule that later network endpoints can attach capsules
 * to without depending on private overlap or peel success. Raw transcript bucket inputs/outputs are local debug data and
 * must not be treated as wire messages.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltTranscriptResult {
    /**
     * plain peel result.
     */
    private final BaSsuIbltPlainResult plainResult;
    /**
     * fixed bucket schedule.
     */
    private final BaSsuIbltFixedBucketSchedule schedule;
    /**
     * retry transcripts.
     */
    private final List<BaSsuIbltRetryTranscript> retryTranscripts;

    private BaSsuIbltTranscriptResult(BaSsuIbltPlainResult plainResult, BaSsuIbltFixedBucketSchedule schedule,
                                      List<BaSsuIbltRetryTranscript> retryTranscripts) {
        if (retryTranscripts.size() != schedule.getRetryCount()) {
            throw new IllegalArgumentException("retry transcript count must equal schedule retry count");
        }
        this.plainResult = plainResult;
        this.schedule = schedule;
        this.retryTranscripts = Collections.unmodifiableList(new ArrayList<>(retryTranscripts));
    }

    /**
     * Runs plain BA-SSU output and builds a fixed direct-all-buckets transcript with the ideal evaluator.
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength element byte length.
     * @param params parameters.
     * @return transcript result.
     */
    static BaSsuIbltTranscriptResult runBiOutput(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                 int elementByteLength,
                                                 BaSsuIbltBiUpsuParams params) {
        return runBiOutput(leftSet, rightSet, elementByteLength, params, BaUpotIdealEvaluator.getInstance());
    }

    /**
     * Runs plain BA-SSU output and builds a fixed direct-all-buckets debug transcript.
     *
     * <p>The evaluator only materializes the debug transcript. It does not drive the plain union output.</p>
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength element byte length.
     * @param params parameters.
     * @param transcriptEvaluator evaluator for fixed transcript buckets.
     * @return transcript result.
     */
    static BaSsuIbltTranscriptResult runBiOutput(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                 int elementByteLength, BaSsuIbltBiUpsuParams params,
                                                 BaUpotBucketEvaluator transcriptEvaluator) {
        BaSsuIbltPlainResult plainResult = BaSsuIbltPlainProtocol.runBiOutput(
            leftSet, rightSet, elementByteLength, params
        );
        BaSsuIbltFixedBucketSchedule schedule = BaSsuIbltFixedBucketSchedule.directAllBuckets(params);
        List<BaSsuIbltRetryTranscript> retryTranscripts = buildRetryTranscripts(
            leftSet, rightSet, elementByteLength, params, schedule, transcriptEvaluator
        );
        return new BaSsuIbltTranscriptResult(plainResult, schedule, retryTranscripts);
    }

    private static List<BaSsuIbltRetryTranscript> buildRetryTranscripts(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        BaSsuIbltFixedBucketSchedule schedule, BaUpotBucketEvaluator evaluator) {
        Set<ByteBuffer> anchorSet = leftSet.size() >= rightSet.size() ? leftSet : rightSet;
        Set<ByteBuffer> shadowSet = leftSet.size() >= rightSet.size() ? rightSet : leftSet;
        List<BaSsuIbltRetryTranscript> retryTranscripts = new ArrayList<>(schedule.getRetryCount());
        for (int retryIndex = 0; retryIndex < schedule.getRetryCount(); retryIndex++) {
            BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, retryIndex, elementByteLength);
            table.insertAnchors(anchorSet);
            table.insertShadows(shadowSet);
            int[] bucketIndices = schedule.getBucketIndices(retryIndex);
            List<BaSsuIbltBucketTranscript> bucketTranscripts = new ArrayList<>(bucketIndices.length);
            for (int offset = 0; offset < bucketIndices.length; offset++) {
                int bucketIndex = bucketIndices[offset];
                BaUpotBucketInput input = table.getBucketInput(bucketIndex);
                BaUpotBucketOutput output = evaluator.evaluate(input);
                bucketTranscripts.add(BaSsuIbltBucketTranscript.of(
                    retryIndex, schedule.globalOrdinal(retryIndex, offset), bucketIndex, input, output
                ));
            }
            retryTranscripts.add(new BaSsuIbltRetryTranscript(schedule, retryIndex, bucketTranscripts));
        }
        return retryTranscripts;
    }

    BaSsuIbltPlainResult getPlainResult() {
        return plainResult;
    }

    BaSsuIbltFixedBucketSchedule getSchedule() {
        return schedule;
    }

    List<BaSsuIbltRetryTranscript> getRetryTranscripts() {
        return retryTranscripts;
    }

    List<BaSsuIbltBucketTranscript> getBucketTranscripts() {
        List<BaSsuIbltBucketTranscript> bucketTranscripts = new ArrayList<>();
        for (BaSsuIbltRetryTranscript retryTranscript : retryTranscripts) {
            bucketTranscripts.addAll(retryTranscript.getBucketTranscripts());
        }
        return Collections.unmodifiableList(bucketTranscripts);
    }

    long getTotalBucketTranscriptCount() {
        return schedule.getTotalBucketCount();
    }
}
