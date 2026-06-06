package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * One fixed-length masked row in a specialized UP-BA-UPOT bucket probe.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltUpBaUpotProbeRow {
    /**
     * row bytes.
     */
    private final byte[] rowBytes;

    private BaSsuIbltUpBaUpotProbeRow(byte[] rowBytes) {
        if (rowBytes == null || rowBytes.length == 0) {
            throw new IllegalArgumentException("rowBytes must be non-empty");
        }
        this.rowBytes = Arrays.copyOf(rowBytes, rowBytes.length);
    }

    static BaSsuIbltUpBaUpotProbeRow of(byte[] rowBytes) {
        return new BaSsuIbltUpBaUpotProbeRow(rowBytes);
    }

    int byteLength() {
        return rowBytes.length;
    }

    byte[] getBytes() {
        return Arrays.copyOf(rowBytes, rowBytes.length);
    }
}
