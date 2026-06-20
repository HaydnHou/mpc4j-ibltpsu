package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVectorFactory;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsRoundStats;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsSketch;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Party-local ABB3 MP-SOGS MPSU runner.
 *
 * <p>Each participant owns only its local input set. The public queue and opened elements are synchronized by the
 * secure uPeel transcript. Termination performs a one-bit secure residual check that reveals only whether some
 * participant still has local residual elements, not which participant or which elements.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class Abb3MpSogsMpsuPartyRunner {
    /**
     * Public hash-seed retry stride.
     */
    private static final long HASH_SEED_RETRY_STRIDE = 0x9E3779B97F4A7C15L;
    /**
     * ABB3 Z2 circuit party.
     */
    private final TripletZ2cParty z2cParty;
    /**
     * Config.
     */
    private final MpSogsMpsuConfig config;
    /**
     * Sorted public parties.
     */
    private final Party[] parties;

    public Abb3MpSogsMpsuPartyRunner(TripletZ2cParty z2cParty, MpSogsMpsuConfig config) {
        this.z2cParty = z2cParty;
        this.config = config;
        if (config.getSecurePeelType() != MpSogsMpsuConfig.SecurePeelType.ABB3) {
            throw new IllegalArgumentException("ABB3 runner requires ABB3 secure peel type");
        }
        if (config.getParams().getPartyNum() != 3) {
            throw new IllegalArgumentException("ABB3 runner currently supports exactly 3 parties");
        }
        parties = sortedParties(z2cParty);
        if (parties.length != config.getParams().getPartyNum()) {
            throw new IllegalArgumentException("ABB3 party count does not match MP-SOGS partyNum");
        }
    }

    public MpSogsMpsuConfig getConfig() {
        return config;
    }

    /**
     * Runs party-local MP-SOGS without an external expected union.
     *
     * @param localInput local input set.
     * @return public transcript.
     */
    public MpSogsTranscript run(Set<Long> localInput) {
        return run(localInput, null);
    }

    /**
     * Runs party-local MP-SOGS with an optional expected union for tests.
     *
     * @param localInput local input set.
     * @param expectedUnion expected union, null outside tests.
     * @return public transcript.
     */
    public MpSogsTranscript run(Set<Long> localInput, Set<Long> expectedUnion) {
        try {
            z2cParty.init();
            return runAfterInit(localInput, expectedUnion);
        } catch (MpcAbortException e) {
            throw new IllegalStateException("ABB3 MP-SOGS MPSU aborts", e);
        }
    }

    /**
     * Runs party-local MP-SOGS after the caller has initialized the ABB3 Z2 party.
     *
     * @param localInput local input set.
     * @return public transcript.
     */
    public MpSogsTranscript runAfterInit(Set<Long> localInput) {
        return runAfterInit(localInput, null);
    }

    /**
     * Runs party-local MP-SOGS after the caller has initialized the ABB3 Z2 party.
     *
     * @param localInput local input set.
     * @param expectedUnion expected union, null outside tests.
     * @return public transcript.
     */
    public MpSogsTranscript runAfterInit(Set<Long> localInput, Set<Long> expectedUnion) {
        try {
            return runInternal(localInput, expectedUnion);
        } catch (MpcAbortException e) {
            throw new IllegalStateException("ABB3 MP-SOGS MPSU aborts", e);
        }
    }

    private MpSogsTranscript runInternal(Set<Long> localInput, Set<Long> expectedUnion) throws MpcAbortException {
        List<MpSogsRoundStats> aggregateStats = new ArrayList<>();
        MpSogsTranscript lastTranscript = null;
        for (int retryIndex = 0; retryIndex < config.getMaxHashSeedRetries(); retryIndex++) {
            MpSogsMpsuParams params = deriveRetryParams(config.getParams(), retryIndex);
            MpSogsTranscript transcript = runSingleAttempt(localInput, expectedUnion, params);
            aggregateStats.addAll(transcript.getRoundStats());
            if (transcript.isSuccess()) {
                return new MpSogsTranscript(transcript.getUnionOutput(), aggregateStats, true, "", retryIndex + 1);
            }
            lastTranscript = transcript;
        }
        if (lastTranscript == null) {
            throw new IllegalStateException("no MP-SOGS attempt was executed");
        }
        return new MpSogsTranscript(
            lastTranscript.getUnionOutput(), aggregateStats, false,
            lastTranscript.getFailureReason() + " after " + config.getMaxHashSeedRetries()
                + " public hash-seed attempt(s)",
            config.getMaxHashSeedRetries()
        );
    }

    private MpSogsTranscript runSingleAttempt(Set<Long> localInput, Set<Long> expectedUnion, MpSogsMpsuParams params)
        throws MpcAbortException {
        MpSogsSketch localSketch = MpSogsSketch.encode(localInput, params);
        SecureMpSogsUnionPeel unionPeel = new Abb3SecureMpSogsUnionPeel(z2cParty, localSketch, params);
        Set<Long> unionOutput = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        int[] queue = allCells(params.getCellNum());
        String failureReason = "";
        boolean residualChecked = false;
        for (int round = 0; queue.length > 0; round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            PeelRoundOutput output = peelQueueInChunks(unionPeel, round, queue, unionOutput);
            Set<Long> newlyOpened = output.newlyOpened;
            int duplicateOpenings = output.openedBatchSize - newlyOpened.size();
            stats.add(new MpSogsRoundStats(
                round, queue.length, output.openedBatchSize, newlyOpened.size(), duplicateOpenings, queue.length,
                output.sendBytes, output.receiveBytes, output.networkRoundCount
            ));
            if (newlyOpened.isEmpty()) {
                residualChecked = true;
                if (openAnyResidual(localSketch)) {
                    failureReason = "stalled before all residual elements were peeled";
                }
                break;
            }
            newlyOpened.forEach(localSketch::deleteIfPresentOnce);
            queue = nextQueueArray(newlyOpened, params);
        }
        if (failureReason.isEmpty() && !residualChecked && openAnyResidual(localSketch)) {
            failureReason = "queue exhausted before all residual elements were peeled";
        }
        boolean success = failureReason.isEmpty();
        if (expectedUnion != null) {
            success = success && unionOutput.equals(expectedUnion);
            if (!success && failureReason.isEmpty()) {
                failureReason = "union output does not match expected union";
            }
        }
        return new MpSogsTranscript(unionOutput, stats, success, failureReason);
    }

    private PeelRoundOutput peelQueueInChunks(SecureMpSogsUnionPeel unionPeel, int round, int[] batchCells,
                                              Set<Long> unionOutput) throws MpcAbortException {
        int maxBatchCells = config.getMaxBatchCells();
        if (batchCells.length <= maxBatchCells) {
            long sendBytesBefore = z2cParty.getRpc().getSendByteLength();
            BatchMpSogsPeelOutput output = unionPeel.peelBatch(new BatchMpSogsPeelInput(
                round, toCellList(batchCells, 0, batchCells.length)
            ));
            long sendBytes = Math.max(0L, z2cParty.getRpc().getSendByteLength() - sendBytesBefore);
            return absorbResults(output, unionOutput, sendBytes);
        }
        Set<Long> newlyOpened = new LinkedHashSet<>();
        int openedBatchSize = 0;
        long sendBytes = 0L;
        long receiveBytes = 0L;
        int networkRoundCount = 0;
        for (int from = 0; from < batchCells.length; from += maxBatchCells) {
            int to = Math.min(batchCells.length, from + maxBatchCells);
            List<Integer> chunkCells = toCellList(batchCells, from, to);
            long sendBytesBefore = z2cParty.getRpc().getSendByteLength();
            BatchMpSogsPeelOutput output = unionPeel.peelBatch(new BatchMpSogsPeelInput(round, chunkCells));
            sendBytes += Math.max(0L, z2cParty.getRpc().getSendByteLength() - sendBytesBefore);
            receiveBytes += output.getReceiveBytes();
            networkRoundCount += output.getRoundCount();
            PeelRoundOutput chunkOutput = absorbResults(output, unionOutput, 0L);
            newlyOpened.addAll(chunkOutput.newlyOpened);
            openedBatchSize += chunkOutput.openedBatchSize;
        }
        return new PeelRoundOutput(newlyOpened, openedBatchSize, sendBytes, receiveBytes, networkRoundCount);
    }

    private static PeelRoundOutput absorbResults(BatchMpSogsPeelOutput output, Set<Long> unionOutput,
                                                 long sendBytes) {
        Set<Long> newlyOpened = new LinkedHashSet<>();
        int openedBatchSize = 0;
        for (MpSogsPeelResult result : output.getResults()) {
            if (!result.isBottom()) {
                openedBatchSize++;
                if (unionOutput.add(result.getValue())) {
                    newlyOpened.add(result.getValue());
                }
            }
        }
        return new PeelRoundOutput(
            newlyOpened, openedBatchSize, sendBytes, output.getReceiveBytes(), output.getRoundCount()
        );
    }

    private static int[] allCells(int cellNum) {
        int[] cells = new int[cellNum];
        for (int cellIndex = 0; cellIndex < cellNum; cellIndex++) {
            cells[cellIndex] = cellIndex;
        }
        return cells;
    }

    private static List<Integer> toCellList(int[] cells, int fromInclusive, int toExclusive) {
        List<Integer> result = new ArrayList<>(toExclusive - fromInclusive);
        for (int i = fromInclusive; i < toExclusive; i++) {
            result.add(cells[i]);
        }
        return result;
    }

    private static int[] nextQueueArray(Set<Long> newlyOpened, MpSogsMpsuParams params) {
        if (newlyOpened.isEmpty()) {
            return new int[0];
        }
        int[] nextQueue = new int[Math.multiplyExact(newlyOpened.size(), params.getHashNum())];
        int size = 0;
        for (long value : newlyOpened) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params)) {
                nextQueue[size++] = cellIndex;
            }
        }
        Arrays.sort(nextQueue, 0, size);
        int uniqueSize = 0;
        int previous = -1;
        for (int i = 0; i < size; i++) {
            int cellIndex = nextQueue[i];
            if (uniqueSize == 0 || cellIndex != previous) {
                nextQueue[uniqueSize++] = cellIndex;
                previous = cellIndex;
            }
        }
        return uniqueSize == nextQueue.length ? nextQueue : Arrays.copyOf(nextQueue, uniqueSize);
    }

    private static MpSogsMpsuParams deriveRetryParams(MpSogsMpsuParams params, int retryIndex) {
        if (retryIndex == 0) {
            return params;
        }
        return new MpSogsMpsuParams.Builder(params.getPartyNum(), params.getTauMax())
            .setAlpha(params.getAlpha())
            .setHashNum(params.getHashNum())
            .setHashSeed(params.getHashSeed() + HASH_SEED_RETRY_STRIDE * retryIndex)
            .setMaxPeelRounds(params.getMaxPeelRounds())
            .build();
    }

    private boolean openAnyResidual(MpSogsSketch localSketch) throws MpcAbortException {
        BitVector localResidual = BitVectorFactory.createZeros(1);
        localResidual.set(0, !localSketch.getRemainingElements().isEmpty());
        MpcZ2Vector anyResidual = z2cParty.createZeros(1);
        int ownPartyId = z2cParty.ownParty().getPartyId();
        for (Party party : parties) {
            MpcZ2Vector residualShare = party.getPartyId() == ownPartyId
                ? z2cParty.shareOwn(localResidual)
                : z2cParty.shareOther(1, party);
            anyResidual = z2cParty.or(anyResidual, residualShare);
        }
        return z2cParty.open(new MpcZ2Vector[]{anyResidual})[0].get(0);
    }

    private static Party[] sortedParties(TripletZ2cParty z2cParty) {
        Party[] result = new Party[1 + z2cParty.otherParties().length];
        result[0] = z2cParty.ownParty();
        System.arraycopy(z2cParty.otherParties(), 0, result, 1, z2cParty.otherParties().length);
        Arrays.sort(result, Comparator.comparingInt(Party::getPartyId));
        return result;
    }

    private static class PeelRoundOutput {
        private final Set<Long> newlyOpened;
        private final int openedBatchSize;
        private final long sendBytes;
        private final long receiveBytes;
        private final int networkRoundCount;

        private PeelRoundOutput(Set<Long> newlyOpened, int openedBatchSize, long sendBytes, long receiveBytes,
                                int networkRoundCount) {
            this.newlyOpened = newlyOpened;
            this.openedBatchSize = openedBatchSize;
            this.sendBytes = sendBytes;
            this.receiveBytes = receiveBytes;
            this.networkRoundCount = networkRoundCount;
        }
    }
}
