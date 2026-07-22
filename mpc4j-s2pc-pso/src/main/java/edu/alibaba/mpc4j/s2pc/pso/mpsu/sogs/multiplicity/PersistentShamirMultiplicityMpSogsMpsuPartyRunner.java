package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.BatchMpSogsPeelInput;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsHashUtils;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsRoundStats;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Party-local SSM-MPSU runner with a persistent Shamir-shared multiplicity graph.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class PersistentShamirMultiplicityMpSogsMpsuPartyRunner {
    private static final long HASH_SEED_RETRY_STRIDE = 0x9E3779B97F4A7C15L;
    private static final int STEP_JOINT_SEED = 43;
    private static final int RESIDUAL_MASK_NUM = 3;

    private final Rpc rpc;
    private final MpSogsMpsuConfig config;
    private final long taskId;
    private final SecureRandom secureRandom;
    private final MultiplicityPayloadEncoding payloadEncoding;
    private final boolean usePrss;
    private final boolean useDoubleShare;
    private final boolean useTerminalFusion;
    private final boolean usePackedWire;

    public PersistentShamirMultiplicityMpSogsMpsuPartyRunner(Rpc rpc, MpSogsMpsuConfig config, long taskId) {
        this.rpc = rpc;
        this.config = config;
        this.taskId = taskId;
        secureRandom = new SecureRandom();
        if (config.getSecurePeelType() == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT) {
            payloadEncoding = MultiplicityPayloadEncoding.FULL_LIMBS;
            usePrss = false;
            useDoubleShare = false;
            useTerminalFusion = false;
            usePackedWire = false;
        } else if (config.getSecurePeelType()
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT) {
            payloadEncoding = MultiplicityPayloadEncoding.EXACT_QUOTIENT;
            usePrss = false;
            useDoubleShare = false;
            useTerminalFusion = false;
            usePackedWire = false;
        } else if (config.getSecurePeelType()
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS) {
            payloadEncoding = MultiplicityPayloadEncoding.EXACT_QUOTIENT;
            usePrss = true;
            useDoubleShare = false;
            useTerminalFusion = false;
            usePackedWire = false;
        } else if (config.getSecurePeelType()
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE) {
            payloadEncoding = MultiplicityPayloadEncoding.EXACT_QUOTIENT;
            usePrss = true;
            useDoubleShare = true;
            useTerminalFusion = false;
            usePackedWire = false;
        } else if (config.getSecurePeelType()
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE) {
            payloadEncoding = MultiplicityPayloadEncoding.EXACT_QUOTIENT;
            usePrss = true;
            useDoubleShare = true;
            useTerminalFusion = true;
            usePackedWire = false;
        } else if (config.getSecurePeelType()
            == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED) {
            payloadEncoding = MultiplicityPayloadEncoding.EXACT_QUOTIENT;
            usePrss = true;
            useDoubleShare = true;
            useTerminalFusion = true;
            usePackedWire = true;
        } else {
            throw new IllegalArgumentException(
                "persistent multiplicity runner requires a persistent multiplicity secure peel type"
            );
        }
        payloadEncoding.validate(config.getParams());
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
            throw new IllegalStateException("no persistent multiplicity MP-SOGS attempt was executed");
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
        MultiplicitySogsSketch localSketch = MultiplicitySogsSketch.encode(
            localInput, params, sessionSeed, payloadEncoding
        );
        long offlineMs = elapsedMs(offlineStart);

        long onlineStart = System.nanoTime();
        Mersenne61ShamirMpc mpc = new Mersenne61ShamirMpc(
            rpc, attemptTaskId + 10_000L,
            usePackedWire ? Mersenne61WireFormat.PACKED_61 : Mersenne61WireFormat.LONG_64
        );
        ShamirRandomnessProvider randomnessProvider = usePrss
            ? new PrssShamirRandomnessProvider(rpc, attemptTaskId + 40_000L, retryIndex, usePackedWire)
            : new NetworkShamirRandomnessProvider(mpc);
        PersistentMultiplicitySogsState sharedState = new PersistentMultiplicitySogsState(
            mpc, params, sessionSeed, payloadEncoding
        );
        sharedState.initializeTier(localSketch, MpSogsTier.MAIN, config.getMaxBatchCells());
        PersistentShamirMultiplicitySecureUnionPeel unionPeel =
                new PersistentShamirMultiplicitySecureUnionPeel(
                    mpc, sharedState, params, sessionSeed, randomnessProvider, useDoubleShare, useTerminalFusion,
                    config.getSsmOpeningMode(), config.getNetworkRttMillis(), config.getNetworkBandwidthMbps(),
                    usePackedWire
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
            Set<Long> newlyOpened = output.countShares.keySet();
            int duplicateOpenings = output.openedBatchSize - newlyOpened.size();
            stats.add(new MpSogsRoundStats(
                round, queue.length, output.openedBatchSize, newlyOpened.size(), duplicateOpenings, queue.length,
                output.sendBytes, output.receiveBytes, output.networkRoundCount
            ));
            if (newlyOpened.isEmpty()) {
                boolean anyResidual = openAnyResidual(
                    localSketch, mpc, randomnessProvider, tier, round, residualCheckCounter++
                );
                if (params.isTwoTier() && tier == MpSogsTier.MAIN && anyResidual) {
                    tier = MpSogsTier.AUXILIARY;
                    sharedState.initializeTier(localSketch, tier, config.getMaxBatchCells());
                    queue = allCells(params.getCellNum(tier));
                    continue;
                }
                residualChecked = true;
                if (anyResidual) {
                    failureReason = "stalled before all residual elements were peeled";
                }
                break;
            }
            for (Map.Entry<Long, Long> opened : output.countShares.entrySet()) {
                long value = opened.getKey();
                sharedState.delete(tier, value, opened.getValue());
                localSketch.deleteAfterPersistentOpen(value, tier);
            }
            queue = nextQueueArray(newlyOpened, params, tier);
        }
        if (failureReason.isEmpty() && !residualChecked
            && openAnyResidual(
                localSketch, mpc, randomnessProvider, tier, stats.size(), residualCheckCounter
            )) {
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

    private PeelRoundOutput peelQueueInChunks(PersistentShamirMultiplicitySecureUnionPeel unionPeel, int round,
                                              MpSogsTier tier, int[] cells, Set<Long> unionOutput) {
        int maxBatchCells = config.getMaxBatchCells();
        Map<Long, Long> countShares = new LinkedHashMap<>();
        int openedBatchSize = 0;
        long sendBytes = 0L;
        int networkRoundCount = 0;
        for (int from = 0; from < cells.length; from += maxBatchCells) {
            int to = Math.min(cells.length, from + maxBatchCells);
            long sendBefore = rpc.getSendByteLength();
            PersistentMultiplicityPeelBatch output = unionPeel.peelBatch(new BatchMpSogsPeelInput(
                round, tier, toCellList(cells, from, to)
            ));
            sendBytes += Math.max(0L, rpc.getSendByteLength() - sendBefore);
            networkRoundCount += output.getRoundCount();
            List<MpSogsPeelResult> results = output.getResults();
            for (int batchIndex = 0; batchIndex < results.size(); batchIndex++) {
                MpSogsPeelResult result = results.get(batchIndex);
                if (!result.isBottom()) {
                    openedBatchSize++;
                    long value = result.getValue();
                    if (unionOutput.add(value)) {
                        countShares.put(value, output.getCountShare(batchIndex));
                    }
                }
            }
        }
        return new PeelRoundOutput(countShares, openedBatchSize, sendBytes, 0L, networkRoundCount);
    }

    private boolean openAnyResidual(MultiplicitySogsSketch localSketch, Mersenne61ShamirMpc mpc,
                                    ShamirRandomnessProvider randomnessProvider, MpSogsTier tier,
                                    int roundIndex, int residualCheckIndex) {
        long[] aggregateFlag = mpc.shareOwnAndAggregate(new long[]{localSketch.hasRemainingElements() ? 1L : 0L});
        long[] randomShares = randomnessProvider.randomDegreeT(new ShamirRandomnessDomain(
            tier, ShamirRandomnessDomain.Phase.RESIDUAL, roundIndex, residualCheckIndex,
            0L, 0, 0, RESIDUAL_MASK_NUM
        ));
        long[] left = new long[RESIDUAL_MASK_NUM];
        Arrays.fill(left, aggregateFlag[0]);
        long[] opened;
        if (useTerminalFusion) {
            long[] zeroShares = randomnessProvider.randomDegree2TZero(new ShamirRandomnessDomain(
                tier, ShamirRandomnessDomain.Phase.RESIDUAL_TERMINAL_ZERO, roundIndex, residualCheckIndex,
                0L, 0, 0, RESIDUAL_MASK_NUM
            ));
            opened = mpc.openTerminalProduct(
                left, randomShares, zeroShares, config.getSsmOpeningMode(), config.getNetworkRttMillis(),
                config.getNetworkBandwidthMbps()
            );
        } else if (useDoubleShare) {
            ShamirDoubleShare doubleShare = randomnessProvider.randomDoubleShare(new ShamirRandomnessDomain(
                tier, ShamirRandomnessDomain.Phase.RESIDUAL_DEGREE_REDUCTION, roundIndex, residualCheckIndex,
                0L, 0, 0, RESIDUAL_MASK_NUM
            ));
            long[] productShares = mpc.mulWithDoubleShare(left, randomShares, doubleShare);
            opened = mpc.openBalanced(productShares);
        } else {
            opened = mpc.open(mpc.mul(left, randomShares));
        }
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
                    throw new IllegalStateException("invalid persistent multiplicity joint-seed payload");
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
        private final Map<Long, Long> countShares;
        private final int openedBatchSize;
        private final long sendBytes;
        private final long receiveBytes;
        private final int networkRoundCount;

        private PeelRoundOutput(Map<Long, Long> countShares, int openedBatchSize, long sendBytes, long receiveBytes,
                                int networkRoundCount) {
            this.countShares = countShares;
            this.openedBatchSize = openedBatchSize;
            this.sendBytes = sendBytes;
            this.receiveBytes = receiveBytes;
            this.networkRoundCount = networkRoundCount;
        }
    }
}
