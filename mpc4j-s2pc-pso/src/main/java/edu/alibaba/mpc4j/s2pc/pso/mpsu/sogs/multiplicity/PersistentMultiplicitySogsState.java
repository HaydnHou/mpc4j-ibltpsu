package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsQuotientLabelCodec;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Persistent Shamir-shared aggregate multiplicity SOGS state.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class PersistentMultiplicitySogsState {
    private final Mersenne61ShamirMpc mpc;
    private final MpSogsMpsuParams params;
    private final long sessionSeed;
    private final MultiplicityPayloadEncoding payloadEncoding;
    private final int wordNum;
    private final boolean useUncheckedHotPath;
    private final Map<MpSogsTier, SharedTier> tiers;

    public PersistentMultiplicitySogsState(Mersenne61ShamirMpc mpc, MpSogsMpsuParams params, long sessionSeed) {
        this(mpc, params, sessionSeed, MultiplicityPayloadEncoding.FULL_LIMBS);
    }

    public PersistentMultiplicitySogsState(Mersenne61ShamirMpc mpc, MpSogsMpsuParams params, long sessionSeed,
                                           MultiplicityPayloadEncoding payloadEncoding) {
        this.mpc = mpc;
        this.params = params;
        this.sessionSeed = sessionSeed;
        this.payloadEncoding = payloadEncoding;
        payloadEncoding.validate(params);
        wordNum = payloadEncoding.getCellWordNum();
        useUncheckedHotPath = mpc.getWireFormat() == Mersenne61WireFormat.PACKED_61;
        tiers = new EnumMap<>(MpSogsTier.class);
        if (mpc.getPartyNum() != params.getPartyNum()) {
            throw new IllegalArgumentException("RPC party count does not match multiplicity SOGS parameters");
        }
    }

    /**
     * Shares one complete tier in bounded chunks and releases its plaintext Cell array.
     */
    public int initializeTier(MultiplicitySogsSketch localSketch, MpSogsTier tier, int maxBatchCells) {
        if (maxBatchCells <= 0) {
            throw new IllegalArgumentException("maxBatchCells must be positive");
        }
        if (tiers.containsKey(tier)) {
            throw new IllegalStateException(tier + " persistent tier is already initialized");
        }
        if (localSketch.getParams() != params && !localSketch.getParams().equals(params)) {
            throw new IllegalArgumentException("local sketch parameters do not match persistent state");
        }
        if (localSketch.getSessionSeed() != sessionSeed) {
            throw new IllegalArgumentException("local sketch session seed does not match persistent state");
        }
        if (localSketch.getPayloadEncoding() != payloadEncoding) {
            throw new IllegalArgumentException("local sketch payload encoding does not match persistent state");
        }
        mpc.resetNetworkRoundCount();
        int cellNum = params.getCellNum(tier);
        SharedTier sharedTier = new SharedTier(wordNum, cellNum);
        for (int fromCell = 0; fromCell < cellNum; fromCell += maxBatchCells) {
            int toCell = Math.min(cellNum, fromCell + maxBatchCells);
            long[] localWords = MultiplicityCellBatch.encode(localSketch, tier, fromCell, toCell);
            long[] aggregateShares = mpc.shareOwnAndAggregate(localWords);
            sharedTier.install(fromCell, toCell, aggregateShares);
        }
        tiers.put(tier, sharedTier);
        localSketch.releaseTier(tier);
        return mpc.getNetworkRoundCount();
    }

    public boolean isInitialized(MpSogsTier tier) {
        return tiers.containsKey(tier);
    }

    /**
     * Gathers persistent shares in the same word-major layout used by the ephemeral Cell codec.
     */
    public long[] gather(BatchMpSogsPeelInput input) {
        SharedTier tier = tier(input.getTier());
        int batchSize = input.size();
        long[] encoded = new long[Math.multiplyExact(batchSize, wordNum)];
        for (int wordIndex = 0; wordIndex < wordNum; wordIndex++) {
            for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
                int cellIndex = input.getCellIndexes().get(batchIndex);
                tier.checkCellIndex(cellIndex);
                encoded[MultiplicityCellBatch.offset(wordIndex, batchIndex, batchSize)] =
                    tier.words[wordIndex][cellIndex];
            }
        }
        return encoded;
    }

    /**
     * Gathers persistent shares directly into word-major vectors without an intermediate flat Cell array.
     */
    long[][] gatherWords(BatchMpSogsPeelInput input) {
        SharedTier tier = tier(input.getTier());
        int batchSize = input.size();
        long[][] words = new long[wordNum][batchSize];
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            int cellIndex = input.getCellIndexes().get(batchIndex);
            tier.checkCellIndex(cellIndex);
            for (int wordIndex = 0; wordIndex < wordNum; wordIndex++) {
                words[wordIndex][batchIndex] = tier.words[wordIndex][cellIndex];
            }
        }
        return words;
    }

    /**
     * Deletes the hidden multiplicity of one public union element from all of its public neighboring Cells.
     */
    public void delete(MpSogsTier tier, long value, long countShare) {
        if (!Mersenne61Field.isElement(countShare)) {
            throw new IllegalArgumentException("count share is outside the Mersenne-61 field");
        }
        SharedTier sharedTier = tier(tier);
        int[] cells = MpSogsHashUtils.cells(value, params, tier);
        long[] moments = MultiplicitySogsHash.moments(value, sessionSeed, tier);
        long[] momentSquares = new long[moments.length];
        for (int domainIndex = 0; domainIndex < moments.length; domainIndex++) {
            momentSquares[domainIndex] = mul(moments[domainIndex], moments[domainIndex]);
        }
        long low = MultiplicitySogsHash.lowLimb(value);
        long high = MultiplicitySogsHash.highLimb(value);
        for (int cellIndex : cells) {
            subtractScaled(sharedTier, MultiplicitySogsCell.COUNT_OFFSET, cellIndex, countShare, 1L);
            for (int domainIndex = 0; domainIndex < moments.length; domainIndex++) {
                subtractScaled(
                    sharedTier, MultiplicitySogsCell.A_OFFSET + domainIndex, cellIndex,
                    countShare, moments[domainIndex]
                );
                subtractScaled(
                    sharedTier, MultiplicitySogsCell.B_OFFSET + domainIndex, cellIndex,
                    countShare, momentSquares[domainIndex]
                );
            }
            if (payloadEncoding == MultiplicityPayloadEncoding.EXACT_QUOTIENT) {
                subtractScaled(
                    sharedTier, MultiplicitySogsCell.PAYLOAD_QUOTIENT_OFFSET, cellIndex, countShare,
                    MpSogsQuotientLabelCodec.encode(value, params, tier, cellIndex)
                );
            } else {
                subtractScaled(
                    sharedTier, MultiplicitySogsCell.PAYLOAD_LOW_OFFSET, cellIndex, countShare, low
                );
                subtractScaled(
                    sharedTier, MultiplicitySogsCell.PAYLOAD_HIGH_OFFSET, cellIndex, countShare, high
                );
            }
        }
    }

    long[] getCellShareWords(MpSogsTier tier, int cellIndex) {
        SharedTier sharedTier = tier(tier);
        sharedTier.checkCellIndex(cellIndex);
        long[] result = new long[wordNum];
        for (int wordIndex = 0; wordIndex < result.length; wordIndex++) {
            result[wordIndex] = sharedTier.words[wordIndex][cellIndex];
        }
        return result;
    }

    public MultiplicityPayloadEncoding getPayloadEncoding() {
        return payloadEncoding;
    }

    public int getWordNum() {
        return wordNum;
    }

    private void subtractScaled(SharedTier tier, int wordIndex, int cellIndex, long countShare,
                                long contribution) {
        tier.words[wordIndex][cellIndex] = sub(
            tier.words[wordIndex][cellIndex], mul(countShare, contribution)
        );
    }

    private long mul(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.mulUnchecked(left, right)
            : Mersenne61Field.mul(left, right);
    }

    private long sub(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.subUnchecked(left, right)
            : Mersenne61Field.sub(left, right);
    }

    private SharedTier tier(MpSogsTier tier) {
        SharedTier result = tiers.get(tier);
        if (result == null) {
            throw new IllegalStateException(tier + " persistent tier is not initialized");
        }
        return result;
    }

    private static final class SharedTier {
        private final long[][] words;

        private SharedTier(int wordNum, int cellNum) {
            words = new long[wordNum][cellNum];
        }

        private void install(int fromCell, int toCell, long[] encoded) {
            int batchSize = toCell - fromCell;
            if (encoded.length != Math.multiplyExact(batchSize, words.length)) {
                throw new IllegalArgumentException("invalid persistent multiplicity tier payload length");
            }
            for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
                System.arraycopy(encoded, wordIndex * batchSize, words[wordIndex], fromCell, batchSize);
            }
        }

        private void checkCellIndex(int cellIndex) {
            if (cellIndex < 0 || cellIndex >= words[0].length) {
                throw new IndexOutOfBoundsException("invalid persistent Cell index: " + cellIndex);
            }
        }
    }
}
