package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.shamir;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLabelEncoding;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsSketch;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic semi-honest Shamir implementation of MP-SOGS secure union-peel.
 *
 * <p>The backend evaluates the same {@code uPeel_t} functionality as the ABB3 backend but supports any honest-majority
 * party count accepted by {@link ShamirMpc}. It first opens the public {@code opened} flags, then opens candidate
 * values only for cells whose flag is one.</p>
 *
 * @author donghai hou
 * @date 2026/06/21
 */
public class ShamirSecureMpSogsUnionPeel implements SecureMpSogsUnionPeel {
    /**
     * Local state bit count: singleton and heavy.
     */
    private static final int STATE_BIT_NUM = 2;
    /**
     * Singleton offset.
     */
    private static final int SINGLETON_OFFSET = 0;
    /**
     * Heavy offset.
     */
    private static final int HEAVY_OFFSET = 1;
    /**
     * Value offset.
     */
    private static final int VALUE_OFFSET = STATE_BIT_NUM;

    private final ShamirMpc mpc;
    private final MpSogsSketch localSketch;
    private final MpSogsMpsuParams params;
    private final MpSogsLabelEncoding labelEncoding;

    public ShamirSecureMpSogsUnionPeel(Rpc rpc, MpSogsSketch localSketch, MpSogsMpsuParams params, long taskId) {
        this(rpc, localSketch, params, taskId, MpSogsLabelEncoding.FULL_VALUE);
    }

    public ShamirSecureMpSogsUnionPeel(Rpc rpc, MpSogsSketch localSketch, MpSogsMpsuParams params, long taskId,
                                       MpSogsLabelEncoding labelEncoding) {
        this.mpc = new ShamirMpc(rpc, taskId);
        this.localSketch = localSketch;
        this.params = params;
        this.labelEncoding = labelEncoding;
        if (mpc.getPartyNum() != params.getPartyNum()) {
            throw new IllegalArgumentException("RPC party count does not match MP-SOGS partyNum");
        }
        if (localSketch.getCellNum() != params.getCellNum()) {
            throw new IllegalArgumentException("local sketch parameters do not match MP-SOGS parameters");
        }
    }

