package edu.alibaba.mpc4j.s2pc.upso.biupsu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Bi-output UPSU sender interface.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public interface BiUpsuSender extends TwoPartyPto {
    /**
     * Sender initializes the protocol.
     *
     * @param maxSenderElementSize max sender element size.
     * @param receiverElementSize receiver element size.
     * @param elementByteLength element byte length.
     * @throws MpcAbortException the protocol failure aborts.
     */
    void init(int maxSenderElementSize, int receiverElementSize, int elementByteLength) throws MpcAbortException;

    /**
     * Sender executes the protocol and obtains its union output.
     *
     * @param senderElementSet sender element set.
     * @return sender output.
     * @throws MpcAbortException the protocol failure aborts.
     */
    BiUpsuPartyOutput psu(Set<ByteBuffer> senderElementSet) throws MpcAbortException;
}
