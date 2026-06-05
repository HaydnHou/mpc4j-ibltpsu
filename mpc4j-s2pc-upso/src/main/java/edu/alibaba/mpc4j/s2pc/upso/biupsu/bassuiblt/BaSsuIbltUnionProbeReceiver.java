package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Receiver interface for specialized queue-peel union-probe BA-UPOT.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public interface BaSsuIbltUnionProbeReceiver {
    /**
     * Initializes the backend.
     *
     * @param maxProbeNum       maximum public probe count.
     * @param elementByteLength element byte length.
     * @throws MpcAbortException if the protocol aborts.
     */
    void init(int maxProbeNum, int elementByteLength) throws MpcAbortException;

    /**
     * Probes one public bucket and opens only bottom or a source-agnostic union singleton.
     *
     * @param bucketIndex   public bucket index.
     * @param bucketInput   local bucket input.
     * @param senderCapsule sender capsule.
     * @return source-agnostic probe output.
     * @throws MpcAbortException if the protocol aborts.
     */
    BaSsuIbltUnionProbeOutput probe(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput,
                                    BaSsuIbltUnionProbeCapsule senderCapsule) throws MpcAbortException;
}
