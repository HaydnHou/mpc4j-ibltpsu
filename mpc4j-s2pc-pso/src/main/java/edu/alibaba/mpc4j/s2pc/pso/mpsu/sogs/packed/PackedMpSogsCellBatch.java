package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;

import java.util.List;

/**
 * Packed local cell batch for MP-SOGS uPeel.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class PackedMpSogsCellBatch {
    /**
     * Element bit length.
     */
    private static final int ELEMENT_BITS = MpSogsMpsuParams.ELEMENT_BIT_LENGTH;

    /**
     * Party number.
     */
    private final int partyNum;
    /**
     * Batch size.
     */
    private final int batchSize;
    /**
     * Singleton flags.
     */
    private final PackedBooleanShare[] singleton;
    /**
     * Heavy flags.
     */
    private final PackedBooleanShare[] heavy;
    /**
     * Value bits indexed by party then bit index.
     */
    private final PackedBooleanShare[][] valueBits;

    private PackedMpSogsCellBatch(int partyNum, int batchSize, PackedBooleanShare[] singleton,
                                  PackedBooleanShare[] heavy, PackedBooleanShare[][] valueBits) {
        this.partyNum = partyNum;
        this.batchSize = batchSize;
        this.singleton = singleton;
        this.heavy = heavy;
        this.valueBits = valueBits;
    }

    public static PackedMpSogsCellBatch fromShares(int batchSize, PackedBooleanShare[] singleton,
                                                   PackedBooleanShare[] heavy, PackedBooleanShare[][] valueBits) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (singleton.length == 0 || singleton.length != heavy.length || singleton.length != valueBits.length) {
            throw new IllegalArgumentException("invalid party share dimensions");
        }
        for (PackedBooleanShare[] partyValueBits : valueBits) {
            if (partyValueBits.length != ELEMENT_BITS) {
                throw new IllegalArgumentException("invalid value bit dimension");
            }
        }
        return new PackedMpSogsCellBatch(singleton.length, batchSize, singleton, heavy, valueBits);
    }

    public static PackedMpSogsCellBatch fromClearViews(PackedBooleanBackend backend,
                                                       List<List<MpSogsLocalCellView>> viewsByParty) {
        if (viewsByParty.isEmpty()) {
            throw new IllegalArgumentException("viewsByParty must not be empty");
        }
        int partyNum = viewsByParty.size();
        int batchSize = viewsByParty.get(0).size();
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        int blockNum = backend.blockNum();
        PackedBooleanShare[] singleton = new PackedBooleanShare[partyNum];
        PackedBooleanShare[] heavy = new PackedBooleanShare[partyNum];
        PackedBooleanShare[][] valueBits = new PackedBooleanShare[partyNum][ELEMENT_BITS];
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            List<MpSogsLocalCellView> views = viewsByParty.get(partyIndex);
            if (views.size() != batchSize) {
                throw new IllegalArgumentException("all parties must use the same batch size");
            }
            long[] singletonBlocks = new long[blockNum];
            long[] heavyBlocks = new long[blockNum];
            long[][] valueBitBlocks = new long[ELEMENT_BITS][blockNum];
            for (int cellIndex = 0; cellIndex < batchSize; cellIndex++) {
                MpSogsLocalCellView view = views.get(cellIndex);
                if (view.isSingleton()) {
                    setLane(singletonBlocks, cellIndex);
                    long value = view.getSingletonValue();
                    for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                        if (((value >>> (ELEMENT_BITS - 1 - bitIndex)) & 1L) != 0L) {
                            setLane(valueBitBlocks[bitIndex], cellIndex);
                        }
                    }
                } else if (view.isHeavy()) {
                    setLane(heavyBlocks, cellIndex);
                }
            }
            singleton[partyIndex] = backend.shareOwn(singletonBlocks);
            heavy[partyIndex] = backend.shareOwn(heavyBlocks);
            for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                valueBits[partyIndex][bitIndex] = backend.shareOwn(valueBitBlocks[bitIndex]);
            }
        }
        return new PackedMpSogsCellBatch(partyNum, batchSize, singleton, heavy, valueBits);
    }

    public int getPartyNum() {
        return partyNum;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public PackedBooleanShare[] getSingleton() {
        return singleton;
    }

    public PackedBooleanShare[] getHeavy() {
        return heavy;
    }

    public PackedBooleanShare[][] getValueBits() {
        return valueBits;
    }

    private static void setLane(long[] blocks, int laneIndex) {
        blocks[laneIndex >>> 6] |= 1L << (laneIndex & (Long.SIZE - 1));
    }
}
