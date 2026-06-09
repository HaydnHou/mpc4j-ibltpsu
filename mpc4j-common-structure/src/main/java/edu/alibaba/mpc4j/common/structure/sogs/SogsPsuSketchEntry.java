package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

/**
 * Signed PSU sketch peel entry.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchEntry {
    /**
     * Label.
     */
    private final long label;
    /**
     * Payload.
     */
    private final byte[] payload;
    /**
     * Sign.
     */
    private final int sign;

    /**
     * Creates an entry.
     *
     * @param label   label.
     * @param payload payload.
     * @param sign    sign, +1 or -1.
     */
    public SogsPsuSketchEntry(long label, byte[] payload, int sign) {
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        if (sign != 1 && sign != -1) {
            throw new IllegalArgumentException("sign must be +1 or -1: " + sign);
        }
        this.label = label;
        this.payload = BytesUtils.clone(payload);
        this.sign = sign;
    }

    /**
     * Gets label.
     *
     * @return label.
     */
    public long label() {
        return label;
    }

    /**
     * Gets payload.
     *
     * @return payload.
     */
    public byte[] payload() {
        return BytesUtils.clone(payload);
    }

    /**
     * Gets sign.
     *
     * @return sign.
     */
    public int sign() {
        return sign;
    }
}
