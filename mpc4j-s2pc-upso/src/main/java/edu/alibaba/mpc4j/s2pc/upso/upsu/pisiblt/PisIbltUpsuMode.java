package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

/**
 * PISF-IBLT enhanced UPSU execution mode.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public enum PisIbltUpsuMode {
    /**
     * Small-scale strict Z2 sort/group backend.
     */
    STRICT_Z2_SORT,
    /**
     * Fast source-split PISF-IBLT backend with explicit IBLT-style leakage.
     */
    PISF_FAST,
    /**
     * Deprecated alias for {@link #STRICT_Z2_SORT}.
     */
    STRICT,
    /**
     * Deprecated alias for {@link #PISF_FAST}.
     */
    HIGH_THROUGHPUT,
    ;

    /**
     * Returns the canonical mode.
     *
     * @return canonical mode.
     */
    public PisIbltUpsuMode canonical() {
        switch (this) {
            case STRICT:
                return STRICT_Z2_SORT;
            case HIGH_THROUGHPUT:
                return PISF_FAST;
            default:
                return this;
        }
    }
}
