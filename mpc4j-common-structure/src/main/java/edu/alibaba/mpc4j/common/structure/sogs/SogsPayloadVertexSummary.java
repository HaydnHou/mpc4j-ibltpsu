package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

/**
 * Signed payload vertex summary.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
class SogsPayloadVertexSummary {
    /**
     * Signed degree.
     */
    private int degree;
    /**
     * Label XOR.
     */
    private long labelXor;
    /**
     * Check XOR.
     */
    private long checkXor;
    /**
     * Payload XOR.
     */
    private final byte[] payloadXor;

    SogsPayloadVertexSummary(int payloadByteLength) {
        payloadXor = new byte[payloadByteLength];
    }

    SogsPayloadVertexSummary(SogsPayloadVertexSummary that) {
        degree = that.degree;
        labelXor = that.labelXor;
        checkXor = that.checkXor;
        payloadXor = BytesUtils.clone(that.payloadXor);
    }

    void update(long label, long check, byte[] payload, int signDelta) {
        if (signDelta != 1 && signDelta != -1) {
            throw new IllegalArgumentException("signDelta must be +1 or -1: " + signDelta);
        }
        degree += signDelta;
        labelXor ^= label;
        checkXor ^= check;
        BytesUtils.xori(payloadXor, payload);
    }

    boolean isSingleton(long checkSeed) {
        return (degree == 1 || degree == -1) && checkXor == SogsHashUtils.check(labelXor, checkSeed);
    }

    boolean isEmpty() {
        return degree == 0 && labelXor == 0L && checkXor == 0L && isZero(payloadXor);
    }

    int getDegree() {
        return degree;
    }

    int getSign() {
        if (degree != 1 && degree != -1) {
            throw new IllegalStateException("vertex is not singleton");
        }
        return degree > 0 ? 1 : -1;
    }

    long getLabelXor() {
        return labelXor;
    }

    byte[] getPayloadXor() {
        return BytesUtils.clone(payloadXor);
    }

    private boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
