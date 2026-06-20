package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVectorFactory;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
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
     * Number of element bits.
     */
    private static final int ELEMENT_BITS = MpSogsMpsuParams.ELEMENT_BIT_LENGTH;
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
     * Total shared vectors per party input.
     */
    private static final int INPUT_VECTOR_NUM = STATE_BIT_NUM + ELEMENT_BITS;

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
    /**
     * All participants sorted by public party id.
     */
    private final Party[] parties;

    public Abb3SecureMpSogsUnionPeel(TripletZ2cParty z2cParty, MpSogsSketch localSketch, MpSogsMpsuParams params) {
        this.z2cParty = z2cParty;
        this.localSketch = localSketch;
        this.params = params;
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
            SharedCellBatch[] batches = shareCellBatches(input);
            CircuitOutput circuitOutput = evaluateCircuit(batches, input.size());
            MpcZ2Vector[] openInput = new MpcZ2Vector[ELEMENT_BITS + 1];
            openInput[0] = circuitOutput.opened;
            System.arraycopy(circuitOutput.openedValueBits, 0, openInput, 1, ELEMENT_BITS);
            BitVector[] openedVectors = z2cParty.open(openInput);
            List<MpSogsPeelResult> results = decodeResults(openedVectors, input.size());
            return new BatchMpSogsPeelOutput(results, 0L, 0L, 0);
        } catch (MpcAbortException e) {
            throw new IllegalStateException("ABB3 secure MP-SOGS union-peel aborts", e);
        }
    }

    private SharedCellBatch[] shareCellBatches(BatchMpSogsPeelInput input) throws MpcAbortException {
        BitVector[] localInput = encodeLocalInput(input);
        int[] bitNums = new int[INPUT_VECTOR_NUM];
        Arrays.fill(bitNums, input.size());
        SharedCellBatch[] batches = new SharedCellBatch[parties.length];
        int ownPartyId = z2cParty.ownParty().getPartyId();
        for (int partyIndex = 0; partyIndex < parties.length; partyIndex++) {
            MpcZ2Vector[] sharedVectors = parties[partyIndex].getPartyId() == ownPartyId
                ? z2cParty.shareOwn(localInput)
                : z2cParty.shareOther(bitNums, parties[partyIndex]);
            batches[partyIndex] = SharedCellBatch.fromSharedVectors(sharedVectors);
        }
        return batches;
    }

    private BitVector[] encodeLocalInput(BatchMpSogsPeelInput input) {
        BitVector[] vectors = new BitVector[INPUT_VECTOR_NUM];
        for (int vectorIndex = 0; vectorIndex < vectors.length; vectorIndex++) {
            vectors[vectorIndex] = BitVectorFactory.createZeros(input.size());
        }
        for (int batchIndex = 0; batchIndex < input.size(); batchIndex++) {
            int cellIndex = input.getCellIndexes().get(batchIndex);
            MpSogsLocalCellView view = localSketch.localCellView(cellIndex);
            if (view.isSingleton()) {
                vectors[SINGLETON_OFFSET].set(batchIndex, true);
                setValueBits(vectors, batchIndex, view.getSingletonValue());
            } else if (view.isHeavy()) {
                vectors[HEAVY_OFFSET].set(batchIndex, true);
            }
        }
        return vectors;
    }

    private CircuitOutput evaluateCircuit(SharedCellBatch[] batches, int batchSize) throws MpcAbortException {
        MpcZ2Vector hasHeavy = z2cParty.createZeros(batchSize);
        MpcZ2Vector hasSingleton = z2cParty.createZeros(batchSize);
        MpcZ2Vector[] candidateBits = createZeroWord(batchSize);
        for (SharedCellBatch batch : batches) {
            hasHeavy = z2cParty.or(hasHeavy, batch.heavy);
            MpcZ2Vector take = z2cParty.and(batch.singleton, z2cParty.not(hasSingleton));
            candidateBits = z2cParty.mux(candidateBits, batch.valueBits, take);
            hasSingleton = z2cParty.or(hasSingleton, batch.singleton);
        }
        MpcZ2Vector allConsistent = z2cParty.createOnes(batchSize);
        for (SharedCellBatch batch : batches) {
            MpcZ2Vector sameCandidate = eqWord(batch.valueBits, candidateBits);
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

    private MpcZ2Vector[] createZeroWord(int batchSize) {
        MpcZ2Vector[] zeroWord = new MpcZ2Vector[ELEMENT_BITS];
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            zeroWord[bitIndex] = z2cParty.createZeros(batchSize);
        }
        return zeroWord;
    }

    private List<MpSogsPeelResult> decodeResults(BitVector[] openedVectors, int batchSize) {
        BitVector opened = openedVectors[0];
        List<MpSogsPeelResult> results = new ArrayList<>(batchSize);
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            if (!opened.get(batchIndex)) {
                results.add(MpSogsPeelResult.bottom());
                continue;
            }
            long value = 0L;
            for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                if (openedVectors[1 + bitIndex].get(batchIndex)) {
                    value |= 1L << (ELEMENT_BITS - 1 - bitIndex);
                }
            }
            results.add(MpSogsPeelResult.element(value));
        }
        return results;
    }

    private static void setValueBits(BitVector[] vectors, int batchIndex, long value) {
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            vectors[VALUE_OFFSET + bitIndex].set(
                batchIndex, ((value >>> (ELEMENT_BITS - 1 - bitIndex)) & 1L) == 1L
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
        private final MpcZ2Vector[] openedValueBits;

        private CircuitOutput(MpcZ2Vector opened, MpcZ2Vector[] openedValueBits) {
            this.opened = opened;
            this.openedValueBits = openedValueBits;
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
        private final MpcZ2Vector[] valueBits;

        private SharedCellBatch(MpcZ2Vector singleton, MpcZ2Vector heavy, MpcZ2Vector[] valueBits) {
            this.singleton = singleton;
            this.heavy = heavy;
            this.valueBits = valueBits;
        }

        private static SharedCellBatch fromSharedVectors(MpcZ2Vector[] vectors) {
            if (vectors.length != INPUT_VECTOR_NUM) {
                throw new IllegalArgumentException("invalid shared vector count: " + vectors.length);
            }
            MpcZ2Vector[] valueBits = new MpcZ2Vector[ELEMENT_BITS];
            System.arraycopy(vectors, VALUE_OFFSET, valueBits, 0, ELEMENT_BITS);
            return new SharedCellBatch(vectors[SINGLETON_OFFSET], vectors[HEAVY_OFFSET], valueBits);
        }
    }
}
