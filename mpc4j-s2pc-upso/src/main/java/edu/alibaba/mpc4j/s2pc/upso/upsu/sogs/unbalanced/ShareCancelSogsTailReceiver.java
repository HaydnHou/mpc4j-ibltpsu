package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

/**
 * Receiver for the share-cancel SOGS tail.
 *
 * <p>The receiver owns the other local share of the hidden row hit bits. It never receives per-row hit / miss bits.
 * Its only structured output is the aggregate SOGS sketch peel result for {@code X \ Y}.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsTailReceiver extends AbstractTwoPartyPto {
    /**
     * Underlying token-keyed SOGS tail receiver.
     */
    private final TokenKeyedSogsTailReceiver tokenTailReceiver;

    public ShareCancelSogsTailReceiver(Rpc receiverRpc, Party senderParty, ShareCancelSogsTailConfig config) {
        super(ShareCancelSogsTailPtoDesc.getInstance(), receiverRpc, senderParty, config);
        tokenTailReceiver = new TokenKeyedSogsTailReceiver(
            receiverRpc, senderParty, config.getTokenKeyedSogsTailConfig()
        );
        addSubPto(tokenTailReceiver);
    }

    /**
     * Initializes the tail.
     */
    public void init() throws MpcAbortException {
        logPhaseInfo(PtoState.INIT_BEGIN);
        tokenTailReceiver.init();
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Executes the receiver side.
     *
     * @param receiverHitShares receiver/server shares of hidden hit bits.
     * @param payloadByteLength payload byte length.
     * @param cellNum           SOGS cell number.
     * @param degree            SOGS degree.
     * @return peel result for {@code X \ Y}.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public TokenKeyedSogsSketch.PeelResult receive(
        boolean[] receiverHitShares, int payloadByteLength, int cellNum, int degree
    ) throws MpcAbortException {
        checkInitialized();
        return tokenTailReceiver.receive(receiverHitShares, payloadByteLength, cellNum, degree);
    }
}
