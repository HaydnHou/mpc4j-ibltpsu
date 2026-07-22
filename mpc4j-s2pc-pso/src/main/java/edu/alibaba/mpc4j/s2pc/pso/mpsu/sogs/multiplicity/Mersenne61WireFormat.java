package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

/**
 * Wire representation for vectors over the Mersenne-61 field.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public enum Mersenne61WireFormat {
    /** One Java long per field element. */
    LONG_64,
    /** Contiguous canonical 61-bit field elements. */
    PACKED_61
}
