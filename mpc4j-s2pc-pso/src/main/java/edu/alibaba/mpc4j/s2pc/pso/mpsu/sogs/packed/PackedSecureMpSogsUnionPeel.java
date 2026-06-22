package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Packed candidate-OR implementation of MP-SOGS uPeel.
 *
 * <p>The class is backend-agnostic. With {@link ClearPackedBooleanBackend} it is a reference circuit; with a future
 * replicated backend it becomes the optimized secure-uPeel core.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class PackedSecureMpSogsUnionPeel {
    /**
     * Element bit length.
     */
    private static final int ELEMENT_BITS = MpSogsMpsuParams.ELEMENT_BIT_LENGTH;

    /**
     * Packed backend.
     */
    private final PackedBooleanBackend backend;

    public PackedSecureMpSogsUnionPeel(PackedBooleanBackend backend) {
        this.backend = backend;
    }

    /**
     * Evaluates a packed uPeel batch.
     *
     * @param batch packed batch.
     * @return public peel results.
     */
    public List<MpSogsPeelResult> peel(PackedMpSogsCellBatch batch) {
        PackedBooleanShare[] singleton = batch.getSingleton();
        PackedBooleanShare[] heavy = batch.getHeavy();
        PackedBooleanShare[][] valueBits = batch.getValueBits();
        PackedBooleanShare[] candidateBits = candidateBits(singleton, valueBits);
        PackedBooleanShare mismatch = mismatch(singleton, valueBits, candidateBits);
        PackedBooleanShare opened = backend.and(
            backend.and(orMany(singleton), backend.not(orMany(heavy))),
            backend.not(mismatch)
        );
        long[] openedBlocks = backend.open(opened);
        int[] selectedIndexes = selectedIndexes(openedBlocks, batch.getBatchSize());
        long[][] selectedCandidateBitBlocks = new long[ELEMENT_BITS][];
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            selectedCandidateBitBlocks[bitIndex] = backend.openSelected(candidateBits[bitIndex], selectedIndexes);
        }
        return decode(openedBlocks, selectedIndexes, selectedCandidateBitBlocks, batch.getBatchSize());
    }

    private PackedBooleanShare[] candidateBits(PackedBooleanShare[] singleton, PackedBooleanShare[][] valueBits) {
        PackedBooleanShare[] candidateBits = new PackedBooleanShare[ELEMENT_BITS];
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            PackedBooleanShare candidate = backend.zero();
            for (int partyIndex = 0; partyIndex < singleton.length; partyIndex++) {
                candidate = backend.or(candidate, backend.and(singleton[partyIndex], valueBits[partyIndex][bitIndex]));
            }
            candidateBits[bitIndex] = candidate;
        }
        return candidateBits;
    }

    private PackedBooleanShare mismatch(PackedBooleanShare[] singleton, PackedBooleanShare[][] valueBits,
                                        PackedBooleanShare[] candidateBits) {
        PackedBooleanShare mismatch = backend.zero();
        for (int partyIndex = 0; partyIndex < singleton.length; partyIndex++) {
            for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                PackedBooleanShare diff = backend.xor(valueBits[partyIndex][bitIndex], candidateBits[bitIndex]);
                mismatch = backend.or(mismatch, backend.and(singleton[partyIndex], diff));
            }
        }
        return mismatch;
    }

    private PackedBooleanShare orMany(PackedBooleanShare[] shares) {
        PackedBooleanShare result = backend.zero();
        for (PackedBooleanShare share : shares) {
            result = backend.or(result, share);
        }
        return result;
    }

    private static int[] selectedIndexes(long[] openedBlocks, int batchSize) {
        int selectedSize = 0;
        for (int laneIndex = 0; laneIndex < batchSize; laneIndex++) {
            if (getLane(openedBlocks, laneIndex)) {
                selectedSize++;
            }
        }
        int[] selectedIndexes = new int[selectedSize];
        int selectedIndex = 0;
        for (int laneIndex = 0; laneIndex < batchSize; laneIndex++) {
            if (getLane(openedBlocks, laneIndex)) {
                selectedIndexes[selectedIndex++] = laneIndex;
            }
        }
        return selectedIndexes;
    }

    private static List<MpSogsPeelResult> decode(
        long[] openedBlocks, int[] selectedIndexes, long[][] selectedCandidateBitBlocks, int batchSize
    ) {
        List<MpSogsPeelResult> results = new ArrayList<>(batchSize);
        int selectedIndex = 0;
        for (int laneIndex = 0; laneIndex < batchSize; laneIndex++) {
            if (!getLane(openedBlocks, laneIndex)) {
                results.add(MpSogsPeelResult.bottom());
                continue;
            }
            if (selectedIndexes[selectedIndex] != laneIndex) {
                throw new IllegalStateException("selected lane mismatch");
            }
            long value = 0L;
            for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                if (getLane(selectedCandidateBitBlocks[bitIndex], selectedIndex)) {
                    value |= 1L << (ELEMENT_BITS - 1 - bitIndex);
                }
            }
            results.add(MpSogsPeelResult.element(value));
            selectedIndex++;
        }
        return results;
    }

    private static boolean getLane(long[] blocks, int laneIndex) {
        return ((blocks[laneIndex >>> 6] >>> (laneIndex & (Long.SIZE - 1))) & 1L) != 0L;
    }
}
