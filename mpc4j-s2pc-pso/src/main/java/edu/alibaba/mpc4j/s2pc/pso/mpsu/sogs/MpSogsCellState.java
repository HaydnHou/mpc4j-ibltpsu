package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Local MP-SOGS cell state.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public enum MpSogsCellState {
    /**
     * Empty cell.
     */
    EMPTY,
    /**
     * Singleton cell.
     */
    SINGLETON,
    /**
     * Heavy, collided, or invalid cell.
     */
    HEAVY,
}
