package edu.alibaba.mpc4j.s2pc.upso.biupsu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Bi-output UPSU receiver interface.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public interface BiUpsuReceiver extends TwoPartyPto {
    /**
     * Receiver initializes the protocol.
     *
     * @param receiverElementSize receiver element size.
     * @param maxSenderElementSize max sender element size.
     * @param elementByteLength element byte length.
     * @throws MpcAbortException the protocol failure aborts.
     */
    void init(int receiverElementSize, int maxSenderElementSize, int elementByteLength) throws MpcAbortException;

    /**
     * Receiver executes the protocol and obtains its union output.
     *
     * @param receiverElementSet receiver element set.
     * @return receiver output.
     * @throws MpcAbortException the protocol failure aborts.
     */
    BiUpsuPartyOutput psu(Set<ByteBuffer> receiverElementSet) throws MpcAbortException;
}
