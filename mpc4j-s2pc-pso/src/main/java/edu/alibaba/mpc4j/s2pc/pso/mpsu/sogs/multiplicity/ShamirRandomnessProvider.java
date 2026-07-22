package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

/**
 * Provides fresh degree-t Shamir shares of random field vectors.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public interface ShamirRandomnessProvider {
    long[] randomDegreeT(ShamirRandomnessDomain domain);

    ShamirDoubleShare randomDoubleShare(ShamirRandomnessDomain domain);

    /**
     * Returns a fresh degree-2t sharing of zero whose nonconstant coefficients remain hidden.
     */
    long[] randomDegree2TZero(ShamirRandomnessDomain domain);

    long getRequestCount();

    long getGeneratedElementCount();

    long getSetupSendBytes();
}
