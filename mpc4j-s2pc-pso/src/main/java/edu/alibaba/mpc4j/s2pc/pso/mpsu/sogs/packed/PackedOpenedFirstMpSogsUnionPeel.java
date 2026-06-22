package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Packed opened-first implementation of MP-SOGS uPeel.
 *
 * <p>This circuit first opens only the public {@code opened} bitmap. Candidate value recovery is then computed on
 * compact selected lanes, so bottom cells do not pay the full 64-bit candidate cost.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class PackedOpenedFirstMpSogsUnionPeel {
    /**
     * Element bit length.
     */
    private static final int ELEMENT_BITS = MpSogsMpsuParams.ELEMENT_BIT_LENGTH;

    /**
     * Full-batch backend.
     */
    private final PackedBooleanBackend backend;

    public PackedOpenedFirstMpSogsUnionPeel(PackedBooleanBackend backend) {
        this.backend = backend;
    }

    /**
     * Evaluates a packed opened-first uPeel batch.
     *
     * @param batch packed batch.
     * @return public peel results.
     */
    public List<MpSogsPeelResult> peel(PackedMpSogsCellBatch batch) {
        PackedBooleanShare[] singleton = batch.getSingleton();
        PackedBooleanShare[] heavy = batch.getHeavy();
        PackedBooleanShare[][] valueBits = batch.getValueBits();
        PackedBooleanShare opened = opened(singleton, heavy, valueBits);
        long[] openedBlocks = backend.open(opened);
        int[] selectedIndexes = selectedIndexes(openedBlocks, batch.getBatchSize());
        if (selectedIndexes.length == 0) {
            return allBottom(batch.getBatchSize());
        }
        PackedBooleanBackend compactBackend = backend.derive(selectedIndexes.length);
        PackedBooleanShare[] compactSingleton = compact(singleton, selectedIndexes);
        PackedBooleanShare[][] compactValueBits = compact(valueBits, selectedIndexes);
        PackedBooleanShare[] candidateBits = candidateBits(compactBackend, compactSingleton, compactValueBits);
        long[][] selectedCandidateBitBlocks = new long[ELEMENT_BITS][];
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            selectedCandidateBitBlocks[bitIndex] = compactBackend.open(candidateBits[bitIndex]);
        }
        return decode(openedBlocks, selectedIndexes, selectedCandidateBitBlocks, batch.getBatchSize());
    }

    private PackedBooleanShare opened(PackedBooleanShare[] singleton, PackedBooleanShare[] heavy,
                                      PackedBooleanShare[][] valueBits) {
        PackedBooleanShare hasSingleton = orMany(singleton);
        PackedBooleanShare hasHeavy = orMany(heavy);
        PackedBooleanShare pairMismatch = pairMismatch(singleton, valueBits);
        return backend.and(backend.and(hasSingleton, backend.not(hasHeavy)), backend.not(pairMismatch));
    }

    private PackedBooleanShare pairMismatch(PackedBooleanShare[] singleton, PackedBooleanShare[][] valueBits) {
        PackedBooleanShare mismatch = null;
        for (int leftParty = 0; leftParty < singleton.length; leftParty++) {
            for (int rightParty = leftParty + 1; rightParty < singleton.length; rightParty++) {
                PackedBooleanShare pairActive = backend.and(singleton[leftParty], singleton[rightParty]);
                PackedBooleanShare valueDiff = null;
                for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                    PackedBooleanShare diff = backend.xor(valueBits[leftParty][bitIndex], valueBits[rightParty][bitIndex]);
                    valueDiff = valueDiff == null ? diff : backend.or(valueDiff, diff);
                }
                PackedBooleanShare term = backend.and(pairActive, valueDiff == null ? backend.zero() : valueDiff);
                mismatch = mismatch == null ? term : backend.or(mismatch, term);
            }
        }
        return mismatch == null ? backend.zero() : mismatch;
    }

    private PackedBooleanShare[] compact(PackedBooleanShare[] shares, int[] selectedIndexes) {
        PackedBooleanShare[] compactShares = new PackedBooleanShare[shares.length];
        for (int i = 0; i < shares.length; i++) {
            compactShares[i] = backend.compact(shares[i], selectedIndexes);
        }
        return compactShares;
    }

    private PackedBooleanShare[][] compact(PackedBooleanShare[][] shares, int[] selectedIndexes) {
        PackedBooleanShare[][] compactShares = new PackedBooleanShare[shares.length][ELEMENT_BITS];
        for (int partyIndex = 0; partyIndex < shares.length; partyIndex++) {
            for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                compactShares[partyIndex][bitIndex] = backend.compact(shares[partyIndex][bitIndex], selectedIndexes);
            }
        }
        return compactShares;
    }

    private static PackedBooleanShare[] candidateBits(PackedBooleanBackend backend, PackedBooleanShare[] singleton,
                                                      PackedBooleanShare[][] valueBits) {
        PackedBooleanShare[] candidateBits = new PackedBooleanShare[ELEMENT_BITS];
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            PackedBooleanShare[] left = new PackedBooleanShare[singleton.length];
            PackedBooleanShare[] right = new PackedBooleanShare[singleton.length];
            for (int partyIndex = 0; partyIndex < singleton.length; partyIndex++) {
                left[partyIndex] = singleton[partyIndex];
                right[partyIndex] = valueBits[partyIndex][bitIndex];
            }
            PackedBooleanShare[] terms = backend.andMany(left, right);
            PackedBooleanShare candidate = null;
            for (PackedBooleanShare term : terms) {
                candidate = candidate == null ? term : backend.or(candidate, term);
            }
            candidateBits[bitIndex] = candidate == null ? backend.zero() : candidate;
        }
        return candidateBits;
    }

    private PackedBooleanShare orMany(PackedBooleanShare[] shares) {
        if (shares.length == 0) {
            return backend.zero();
        }
        PackedBooleanShare result = shares[0];
        for (int i = 1; i < shares.length; i++) {
            result = backend.or(result, shares[i]);
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

    private static List<MpSogsPeelResult> allBottom(int batchSize) {
        List<MpSogsPeelResult> results = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            results.add(MpSogsPeelResult.bottom());
        }
        return results;
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
