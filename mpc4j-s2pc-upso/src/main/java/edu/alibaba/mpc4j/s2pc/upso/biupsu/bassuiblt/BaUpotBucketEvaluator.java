package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UPOT bucket evaluator.
 *
 * <p>This is the replacement point between the BA-SSU-IBLT peel driver and a concrete BA-UPOT realization. Test and
 * plain implementations may evaluate locally; the final secure implementation must preserve this functionality while
 * hiding the bucket case according to the protocol design.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
interface BaUpotBucketEvaluator {
    /**
     * Evaluates one bucket input.
     *
     * @param input bucket input.
     * @return bucket output.
     */
    BaUpotBucketOutput evaluate(BaUpotBucketInput input);
}
