package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Sender interface for specialized queue-peel union-probe BA-UPOT.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public interface BaSsuIbltUnionProbeSender {
    /**
     * Initializes the backend.
     *
     * @param maxProbeNum       maximum public probe count.
     * @param elementByteLength element byte length.
     * @throws MpcAbortException if the protocol aborts.
     */
    void init(int maxProbeNum, int elementByteLength) throws MpcAbortException;

    /**
     * Builds one fixed-size sender capsule for a public bucket.
     *
     * @param bucketIndex public bucket index.
     * @param bucketInput local bucket input.
     * @return masked capsule.
     * @throws MpcAbortException if the protocol aborts.
     */
    BaSsuIbltUnionProbeCapsule probe(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput)
        throws MpcAbortException;
}
