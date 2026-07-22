package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsQuotientLabelCodec;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

import java.util.Arrays;

/**
 * Party-local secret-shared-multiplicity SOGS cell.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class MultiplicitySogsCell {
    public static final int COUNT_OFFSET = 0;
    public static final int A_OFFSET = 1;
    public static final int B_OFFSET = A_OFFSET + MultiplicitySogsHash.MOMENT_DOMAIN_NUM;
    public static final int BASE_WORD_NUM = B_OFFSET + MultiplicitySogsHash.MOMENT_DOMAIN_NUM;
    public static final int PAYLOAD_LOW_OFFSET = BASE_WORD_NUM;
    public static final int PAYLOAD_HIGH_OFFSET = PAYLOAD_LOW_OFFSET + 1;
    public static final int PAYLOAD_QUOTIENT_OFFSET = BASE_WORD_NUM;
    public static final int WORD_NUM = PAYLOAD_HIGH_OFFSET + 1;

    private final MultiplicityPayloadEncoding payloadEncoding;
    private final MpSogsMpsuParams params;
    private final MpSogsTier fixedTier;
    private final int cellIndex;
    private final long[] words;

    public MultiplicitySogsCell() {
        this(null, null, -1, MultiplicityPayloadEncoding.FULL_LIMBS);
    }

    MultiplicitySogsCell(MpSogsMpsuParams params, MpSogsTier tier, int cellIndex,
                         MultiplicityPayloadEncoding payloadEncoding) {
        this.params = params;
        fixedTier = tier;
        this.cellIndex = cellIndex;
        this.payloadEncoding = payloadEncoding;
        words = new long[payloadEncoding.getCellWordNum()];
    }

    public void add(long value, long sessionSeed, MpSogsTier tier) {
        update(value, sessionSeed, tier, true);
    }

    public void remove(long value, long sessionSeed, MpSogsTier tier) {
        update(value, sessionSeed, tier, false);
    }

    public long getWord(int wordIndex) {
        return words[wordIndex];
    }

    public long[] toArray() {
        return Arrays.copyOf(words, words.length);
    }

    public boolean isZero() {
        for (long word : words) {
            if (word != 0L) {
                return false;
            }
        }
        return true;
    }

    private void update(long value, long sessionSeed, MpSogsTier tier, boolean add) {
        long[] moments = MultiplicitySogsHash.moments(value, sessionSeed, tier);
        apply(COUNT_OFFSET, 1L, add);
        for (int domainIndex = 0; domainIndex < moments.length; domainIndex++) {
            long moment = moments[domainIndex];
            apply(A_OFFSET + domainIndex, moment, add);
            apply(B_OFFSET + domainIndex, Mersenne61Field.square(moment), add);
        }
        if (payloadEncoding == MultiplicityPayloadEncoding.EXACT_QUOTIENT) {
            if (params == null || fixedTier != tier || cellIndex < 0) {
                throw new IllegalStateException("exact quotient Cell is missing its public coordinate");
            }
            apply(PAYLOAD_QUOTIENT_OFFSET, MpSogsQuotientLabelCodec.encode(value, params, tier, cellIndex), add);
        } else {
            apply(PAYLOAD_LOW_OFFSET, MultiplicitySogsHash.lowLimb(value), add);
            apply(PAYLOAD_HIGH_OFFSET, MultiplicitySogsHash.highLimb(value), add);
        }
    }

    private void apply(int offset, long contribution, boolean add) {
        words[offset] = add
            ? Mersenne61Field.add(words[offset], contribution)
            : Mersenne61Field.sub(words[offset], contribution);
    }
}
