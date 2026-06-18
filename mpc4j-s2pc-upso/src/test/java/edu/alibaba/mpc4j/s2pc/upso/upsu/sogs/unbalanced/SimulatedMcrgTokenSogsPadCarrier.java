package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Test-only simulator for the MCRG-token SOGS pad-equality carrier.
 *
 * <p>This is not a protocol. It only materializes the carrier contract for deterministic tests:
 * miss rows have {@code u_i = v_i}, while hit and dummy rows have {@code u_i != v_i}.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
class SimulatedMcrgTokenSogsPadCarrier {
    /**
     * Sender output.
     */
    private final McrgTokenSogsPadCarrierSenderOutput senderOutput;
    /**
     * Receiver output.
     */
    private final McrgTokenSogsPadCarrierReceiverOutput receiverOutput;
    /**
     * Expected opened miss payloads.
     */
    private final Set<ByteBuffer> expectedMisses;

    private SimulatedMcrgTokenSogsPadCarrier(
        McrgTokenSogsPadCarrierSenderOutput senderOutput,
        McrgTokenSogsPadCarrierReceiverOutput receiverOutput,
        Set<ByteBuffer> expectedMisses
    ) {
        this.senderOutput = senderOutput;
        this.receiverOutput = receiverOutput;
        this.expectedMisses = expectedMisses;
    }

    static SimulatedMcrgTokenSogsPadCarrier create(
        byte[][] uPads, byte[][] vPads, boolean[] realRowBits, boolean[] openRowBits, byte[][] payloads
    ) {
        checkInput(uPads, vPads, realRowBits, openRowBits, payloads);
        byte[][] adjustedVPads = copy(vPads);
        Set<ByteBuffer> expectedMisses = new HashSet<>();
        for (int row = 0; row < uPads.length; row++) {
            if (openRowBits[row]) {
                adjustedVPads[row] = Arrays.copyOf(uPads[row], uPads[row].length);
                expectedMisses.add(ByteBuffer.wrap(Arrays.copyOf(payloads[row], payloads[row].length)));
            } else {
                int attempt = 0;
                while (Arrays.equals(uPads[row], adjustedVPads[row])) {
                    randomize(adjustedVPads[row], row, attempt);
                    attempt++;
                }
            }
        }
        return new SimulatedMcrgTokenSogsPadCarrier(
            new McrgTokenSogsPadCarrierSenderOutput(uPads, realRowBits, payloads),
            new McrgTokenSogsPadCarrierReceiverOutput(adjustedVPads),
            expectedMisses
        );
    }

    McrgTokenSogsPadCarrierSenderOutput getSenderOutput() {
        return senderOutput;
    }

    McrgTokenSogsPadCarrierReceiverOutput getReceiverOutput() {
        return receiverOutput;
    }

    Set<ByteBuffer> getExpectedMisses() {
        return new HashSet<>(expectedMisses);
    }

    private static void checkInput(
        byte[][] uPads, byte[][] vPads, boolean[] realRowBits, boolean[] openRowBits, byte[][] payloads
    ) {
        if (uPads.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (uPads.length != vPads.length || uPads.length != realRowBits.length
            || uPads.length != openRowBits.length || uPads.length != payloads.length) {
            throw new IllegalArgumentException("all row arrays must have the same length");
        }
        int payloadByteLength = payloads[0].length;
        if (payloadByteLength <= 0) {
            throw new IllegalArgumentException("payload length must be positive");
        }
        for (int row = 0; row < uPads.length; row++) {
            if (uPads[row] == null || vPads[row] == null || uPads[row].length == 0
                || uPads[row].length != vPads[row].length) {
                throw new IllegalArgumentException("pad length mismatch");
            }
            if (payloads[row] == null || payloads[row].length != payloadByteLength) {
                throw new IllegalArgumentException("payload length mismatch");
            }
            if (openRowBits[row] && !realRowBits[row]) {
                throw new IllegalArgumentException("dummy rows cannot open");
            }
        }
    }

    private static byte[][] copy(byte[][] input) {
        byte[][] output = new byte[input.length][];
        for (int i = 0; i < input.length; i++) {
            output[i] = Arrays.copyOf(input[i], input[i].length);
        }
        return output;
    }

    private static void randomize(byte[] bytes, int row, int attempt) {
        Random random = new Random(2026061705L + row * 65537L + attempt);
        random.nextBytes(bytes);
    }
}
