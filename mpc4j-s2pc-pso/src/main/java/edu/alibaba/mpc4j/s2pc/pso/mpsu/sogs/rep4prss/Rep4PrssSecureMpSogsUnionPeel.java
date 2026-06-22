package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsSketch;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedMpSogsCellBatch;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedSecureMpSogsUnionPeel;

import java.util.List;

/**
 * 4-party PRSS packed replicated Z2 implementation of MP-SOGS secure union-peel.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep4PrssSecureMpSogsUnionPeel implements SecureMpSogsUnionPeel {
    /**
     * Element bit length.
     */
    private static final int ELEMENT_BITS = MpSogsMpsuParams.ELEMENT_BIT_LENGTH;

    private final Rep4PrssPackedBooleanBackend backend;
    private final MpSogsSketch localSketch;
    private final MpSogsMpsuParams params;
    private final int maxBatchSize;

    public Rep4PrssSecureMpSogsUnionPeel(Rpc rpc, MpSogsSketch localSketch, MpSogsMpsuParams params, long taskId,
                                         int batchSize) {
        if (params.getPartyNum() != Rep4PrssPackedBooleanBackend.PARTY_NUM) {
            throw new IllegalArgumentException("REP4 PRSS secure-uPeel requires exactly 4 parties: "
                + params.getPartyNum());
        }
        backend = new Rep4PrssPackedBooleanBackend(rpc, batchSize, taskId);
        this.localSketch = localSketch;
        this.params = params;
        maxBatchSize = batchSize;
        if (localSketch.getCellNum() != params.getCellNum()) {
            throw new IllegalArgumentException("local sketch parameters do not match MP-SOGS parameters");
        }
    }

    @Override
    public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
        if (input.size() == 0) {
            return new BatchMpSogsPeelOutput(List.of(), 0L, 0L, 0);
        }
        if (input.size() > maxBatchSize) {
            throw new IllegalArgumentException("batch exceeds REP4 PRSS backend capacity: " + input.size()
                + " > " + maxBatchSize);
        }
        backend.resetNetworkRoundCount();
        LocalPackedWires localWires = encodeLocalWires(input);
        PackedBooleanShare[] singleton = shareWire(localWires.singleton);
        PackedBooleanShare[] heavy = shareWire(localWires.heavy);
        PackedBooleanShare[][] valueBits = new PackedBooleanShare[params.getPartyNum()][ELEMENT_BITS];
        for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
            PackedBooleanShare[] bitShares = shareWire(localWires.valueBits[bitIndex]);
            for (int partyIndex = 0; partyIndex < params.getPartyNum(); partyIndex++) {
                valueBits[partyIndex][bitIndex] = bitShares[partyIndex];
            }
        }
        PackedMpSogsCellBatch batch = PackedMpSogsCellBatch.fromShares(input.size(), singleton, heavy, valueBits);
        List<MpSogsPeelResult> results = new PackedSecureMpSogsUnionPeel(backend).peel(batch);
        return new BatchMpSogsPeelOutput(results, 0L, 0L, backend.getNetworkRoundCount());
    }

    private PackedBooleanShare[] shareWire(long[] wire) {
        Rep4PrssPackedBooleanShare[] shares = backend.shareOwnAndReceiveAll(wire);
        PackedBooleanShare[] result = new PackedBooleanShare[shares.length];
        System.arraycopy(shares, 0, result, 0, shares.length);
        return result;
    }

    private LocalPackedWires encodeLocalWires(BatchMpSogsPeelInput input) {
        int blockNum = backend.blockNum();
        long[] singleton = new long[blockNum];
        long[] heavy = new long[blockNum];
        long[][] valueBits = new long[ELEMENT_BITS][blockNum];
        for (int batchIndex = 0; batchIndex < input.size(); batchIndex++) {
            int cellIndex = input.getCellIndexes().get(batchIndex);
            MpSogsLocalCellView view = localSketch.localCellView(cellIndex);
            if (view.isSingleton()) {
                setLane(singleton, batchIndex);
                long value = view.getSingletonValue();
                for (int bitIndex = 0; bitIndex < ELEMENT_BITS; bitIndex++) {
                    if (((value >>> (ELEMENT_BITS - 1 - bitIndex)) & 1L) != 0L) {
                        setLane(valueBits[bitIndex], batchIndex);
                    }
                }
            } else if (view.isHeavy()) {
                setLane(heavy, batchIndex);
            }
        }
        return new LocalPackedWires(singleton, heavy, valueBits);
    }

    private static void setLane(long[] blocks, int laneIndex) {
        blocks[laneIndex >>> 6] |= 1L << (laneIndex & (Long.SIZE - 1));
    }

    private record LocalPackedWires(long[] singleton, long[] heavy, long[][] valueBits) {
        // empty
    }
}
