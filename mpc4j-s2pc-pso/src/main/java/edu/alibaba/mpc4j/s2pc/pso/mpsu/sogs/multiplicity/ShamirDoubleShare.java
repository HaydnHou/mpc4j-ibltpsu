package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

/**
 * Correlated Shamir shares of one random vector at degrees t and 2t.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public record ShamirDoubleShare(long[] degreeT, long[] degree2T) {
    public ShamirDoubleShare {
        if (degreeT == null || degree2T == null || degreeT.length != degree2T.length) {
            throw new IllegalArgumentException("correlated Shamir share vectors must have the same length");
        }
    }
}
