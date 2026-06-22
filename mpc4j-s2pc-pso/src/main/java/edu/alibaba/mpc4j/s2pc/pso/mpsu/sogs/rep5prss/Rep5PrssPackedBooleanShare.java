package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare;

import java.util.Arrays;

/**
 * 5-party PRSS packed replicated Boolean share.
 *
 * <p>The secret is represented as {@code x = r0 xor r1 xor r2 xor r3 xor r4}. Party {@code Pi} holds every component
 * except {@code ri}. Missing local components are stored as {@code null}.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep5PrssPackedBooleanShare extends PackedBooleanShare {
    /**
     * Party number.
     */
    static final int PARTY_NUM = 5;
    /**
     * Component arrays indexed by component id.
     */
    private final long[][] components;
    /**
     * Block count.
     */
    private final int blockNum;

    public Rep5PrssPackedBooleanShare(long[][] components, int blockNum) {
        super(new long[blockNum]);
        this.blockNum = blockNum;
        if (components.length != PARTY_NUM) {
            throw new IllegalArgumentException("REP5 PRSS share must have 5 components");
        }
        this.components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (components[componentIndex] != null && components[componentIndex].length != blockNum) {
                throw new IllegalArgumentException("invalid REP5 PRSS component block length");
            }
            this.components[componentIndex] = components[componentIndex] == null
                ? null
                : Arrays.copyOf(components[componentIndex], blockNum);
        }
    }

    long[] component(int componentIndex) {
        return components[componentIndex];
    }

    long[][] copyComponents() {
        long[][] copy = new long[PARTY_NUM][];
        for (int i = 0; i < PARTY_NUM; i++) {
            copy[i] = components[i] == null ? null : Arrays.copyOf(components[i], components[i].length);
        }
        return copy;
    }

    int blockNum() {
        return blockNum;
    }
}
