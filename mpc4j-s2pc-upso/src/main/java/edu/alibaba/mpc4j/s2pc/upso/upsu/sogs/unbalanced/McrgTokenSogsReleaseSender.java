package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.ArrayList;
import java.util.List;

/**
 * Sender for ePSU / pnMCRG-shaped token SOGS conditional release.
 *
 * <p>Tokens are hidden inside fixed-length records masked by sender-side pnMCRG pads. A miss row opens only when the
 * receiver has the same pad. Hit rows decrypt to random junk and expose no token or SOGS cell metadata.</p>
 *
 * <p>The row order is part of the security contract. The caller must pass rows in a fresh anonymous carrier order, not
 * in any order linkable to Y elements, buckets, candidates, or graph cells.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class McrgTokenSogsReleaseSender extends AbstractTwoPartyPto {

    public McrgTokenSogsReleaseSender(Rpc senderRpc, Party receiverParty, McrgTokenSogsReleaseConfig config) {
        super(McrgTokenSogsReleasePtoDesc.getInstance(), senderRpc, receiverParty, config);
    }

    /**
     * Initializes the release profile.
     */
    public void init() {
        logPhaseInfo(PtoState.INIT_BEGIN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Sends fixed-length records masked by sender-side pnMCRG pads.
     *
     * @param carrierOutput sender output of the pad-equality carrier.
     */
    public void send(McrgTokenSogsPadCarrierSenderOutput carrierOutput) throws MpcAbortException {
        send(carrierOutput.getUPads(), carrierOutput.getRealRowBits(), carrierOutput.getPayloads());
    }

    /**
     * Sends fixed-length records masked by sender-side pnMCRG pads.
     *
     * @param uPads       sender-side pnMCRG pads.
     * @param realRowBits real-row bits; this is not a hit/miss selector.
     * @param payloads    row payloads.
     */
    public void send(byte[][] uPads, boolean[] realRowBits, byte[][] payloads) throws MpcAbortException {
        checkInitialized();
        checkInput(uPads, realRowBits, payloads);
        extraInfo++;
        int rowNum = uPads.length;
        int payloadByteLength = payloads[0].length;
        int atomByteLength = TokenKeyedSogsSketch.atomByteLength(payloadByteLength);
        TokenKeyedSogsSketch helper = new TokenKeyedSogsSketch(1, payloadByteLength, 1);
        List<byte[]> maskedRecords = new ArrayList<>(rowNum);
        for (int i = 0; i < rowNum; i++) {
            byte[] atomBytes;
            if (realRowBits[i]) {
                byte[] token = new byte[TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH];
                secureRandom.nextBytes(token);
                atomBytes = helper.createAtomBytes(token, payloads[i]);
            } else {
                atomBytes = new byte[atomByteLength];
                secureRandom.nextBytes(atomBytes);
            }
            byte[] pad = McrgTokenSogsReleaseUtils.expandPad(uPads[i], i, atomByteLength);
            maskedRecords.add(TokenKeyedSogsSketch.xor(atomBytes, pad));
        }
        sendOtherPartyEqualSizePayload(
            McrgTokenSogsReleasePtoDesc.PtoStep.SENDER_SEND_MASKED_RECORDS.ordinal(), maskedRecords
        );
    }

    private static void checkInput(byte[][] uPads, boolean[] realRowBits, byte[][] payloads) {
        if (uPads.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (uPads.length != realRowBits.length || uPads.length != payloads.length) {
            throw new IllegalArgumentException("all row arrays must have the same length");
        }
        int payloadByteLength = payloads[0].length;
        for (int i = 0; i < uPads.length; i++) {
            if (uPads[i] == null || uPads[i].length == 0) {
                throw new IllegalArgumentException("pad must be non-empty");
            }
            if (payloads[i] == null || payloads[i].length != payloadByteLength) {
                throw new IllegalArgumentException("payload length mismatch");
            }
        }
        if (payloadByteLength <= 0) {
            throw new IllegalArgumentException("payload length must be positive");
        }
    }
}
