package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import java.util.Arrays;

/**
 * Receiver output of an anonymous pad-equality carrier for MCRG-token SOGS release.
 *
 * <p>The receiver receives only pads. It must not receive hit/miss bits, public tokens, bucket counts, or any row
 * metadata that links a pad row to an element or a subset of the large set.</p>
 *
 * <p>Security precondition: the order of {@code vPads} must be the same one-time anonymous carrier order used by the
 * sender output. It must not be the receiver's native Y order, bucket order, bin order, candidate order, graph-cell
 * order, or any order reused across protocol executions.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class McrgTokenSogsPadCarrierReceiverOutput {
    /**
     * Receiver-side pads.
     */
    private final byte[][] vPads;
    /**
     * Pad byte length.
     */
    private final int padByteLength;

    public McrgTokenSogsPadCarrierReceiverOutput(byte[][] vPads) {
        checkInput(vPads);
        this.vPads = copy(vPads);
        padByteLength = vPads[0].length;
    }

    /**
     * Gets row count.
     */
    public int getRowNum() {
        return vPads.length;
    }

    /**
     * Gets pad byte length.
     */
    public int getPadByteLength() {
        return padByteLength;
    }

    /**
     * Gets receiver-side pads.
     */
    public byte[][] getVPads() {
        return copy(vPads);
    }

    private static void checkInput(byte[][] vPads) {
        if (vPads.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        for (byte[] vPad : vPads) {
            if (vPad == null || vPad.length == 0) {
                throw new IllegalArgumentException("pad must be non-empty");
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
