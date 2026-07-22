package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelOutput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsRoundStats;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.SecureMpSogsUnionPeel;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Party-local MP-SOGS runner using secret-shared multiplicity cells and honest-majority Shamir MPC.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class ShamirMultiplicityMpSogsMpsuPartyRunner {
    private static final long HASH_SEED_RETRY_STRIDE = 0x9E3779B97F4A7C15L;
    private static final int STEP_JOINT_SEED = 43;
    private static final int RESIDUAL_MASK_NUM = 3;

    private final Rpc rpc;
    private final MpSogsMpsuConfig config;
    private final long taskId;
    private final SecureRandom secureRandom;

    public ShamirMultiplicityMpSogsMpsuPartyRunner(Rpc rpc, MpSogsMpsuConfig config, long taskId) {
        this.rpc = rpc;
        this.config = config;
        this.taskId = taskId;
        secureRandom = new SecureRandom();
        if (config.getSecurePeelType() != MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY) {
            throw new IllegalArgumentException("multiplicity runner requires SHAMIR_MULTIPLICITY secure peel type");
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
            throw new IllegalStateException("no multiplicity MP-SOGS attempt was executed");
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
        long seedStart = System.nanoTime();
        long attemptTaskId = taskId + retryIndex * 100_000L;
        long sessionSeed = agreeSessionSeed(attemptTaskId);
        long seedMs = elapsedMs(seedStart);

        long offlineStart = System.nanoTime();
        MultiplicitySogsSketch localSketch = MultiplicitySogsSketch.encode(localInput, params, sessionSeed);
        long offlineMs = elapsedMs(offlineStart);

        long onlineStart = System.nanoTime();
        SecureMpSogsUnionPeel unionPeel = new ShamirMultiplicitySecureUnionPeel(
            rpc, localSketch, params, attemptTaskId + 10_000L, sessionSeed
        );
        Set<Long> unionOutput = new LinkedHashSet<>();
        List<MpSogsRoundStats> stats = new ArrayList<>();
        MpSogsTier tier = MpSogsTier.MAIN;
        int[] queue = allCells(params.getCellNum(tier));
        String failureReason = "";
        boolean residualChecked = false;
        int residualCheckCounter = 0;
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
                boolean anyResidual = openAnyResidual(
                    localSketch, attemptTaskId + 20_000L + residualCheckCounter++
                );
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
        if (failureReason.isEmpty() && !residualChecked
            && openAnyResidual(localSketch, attemptTaskId + 30_000L + residualCheckCounter)) {
            failureReason = "queue exhausted before all residual elements were peeled";
        }
        boolean success = failureReason.isEmpty();
        if (expectedUnion != null) {
            success &= unionOutput.equals(expectedUnion);
            if (!success && failureReason.isEmpty()) {
                failureReason = "union output does not match expected union";
            }
        }
        long onlineMs = seedMs + elapsedMs(onlineStart);
        return new MpSogsTranscript(unionOutput, stats, success, failureReason, 1, offlineMs, onlineMs);
    }

    private PeelRoundOutput peelQueueInChunks(SecureMpSogsUnionPeel unionPeel, int round, MpSogsTier tier,
                                              int[] cells, Set<Long> unionOutput) {
        int maxBatchCells = config.getMaxBatchCells();
        Set<Long> newlyOpened = new LinkedHashSet<>();
        int openedBatchSize = 0;
        long sendBytes = 0L;
        long receiveBytes = 0L;
        int networkRoundCount = 0;
        for (int from = 0; from < cells.length; from += maxBatchCells) {
            int to = Math.min(cells.length, from + maxBatchCells);
            long sendBefore = rpc.getSendByteLength();
            BatchMpSogsPeelOutput output = unionPeel.peelBatch(new BatchMpSogsPeelInput(
                round, tier, toCellList(cells, from, to)
            ));
            sendBytes += Math.max(0L, rpc.getSendByteLength() - sendBefore);
            receiveBytes += output.getReceiveBytes();
            networkRoundCount += output.getRoundCount();
            for (MpSogsPeelResult result : output.getResults()) {
                if (!result.isBottom()) {
                    openedBatchSize++;
                    if (unionOutput.add(result.getValue())) {
                        newlyOpened.add(result.getValue());
                    }
                }
            }
        }
        return new PeelRoundOutput(newlyOpened, openedBatchSize, sendBytes, receiveBytes, networkRoundCount);
    }

    private boolean openAnyResidual(MultiplicitySogsSketch localSketch, long residualTaskId) {
        Mersenne61ShamirMpc mpc = new Mersenne61ShamirMpc(rpc, residualTaskId);
        long[] local = new long[1 + RESIDUAL_MASK_NUM];
        local[0] = localSketch.hasRemainingElements() ? 1L : 0L;
        long[] random = mpc.randomFieldVector(RESIDUAL_MASK_NUM);
        System.arraycopy(random, 0, local, 1, random.length);
        long[] aggregate = mpc.shareOwnAndAggregate(local);
        long[] left = new long[RESIDUAL_MASK_NUM];
        long[] right = new long[RESIDUAL_MASK_NUM];
        Arrays.fill(left, aggregate[0]);
        System.arraycopy(aggregate, 1, right, 0, RESIDUAL_MASK_NUM);
        long[] opened = mpc.open(mpc.mul(left, right));
        for (long value : opened) {
            if (value != 0L) {
                return true;
            }
        }
        return false;
    }

    private long agreeSessionSeed(long seedTaskId) {
        Party[] parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        int ownPartyId = rpc.ownParty().getPartyId();
        long ownNonce = secureRandom.nextLong();
        byte[] ownPayload = ByteBuffer.allocate(Long.BYTES).putLong(ownNonce).array();
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    seedTaskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_JOINT_SEED, 0L,
                    ownPartyId, party.getPartyId()
                );
                rpc.send(DataPacket.fromByteArrayList(header, List.of(ownPayload)));
            }
        }
        long[] nonces = new long[parties.length];
        nonces[ownPartyId] = ownNonce;
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    seedTaskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_JOINT_SEED, 0L,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1 || payload.get(0).length != Long.BYTES) {
                    throw new IllegalStateException("invalid multiplicity joint-seed payload");
                }
                nonces[party.getPartyId()] = ByteBuffer.wrap(payload.get(0)).getLong();
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("MP-SOGS-SSM-SESSION-V1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            for (long nonce : nonces) {
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(nonce).array());
            }
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static int[] allCells(int cellNum) {
        int[] result = new int[cellNum];
        for (int cellIndex = 0; cellIndex < cellNum; cellIndex++) {
            result[cellIndex] = cellIndex;
        }
        return result;
    }

    private static List<Integer> toCellList(int[] cells, int from, int to) {
        List<Integer> result = new ArrayList<>(to - from);
        for (int index = from; index < to; index++) {
            result.add(cells[index]);
        }
        return result;
    }

    private static int[] nextQueueArray(Set<Long> opened, MpSogsMpsuParams params, MpSogsTier tier) {
        int[] queue = new int[Math.multiplyExact(opened.size(), params.getHashNum(tier))];
        int size = 0;
        for (long value : opened) {
            for (int cellIndex : MpSogsHashUtils.cells(value, params, tier)) {
                queue[size++] = cellIndex;
            }
        }
        Arrays.sort(queue, 0, size);
        int unique = 0;
        int previous = -1;
        for (int index = 0; index < size; index++) {
            int cell = queue[index];
            if (unique == 0 || cell != previous) {
                queue[unique++] = cell;
                previous = cell;
            }
        }
        return unique == queue.length ? queue : Arrays.copyOf(queue, unique);
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

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static final class PeelRoundOutput {
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
