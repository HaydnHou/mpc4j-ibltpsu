package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * BA-SSU-IBLT protocol mode.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public enum BaSsuIbltProtocolMode {
    /**
     * Explicitly enabled fixed-layer reference endpoint for correctness tests only.
     */
    REFERENCE_FIXED_LAYER,
    /**
     * Costed/reference benchmark path.
     */
    COSTED_BENCHMARK,
    /**
     * Future production secure semi-honest protocol path.
     */
    SECURE_SEMI_HONEST
}
