package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shamir/BGW secret-shared multiplicity union peel.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class ShamirMultiplicitySecureUnionPeel implements SecureMpSogsUnionPeel {
    private static final int ZERO_TEST_REPETITIONS = 3;
    private static final int RECOVERY_MASK_NUM = 3;

    private final Mersenne61ShamirMpc mpc;
    private final MultiplicitySogsSketch localSketch;
    private final MpSogsMpsuParams params;
    private final long sessionSeed;

    public ShamirMultiplicitySecureUnionPeel(Rpc rpc, MultiplicitySogsSketch localSketch,
                                              MpSogsMpsuParams params, long taskId, long sessionSeed) {
        mpc = new Mersenne61ShamirMpc(rpc, taskId);
        this.localSketch = localSketch;
        this.params = params;
        this.sessionSeed = sessionSeed;
        if (mpc.getPartyNum() != params.getPartyNum()) {
            throw new IllegalArgumentException("RPC party count does not match multiplicity SOGS parameters");
        }
    }

    @Override
    public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
        if (input.size() == 0) {
            return new BatchMpSogsPeelOutput(List.of(), 0L, 0L, 0);
        }
        mpc.resetNetworkRoundCount();
        int batchSize = input.size();
        long[] localCells = MultiplicityCellBatch.encode(localSketch, input);
        long[] localMasks = mpc.randomFieldVector(ZERO_TEST_REPETITIONS * batchSize);
        long[] aggregateInput = mpc.shareOwnAndAggregate(concat(localCells, localMasks));
        long[] aggregateCells = Arrays.copyOf(aggregateInput, localCells.length);
        long[] zeroMasks = Arrays.copyOfRange(aggregateInput, localCells.length, aggregateInput.length);

        SharedCell cell = SharedCell.fromEncoded(aggregateCells, batchSize);
        PredicateValues predicate = evaluatePredicate(cell, batchSize);
        long[] maskedTests = new long[ZERO_TEST_REPETITIONS * batchSize];
        long[] maskShares = new long[maskedTests.length];
        for (int repetition = 0; repetition < ZERO_TEST_REPETITIONS; repetition++) {
            long[] compressed = compressPredicate(predicate, input, repetition);
            System.arraycopy(compressed, 0, maskedTests, repetition * batchSize, batchSize);
            System.arraycopy(zeroMasks, repetition * batchSize, maskShares, repetition * batchSize, batchSize);
        }
        long[] openedTests = mpc.open(mpc.mul(maskShares, maskedTests));
        int[] selectedIndexes = selectedIndexes(openedTests, batchSize);
        List<MpSogsPeelResult> results = bottomResults(batchSize);
        if (selectedIndexes.length > 0) {
            recoverCandidates(results, selectedIndexes, cell, input);
        }
        return new BatchMpSogsPeelOutput(results, 0L, 0L, mpc.getNetworkRoundCount());
    }

    private PredicateValues evaluatePredicate(SharedCell cell, int batchSize) {
        List<ProductInput> firstLayer = new ArrayList<>();
        for (int domainIndex = 0; domainIndex < MultiplicitySogsHash.MOMENT_DOMAIN_NUM; domainIndex++) {
            firstLayer.add(new ProductInput(cell.count, cell.secondMoments[domainIndex]));
            firstLayer.add(new ProductInput(cell.firstMoments[domainIndex], cell.firstMoments[domainIndex]));
        }
        List<long[]> rangeLayer = new ArrayList<>();
        for (int multiplicity = 1; multiplicity <= params.getPartyNum(); multiplicity++) {
            rangeLayer.add(Mersenne61ShamirMpc.subPublic(cell.count, multiplicity));
        }
        int pairedRange = rangeLayer.size() / 2;
        for (int pairIndex = 0; pairIndex < pairedRange; pairIndex++) {
            firstLayer.add(new ProductInput(rangeLayer.get(2 * pairIndex), rangeLayer.get(2 * pairIndex + 1)));
        }
        List<long[]> firstProducts = multiplyLayer(firstLayer, batchSize);
        long[][] errors = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
        for (int domainIndex = 0; domainIndex < errors.length; domainIndex++) {
            errors[domainIndex] = Mersenne61ShamirMpc.sub(
                firstProducts.get(2 * domainIndex), firstProducts.get(2 * domainIndex + 1)
            );
        }
        List<long[]> currentRange = new ArrayList<>();
        int rangeOffset = 2 * MultiplicitySogsHash.MOMENT_DOMAIN_NUM;
        for (int pairIndex = 0; pairIndex < pairedRange; pairIndex++) {
            currentRange.add(firstProducts.get(rangeOffset + pairIndex));
        }
        if ((rangeLayer.size() & 1) != 0) {
            currentRange.add(rangeLayer.get(rangeLayer.size() - 1));
        }
        while (currentRange.size() > 1) {
            List<ProductInput> products = new ArrayList<>();
            int pairNum = currentRange.size() / 2;
            for (int pairIndex = 0; pairIndex < pairNum; pairIndex++) {
                products.add(new ProductInput(
                    currentRange.get(2 * pairIndex), currentRange.get(2 * pairIndex + 1)
                ));
            }
            List<long[]> nextRange = multiplyLayer(products, batchSize);
            if ((currentRange.size() & 1) != 0) {
                nextRange.add(currentRange.get(currentRange.size() - 1));
            }
            currentRange = nextRange;
        }
        return new PredicateValues(currentRange.get(0), errors);
    }

    private long[] compressPredicate(PredicateValues predicate, BatchMpSogsPeelInput input, int repetition) {
        long gamma = publicCoefficient(input, repetition, -1);
        long[] compressed = Mersenne61ShamirMpc.mulPublic(predicate.range, gamma);
        for (int domainIndex = 0; domainIndex < predicate.errors.length; domainIndex++) {
            long beta = publicCoefficient(input, repetition, domainIndex);
            compressed = Mersenne61ShamirMpc.add(
                compressed, Mersenne61ShamirMpc.mulPublic(predicate.errors[domainIndex], beta)
            );
        }
        return compressed;
    }

    private void recoverCandidates(List<MpSogsPeelResult> results, int[] selectedIndexes, SharedCell cell,
                                   BatchMpSogsPeelInput input) {
        int selectedSize = selectedIndexes.length;
        long[] selectedCount = select(cell.count, selectedIndexes);
        long[] selectedLow = select(cell.payloadLow, selectedIndexes);
        long[] selectedHigh = select(cell.payloadHigh, selectedIndexes);
        long[] localRecoveryMasks = mpc.randomFieldVector(RECOVERY_MASK_NUM * selectedSize);
        long[] recoveryMasks = mpc.shareOwnAndAggregate(localRecoveryMasks);
        List<ProductInput> products = new ArrayList<>();
        for (int repetition = 0; repetition < RECOVERY_MASK_NUM; repetition++) {
            long[] mask = slice(recoveryMasks, repetition * selectedSize, selectedSize);
            products.add(new ProductInput(mask, selectedCount));
            products.add(new ProductInput(mask, selectedLow));
            products.add(new ProductInput(mask, selectedHigh));
        }
        List<long[]> productShares = multiplyLayer(products, selectedSize);
        long[] opened = mpc.open(flatten(productShares, selectedSize));
        for (int selectedIndex = 0; selectedIndex < selectedSize; selectedIndex++) {
            long candidate = 0L;
            boolean recovered = false;
            for (int repetition = 0; repetition < RECOVERY_MASK_NUM && !recovered; repetition++) {
                int base = repetition * 3 * selectedSize;
                long denominator = opened[base + selectedIndex];
                if (denominator == 0L) {
                    continue;
                }
                long inverse = Mersenne61Field.inv(denominator);
                long low = Mersenne61Field.mul(opened[base + selectedSize + selectedIndex], inverse);
                long high = Mersenne61Field.mul(opened[base + 2 * selectedSize + selectedIndex], inverse);
                if ((low >>> Integer.SIZE) == 0L && (high >>> Integer.SIZE) == 0L) {
                    candidate = MultiplicitySogsHash.joinLimbs(low, high);
                    recovered = true;
                }
            }
            int batchIndex = selectedIndexes[selectedIndex];
            int cellIndex = input.getCellIndexes().get(batchIndex);
            if (recovered && belongsToCell(candidate, input, cellIndex)) {
                results.set(batchIndex, MpSogsPeelResult.element(candidate));
            }
        }
    }

    private List<long[]> multiplyLayer(List<ProductInput> inputs, int vectorLength) {
        if (inputs.isEmpty()) {
            return new ArrayList<>();
        }
        long[] left = new long[Math.multiplyExact(inputs.size(), vectorLength)];
        long[] right = new long[left.length];
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            ProductInput input = inputs.get(inputIndex);
            if (input.left.length != vectorLength || input.right.length != vectorLength) {
                throw new IllegalArgumentException("invalid multiplication-layer vector length");
            }
            System.arraycopy(input.left, 0, left, inputIndex * vectorLength, vectorLength);
            System.arraycopy(input.right, 0, right, inputIndex * vectorLength, vectorLength);
        }
        long[] products = mpc.mul(left, right);
        List<long[]> output = new ArrayList<>(inputs.size());
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            output.add(slice(products, inputIndex * vectorLength, vectorLength));
        }
        return output;
    }

    private long publicCoefficient(BatchMpSogsPeelInput input, int repetition, int domainIndex) {
        long label = ((long) input.getRoundIndex() << 32)
            ^ ((long) input.getTier().ordinal() << 24)
            ^ ((long) repetition << 16)
            ^ (domainIndex + 2L);
        long coefficient = MultiplicitySogsHash.moments(label, sessionSeed, input.getTier())[repetition];
        return coefficient == 0L ? 1L : coefficient;
    }

    private boolean belongsToCell(long value, BatchMpSogsPeelInput input, int cellIndex) {
        for (int candidateCell : MpSogsHashUtils.cells(value, params, input.getTier())) {
            if (candidateCell == cellIndex) {
                return true;
            }
        }
        return false;
    }

    private static int[] selectedIndexes(long[] openedTests, int batchSize) {
        int selectedSize = 0;
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            boolean selected = true;
            for (int repetition = 0; repetition < ZERO_TEST_REPETITIONS; repetition++) {
                selected &= openedTests[repetition * batchSize + batchIndex] == 0L;
            }
            if (selected) {
                selectedSize++;
            }
        }
        int[] indexes = new int[selectedSize];
        int offset = 0;
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            boolean selected = true;
            for (int repetition = 0; repetition < ZERO_TEST_REPETITIONS; repetition++) {
                selected &= openedTests[repetition * batchSize + batchIndex] == 0L;
            }
            if (selected) {
                indexes[offset++] = batchIndex;
            }
        }
        return indexes;
    }

    private static List<MpSogsPeelResult> bottomResults(int size) {
        List<MpSogsPeelResult> results = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            results.add(MpSogsPeelResult.bottom());
        }
        return results;
    }

    private static long[] select(long[] source, int[] indexes) {
        long[] selected = new long[indexes.length];
        for (int index = 0; index < indexes.length; index++) {
            selected[index] = source[indexes[index]];
        }
        return selected;
    }

    private static long[] slice(long[] source, int offset, int length) {
        return Arrays.copyOfRange(source, offset, offset + length);
    }

    private static long[] concat(long[] first, long[] second) {
        long[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static long[] flatten(List<long[]> vectors, int vectorLength) {
        long[] result = new long[Math.multiplyExact(vectors.size(), vectorLength)];
        for (int vectorIndex = 0; vectorIndex < vectors.size(); vectorIndex++) {
            System.arraycopy(vectors.get(vectorIndex), 0, result, vectorIndex * vectorLength, vectorLength);
        }
        return result;
    }

    private static final class ProductInput {
        private final long[] left;
        private final long[] right;

        private ProductInput(long[] left, long[] right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class PredicateValues {
        private final long[] range;
        private final long[][] errors;

        private PredicateValues(long[] range, long[][] errors) {
            this.range = range;
            this.errors = errors;
        }
    }

    private static final class SharedCell {
        private final long[] count;
        private final long[][] firstMoments;
        private final long[][] secondMoments;
        private final long[] payloadLow;
        private final long[] payloadHigh;

        private SharedCell(long[] count, long[][] firstMoments, long[][] secondMoments,
                           long[] payloadLow, long[] payloadHigh) {
            this.count = count;
            this.firstMoments = firstMoments;
            this.secondMoments = secondMoments;
            this.payloadLow = payloadLow;
            this.payloadHigh = payloadHigh;
        }

        private static SharedCell fromEncoded(long[] encoded, int batchSize) {
            long[][] first = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
            long[][] second = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
            for (int domainIndex = 0; domainIndex < MultiplicitySogsHash.MOMENT_DOMAIN_NUM; domainIndex++) {
                first[domainIndex] = MultiplicityCellBatch.word(
                    encoded, MultiplicitySogsCell.A_OFFSET + domainIndex, batchSize
                );
                second[domainIndex] = MultiplicityCellBatch.word(
                    encoded, MultiplicitySogsCell.B_OFFSET + domainIndex, batchSize
                );
            }
            return new SharedCell(
                MultiplicityCellBatch.word(encoded, MultiplicitySogsCell.COUNT_OFFSET, batchSize),
                first,
                second,
                MultiplicityCellBatch.word(encoded, MultiplicitySogsCell.PAYLOAD_LOW_OFFSET, batchSize),
                MultiplicityCellBatch.word(encoded, MultiplicitySogsCell.PAYLOAD_HIGH_OFFSET, batchSize)
            );
        }
    }
}
