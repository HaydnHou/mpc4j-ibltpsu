package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import java.util.Arrays;

/**
 * Clear packed Boolean backend used as a reference for the packed uPeel circuit.
 *
 * <p>This backend is not secure. It exists to validate packed layout and circuit logic before replacing the direct
 * operations with replicated sharing.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class ClearPackedBooleanBackend implements PackedBooleanBackend {
    /**
     * Batch size.
     */
    private final int batchSize;
    /**
     * Block count.
     */
    private final int blockNum;
    /**
     * Last block mask.
     */
    private final long lastBlockMask;

    public ClearPackedBooleanBackend(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.batchSize = batchSize;
        blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
        int lastBits = batchSize & (Long.SIZE - 1);
        lastBlockMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
    }

    @Override
    public int blockNum() {
        return blockNum;
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public PackedBooleanShare shareOwn(long[] bits) {
        checkBlockNum(bits);
        return new PackedBooleanShare(maskValidBits(bits));
    }

    @Override
    public PackedBooleanShare zero() {
        return new PackedBooleanShare(new long[blockNum]);
    }

    @Override
    public PackedBooleanShare one() {
        long[] blocks = new long[blockNum];
        Arrays.fill(blocks, -1L);
        blocks[blockNum - 1] &= lastBlockMask;
        return new PackedBooleanShare(blocks);
    }

    @Override
    public PackedBooleanShare xor(PackedBooleanShare x, PackedBooleanShare y) {
        long[] left = x.blocks();
        long[] right = y.blocks();
        checkBlockNum(left);
        checkBlockNum(right);
        long[] result = new long[blockNum];
        for (int i = 0; i < blockNum; i++) {
            result[i] = left[i] ^ right[i];
        }
        return new PackedBooleanShare(maskValidBits(result));
    }

    @Override
    public PackedBooleanShare not(PackedBooleanShare x) {
        return xor(x, one());
    }

    @Override
    public PackedBooleanShare and(PackedBooleanShare x, PackedBooleanShare y) {
        long[] left = x.blocks();
        long[] right = y.blocks();
        checkBlockNum(left);
        checkBlockNum(right);
        long[] result = new long[blockNum];
        for (int i = 0; i < blockNum; i++) {
            result[i] = left[i] & right[i];
        }
        return new PackedBooleanShare(maskValidBits(result));
    }

    @Override
    public long[] open(PackedBooleanShare x) {
        long[] blocks = x.getBlocks();
        checkBlockNum(blocks);
        return maskValidBits(blocks);
    }

    @Override
    public long[] openSelected(PackedBooleanShare x, int[] selectedIndexes) {
        long[] blocks = x.getBlocks();
        checkBlockNum(blocks);
        return select(blocks, selectedIndexes);
    }

    @Override
    public PackedBooleanShare compact(PackedBooleanShare x, int[] selectedIndexes) {
        long[] blocks = x.getBlocks();
        checkBlockNum(blocks);
        return new PackedBooleanShare(select(blocks, selectedIndexes));
    }

    @Override
    public PackedBooleanBackend derive(int compactBatchSize) {
        return new ClearPackedBooleanBackend(compactBatchSize);
    }

    private long[] select(long[] blocks, int[] selectedIndexes) {
        int compactBlockNum = (selectedIndexes.length + Long.SIZE - 1) / Long.SIZE;
        long[] selected = new long[compactBlockNum];
        for (int selectedIndex = 0; selectedIndex < selectedIndexes.length; selectedIndex++) {
            int laneIndex = selectedIndexes[selectedIndex];
            if (laneIndex < 0 || laneIndex >= batchSize) {
                throw new IllegalArgumentException("selected lane out of range: " + laneIndex);
            }
            if (((blocks[laneIndex >>> 6] >>> (laneIndex & (Long.SIZE - 1))) & 1L) != 0L) {
                selected[selectedIndex >>> 6] |= 1L << (selectedIndex & (Long.SIZE - 1));
            }
        }
        return selected;
    }

    private long[] maskValidBits(long[] blocks) {
        long[] copy = Arrays.copyOf(blocks, blocks.length);
        copy[blockNum - 1] &= lastBlockMask;
        return copy;
    }

    private void checkBlockNum(long[] blocks) {
        if (blocks.length != blockNum) {
            throw new IllegalArgumentException("invalid block length: " + blocks.length + ", expected " + blockNum);
        }
    }
}
