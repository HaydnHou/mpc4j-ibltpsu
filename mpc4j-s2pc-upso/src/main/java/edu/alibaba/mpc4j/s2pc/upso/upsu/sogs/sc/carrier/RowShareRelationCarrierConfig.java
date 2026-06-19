package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * Row-level share relation carrier config.
 *
 * <p>The carrier outputs secret shares of one folded hit bit per anonymous row. It must not output plaintext
 * membership bits, open status, failure status, or row-linked labels.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public interface RowShareRelationCarrierConfig extends MultiPartyPtoConfig {
    /**
     * Relation-carrier input type.
     */
    enum InputType {
        /**
         * The caller provides PEQT digests.
         */
        DIGEST,
        /**
         * The caller provides raw FHE response / mask slots.
         */
        RAW,
    }

    /**
     * Public network shape.
     */
    enum NetworkShape {
        /**
         * The public carrier transcript scales with the internal alpha-by-bin relation matrix.
         */
        ALPHA_BY_BIN,
        /**
         * The public carrier transcript is aggregate / bin-shaped and does not serialize alpha-by-bin labels.
         */
        BIN_AGGREGATE,
    }

    /**
     * Gets the protocol type.
     *
     * @return protocol type.
     */
    RowShareRelationCarrierFactory.RowShareRelationCarrierType getPtoType();

    /**
     * Gets the input type expected by the carrier.
     *
     * @return input type.
     */
    InputType getInputType();

    /**
     * Gets the public network shape exposed by this carrier.
     *
     * @return public network shape.
     */
    NetworkShape getNetworkShape();
}
