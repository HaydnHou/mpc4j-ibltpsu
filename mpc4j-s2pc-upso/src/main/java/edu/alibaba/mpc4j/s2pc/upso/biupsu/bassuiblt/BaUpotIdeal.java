package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ideal payload-bound BA-UPOT bucket functionality.
 *
 * <p>This class is intentionally not a secure protocol. It defines the exact bucket semantics that a later
 * semi-honest BA-UPOT realization must implement on masked or secret-shared bucket inputs.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotIdeal {
    /**
     * private constructor.
     */
    private BaUpotIdeal() {
        // empty
    }

    /**
     * Evaluates one bucket.
     *
     * @param input bucket input.
     * @return bucket output.
     */
    public static BaUpotBucketOutput evaluate(BaUpotBucketInput input) {
        boolean anchorSingleton = input.getAnchorCount() == 1
            && validSingleton(input.getAnchorKeyXorReference(), input.getAnchorCheckXorReference());
        boolean shadowSingleton = input.getShadowCount() == 1
            && validSingleton(input.getShadowKeyXorReference(), input.getShadowCheckXorReference());
        if (anchorSingleton && input.getShadowCount() == 0) {
            return BaUpotBucketOutput.singleton(input.getBucketIndex(), BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON,
                input.getAnchorKeyXorReference());
        }
        if (shadowSingleton && input.getAnchorCount() == 0) {
            return BaUpotBucketOutput.singleton(input.getBucketIndex(), BaUpotBucketOutput.CaseType.SHADOW_SINGLETON,
                input.getShadowKeyXorReference());
        }
        if (anchorSingleton && shadowSingleton
            && Arrays.equals(input.getAnchorKeyXorReference(), input.getShadowKeyXorReference())) {
            return BaUpotBucketOutput.singleton(input.getBucketIndex(), BaUpotBucketOutput.CaseType.SHARED_SINGLETON,
                input.getAnchorKeyXorReference());
        }
        if (input.getAnchorCount() == 0 && input.getShadowCount() == 0 && isZero(input.getAnchorKeyXorReference())
            && isZero(input.getShadowKeyXorReference()) && isZero(input.getAnchorCheckXorReference())
            && isZero(input.getShadowCheckXorReference())) {
            return BaUpotBucketOutput.empty(input.getBucketIndex());
        }
        return BaUpotBucketOutput.blocked(input.getBucketIndex());
    }

    /**
     * Evaluates a batch of buckets.
     *
     * @param inputs bucket inputs.
     * @return bucket outputs.
     */
    public static List<BaUpotBucketOutput> evaluateBatch(List<BaUpotBucketInput> inputs) {
        List<BaUpotBucketOutput> outputs = new ArrayList<>(inputs.size());
        for (BaUpotBucketInput input : inputs) {
            outputs.add(evaluate(input));
        }
        return outputs;
    }

    private static boolean validSingleton(byte[] candidate, byte[] checkXor) {
        return BaSsuIbltOprfTagPipeline.equalCheck(candidate, checkXor);
    }

    static byte[] digest(byte[] input, int outputByteLength) {
        return BaSsuIbltOprfTagPipeline.referenceCheck(input, outputByteLength);
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
