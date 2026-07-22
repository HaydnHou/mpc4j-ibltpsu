package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

/**
 * Public domain separator for one vector of Shamir-shared randomness.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public record ShamirRandomnessDomain(MpSogsTier tier, Phase phase, int roundId, long batchId,
                                     long operationId, int repetition, int vectorOffset, int vectorLength) {
    public enum Phase {
        PREDICATE,
        RECOVERY,
        RESIDUAL,
        DEGREE_REDUCTION,
        RESIDUAL_DEGREE_REDUCTION,
        TERMINAL_ZERO,
        RESIDUAL_TERMINAL_ZERO
    }

    public ShamirRandomnessDomain {
        if (tier == null || phase == null) {
            throw new IllegalArgumentException("randomness tier and phase must be set");
        }
        if (roundId < 0 || batchId < 0L || operationId < 0L || repetition < 0 || vectorOffset < 0
            || vectorLength < 0) {
            throw new IllegalArgumentException("randomness domain coordinates must be non-negative");
        }
    }
}
