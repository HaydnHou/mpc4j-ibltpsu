package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import java.util.Arrays;

/**
 * Sender output of an anonymous pad-equality carrier for MCRG-token SOGS release.
 *
 * <p>This object deliberately contains no hit/miss bit. {@code realRowBits} only marks sender-owned padded rows; the
 * miss condition is encoded only by equality between this side's {@code uPads} and the receiver side's {@code vPads}.
 * A valid carrier must satisfy {@code u_i = v_i} exactly for rows that should open to the receiver.</p>
 *
 * <p>Security precondition: row indices must be one-time anonymous carrier positions. They must not encode Y element
 * positions, buckets, bins, shards, candidate rows, graph cells, or stable cross-round locations. The release layer
 * leaks which anonymous rows open, so locator-linked rows would become process leakage.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class McrgTokenSogsPadCarrierSenderOutput {
    /**
     * Sender-side pads.
     */
    private final byte[][] uPads;
    /**
     * Sender real-row markers, not hit/miss markers.
     */
    private final boolean[] realRowBits;
    /**
     * Sender payloads.
     */
    private final byte[][] payloads;
    /**
     * Payload byte length.
     */
    private final int payloadByteLength;
    /**
     * Pad byte length.
     */
    private final int padByteLength;

    public McrgTokenSogsPadCarrierSenderOutput(byte[][] uPads, boolean[] realRowBits, byte[][] payloads) {
        checkInput(uPads, realRowBits, payloads);
        this.uPads = copy(uPads);
        this.realRowBits = Arrays.copyOf(realRowBits, realRowBits.length);
        this.payloads = copy(payloads);
        payloadByteLength = payloads[0].length;
        padByteLength = uPads[0].length;
    }

    /**
     * Gets row count.
     */
    public int getRowNum() {
        return uPads.length;
    }

    /**
     * Gets payload byte length.
     */
    public int getPayloadByteLength() {
        return payloadByteLength;
    }

    /**
     * Gets pad byte length.
     */
    public int getPadByteLength() {
        return padByteLength;
    }

    /**
     * Gets sender-side pads.
     */
    public byte[][] getUPads() {
        return copy(uPads);
    }

    /**
     * Gets sender real-row markers.
     */
    public boolean[] getRealRowBits() {
        return Arrays.copyOf(realRowBits, realRowBits.length);
    }

    /**
     * Gets sender payloads.
     */
    public byte[][] getPayloads() {
        return copy(payloads);
    }

    private static void checkInput(byte[][] uPads, boolean[] realRowBits, byte[][] payloads) {
        if (uPads.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        if (uPads.length != realRowBits.length || uPads.length != payloads.length) {
            throw new IllegalArgumentException("all row arrays must have the same length");
        }
        int payloadByteLength = payloads[0].length;
        if (payloadByteLength <= 0) {
            throw new IllegalArgumentException("payload length must be positive");
        }
        for (int i = 0; i < uPads.length; i++) {
            if (uPads[i] == null || uPads[i].length == 0) {
                throw new IllegalArgumentException("pad must be non-empty");
            }
            if (payloads[i] == null || payloads[i].length != payloadByteLength) {
                throw new IllegalArgumentException("payload length mismatch");
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
}
