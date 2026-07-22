package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsRoundStats;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsSketch;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Party-local REP4 packed MP-SOGS MPSU runner.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep4MpSogsMpsuPartyRunner {
    /**
     * Public hash-seed retry stride.
     */
    private static final long HASH_SEED_RETRY_STRIDE = 0x9E3779B97F4A7C15L;

    private final Rpc rpc;
    private final MpSogsMpsuConfig config;
    private final long taskId;

    public Rep4MpSogsMpsuPartyRunner(Rpc rpc, MpSogsMpsuConfig config, long taskId) {
        this.rpc = rpc;
        this.config = config;
        this.taskId = taskId;
        if (config.getSecurePeelType() != MpSogsMpsuConfig.SecurePeelType.REP4_PACKED) {
            throw new IllegalArgumentException("REP4 runner requires REP4_PACKED secure peel type");
        }
        if (config.getParams().getPartyNum() != Rep4PackedBooleanBackend.PARTY_NUM) {
            throw new IllegalArgumentException("REP4 runner requires exactly 4 parties");
        }
        if (rpc.getPartySet().size() != config.getParams().getPartyNum()) {
            throw new IllegalArgumentException("RPC party count does not match MP-SOGS partyNum");
        }
    }

    public MpSogsTranscript run(Set<Long> localInput) {
        return run(localInput, null);
    }

    public MpSogsTranscript run(Set<Long> localInput, Set<Long> expectedUnion) {
        List<MpSogsRoundStats> aggregateStats = new ArrayList<>();
        MpSogsTranscript lastTranscript = null;
        long aggregateOfflineMs = 0L;
        long aggregateOnlineMs = 0L;
        for (int retryIndex = 0; retryIndex < config.getMaxHashSeedRetries(); retryIndex++) {
            MpSogsMpsuParams params = deriveRetryParams(config.getParams(), retryIndex);
            MpSogsTranscript transcript = runSingleAttempt(localInput, expectedUnion, params, retryIndex);
            aggregateStats.addAll(transcript.getRoundStats());
            aggregateOfflineMs += transcript.getOfflineMs();
            aggregateOnlineMs += transcript.getOnlineMs();
            if (transcript.isSuccess()) {
                return new MpSogsTranscript(
                    transcript.getUnionOutput(), aggregateStats, true, "", retryIndex + 1,
                    aggregateOfflineMs, aggregateOnlineMs
                );
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
            config.getMaxHashSeedRetries(), aggregateOfflineMs, aggregateOnlineMs
        );
    }

    private MpSogsTranscript runSingleAttempt(Set<Long> localInput, Set<Long> expectedUnion,
                                              MpSogsMpsuParams params, int retryIndex) {
        long offlineStart = System.nanoTime();
        MpSogsSketch localSketch = MpSogsSketch.encode(localInput, params);
        long offlineMs = (System.nanoTime() - offlineStart) / 1_000_000L;
        long onlineStart = System.nanoTime();
        int backendBatchSize = Math.min(config.getMaxBatchCells(), maxTierCellNum(params));
        SecureMpSogsUnionPeel unionPeel = new Rep4SecureMpSogsUnionPeel(
            rpc, localSketch, params, taskId + retryIndex, backendBatchSize, config.getLabelEncoding()
        );
        Set<Long> unionOutput = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        MpSogsTier tier = MpSogsTier.MAIN;
        int[] queue = allCells(params.getCellNum(tier));
        String failureReason = "";
        boolean residualChecked = false;
        for (int round = 0; queue.length > 0; round++) {
            if (round >= params.getMaxPeelRounds()) {
                failureReason = "exceeds max peel rounds";
                break;
            }
            PeelRoundOutput output = peelQueueInChunks(unionPeel, round, tier, queue, unionOutput);
            Set<Long> newlyOpened = output.newlyOpened;
            int duplicateOpenings = output.openedBatchSize - newlyOpened.size();
            stats.add(new MpSogsRoundStats(
                round, queue.length, output.openedBatchSize, newlyOpened.size(), duplicateOpenings, queue.length,
                output.sendBytes, output.receiveBytes, output.networkRoundCount
            ));
            if (newlyOpened.isEmpty()) {
                boolean anyResidual = openAnyResidual(localSketch);
                if (params.isTwoTier() && tier == MpSogsTier.MAIN && anyResidual) {
                    tier = MpSogsTier.AUXILIARY;
                    queue = allCells(params.getCellNum(tier));
                    continue;
                }
                residualChecked = true;
                if (anyResidual) {
                    failureReason = "stalled before all residual elements were peeled";
                }
                break;
            }
            newlyOpened.forEach(localSketch::deleteIfPresentOnce);
            queue = nextQueueArray(newlyOpened, params, tier);
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
        long onlineMs = (System.nanoTime() - onlineStart) / 1_000_000L;
        return new MpSogsTranscript(unionOutput, stats, success, failureReason, 1, offlineMs, onlineMs);
    }

    private PeelRoundOutput peelQueueInChunks(SecureMpSogsUnionPeel unionPeel, int round, MpSogsTier tier,
                                              int[] batchCells,
                                              Set<Long> unionOutput) {
        int maxBatchCells = config.getMaxBatchCells();
        if (batchCells.length <= maxBatchCells) {
            long sendBytesBefore = rpc.getSendByteLength();
            BatchMpSogsPeelOutput output = unionPeel.peelBatch(new BatchMpSogsPeelInput(
                round, tier, toCellList(batchCells, 0, batchCells.length)
            ));
            long sendBytes = Math.max(0L, rpc.getSendByteLength() - sendBytesBefore);
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
            long sendBytesBefore = rpc.getSendByteLength();
            BatchMpSogsPeelOutput output = unionPeel.peelBatch(new BatchMpSogsPeelInput(round, tier, chunkCells));
            sendBytes += Math.max(0L, rpc.getSendByteLength() - sendBytesBefore);
            receiveBytes += output.getReceiveBytes();
            networkRoundCount += output.getRoundCount();
            PeelRoundOutput chunkOutput = absorbResults(output, unionOutput, 0L);
            newlyOpened.addAll(chunkOutput.newlyOpened);
            openedBatchSize += chunkOutput.openedBatchSize;
        }
        return new PeelRoundOutput(newlyOpened, openedBatchSize, sendBytes, receiveBytes, networkRoundCount);
    }

    private boolean openAnyResidual(MpSogsSketch localSketch) {
        Rep4PackedBooleanBackend backend = new Rep4PackedBooleanBackend(rpc, 1, taskId + 0x4E17_0000L);
        long[] localResidual = new long[]{localSketch.getRemainingElements().isEmpty() ? 0L : 1L};
        Rep4PackedBooleanShare[] sharesByParty = backend.shareOwnAndReceiveAll(localResidual);
        PackedOrAccumulator accumulator = new PackedOrAccumulator(backend);
        for (Rep4PackedBooleanShare share : sharesByParty) {
            accumulator.orIn(share);
        }
        return (backend.open(accumulator.value())[0] & 1L) != 0L;
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

    private static int[] nextQueueArray(Set<Long> newlyOpened, MpSogsMpsuParams params, MpSogsTier tier) {
        if (newlyOpened.isEmpty()) {
            return new int[0];
        }
        int[] nextQueue = new int[Math.multiplyExact(newlyOpened.size(), params.getHashNum(tier))];
        int size = 0;
        for (long value : newlyOpened) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
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

    private static int maxTierCellNum(MpSogsMpsuParams params) {
        return params.isTwoTier()
            ? Math.max(params.getCellNum(MpSogsTier.MAIN), params.getCellNum(MpSogsTier.AUXILIARY))
            : params.getCellNum();
    }

    private static MpSogsMpsuParams deriveRetryParams(MpSogsMpsuParams params, int retryIndex) {
        if (retryIndex == 0) {
            return params;
        }
        return new MpSogsMpsuParams.Builder(params.getPartyNum(), params.getTauMax())
            .setAlpha(params.getAlpha())
            .setHashNum(params.getHashNum())
            .setTwoTier(params.isTwoTier())
            .setAuxiliaryHashNum(params.getAuxiliaryHashNum())
            .setAuxiliaryCellNum(params.getAuxiliaryCellNum())
            .setHashSeed(params.getHashSeed() + HASH_SEED_RETRY_STRIDE * retryIndex)
            .setMaxPeelRounds(params.getMaxPeelRounds())
            .build();
    }

    private static class PackedOrAccumulator {
        private final Rep4PackedBooleanBackend backend;
        private edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare value;

        private PackedOrAccumulator(Rep4PackedBooleanBackend backend) {
            this.backend = backend;
            value = backend.zero();
        }

        private void orIn(Rep4PackedBooleanShare share) {
            value = backend.or(value, share);
        }

        private edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare value() {
            return value;
        }
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
