package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

/**
 * Public collective-opening policy for the RTT-aware SSM backend.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public enum SsmOpeningMode {
    /** Minimum payload with root collection followed by root broadcast. */
    BALANCED_TWO_PHASE,
    /** One communication phase in which every party sends to every other party. */
    ALL_TO_ALL_ONE_PHASE,
    /** Selects from public vector length and configured public network measurements. */
    AUTO;

    /**
     * Returns whether this opening should use the one-phase all-to-all path.
     */
    public boolean useAllToAll(int partyNum, int fieldElementCount, double rttMillis,
                               double bandwidthMbps) {
        if (partyNum < 2 || fieldElementCount < 0) {
            throw new IllegalArgumentException("invalid public opening dimensions");
        }
        if (this == ALL_TO_ALL_ONE_PHASE) {
            return true;
        }
        if (this == BALANCED_TWO_PHASE || rttMillis <= 0.0 || bandwidthMbps <= 0.0) {
            return false;
        }
        double vectorBytes = (double) fieldElementCount * Long.BYTES;
        double savedTransmissionMillis = (partyNum - 1.0) * (1.0 - 2.0 / partyNum)
            * vectorBytes * Byte.SIZE / (bandwidthMbps * 1_000_000.0) * 1_000.0;
        return rttMillis / 2.0 > savedTransmissionMillis;
    }
}
