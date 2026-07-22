package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

/**
 * Fixed-width batch codec for local multiplicity SOGS cells.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class MultiplicityCellBatch {
    private MultiplicityCellBatch() {
        // empty
    }

    public static long[] encode(MultiplicitySogsSketch sketch, BatchMpSogsPeelInput input) {
        long[] encoded = new long[Math.multiplyExact(input.size(), sketch.getWordNum())];
        for (int batchIndex = 0; batchIndex < input.size(); batchIndex++) {
            long[] words = sketch.getCellWords(input.getTier(), input.getCellIndexes().get(batchIndex));
            for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
                encoded[offset(wordIndex, batchIndex, input.size())] = words[wordIndex];
            }
        }
        return encoded;
    }

    public static long[] encode(MultiplicitySogsSketch sketch, MpSogsTier tier, int fromCell, int toCell) {
        if (fromCell < 0 || toCell < fromCell || toCell > sketch.getCellNum(tier)) {
            throw new IndexOutOfBoundsException("invalid multiplicity Cell range: [" + fromCell + ", " + toCell + ")");
        }
        int batchSize = toCell - fromCell;
        long[] encoded = new long[Math.multiplyExact(batchSize, sketch.getWordNum())];
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            long[] words = sketch.getCellWords(tier, fromCell + batchIndex);
            for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
                encoded[offset(wordIndex, batchIndex, batchSize)] = words[wordIndex];
            }
        }
        return encoded;
    }

    public static long[] word(long[] encoded, int wordIndex, int batchSize) {
        return word(encoded, wordIndex, batchSize, MultiplicitySogsCell.WORD_NUM);
    }

    public static long[] word(long[] encoded, int wordIndex, int batchSize, int wordNum) {
        if (encoded.length != Math.multiplyExact(batchSize, wordNum)) {
            throw new IllegalArgumentException("invalid encoded multiplicity batch length");
        }
        if (wordIndex < 0 || wordIndex >= wordNum) {
            throw new IndexOutOfBoundsException("invalid multiplicity word index: " + wordIndex);
        }
        long[] result = new long[batchSize];
        System.arraycopy(encoded, wordIndex * batchSize, result, 0, batchSize);
        return result;
    }

    public static int offset(int wordIndex, int batchIndex, int batchSize) {
        return wordIndex * batchSize + batchIndex;
    }
}
