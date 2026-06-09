package edu.alibaba.mpc4j.common.structure.sogs;

/**
 * PSU sketch backend type.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public enum SogsPsuSketchBackendType {
    /**
     * Five-hash long IBLT.
     */
    H5_IBLT,
    /**
     * Source-oblivious graph sketch.
     */
    SOGS_GRAPH,
}
