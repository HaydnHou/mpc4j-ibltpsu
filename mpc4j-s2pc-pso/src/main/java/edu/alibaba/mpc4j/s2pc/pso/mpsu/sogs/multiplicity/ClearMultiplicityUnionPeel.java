package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;

import java.util.ArrayList;
import java.util.List;

/**
 * Clear oracle for multiplicity SOGS union peeling. This class is not secure.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class ClearMultiplicityUnionPeel implements SecureMpSogsUnionPeel {
    private final List<MultiplicitySogsSketch> sketches;
    private final MpSogsMpsuParams params;

    public ClearMultiplicityUnionPeel(List<MultiplicitySogsSketch> sketches, MpSogsMpsuParams params) {
        if (sketches.size() != params.getPartyNum()) {
            throw new IllegalArgumentException("sketch count must equal party count");
        }
        this.sketches = new ArrayList<>(sketches);
        this.params = params;
    }

    @Override
    public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
        List<MpSogsPeelResult> results = new ArrayList<>(input.size());
        for (int cellIndex : input.getCellIndexes()) {
            long[] aggregate = aggregate(input, cellIndex);
            results.add(decode(aggregate, input, cellIndex));
        }
        return new BatchMpSogsPeelOutput(results, 0L, 0L, 0);
    }

    public static boolean isSingleDistinct(long[] aggregate, int partyNum) {
        long count = aggregate[MultiplicitySogsCell.COUNT_OFFSET];
        long range = 1L;
        for (int multiplicity = 1; multiplicity <= partyNum; multiplicity++) {
            range = Mersenne61Field.mul(range, Mersenne61Field.sub(count, multiplicity));
        }
        if (range != 0L) {
            return false;
        }
        for (int domainIndex = 0; domainIndex < MultiplicitySogsHash.MOMENT_DOMAIN_NUM; domainIndex++) {
            long left = Mersenne61Field.mul(
                count, aggregate[MultiplicitySogsCell.B_OFFSET + domainIndex]
            );
            long right = Mersenne61Field.square(aggregate[MultiplicitySogsCell.A_OFFSET + domainIndex]);
            if (left != right) {
                return false;
            }
        }
        return true;
    }

    private long[] aggregate(BatchMpSogsPeelInput input, int cellIndex) {
        long[] aggregate = new long[MultiplicitySogsCell.WORD_NUM];
        for (MultiplicitySogsSketch sketch : sketches) {
            long[] words = sketch.getCellWords(input.getTier(), cellIndex);
            for (int wordIndex = 0; wordIndex < aggregate.length; wordIndex++) {
                aggregate[wordIndex] = Mersenne61Field.add(aggregate[wordIndex], words[wordIndex]);
            }
        }
        return aggregate;
    }

    private MpSogsPeelResult decode(long[] aggregate, BatchMpSogsPeelInput input, int cellIndex) {
        if (!isSingleDistinct(aggregate, params.getPartyNum())) {
            return MpSogsPeelResult.bottom();
        }
        long count = aggregate[MultiplicitySogsCell.COUNT_OFFSET];
        long inverseCount = Mersenne61Field.inv(count);
        long low = Mersenne61Field.mul(aggregate[MultiplicitySogsCell.PAYLOAD_LOW_OFFSET], inverseCount);
        long high = Mersenne61Field.mul(aggregate[MultiplicitySogsCell.PAYLOAD_HIGH_OFFSET], inverseCount);
        if ((low >>> Integer.SIZE) != 0L || (high >>> Integer.SIZE) != 0L) {
            return MpSogsPeelResult.bottom();
        }
        long value = MultiplicitySogsHash.joinLimbs(low, high);
        for (int expectedCell : MpSogsHashUtils.cells(value, params, input.getTier())) {
            if (expectedCell == cellIndex) {
                return MpSogsPeelResult.element(value);
            }
        }
        return MpSogsPeelResult.bottom();
    }
}
