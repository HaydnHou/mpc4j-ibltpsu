package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sender for the token-keyed aggregate SOGS tail.
 *
 * <p>The sender owns payload atoms and a share of each row release bit. It sends only OT-masked row corrections and an
 * aggregate cell share.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsTailSender extends AbstractTwoPartyPto {
    /**
     * Core COT sender.
     */
    private final CoreCotSender coreCotSender;

    public TokenKeyedSogsTailSender(Rpc senderRpc, Party receiverParty, TokenKeyedSogsTailConfig config) {
        super(TokenKeyedSogsTailPtoDesc.getInstance(), senderRpc, receiverParty, config);
        coreCotSender = CoreCotFactory.createSender(senderRpc, receiverParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    /**
     * Initializes the tail.
     */
    public void init() throws MpcAbortException {
        logPhaseInfo(PtoState.INIT_BEGIN);
        byte[] delta = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        secureRandom.nextBytes(delta);
        coreCotSender.init(delta);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Executes the sender side.
     *
     * @param clientSelectorShares sender/client shares of release bits.
     * @param validBits            real-row bits; invalid rows use a zero atom.
     * @param tokens               public row tokens.
     * @param payloads             row payloads.
     * @param cellNum              SOGS cell number.
     * @param degree               SOGS degree.
     */
    public void send(
        boolean[] clientSelectorShares, boolean[] validBits, byte[][] tokens, byte[][] payloads, int cellNum, int degree
    ) throws MpcAbortException {
        checkInitialized();
        checkInput(clientSelectorShares, validBits, tokens, payloads, cellNum, degree);
        extraInfo++;
        int rowNum = clientSelectorShares.length;
        int payloadByteLength = payloads[0].length;
        int atomByteLength = TokenKeyedSogsSketch.atomByteLength(payloadByteLength);
        TokenKeyedSogsSketch helper = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);

        List<byte[]> tokenPayload = new ArrayList<>(rowNum);
        for (byte[] token : tokens) {
            tokenPayload.add(Arrays.copyOf(token, token.length));
        }
        sendOtherPartyEqualSizePayload(
            TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal(), tokenPayload
        );

        CotSenderOutput cotSenderOutput = coreCotSender.send(rowNum);
        TokenKeyedSogsSketch clientSketch = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        List<byte[]> otMaskPayload = new ArrayList<>(rowNum * 2);
        for (int i = 0; i < rowNum; i++) {
            byte[] atom = validBits[i]
                ? helper.createAtomBytes(tokens[i], payloads[i])
                : TokenKeyedSogsSketch.zeroAtomBytes(payloadByteLength);
            byte[] pad = TokenKeyedSogsSketch.randomAtomBytes(payloadByteLength, secureRandom);
            byte[] message0 = Arrays.copyOf(pad, atomByteLength);
            byte[] message1 = TokenKeyedSogsSketch.xor(pad, atom);
            byte[] key0 = TokenKeyedSogsSketch.expandOtPad(cotSenderOutput.getR0(i), i, atomByteLength);
            byte[] key1 = TokenKeyedSogsSketch.expandOtPad(cotSenderOutput.getR1(i), i, atomByteLength);
            TokenKeyedSogsSketch.xori(message0, key0);
            TokenKeyedSogsSketch.xori(message1, key1);
            otMaskPayload.add(message0);
            otMaskPayload.add(message1);

            byte[] clientPiece = Arrays.copyOf(pad, atomByteLength);
            if (clientSelectorShares[i]) {
                TokenKeyedSogsSketch.xori(clientPiece, atom);
            }
            clientSketch.xorAtomBytesToPositions(tokens[i], clientPiece);
        }
        sendOtherPartyEqualSizePayload(
            TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_OT_MASKS.ordinal(), otMaskPayload
        );
        sendOtherPartyEqualSizePayload(
            TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_CELL_SHARE.ordinal(), clientSketch.toCellPayload()
        );
    }

    private static void checkInput(
        boolean[] clientSelectorShares, boolean[] validBits, byte[][] tokens, byte[][] payloads, int cellNum, int degree
    ) {
        if (clientSelectorShares.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (clientSelectorShares.length != validBits.length || clientSelectorShares.length != tokens.length
            || clientSelectorShares.length != payloads.length) {
            throw new IllegalArgumentException("all row arrays must have the same length");
        }
        if (cellNum <= 0 || degree <= 0 || degree > cellNum) {
            throw new IllegalArgumentException("invalid graph parameters");
        }
        int payloadByteLength = payloads[0].length;
        for (int i = 0; i < clientSelectorShares.length; i++) {
            if (tokens[i].length != TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH) {
                throw new IllegalArgumentException("invalid token length");
            }
            if (payloads[i].length != payloadByteLength) {
                throw new IllegalArgumentException("payload length mismatch");
            }
        }
    }
}
