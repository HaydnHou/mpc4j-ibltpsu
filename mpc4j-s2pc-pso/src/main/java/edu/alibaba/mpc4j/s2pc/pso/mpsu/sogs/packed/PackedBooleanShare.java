package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import java.util.Arrays;

/**
 * Packed Boolean share/reference value for MP-SOGS secure-uPeel.
 *
 * <p>Each long block stores 64 public cell lanes. Secure backends can subclass or wrap this shape later; the clear
 * backend uses the blocks directly.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class PackedBooleanShare {
    /**
     * Packed blocks.
     */
    private final long[] blocks;

    public PackedBooleanShare(long[] blocks) {
        this.blocks = Arrays.copyOf(blocks, blocks.length);
    }

    long[] blocks() {
        return blocks;
    }

    public long[] getBlocks() {
        return Arrays.copyOf(blocks, blocks.length);
    }
}
