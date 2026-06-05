package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-SSU-IBLT bi-output delivery mode.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public enum BaSsuIbltBiOutputDeliveryMode {
    /**
     * One signed/source-split peel stream carries both anchor-only and shadow-only difference elements.
     */
    SIGNED_SOURCE_SPLIT,
    /**
     * Legacy conservative accounting with two independent one-way delivery streams.
     */
    TWO_PASS,
    /**
     * One-way output comparison mode.
     */
    SINGLE_OUTPUT,
}
