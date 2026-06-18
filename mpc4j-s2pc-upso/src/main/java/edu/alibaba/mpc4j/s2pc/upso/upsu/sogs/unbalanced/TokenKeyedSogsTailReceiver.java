package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;

import java.util.List;

/**
 * Receiver for the token-keyed aggregate SOGS tail.
 *
 * <p>The receiver owns the other release-bit shares. It learns only the final aggregate sketch and peels the allowed
 * output X \ Y.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsTailReceiver extends AbstractTwoPartyPto {
    /**
     * Core COT receiver.
     */
    private final CoreCotReceiver coreCotReceiver;

    public TokenKeyedSogsTailReceiver(Rpc receiverRpc, Party senderParty, TokenKeyedSogsTailConfig config) {
        super(TokenKeyedSogsTailPtoDesc.getInstance(), receiverRpc, senderParty, config);
        coreCotReceiver = CoreCotFactory.createReceiver(receiverRpc, senderParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
    }

    /**
     * Initializes the tail.
     */
    public void init() throws MpcAbortException {
        logPhaseInfo(PtoState.INIT_BEGIN);
        coreCotReceiver.init();
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Executes the receiver side.
     *
     * @param serverSelectorShares receiver/server shares of release bits.
     * @param payloadByteLength    payload byte length.
     * @param cellNum              SOGS cell number.
     * @param degree               SOGS degree.
     * @return peel result for X \ Y.
     */
    public TokenKeyedSogsSketch.PeelResult receive(
        boolean[] serverSelectorShares, int payloadByteLength, int cellNum, int degree
    ) throws MpcAbortException {
        checkInitialized();
        if (serverSelectorShares.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (payloadByteLength <= 0 || cellNum <= 0 || degree <= 0 || degree > cellNum) {
            throw new IllegalArgumentException("invalid parameters");
        }
        extraInfo++;
        int rowNum = serverSelectorShares.length;
        int atomByteLength = TokenKeyedSogsSketch.atomByteLength(payloadByteLength);
        List<byte[]> tokenPayload = receiveOtherPartyEqualSizePayload(
            TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal(),
            rowNum,
            TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH
        );

        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(serverSelectorShares);
        List<byte[]> otMaskPayload = receiveOtherPartyEqualSizePayload(
            TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_OT_MASKS.ordinal(), rowNum * 2, atomByteLength
        );
        TokenKeyedSogsSketch serverSketch = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        for (int i = 0; i < rowNum; i++) {
            byte[] selectedCiphertext = otMaskPayload.get(2 * i + (serverSelectorShares[i] ? 1 : 0));
            byte[] key = TokenKeyedSogsSketch.expandOtPad(cotReceiverOutput.getRb(i), i, atomByteLength);
            byte[] serverPiece = TokenKeyedSogsSketch.xor(selectedCiphertext, key);
            serverSketch.xorAtomBytesToPositions(tokenPayload.get(i), serverPiece);
        }

        List<byte[]> clientCells = receiveOtherPartyEqualSizePayload(
            TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_CELL_SHARE.ordinal(), cellNum, atomByteLength
        );
        serverSketch.xorCellPayload(clientCells);
        return serverSketch.peel();
    }
}
