package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

/**
 * Sender for the share-cancel SOGS tail.
 *
 * <p>The sender owns payload atoms, private real-row bits, and one local share of the row hit bits. No local hit / miss
 * vector is opened. For each row, this wrapper converts the sender hit share into a release share:</p>
 *
 * <pre>
 * senderReleaseShare[i] = senderHitShare[i] XOR realRowBit[i].
 * </pre>
 *
 * <p>When XORed with the receiver hit share, the selected release bit is {@code realRowBit XOR hitBit}. Therefore real
 * hit rows cancel, real miss rows are opened into the aggregate SOGS sketch, and dummy rows contribute zero atoms.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsTailSender extends AbstractTwoPartyPto {
    /**
     * Underlying token-keyed SOGS tail sender.
     */
    private final TokenKeyedSogsTailSender tokenTailSender;

    public ShareCancelSogsTailSender(Rpc senderRpc, Party receiverParty, ShareCancelSogsTailConfig config) {
        super(ShareCancelSogsTailPtoDesc.getInstance(), senderRpc, receiverParty, config);
        tokenTailSender = new TokenKeyedSogsTailSender(
            senderRpc, receiverParty, config.getTokenKeyedSogsTailConfig()
        );
        addSubPto(tokenTailSender);
    }

    /**
     * Initializes the tail.
     */
    public void init() throws MpcAbortException {
        logPhaseInfo(PtoState.INIT_BEGIN);
        tokenTailSender.init();
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Executes the sender side.
     *
     * @param senderHitShares sender/client shares of hidden hit bits.
     * @param realRowBits     private real-row bits; false rows use zero atoms.
     * @param tokens          public one-time row tokens.
     * @param payloads        row payloads.
     * @param cellNum         SOGS cell number.
     * @param degree          SOGS degree.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public void send(
        boolean[] senderHitShares, boolean[] realRowBits, byte[][] tokens, byte[][] payloads, int cellNum, int degree
    ) throws MpcAbortException {
        checkInitialized();
        checkInput(senderHitShares, realRowBits, tokens, payloads);
        boolean[] senderReleaseShares = new boolean[senderHitShares.length];
        for (int i = 0; i < senderHitShares.length; i++) {
            senderReleaseShares[i] = senderHitShares[i] ^ realRowBits[i];
        }
        tokenTailSender.send(senderReleaseShares, realRowBits, tokens, payloads, cellNum, degree);
    }

    private static void checkInput(boolean[] senderHitShares, boolean[] realRowBits, byte[][] tokens, byte[][] payloads) {
        if (senderHitShares.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (senderHitShares.length != realRowBits.length || senderHitShares.length != tokens.length
            || senderHitShares.length != payloads.length) {
            throw new IllegalArgumentException("all row arrays must have the same length");
        }
    }
}
