package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.List;

/**
 * Receiver for ePSU / pnMCRG-shaped token SOGS conditional release.
 *
 * <p>The receiver tries to open each fixed-length record with its pnMCRG pad. Valid openings are exactly miss records
 * and are inserted into a Token-SOGS sketch. Failed records are ignored uniformly.</p>
 *
 * <p>The receiver will observe which anonymous carrier rows open. This is safe only when row indices are not linkable to
 * Y elements, buckets, candidates, graph cells, or stable cross-round locations.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class McrgTokenSogsReleaseReceiver extends AbstractTwoPartyPto {

    public McrgTokenSogsReleaseReceiver(Rpc receiverRpc, Party senderParty, McrgTokenSogsReleaseConfig config) {
        super(McrgTokenSogsReleasePtoDesc.getInstance(), receiverRpc, senderParty, config);
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
     * Receives masked records and peels the miss-only SOGS sketch.
     *
     * @param carrierOutput    receiver output of the pad-equality carrier.
     * @param payloadByteLength payload byte length.
     * @param cellNum          SOGS cell number.
     * @param degree           SOGS degree.
     * @return peel result for X \ Y.
     */
    public TokenKeyedSogsSketch.PeelResult receive(
        McrgTokenSogsPadCarrierReceiverOutput carrierOutput, int payloadByteLength, int cellNum, int degree
    ) throws MpcAbortException {
        return receive(carrierOutput.getVPads(), payloadByteLength, cellNum, degree);
    }

    /**
     * Receives masked records and peels the miss-only SOGS sketch.
     *
     * @param vPads             receiver-side pnMCRG pads.
     * @param payloadByteLength payload byte length.
     * @param cellNum           SOGS cell number.
     * @param degree            SOGS degree.
     * @return peel result for X \ Y.
     */
    public TokenKeyedSogsSketch.PeelResult receive(
        byte[][] vPads, int payloadByteLength, int cellNum, int degree
    ) throws MpcAbortException {
        checkInitialized();
        checkInput(vPads, payloadByteLength, cellNum, degree);
        extraInfo++;
        int rowNum = vPads.length;
        int atomByteLength = TokenKeyedSogsSketch.atomByteLength(payloadByteLength);
        List<byte[]> maskedRecords = receiveOtherPartyEqualSizePayload(
            McrgTokenSogsReleasePtoDesc.PtoStep.SENDER_SEND_MASKED_RECORDS.ordinal(), rowNum, atomByteLength
        );
        TokenKeyedSogsSketch sketch = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        for (int i = 0; i < rowNum; i++) {
            byte[] pad = McrgTokenSogsReleaseUtils.expandPad(vPads[i], i, atomByteLength);
            byte[] atomBytes = TokenKeyedSogsSketch.xor(maskedRecords.get(i), pad);
            McrgTokenSogsReleaseUtils.DecodedAtom decodedAtom =
                McrgTokenSogsReleaseUtils.decodeValidAtom(atomBytes, payloadByteLength);
            if (decodedAtom != null) {
                sketch.add(decodedAtom.token, decodedAtom.payload);
            }
        }
        return sketch.peel();
    }

    private static void checkInput(byte[][] vPads, int payloadByteLength, int cellNum, int degree) {
        if (vPads.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (payloadByteLength <= 0 || cellNum <= 0 || degree <= 0 || degree > cellNum) {
            throw new IllegalArgumentException("invalid parameters");
        }
        for (byte[] vPad : vPads) {
            if (vPad == null || vPad.length == 0) {
                throw new IllegalArgumentException("pad must be non-empty");
            }
        }
    }
}
