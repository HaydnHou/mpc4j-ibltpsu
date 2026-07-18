package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

/**
 * Domain phase for packed PRSS backends.
 *
 * @author donghai hou
 * @date 2026/07/18
 */
public enum PrssPhase {
    /** Full public frontier batch. */
    FULL,
    /** Public lanes selected after opening the peel bitmap. */
    SELECTED,
    /** Public residual-existence check. */
    RESIDUAL
}
