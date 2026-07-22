package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsQuotientLabelCodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Moment-uPeel over a persistent Shamir-shared multiplicity SOGS state.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class PersistentShamirMultiplicitySecureUnionPeel {
    private static final int ZERO_TEST_REPETITIONS = 3;
    private static final int RECOVERY_MASK_NUM = 3;

    private final Mersenne61ShamirMpc mpc;
    private final PersistentMultiplicitySogsState state;
    private final MpSogsMpsuParams params;
    private final long sessionSeed;
    private final MultiplicityPayloadEncoding payloadEncoding;
    private final ShamirRandomnessProvider randomnessProvider;
    private final boolean useDoubleShare;
    private final boolean useTerminalFusion;
    private final boolean useHotPath;
    private final SsmOpeningMode openingMode;
    private final double networkRttMillis;
    private final double networkBandwidthMbps;
    private long batchCounter;

    public PersistentShamirMultiplicitySecureUnionPeel(Mersenne61ShamirMpc mpc,
                                                        PersistentMultiplicitySogsState state,
                                                        MpSogsMpsuParams params, long sessionSeed) {
        this(
            mpc, state, params, sessionSeed, new NetworkShamirRandomnessProvider(mpc), false, false,
            SsmOpeningMode.BALANCED_TWO_PHASE, 0.0, 0.0, false
        );
    }

    public PersistentShamirMultiplicitySecureUnionPeel(Mersenne61ShamirMpc mpc,
                                                        PersistentMultiplicitySogsState state,
                                                        MpSogsMpsuParams params, long sessionSeed,
                                                        ShamirRandomnessProvider randomnessProvider) {
        this(
            mpc, state, params, sessionSeed, randomnessProvider, false, false,
            SsmOpeningMode.BALANCED_TWO_PHASE, 0.0, 0.0, false
        );
    }

    public PersistentShamirMultiplicitySecureUnionPeel(Mersenne61ShamirMpc mpc,
                                                        PersistentMultiplicitySogsState state,
                                                        MpSogsMpsuParams params, long sessionSeed,
                                                        ShamirRandomnessProvider randomnessProvider,
                                                        boolean useDoubleShare) {
        this(
            mpc, state, params, sessionSeed, randomnessProvider, useDoubleShare, false,
            SsmOpeningMode.BALANCED_TWO_PHASE, 0.0, 0.0, false
        );
    }

    public PersistentShamirMultiplicitySecureUnionPeel(Mersenne61ShamirMpc mpc,
                                                        PersistentMultiplicitySogsState state,
                                                        MpSogsMpsuParams params, long sessionSeed,
                                                        ShamirRandomnessProvider randomnessProvider,
                                                        boolean useDoubleShare, boolean useTerminalFusion,
                                                        SsmOpeningMode openingMode, double networkRttMillis,
                                                        double networkBandwidthMbps) {
        this(
            mpc, state, params, sessionSeed, randomnessProvider, useDoubleShare, useTerminalFusion,
            openingMode, networkRttMillis, networkBandwidthMbps, false
        );
    }

    public PersistentShamirMultiplicitySecureUnionPeel(Mersenne61ShamirMpc mpc,
                                                        PersistentMultiplicitySogsState state,
                                                        MpSogsMpsuParams params, long sessionSeed,
                                                        ShamirRandomnessProvider randomnessProvider,
                                                        boolean useDoubleShare, boolean useTerminalFusion,
                                                        SsmOpeningMode openingMode, double networkRttMillis,
                                                        double networkBandwidthMbps, boolean useHotPath) {
        this.mpc = mpc;
        this.state = state;
        this.params = params;
        this.sessionSeed = sessionSeed;
        this.randomnessProvider = randomnessProvider;
        this.useDoubleShare = useDoubleShare;
        this.useTerminalFusion = useTerminalFusion;
        this.useHotPath = useHotPath;
        this.openingMode = openingMode;
        this.networkRttMillis = networkRttMillis;
        this.networkBandwidthMbps = networkBandwidthMbps;
        payloadEncoding = state.getPayloadEncoding();
        if (mpc.getPartyNum() != params.getPartyNum()) {
            throw new IllegalArgumentException("RPC party count does not match multiplicity SOGS parameters");
        }
    }

    public PersistentMultiplicityPeelBatch peelBatch(BatchMpSogsPeelInput input) {
        if (input.size() == 0) {
            return new PersistentMultiplicityPeelBatch(List.of(), new long[0], 0);
        }
        if (!state.isInitialized(input.getTier())) {
            throw new IllegalStateException(input.getTier() + " persistent tier is not initialized");
        }
        mpc.resetNetworkRoundCount();
        int batchSize = input.size();
        long batchId = batchCounter++;
        MultiplicationContext multiplicationContext = new MultiplicationContext(input, batchId);
        long[] zeroMasks = randomnessProvider.randomDegreeT(new ShamirRandomnessDomain(
            input.getTier(), ShamirRandomnessDomain.Phase.PREDICATE, input.getRoundIndex(), batchId,
            0L, 0, 0, ZERO_TEST_REPETITIONS * batchSize
        ));

        SharedCell cell = useHotPath
            ? SharedCell.fromWords(state.gatherWords(input), batchSize, payloadEncoding)
            : SharedCell.fromEncoded(state.gather(input), batchSize, payloadEncoding);
        PredicateValues predicate = useHotPath
            ? evaluatePredicateHot(cell, batchSize, multiplicationContext)
            : evaluatePredicate(cell, batchSize, multiplicationContext);
        long[] maskedTests = new long[ZERO_TEST_REPETITIONS * batchSize];
        if (useHotPath) {
            for (int repetition = 0; repetition < ZERO_TEST_REPETITIONS; repetition++) {
                compressPredicateInto(
                    maskedTests, repetition * batchSize, predicate, input, repetition, batchSize
                );
            }
        } else {
            for (int repetition = 0; repetition < ZERO_TEST_REPETITIONS; repetition++) {
                long[] compressed = compressPredicate(predicate, input, repetition);
                System.arraycopy(compressed, 0, maskedTests, repetition * batchSize, batchSize);
            }
        }
        long[] openedTests = useTerminalFusion
            ? openTerminalProduct(zeroMasks, maskedTests, multiplicationContext)
            : openVector(multiplyVector(zeroMasks, maskedTests, multiplicationContext));
        int[] selectedIndexes = selectedIndexes(openedTests, batchSize);
        List<MpSogsPeelResult> results = bottomResults(batchSize);
        long[] countShares = new long[batchSize];
        if (selectedIndexes.length > 0) {
            recoverCandidates(
                results, countShares, selectedIndexes, cell, input, batchId, multiplicationContext
            );
        }
        return new PersistentMultiplicityPeelBatch(results, countShares, mpc.getNetworkRoundCount());
    }

    private PredicateValues evaluatePredicate(SharedCell cell, int batchSize,
                                              MultiplicationContext multiplicationContext) {
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
        List<long[]> firstProducts = multiplyLayer(firstLayer, batchSize, multiplicationContext);
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
            List<long[]> nextRange = multiplyLayer(products, batchSize, multiplicationContext);
            if ((currentRange.size() & 1) != 0) {
                nextRange.add(currentRange.get(currentRange.size() - 1));
            }
            currentRange = nextRange;
        }
        return new PredicateValues(currentRange.get(0), errors);
    }

    private PredicateValues evaluatePredicateHot(SharedCell cell, int batchSize,
                                                  MultiplicationContext multiplicationContext) {
        int momentProductNum = 2 * MultiplicitySogsHash.MOMENT_DOMAIN_NUM;
        int rangePairNum = params.getPartyNum() / 2;
        int productNum = momentProductNum + rangePairNum;
        long[][] leftVectors = new long[productNum][];
        long[][] rightVectors = new long[productNum][];
        for (int domainIndex = 0; domainIndex < MultiplicitySogsHash.MOMENT_DOMAIN_NUM; domainIndex++) {
            int offset = 2 * domainIndex;
            leftVectors[offset] = cell.count;
            rightVectors[offset] = cell.secondMoments[domainIndex];
            leftVectors[offset + 1] = cell.firstMoments[domainIndex];
            rightVectors[offset + 1] = cell.firstMoments[domainIndex];
        }
        long[][] currentRange = new long[(params.getPartyNum() + 1) / 2][];
        for (int pairIndex = 0; pairIndex < rangePairNum; pairIndex++) {
            int productIndex = momentProductNum + pairIndex;
            leftVectors[productIndex] = subPublicHot(cell.count, 2L * pairIndex + 1L);
            rightVectors[productIndex] = subPublicHot(cell.count, 2L * pairIndex + 2L);
        }
        if ((params.getPartyNum() & 1) != 0) {
            currentRange[currentRange.length - 1] = subPublicHot(cell.count, params.getPartyNum());
        }
        long[] products = multiplyFlatLayer(leftVectors, rightVectors, batchSize, multiplicationContext);
        long[][] errors = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
        for (int domainIndex = 0; domainIndex < errors.length; domainIndex++) {
            errors[domainIndex] = subSegmentsHot(
                products, 2 * domainIndex * batchSize, (2 * domainIndex + 1) * batchSize, batchSize
            );
        }
        for (int pairIndex = 0; pairIndex < rangePairNum; pairIndex++) {
            currentRange[pairIndex] = copySegment(products, (momentProductNum + pairIndex) * batchSize, batchSize);
        }
        while (currentRange.length > 1) {
            int pairNum = currentRange.length / 2;
            long[][] rangeLeft = new long[pairNum][];
            long[][] rangeRight = new long[pairNum][];
            for (int pairIndex = 0; pairIndex < pairNum; pairIndex++) {
                rangeLeft[pairIndex] = currentRange[2 * pairIndex];
                rangeRight[pairIndex] = currentRange[2 * pairIndex + 1];
            }
            long[] nextProducts = multiplyFlatLayer(
                rangeLeft, rangeRight, batchSize, multiplicationContext
            );
            long[][] nextRange = new long[(currentRange.length + 1) / 2][];
            for (int pairIndex = 0; pairIndex < pairNum; pairIndex++) {
                nextRange[pairIndex] = copySegment(nextProducts, pairIndex * batchSize, batchSize);
            }
            if ((currentRange.length & 1) != 0) {
                nextRange[nextRange.length - 1] = currentRange[currentRange.length - 1];
            }
            currentRange = nextRange;
        }
        return new PredicateValues(currentRange[0], errors);
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

    private void compressPredicateInto(long[] output, int outputOffset, PredicateValues predicate,
                                       BatchMpSogsPeelInput input, int repetition, int batchSize) {
        long gamma = publicCoefficient(input, repetition, -1);
        long[] betas = new long[predicate.errors.length];
        for (int domainIndex = 0; domainIndex < betas.length; domainIndex++) {
            betas[domainIndex] = publicCoefficient(input, repetition, domainIndex);
        }
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            long compressed = Mersenne61Field.mulUnchecked(predicate.range[batchIndex], gamma);
            for (int domainIndex = 0; domainIndex < predicate.errors.length; domainIndex++) {
                compressed = Mersenne61Field.addUnchecked(
                    compressed,
                    Mersenne61Field.mulUnchecked(predicate.errors[domainIndex][batchIndex], betas[domainIndex])
                );
            }
            output[outputOffset + batchIndex] = compressed;
        }
    }

    private void recoverCandidates(List<MpSogsPeelResult> results, long[] countShares, int[] selectedIndexes,
                                   SharedCell cell, BatchMpSogsPeelInput input, long batchId,
                                   MultiplicationContext multiplicationContext) {
        if (payloadEncoding == MultiplicityPayloadEncoding.EXACT_QUOTIENT) {
            recoverQuotientCandidates(
                results, countShares, selectedIndexes, cell, input, batchId, multiplicationContext
            );
        } else {
            recoverFullCandidates(
                results, countShares, selectedIndexes, cell, input, batchId, multiplicationContext
            );
        }
    }

    private void recoverFullCandidates(List<MpSogsPeelResult> results, long[] countShares, int[] selectedIndexes,
                                       SharedCell cell, BatchMpSogsPeelInput input, long batchId,
                                       MultiplicationContext multiplicationContext) {
        int selectedSize = selectedIndexes.length;
        long[] selectedCount = select(cell.count, selectedIndexes);
        long[] selectedLow = select(cell.payload0, selectedIndexes);
        long[] selectedHigh = select(cell.payload1, selectedIndexes);
        long[] recoveryMasks = recoveryMasks(input, batchId, selectedSize);
        List<ProductInput> products = new ArrayList<>();
        for (int repetition = 0; repetition < RECOVERY_MASK_NUM; repetition++) {
            long[] mask = slice(recoveryMasks, repetition * selectedSize, selectedSize);
            products.add(new ProductInput(mask, selectedCount));
            products.add(new ProductInput(mask, selectedLow));
            products.add(new ProductInput(mask, selectedHigh));
        }
        long[] opened = useTerminalFusion
            ? openTerminalLayer(products, selectedSize, multiplicationContext)
            : openVector(flatten(multiplyLayer(products, selectedSize, multiplicationContext), selectedSize));
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
                countShares[batchIndex] = cell.count[batchIndex];
            }
        }
    }

    private void recoverQuotientCandidates(List<MpSogsPeelResult> results, long[] countShares,
                                           int[] selectedIndexes, SharedCell cell, BatchMpSogsPeelInput input,
                                           long batchId, MultiplicationContext multiplicationContext) {
        int selectedSize = selectedIndexes.length;
        long[] selectedCount = select(cell.count, selectedIndexes);
        long[] selectedQuotient = select(cell.payload0, selectedIndexes);
        long[] recoveryMasks = recoveryMasks(input, batchId, selectedSize);
        long[] opened;
        if (useHotPath) {
            opened = openRepeatedRecoveryProducts(
                recoveryMasks, selectedSize, new long[][]{selectedCount, selectedQuotient}, multiplicationContext
            );
        } else {
            List<ProductInput> products = new ArrayList<>();
            for (int repetition = 0; repetition < RECOVERY_MASK_NUM; repetition++) {
                long[] mask = slice(recoveryMasks, repetition * selectedSize, selectedSize);
                products.add(new ProductInput(mask, selectedCount));
                products.add(new ProductInput(mask, selectedQuotient));
            }
            opened = useTerminalFusion
                ? openTerminalLayer(products, selectedSize, multiplicationContext)
                : openVector(flatten(multiplyLayer(products, selectedSize, multiplicationContext), selectedSize));
        }
        for (int selectedIndex = 0; selectedIndex < selectedSize; selectedIndex++) {
            long candidate = 0L;
            boolean recovered = false;
            int batchIndex = selectedIndexes[selectedIndex];
            int cellIndex = input.getCellIndexes().get(batchIndex);
            for (int repetition = 0; repetition < RECOVERY_MASK_NUM && !recovered; repetition++) {
                int base = repetition * 2 * selectedSize;
                long denominator = opened[base + selectedIndex];
                if (denominator == 0L) {
                    continue;
                }
                long quotient = Mersenne61Field.mul(
                    opened[base + selectedSize + selectedIndex], Mersenne61Field.inv(denominator)
                );
                try {
                    candidate = MpSogsQuotientLabelCodec.decode(
                        quotient, params, input.getTier(), cellIndex
                    );
                    recovered = MpSogsQuotientLabelCodec.encode(
                        candidate, params, input.getTier(), cellIndex
                    ) == quotient && belongsToCell(candidate, input, cellIndex);
                } catch (IllegalArgumentException ignored) {
                    recovered = false;
                }
            }
            if (recovered) {
                results.set(batchIndex, MpSogsPeelResult.element(candidate));
                countShares[batchIndex] = cell.count[batchIndex];
            }
        }
    }

    private long[] recoveryMasks(BatchMpSogsPeelInput input, long batchId, int selectedSize) {
        return randomnessProvider.randomDegreeT(new ShamirRandomnessDomain(
            input.getTier(), ShamirRandomnessDomain.Phase.RECOVERY, input.getRoundIndex(), batchId,
            0L, 0, 0, RECOVERY_MASK_NUM * selectedSize
        ));
    }

    private List<long[]> multiplyLayer(List<ProductInput> inputs, int vectorLength,
                                       MultiplicationContext multiplicationContext) {
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
        long[] products = multiplyVector(left, right, multiplicationContext);
        List<long[]> output = new ArrayList<>(inputs.size());
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            output.add(slice(products, inputIndex * vectorLength, vectorLength));
        }
        return output;
    }

    private long[] multiplyFlatLayer(long[][] leftVectors, long[][] rightVectors, int vectorLength,
                                     MultiplicationContext multiplicationContext) {
        if (leftVectors.length != rightVectors.length) {
            throw new IllegalArgumentException("flat multiplication-layer vector count mismatch");
        }
        long[] left = new long[Math.multiplyExact(leftVectors.length, vectorLength)];
        long[] right = new long[left.length];
        for (int vectorIndex = 0; vectorIndex < leftVectors.length; vectorIndex++) {
            if (leftVectors[vectorIndex].length != vectorLength || rightVectors[vectorIndex].length != vectorLength) {
                throw new IllegalArgumentException("invalid flat multiplication-layer vector length");
            }
            System.arraycopy(leftVectors[vectorIndex], 0, left, vectorIndex * vectorLength, vectorLength);
            System.arraycopy(rightVectors[vectorIndex], 0, right, vectorIndex * vectorLength, vectorLength);
        }
        return multiplyVector(left, right, multiplicationContext);
    }

    private long[] openRepeatedRecoveryProducts(long[] masks, int vectorLength, long[][] rightVectors,
                                                MultiplicationContext context) {
        int productNum = Math.multiplyExact(RECOVERY_MASK_NUM, rightVectors.length);
        long[] left = new long[Math.multiplyExact(productNum, vectorLength)];
        long[] right = new long[left.length];
        int productIndex = 0;
        for (int repetition = 0; repetition < RECOVERY_MASK_NUM; repetition++) {
            int maskOffset = repetition * vectorLength;
            for (long[] rightVector : rightVectors) {
                if (rightVector.length != vectorLength) {
                    throw new IllegalArgumentException("invalid recovery vector length");
                }
                int productOffset = productIndex++ * vectorLength;
                System.arraycopy(masks, maskOffset, left, productOffset, vectorLength);
                System.arraycopy(rightVector, 0, right, productOffset, vectorLength);
            }
        }
        return openTerminalProduct(left, right, context);
    }

    private long[] multiplyVector(long[] left, long[] right, MultiplicationContext context) {
        if (!useDoubleShare) {
            return mpc.mul(left, right);
        }
        ShamirRandomnessDomain domain = new ShamirRandomnessDomain(
            context.input.getTier(), ShamirRandomnessDomain.Phase.DEGREE_REDUCTION,
            context.input.getRoundIndex(), context.batchId, context.nextOperationId++, 0, 0, left.length
        );
        return mpc.mulWithDoubleShare(
            left, right, randomnessProvider.randomDoubleShare(domain), openingMode,
            networkRttMillis, networkBandwidthMbps
        );
    }

    private long[] openVector(long[] shares) {
        return useDoubleShare
            ? mpc.openRttAware(shares, openingMode, networkRttMillis, networkBandwidthMbps)
            : mpc.open(shares);
    }

    private long[] openTerminalLayer(List<ProductInput> inputs, int vectorLength,
                                     MultiplicationContext context) {
        long[] left = new long[Math.multiplyExact(inputs.size(), vectorLength)];
        long[] right = new long[left.length];
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            ProductInput input = inputs.get(inputIndex);
            if (input.left.length != vectorLength || input.right.length != vectorLength) {
                throw new IllegalArgumentException("invalid terminal multiplication-layer vector length");
            }
            System.arraycopy(input.left, 0, left, inputIndex * vectorLength, vectorLength);
            System.arraycopy(input.right, 0, right, inputIndex * vectorLength, vectorLength);
        }
        return openTerminalProduct(left, right, context);
    }

    private long[] openTerminalProduct(long[] left, long[] right, MultiplicationContext context) {
        ShamirRandomnessDomain domain = new ShamirRandomnessDomain(
            context.input.getTier(), ShamirRandomnessDomain.Phase.TERMINAL_ZERO,
            context.input.getRoundIndex(), context.batchId, context.nextOperationId++, 0, 0, left.length
        );
        return mpc.openTerminalProduct(
            left, right, randomnessProvider.randomDegree2TZero(domain), openingMode,
            networkRttMillis, networkBandwidthMbps
        );
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

    private static long[] subPublicHot(long[] shares, long value) {
        long[] result = new long[shares.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = Mersenne61Field.subUnchecked(shares[index], value);
        }
        return result;
    }

    private static long[] subSegmentsHot(long[] source, int leftOffset, int rightOffset, int length) {
        long[] result = new long[length];
        for (int index = 0; index < length; index++) {
            result[index] = Mersenne61Field.subUnchecked(
                source[leftOffset + index], source[rightOffset + index]
            );
        }
        return result;
    }

    private static long[] copySegment(long[] source, int offset, int length) {
        long[] result = new long[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }

    private static long[] slice(long[] source, int offset, int length) {
        return Arrays.copyOfRange(source, offset, offset + length);
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

    private static final class MultiplicationContext {
        private final BatchMpSogsPeelInput input;
        private final long batchId;
        private long nextOperationId;

        private MultiplicationContext(BatchMpSogsPeelInput input, long batchId) {
            this.input = input;
            this.batchId = batchId;
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
        private final long[] payload0;
        private final long[] payload1;

        private SharedCell(long[] count, long[][] firstMoments, long[][] secondMoments,
                           long[] payload0, long[] payload1) {
            this.count = count;
            this.firstMoments = firstMoments;
            this.secondMoments = secondMoments;
            this.payload0 = payload0;
            this.payload1 = payload1;
        }

        private static SharedCell fromEncoded(long[] encoded, int batchSize,
                                              MultiplicityPayloadEncoding payloadEncoding) {
            int wordNum = payloadEncoding.getCellWordNum();
            long[][] first = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
            long[][] second = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
            for (int domainIndex = 0; domainIndex < MultiplicitySogsHash.MOMENT_DOMAIN_NUM; domainIndex++) {
                first[domainIndex] = MultiplicityCellBatch.word(
                    encoded, MultiplicitySogsCell.A_OFFSET + domainIndex, batchSize, wordNum
                );
                second[domainIndex] = MultiplicityCellBatch.word(
                    encoded, MultiplicitySogsCell.B_OFFSET + domainIndex, batchSize, wordNum
                );
            }
            return new SharedCell(
                MultiplicityCellBatch.word(encoded, MultiplicitySogsCell.COUNT_OFFSET, batchSize, wordNum),
                first,
                second,
                MultiplicityCellBatch.word(encoded, MultiplicitySogsCell.PAYLOAD_LOW_OFFSET, batchSize, wordNum),
                payloadEncoding == MultiplicityPayloadEncoding.FULL_LIMBS
                    ? MultiplicityCellBatch.word(encoded, MultiplicitySogsCell.PAYLOAD_HIGH_OFFSET, batchSize, wordNum)
                    : null
            );
        }

        private static SharedCell fromWords(long[][] words, int batchSize,
                                            MultiplicityPayloadEncoding payloadEncoding) {
            int wordNum = payloadEncoding.getCellWordNum();
            if (words.length != wordNum) {
                throw new IllegalArgumentException("invalid word-major shared Cell width");
            }
            for (long[] word : words) {
                if (word.length != batchSize) {
                    throw new IllegalArgumentException("invalid word-major shared Cell batch length");
                }
            }
            long[][] first = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
            long[][] second = new long[MultiplicitySogsHash.MOMENT_DOMAIN_NUM][];
            for (int domainIndex = 0; domainIndex < MultiplicitySogsHash.MOMENT_DOMAIN_NUM; domainIndex++) {
                first[domainIndex] = words[MultiplicitySogsCell.A_OFFSET + domainIndex];
                second[domainIndex] = words[MultiplicitySogsCell.B_OFFSET + domainIndex];
            }
            return new SharedCell(
                words[MultiplicitySogsCell.COUNT_OFFSET], first, second,
                words[MultiplicitySogsCell.PAYLOAD_LOW_OFFSET],
                payloadEncoding == MultiplicityPayloadEncoding.FULL_LIMBS
                    ? words[MultiplicitySogsCell.PAYLOAD_HIGH_OFFSET]
                    : null
            );
        }
    }
}