    @Override
    public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
        if (input.size() == 0) {
            return new BatchMpSogsPeelOutput(List.of(), 0L, 0L, 0);
        }
        mpc.resetNetworkRoundCount();
        int labelBitLength = labelEncoding.bitLength(params, input.getTier());
        int inputsPerCell = STATE_BIT_NUM + labelBitLength;
        long[][] sharesByParty = mpc.shareOwnAndReceiveAll(encodeLocalInput(input, labelBitLength));
        CircuitInput circuitInput = extractCircuitInput(sharesByParty, input.size(), labelBitLength, inputsPerCell);
        long[] openedShares = evaluateOpened(circuitInput, input.size());
        long[] opened = mpc.open(openedShares);
        int[] selectedIndexes = selectedIndexes(opened);
        long[] selectedLabels = selectedIndexes.length == 0
            ? new long[0]
            : mpc.open(flatten(evaluateCandidateBits(circuitInput, selectedIndexes), selectedIndexes.length));
        return new BatchMpSogsPeelOutput(
            decodeResults(opened, selectedIndexes, selectedLabels, input, labelBitLength),
            0L, 0L, mpc.getNetworkRoundCount()
        );
    }

    private long[] encodeLocalInput(BatchMpSogsPeelInput input, int labelBitLength) {
        int inputsPerCell = STATE_BIT_NUM + labelBitLength;
        long[] values = new long[input.size() * inputsPerCell];
        for (int batchIndex = 0; batchIndex < input.size(); batchIndex++) {
            int cellIndex = input.getCellIndexes().get(batchIndex);
            MpSogsLocalCellView view = localSketch.localCellView(input.getTier(), cellIndex);
            int base = batchIndex * inputsPerCell;
            if (view.isSingleton()) {
                values[base + SINGLETON_OFFSET] = 1L;
                long label = labelEncoding.encode(
                    view.getSingletonValue(), params, input.getTier(), cellIndex
                );
                setLabelBits(values, base + VALUE_OFFSET, label, labelBitLength);
            } else if (view.isHeavy()) {
                values[base + HEAVY_OFFSET] = 1L;
            }
        }
        return values;
    }

    private CircuitInput extractCircuitInput(long[][] sharesByParty, int batchSize, int labelBitLength,
                                             int inputsPerCell) {
        int partyNum = sharesByParty.length;
        long[][] singleton = new long[partyNum][];
        long[][] heavy = new long[partyNum][];
        long[][][] labelBits = new long[partyNum][labelBitLength][];
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            singleton[partyIndex] = extractWire(
                sharesByParty[partyIndex], batchSize, inputsPerCell, SINGLETON_OFFSET
            );
            heavy[partyIndex] = extractWire(
                sharesByParty[partyIndex], batchSize, inputsPerCell, HEAVY_OFFSET
            );
            for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
                labelBits[partyIndex][bitIndex] = extractWire(
                    sharesByParty[partyIndex], batchSize, inputsPerCell, VALUE_OFFSET + bitIndex
                );
            }
        }
        return new CircuitInput(singleton, heavy, labelBits);
    }

    private long[] evaluateOpened(CircuitInput input, int batchSize) {
        int partyNum = input.singleton.length;
        long[] hasSingleton = orMany(input.singleton, batchSize);
        long[] hasHeavy = orMany(input.heavy, batchSize);
        long[] mismatch = ShamirMpc.zeros(batchSize);
        for (int i = 0; i < partyNum; i++) {
            for (int j = i + 1; j < partyNum; j++) {
                long[] bothSingleton = mpc.mul(input.singleton[i], input.singleton[j]);
                long[] wordDiff = wordDiff(input.labelBits[i], input.labelBits[j], batchSize);
                mismatch = or(mismatch, mpc.mul(bothSingleton, wordDiff));
            }
        }
        return mpc.mul(mpc.mul(hasSingleton, ShamirMpc.not(hasHeavy)), ShamirMpc.not(mismatch));
    }

    private long[][] evaluateCandidateBits(CircuitInput input, int[] selectedIndexes) {
        int partyNum = input.singleton.length;
        int selectedSize = selectedIndexes.length;
        long[][] selectedSingleton = new long[partyNum][];
        int labelBitLength = input.labelBits[0].length;
        long[][][] selectedLabelBits = new long[partyNum][labelBitLength][];
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            selectedSingleton[partyIndex] = select(input.singleton[partyIndex], selectedIndexes);
            for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
                selectedLabelBits[partyIndex][bitIndex] = select(
                    input.labelBits[partyIndex][bitIndex], selectedIndexes
                );
            }
        }
        long[][] candidateBits = new long[labelBitLength][];
        for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
            long[][] terms = new long[partyNum][];
            for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
                terms[partyIndex] = mpc.mul(selectedSingleton[partyIndex], selectedLabelBits[partyIndex][bitIndex]);
            }
            candidateBits[bitIndex] = orMany(terms, selectedSize);
        }
        return candidateBits;
    }

    private long[] wordDiff(long[][] leftBits, long[][] rightBits, int batchSize) {
        long[] result = ShamirMpc.zeros(batchSize);
        for (int bitIndex = 0; bitIndex < leftBits.length; bitIndex++) {
            result = or(result, xor(leftBits[bitIndex], rightBits[bitIndex]));
        }
        return result;
    }

    private long[] orMany(long[][] vectors, int batchSize) {
        long[] result = ShamirMpc.zeros(batchSize);
        for (long[] vector : vectors) {
            result = or(result, vector);
        }
        return result;
    }

    private long[] or(long[] left, long[] right) {
        return ShamirMpc.sub(ShamirMpc.add(left, right), mpc.mul(left, right));
    }

    private long[] xor(long[] left, long[] right) {
        long[] twoAnd = mpc.mul(left, right);
        for (int i = 0; i < twoAnd.length; i++) {
            twoAnd[i] = ShamirMpc.add(twoAnd[i], twoAnd[i]);
        }
        return ShamirMpc.sub(ShamirMpc.add(left, right), twoAnd);
    }

    private static long[] extractWire(long[] flattened, int batchSize, int inputsPerCell, int wireOffset) {
        long[] wire = new long[batchSize];
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            wire[batchIndex] = flattened[batchIndex * inputsPerCell + wireOffset];
        }
        return wire;
    }

    private static long[] flatten(long[][] vectors, int batchSize) {
        long[] flattened = new long[vectors.length * batchSize];
        for (int vectorIndex = 0; vectorIndex < vectors.length; vectorIndex++) {
            if (vectors[vectorIndex].length != batchSize) {
                throw new IllegalArgumentException("invalid vector length");
            }
            System.arraycopy(vectors[vectorIndex], 0, flattened, vectorIndex * batchSize, batchSize);
        }
        return flattened;
    }

    private static int[] selectedIndexes(long[] opened) {
        int selectedSize = 0;
        for (long openedFlag : opened) {
            if (openedFlag != 0L) {
                selectedSize++;
            }
        }
        int[] selectedIndexes = new int[selectedSize];
        int selectedIndex = 0;
        for (int batchIndex = 0; batchIndex < opened.length; batchIndex++) {
            if (opened[batchIndex] != 0L) {
                selectedIndexes[selectedIndex++] = batchIndex;
            }
        }
        return selectedIndexes;
    }

    private static long[] select(long[] vector, int[] selectedIndexes) {
        long[] selected = new long[selectedIndexes.length];
        for (int i = 0; i < selectedIndexes.length; i++) {
            selected[i] = vector[selectedIndexes[i]];
        }
        return selected;
    }

    private List<MpSogsPeelResult> decodeResults(
        long[] opened, int[] selectedIndexes, long[] selectedLabelBits, BatchMpSogsPeelInput input,
        int labelBitLength
    ) {
        int batchSize = input.size();
        if (opened.length != batchSize) {
            throw new IllegalArgumentException("invalid opened vector length: " + opened.length);
        }
        if (selectedLabelBits.length != labelBitLength * selectedIndexes.length) {
            throw new IllegalArgumentException("invalid selected label vector length: " + selectedLabelBits.length);
        }
        List<MpSogsPeelResult> results = new ArrayList<>(batchSize);
        long[] selectedLabels = decodeSelectedLabels(selectedLabelBits, selectedIndexes.length, labelBitLength);
        int selectedIndex = 0;
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            if (opened[batchIndex] == 0L) {
                results.add(MpSogsPeelResult.bottom());
                continue;
            }
            if (selectedIndexes[selectedIndex] != batchIndex) {
                throw new IllegalStateException("selected index mismatch");
            }
            int selectedLane = selectedIndexes[selectedIndex];
            long value = labelEncoding.decode(
                selectedLabels[selectedIndex++], params, input.getTier(), input.getCellIndexes().get(selectedLane)
            );
            results.add(MpSogsPeelResult.element(value));
        }
        return results;
    }

    private static long[] decodeSelectedLabels(long[] selectedLabelBits, int selectedSize, int labelBitLength) {
        long[] selectedLabels = new long[selectedSize];
        for (int selectedIndex = 0; selectedIndex < selectedSize; selectedIndex++) {
            long label = 0L;
            for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
                if (selectedLabelBits[bitIndex * selectedSize + selectedIndex] != 0L) {
                    label |= 1L << (labelBitLength - 1 - bitIndex);
                }
            }
            selectedLabels[selectedIndex] = label;
        }
        return selectedLabels;
    }

    private static void setLabelBits(long[] values, int offset, long label, int labelBitLength) {
        for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
            values[offset + bitIndex] = ((label >>> (labelBitLength - 1 - bitIndex)) & 1L);
        }
    }

    /**
     * Extracted secret input wires.
     */
    private static class CircuitInput {
        private final long[][] singleton;
        private final long[][] heavy;
        private final long[][][] labelBits;

        private CircuitInput(long[][] singleton, long[][] heavy, long[][][] labelBits) {
            this.singleton = singleton;
            this.heavy = heavy;
            this.labelBits = labelBits;
        }
    }
}
