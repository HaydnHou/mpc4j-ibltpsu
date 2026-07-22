package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLabelEncoding;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

import java.util.List;

/**
 * Packed local cell batch for MP-SOGS uPeel.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class PackedMpSogsCellBatch {
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
     * Label bits indexed by party then bit index.
     */
    private final PackedBooleanShare[][] labelBits;
    /** Label representation. */
    private final MpSogsLabelEncoding labelEncoding;
    /** Public parameters required by exact label decoding. */
    private final MpSogsMpsuParams params;
    /** Public SOGS tier. */
    private final MpSogsTier tier;
    /** Public global cell index for each batch lane. */
    private final int[] cellIndexes;

    private PackedMpSogsCellBatch(int partyNum, int batchSize, PackedBooleanShare[] singleton,
                                  PackedBooleanShare[] heavy, PackedBooleanShare[][] labelBits,
                                  MpSogsLabelEncoding labelEncoding, MpSogsMpsuParams params,
                                  MpSogsTier tier, int[] cellIndexes) {
        this.partyNum = partyNum;
        this.batchSize = batchSize;
        this.singleton = singleton;
        this.heavy = heavy;
        this.labelBits = labelBits;
        this.labelEncoding = labelEncoding;
        this.params = params;
        this.tier = tier;
        this.cellIndexes = cellIndexes;
    }

    public static PackedMpSogsCellBatch fromShares(int batchSize, PackedBooleanShare[] singleton,
                                                   PackedBooleanShare[] heavy, PackedBooleanShare[][] valueBits) {
        int[] cellIndexes = new int[batchSize];
        for (int laneIndex = 0; laneIndex < batchSize; laneIndex++) {
            cellIndexes[laneIndex] = laneIndex;
        }
        return fromShares(batchSize, singleton, heavy, valueBits, MpSogsLabelEncoding.FULL_VALUE,
            null, MpSogsTier.MAIN, cellIndexes);
    }

    public static PackedMpSogsCellBatch fromShares(
        int batchSize, PackedBooleanShare[] singleton, PackedBooleanShare[] heavy,
        PackedBooleanShare[][] labelBits, MpSogsLabelEncoding labelEncoding, MpSogsMpsuParams params,
        MpSogsTier tier, int[] cellIndexes
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (singleton.length == 0 || singleton.length != heavy.length || singleton.length != labelBits.length) {
            throw new IllegalArgumentException("invalid party share dimensions");
        }
        int labelBitLength = labelEncoding == MpSogsLabelEncoding.FULL_VALUE
            ? MpSogsMpsuParams.ELEMENT_BIT_LENGTH
            : labelEncoding.bitLength(params, tier);
        for (PackedBooleanShare[] partyLabelBits : labelBits) {
            if (partyLabelBits.length != labelBitLength) {
                throw new IllegalArgumentException("invalid label bit dimension: " + partyLabelBits.length
                    + " != " + labelBitLength);
            }
        }
        if (cellIndexes.length != batchSize) {
            throw new IllegalArgumentException("invalid public cell-index dimension");
        }
        return new PackedMpSogsCellBatch(singleton.length, batchSize, singleton, heavy, labelBits,
            labelEncoding, params, tier, cellIndexes.clone());
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
        int labelBitLength = MpSogsMpsuParams.ELEMENT_BIT_LENGTH;
        PackedBooleanShare[][] valueBits = new PackedBooleanShare[partyNum][labelBitLength];
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            List<MpSogsLocalCellView> views = viewsByParty.get(partyIndex);
            if (views.size() != batchSize) {
                throw new IllegalArgumentException("all parties must use the same batch size");
            }
            long[] singletonBlocks = new long[blockNum];
            long[] heavyBlocks = new long[blockNum];
            long[][] valueBitBlocks = new long[labelBitLength][blockNum];
            for (int cellIndex = 0; cellIndex < batchSize; cellIndex++) {
                MpSogsLocalCellView view = views.get(cellIndex);
                if (view.isSingleton()) {
                    setLane(singletonBlocks, cellIndex);
                    long value = view.getSingletonValue();
                    for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
                        if (((value >>> (labelBitLength - 1 - bitIndex)) & 1L) != 0L) {
                            setLane(valueBitBlocks[bitIndex], cellIndex);
                        }
                    }
                } else if (view.isHeavy()) {
                    setLane(heavyBlocks, cellIndex);
                }
            }
            singleton[partyIndex] = backend.shareOwn(singletonBlocks);
            heavy[partyIndex] = backend.shareOwn(heavyBlocks);
            for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
                valueBits[partyIndex][bitIndex] = backend.shareOwn(valueBitBlocks[bitIndex]);
            }
        }
        int[] cellIndexes = new int[batchSize];
        for (int laneIndex = 0; laneIndex < batchSize; laneIndex++) {
            cellIndexes[laneIndex] = laneIndex;
        }
        return new PackedMpSogsCellBatch(partyNum, batchSize, singleton, heavy, valueBits,
            MpSogsLabelEncoding.FULL_VALUE, null, MpSogsTier.MAIN, cellIndexes);
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
        return labelBits;
    }

    public PackedBooleanShare[][] getLabelBits() {
        return labelBits;
    }

    public int getLabelBitLength() {
        return labelBits[0].length;
    }

    public long decodeLabel(long label, int batchLaneIndex) {
        return labelEncoding.decode(label, params, tier, cellIndexes[batchLaneIndex]);
    }

    private static void setLane(long[] blocks, int laneIndex) {
        blocks[laneIndex >>> 6] |= 1L << (laneIndex & (Long.SIZE - 1));
    }
}
