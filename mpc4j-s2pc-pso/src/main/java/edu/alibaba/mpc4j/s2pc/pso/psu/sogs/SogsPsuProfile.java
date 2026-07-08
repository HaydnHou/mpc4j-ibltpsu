package edu.alibaba.mpc4j.s2pc.pso.psu.sogs;

/**
 * SOGS-PSU execution profile.
 *
 * @author donghai hou
 * @date 2026/06/12
 */
public enum SogsPsuProfile {
    /**
     * Balanced single-session SOGS-PSU. This is the current IBLT-style union-peel baseline.
     */
    BALANCED,
    /**
     * Reusable unbalanced SOGS-PSU for static large-set repeated sessions.
     */
    UNBALANCED_REUSABLE,
}
