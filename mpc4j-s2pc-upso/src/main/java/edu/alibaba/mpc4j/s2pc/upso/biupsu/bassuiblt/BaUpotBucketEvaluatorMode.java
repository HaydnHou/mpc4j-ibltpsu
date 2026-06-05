package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-UPOT bucket evaluator mode.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public enum BaUpotBucketEvaluatorMode {
    /**
     * Direct ideal bucket semantics.
     */
    IDEAL,
    /**
     * Fixed case-gate evaluator with live gate counting.
     */
    CASE_GATE,
    /**
     * Fixed case-gate evaluator followed by wire-masked fixed output capsules.
     */
    CASE_GATE_WIRE_MASKED,
}
