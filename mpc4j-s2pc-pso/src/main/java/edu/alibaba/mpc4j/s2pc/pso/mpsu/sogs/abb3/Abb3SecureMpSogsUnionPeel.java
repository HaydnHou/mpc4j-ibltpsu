package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVectorFactory;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLabelEncoding;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsSketch;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Three-party ABB3 implementation of MP-SOGS secure union-peel.
 *
 * <p>The circuit opens only {@code ok} and {@code ok * x} for every public cell. Local count/state/source
 * information remains secret-shared inside ABB3. The first engineering version targets the symmetric
 * three-party setting used by ABY3/ABB3.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class Abb3SecureMpSogsUnionPeel implements SecureMpSogsUnionPeel {
    /**
     * Local state bit count: singleton and heavy.
     */
    private static final int STATE_BIT_NUM = 2;
    /**
     * Singleton flag vector offset.
     */
    private static final int SINGLETON_OFFSET = 0;
    /**
     * Heavy flag vector offset.
     */
    private static final int HEAVY_OFFSET = 1;
    /**
     * Value bit vector offset.
     */
    private static final int VALUE_OFFSET = STATE_BIT_NUM;
    /**
     * ABB3 Z2 circuit party.
     */
    private final TripletZ2cParty z2cParty;
    /**
     * This participant's local SOGS sketch.
     */
    private final MpSogsSketch localSketch;
    /**
     * Public MP-SOGS parameters.
     */
    private final MpSogsMpsuParams params;
    /** Secure label representation. */
    private final MpSogsLabelEncoding labelEncoding;
    /**
     * All participants sorted by public party id.
     */
    private final Party[] parties;

    public Abb3SecureMpSogsUnionPeel(TripletZ2cParty z2cParty, MpSogsSketch localSketch, MpSogsMpsuParams params) {
        this(z2cParty, localSketch, params, MpSogsLabelEncoding.FULL_VALUE);
    }

    public Abb3SecureMpSogsUnionPeel(TripletZ2cParty z2cParty, MpSogsSketch localSketch, MpSogsMpsuParams params,
                                     MpSogsLabelEncoding labelEncoding) {
        this.z2cParty = z2cParty;
        this.localSketch = localSketch;
        this.params = params;
        this.labelEncoding = labelEncoding;
        if (params.getPartyNum() != 3) {
            throw new IllegalArgumentException("ABB3 backend currently supports exactly 3 parties");
        }
        if (localSketch.getCellNum() != params.getCellNum()) {
            throw new IllegalArgumentException("local sketch parameters do not match MP-SOGS parameters");
        }
        parties = sortedParties(z2cParty);
        if (parties.length != params.getPartyNum()) {
            throw new IllegalArgumentException("ABB3 party count does not match MP-SOGS partyNum");
        }
    }

    @Override
    public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
        if (input.size() == 0) {
            return new BatchMpSogsPeelOutput(List.of(), 0L, 0L, 0);
        }
        try {
            int labelBitLength = labelEncoding.bitLength(params, input.getTier());
            SharedCellBatch[] batches = shareCellBatches(input, labelBitLength);
            CircuitOutput circuitOutput = evaluateCircuit(batches, input.size(), labelBitLength);
            MpcZ2Vector[] openInput = new MpcZ2Vector[labelBitLength + 1];
            openInput[0] = circuitOutput.opened;
            System.arraycopy(circuitOutput.openedLabelBits, 0, openInput, 1, labelBitLength);
            BitVector[] openedVectors = z2cParty.open(openInput);
            List<MpSogsPeelResult> results = decodeResults(openedVectors, input, labelBitLength);
            return new BatchMpSogsPeelOutput(results, 0L, 0L, 0);
        } catch (MpcAbortException e) {
            throw new IllegalStateException("ABB3 secure MP-SOGS union-peel aborts", e);
        }
    }

    private SharedCellBatch[] shareCellBatches(BatchMpSogsPeelInput input, int labelBitLength)
        throws MpcAbortException {
        BitVector[] localInput = encodeLocalInput(input, labelBitLength);
        int[] bitNums = new int[STATE_BIT_NUM + labelBitLength];
        Arrays.fill(bitNums, input.size());
        SharedCellBatch[] batches = new SharedCellBatch[parties.length];
        int ownPartyId = z2cParty.ownParty().getPartyId();
        for (int partyIndex = 0; partyIndex < parties.length; partyIndex++) {
            MpcZ2Vector[] sharedVectors = parties[partyIndex].getPartyId() == ownPartyId
                ? z2cParty.shareOwn(localInput)
                : z2cParty.shareOther(bitNums, parties[partyIndex]);
            batches[partyIndex] = SharedCellBatch.fromSharedVectors(sharedVectors, labelBitLength);
        }
        return batches;
    }

    private BitVector[] encodeLocalInput(BatchMpSogsPeelInput input, int labelBitLength) {
        BitVector[] vectors = new BitVector[STATE_BIT_NUM + labelBitLength];
        for (int vectorIndex = 0; vectorIndex < vectors.length; vectorIndex++) {
            vectors[vectorIndex] = BitVectorFactory.createZeros(input.size());
        }
        for (int batchIndex = 0; batchIndex < input.size(); batchIndex++) {
            int cellIndex = input.getCellIndexes().get(batchIndex);
            MpSogsLocalCellView view = localSketch.localCellView(input.getTier(), cellIndex);
            if (view.isSingleton()) {
                vectors[SINGLETON_OFFSET].set(batchIndex, true);
                long label = labelEncoding.encode(
                    view.getSingletonValue(), params, input.getTier(), cellIndex
                );
                setLabelBits(vectors, batchIndex, label, labelBitLength);
            } else if (view.isHeavy()) {
                vectors[HEAVY_OFFSET].set(batchIndex, true);
            }
        }
        return vectors;
    }

    private CircuitOutput evaluateCircuit(SharedCellBatch[] batches, int batchSize, int labelBitLength)
        throws MpcAbortException {
        MpcZ2Vector hasHeavy = z2cParty.createZeros(batchSize);
        MpcZ2Vector hasSingleton = z2cParty.createZeros(batchSize);
        MpcZ2Vector[] candidateBits = createZeroWord(batchSize, labelBitLength);
        for (SharedCellBatch batch : batches) {
            hasHeavy = z2cParty.or(hasHeavy, batch.heavy);
            MpcZ2Vector take = z2cParty.and(batch.singleton, z2cParty.not(hasSingleton));
            candidateBits = z2cParty.mux(candidateBits, batch.labelBits, take);
            hasSingleton = z2cParty.or(hasSingleton, batch.singleton);
        }
        MpcZ2Vector allConsistent = z2cParty.createOnes(batchSize);
        for (SharedCellBatch batch : batches) {
            MpcZ2Vector sameCandidate = eqWord(batch.labelBits, candidateBits);
            MpcZ2Vector consistent = z2cParty.or(z2cParty.not(batch.singleton), sameCandidate);
            allConsistent = z2cParty.and(allConsistent, consistent);
        }
        MpcZ2Vector notHeavy = z2cParty.not(hasHeavy);
        MpcZ2Vector opened = z2cParty.and(hasSingleton, z2cParty.and(notHeavy, allConsistent));
        return new CircuitOutput(opened, z2cParty.and(opened, candidateBits));
    }

    private MpcZ2Vector eqWord(MpcZ2Vector[] left, MpcZ2Vector[] right) throws MpcAbortException {
        MpcZ2Vector[] equalBits = z2cParty.not(z2cParty.xor(left, right));
        while (equalBits.length > 1) {
            int nodeNum = equalBits.length / 2;
            MpcZ2Vector[] leftNodes = new MpcZ2Vector[nodeNum];
            MpcZ2Vector[] rightNodes = new MpcZ2Vector[nodeNum];
            for (int index = 0; index < nodeNum; index++) {
                leftNodes[index] = equalBits[index * 2];
                rightNodes[index] = equalBits[index * 2 + 1];
            }
            MpcZ2Vector[] parentNodes = z2cParty.and(leftNodes, rightNodes);
            if (equalBits.length % 2 == 1) {
                parentNodes = Arrays.copyOf(parentNodes, nodeNum + 1);
                parentNodes[nodeNum] = equalBits[equalBits.length - 1];
            }
            equalBits = parentNodes;
        }
        return equalBits[0];
    }

    private MpcZ2Vector[] createZeroWord(int batchSize, int labelBitLength) {
        MpcZ2Vector[] zeroWord = new MpcZ2Vector[labelBitLength];
        for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
            zeroWord[bitIndex] = z2cParty.createZeros(batchSize);
        }
        return zeroWord;
    }

    private List<MpSogsPeelResult> decodeResults(BitVector[] openedVectors, BatchMpSogsPeelInput input,
                                                  int labelBitLength) {
        int batchSize = input.size();
        BitVector opened = openedVectors[0];
        List<MpSogsPeelResult> results = new ArrayList<>(batchSize);
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            if (!opened.get(batchIndex)) {
                results.add(MpSogsPeelResult.bottom());
                continue;
            }
            long label = 0L;
            for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
                if (openedVectors[1 + bitIndex].get(batchIndex)) {
                    label |= 1L << (labelBitLength - 1 - bitIndex);
                }
            }
            long value = labelEncoding.decode(
                label, params, input.getTier(), input.getCellIndexes().get(batchIndex)
            );
            results.add(MpSogsPeelResult.element(value));
        }
        return results;
    }

    private static void setLabelBits(BitVector[] vectors, int batchIndex, long label, int labelBitLength) {
        for (int bitIndex = 0; bitIndex < labelBitLength; bitIndex++) {
            vectors[VALUE_OFFSET + bitIndex].set(
                batchIndex, ((label >>> (labelBitLength - 1 - bitIndex)) & 1L) == 1L
            );
        }
    }

    private static Party[] sortedParties(TripletZ2cParty z2cParty) {
        Party[] result = new Party[1 + z2cParty.otherParties().length];
        result[0] = z2cParty.ownParty();
        System.arraycopy(z2cParty.otherParties(), 0, result, 1, z2cParty.otherParties().length);
        Arrays.sort(result, Comparator.comparingInt(Party::getPartyId));
        return result;
    }

    /**
     * Secret circuit output before public opening.
     */
    private static class CircuitOutput {
        /**
         * Secret-shared public-open flag.
         */
        private final MpcZ2Vector opened;
        /**
         * Secret-shared opened value bits, masked by {@code opened}.
         */
        private final MpcZ2Vector[] openedLabelBits;

        private CircuitOutput(MpcZ2Vector opened, MpcZ2Vector[] openedLabelBits) {
            this.opened = opened;
            this.openedLabelBits = openedLabelBits;
        }
    }

    /**
     * Shared batch for one participant's private cell views.
     */
    private static class SharedCellBatch {
        /**
         * Secret-shared local singleton flag.
         */
        private final MpcZ2Vector singleton;
        /**
         * Secret-shared local heavy flag.
         */
        private final MpcZ2Vector heavy;
        /**
         * Secret-shared singleton value bits, MSB first.
         */
        private final MpcZ2Vector[] labelBits;

        private SharedCellBatch(MpcZ2Vector singleton, MpcZ2Vector heavy, MpcZ2Vector[] labelBits) {
            this.singleton = singleton;
            this.heavy = heavy;
            this.labelBits = labelBits;
        }

        private static SharedCellBatch fromSharedVectors(MpcZ2Vector[] vectors, int labelBitLength) {
            if (vectors.length != STATE_BIT_NUM + labelBitLength) {
                throw new IllegalArgumentException("invalid shared vector count: " + vectors.length);
            }
            MpcZ2Vector[] labelBits = new MpcZ2Vector[labelBitLength];
            System.arraycopy(vectors, VALUE_OFFSET, labelBits, 0, labelBitLength);
            return new SharedCellBatch(vectors[SINGLETON_OFFSET], vectors[HEAVY_OFFSET], labelBits);
        }
    }
}
