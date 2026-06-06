package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * RPC-backed queue-peel endpoint for BA-SSU-IBLT bi-output UPSU.
 *
 * <p>The endpoint keeps only the local party's source layer in memory. Anchor/shadow roles are public capacity roles:
 * the anchor party runs the UP-BA-UPOT sender side and the shadow party runs the UP-BA-UPOT receiver side. These roles
 * are independent from the MPC4J UPSU sender/receiver API roles.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltQueuePeelEndpoint {
    /**
     * fixed dummy derivation domain.
     */
    private static final byte[] DUMMY_DOMAIN = new byte[]{
        0x42, 0x41, 0x2D, 0x53, 0x53, 0x55, 0x2D, 0x46, 0x49, 0x58, 0x45, 0x44, 0x2D, 0x44, 0x55, 0x4D
    };
    /**
     * queue-peel auth domain.
     */
    private static final byte[] QUEUE_PEEL_AUTH_DOMAIN = new byte[]{
        0x42, 0x41, 0x2D, 0x53, 0x53, 0x55, 0x2D, 0x51, 0x55, 0x45, 0x55, 0x45, 0x2D, 0x41, 0x55, 0x54
    };

    /**
     * private constructor.
     */
    private BaSsuIbltQueuePeelEndpoint() {
        // empty
    }

    static BiUpsuPartyOutput runCandidateEndpoint(
        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,
        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,
        boolean parallel) throws MpcAbortException {
        return runCandidateEndpoint(
            rpc, otherParty, localIsProtocolSender, localElementSet, senderCapacity, receiverCapacity,
            elementByteLength, config, taskId, parallel, null
        );
    }

    /**
     * Runs the measured candidate endpoint with an optional phase recorder. If a recorder is supplied, both parties
     * must supply one because the recorder inserts a phase boundary synchronization before online peeling.
     */
    static BiUpsuPartyOutput runCandidateEndpoint(
        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,
        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,
        boolean parallel, PhaseRecorder phaseRecorder) throws MpcAbortException {
        return runEndpointCore(
            rpc, otherParty, localIsProtocolSender, localElementSet, senderCapacity, receiverCapacity,
            elementByteLength, config, taskId, parallel, phaseRecorder
        );
    }

    static BiUpsuPartyOutput runProductionEndpoint(
        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,
        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,
        boolean parallel) throws MpcAbortException {
        return runProductionEndpoint(
            rpc, otherParty, localIsProtocolSender, localElementSet, senderCapacity, receiverCapacity,
            elementByteLength, config, taskId, parallel, null
        );
    }

    /**
     * Runs the production endpoint after the endpoint-local production gate passes.
     */
    static BiUpsuPartyOutput runProductionEndpoint(
        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,
        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,
        boolean parallel, PhaseRecorder phaseRecorder) throws MpcAbortException {
        BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config);
        return runEndpointCore(
            rpc, otherParty, localIsProtocolSender, localElementSet, senderCapacity, receiverCapacity,
            elementByteLength, config, taskId, parallel, phaseRecorder
        );
    }

    private static BiUpsuPartyOutput runEndpointCore(
        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,
        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,
        boolean parallel, PhaseRecorder phaseRecorder) throws MpcAbortException {
        if (rpc == null || otherParty == null || config == null) {
            throw new IllegalArgumentException("rpc, otherParty, and config must be non-null");
        }
        if (localElementSet == null) {
            throw new IllegalArgumentException("localElementSet must be non-null");
        }
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        boolean senderAnchor = senderCapacity >= receiverCapacity;
        boolean localAnchor = localIsProtocolSender ? senderAnchor : !senderAnchor;
        BaSsuIbltBiUpsuParams params = config.createParams(senderCapacity, receiverCapacity);
        int localCapacity = localAnchor ? params.getNLarge() : Math.max(2, params.getNShadow());
        FixedInput localInput = FixedInput.fromSet(
            localElementSet, localCapacity, elementByteLength, localAnchor, localIsProtocolSender
        );
        BaSsuIbltProductionUnionProbeBackendConfig backendConfig =
            BaSsuIbltProductionQueuePeelAdapter.requireExactProductionBackend(config.getUnionProbeBackendConfig());
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionQueuePeelAdapter.offlineSchedule(params, elementByteLength, backendConfig);
        if (phaseRecorder != null) {
            phaseRecorder.startOffline();
        }
        BaSsuIbltOprfTagOutput tagOutput = generateTags(
            rpc, otherParty, localAnchor, localInput.fixedInputs, params, config, taskId, parallel
        );
        return localAnchor
            ? runAnchorEndpoint(rpc, otherParty, localInput, tagOutput, params, schedule, backendConfig, taskId,
            parallel, phaseRecorder)
            : runShadowEndpoint(rpc, otherParty, localInput, tagOutput, params, schedule, backendConfig, taskId,
            parallel, phaseRecorder);
    }

    private static BaSsuIbltOprfTagOutput generateTags(
        Rpc rpc, Party otherParty, boolean localAnchor, byte[][] fixedInputs, BaSsuIbltBiUpsuParams params,
        BaSsuIbltBiUpsuConfig config, int taskId, boolean parallel) throws MpcAbortException {
        BaSsuIbltOprfTagConfig tagConfig = BaSsuIbltOprfTagConfig.fromParams(config.getMpOprfConfig(), params);
        if (localAnchor) {
            BaSsuIbltOprfTagSender tagSender = new BaSsuIbltOprfTagSender(rpc, otherParty, tagConfig);
            tagSender.setTaskId(taskId);
            tagSender.setParallel(parallel);
            tagSender.init();
            return tagSender.generate(fixedInputs);
        } else {
            BaSsuIbltOprfTagReceiver tagReceiver = new BaSsuIbltOprfTagReceiver(rpc, otherParty, tagConfig);
            tagReceiver.setTaskId(taskId);
            tagReceiver.setParallel(parallel);
            tagReceiver.init();
            return tagReceiver.generate(fixedInputs);
        }
    }

    private static BiUpsuPartyOutput runAnchorEndpoint(
        Rpc rpc, Party otherParty, FixedInput localInput, BaSsuIbltOprfTagOutput tagOutput,
        BaSsuIbltBiUpsuParams params, BaSsuIbltUpBaUpotOfflineSchedule schedule,
        BaSsuIbltProductionUnionProbeBackendConfig backendConfig, int taskId, boolean parallel,
        PhaseRecorder phaseRecorder)
        throws MpcAbortException {
        BaSsuIbltRpcUpBaUpotSender upotSender = new BaSsuIbltRpcUpBaUpotSender(
            rpc, otherParty, backendConfig
        );
        upotSender.setTaskId(taskId);
        upotSender.setParallel(parallel);
        upotSender.init(schedule);
        upotSender.prepareOfflineMaterial();
        if (phaseRecorder != null) {
            phaseRecorder.finishOffline(rpc);
        }
        try {
            return runLocalQueuePeel(
                rpc, otherParty, taskId, localInput, tagOutput, params, schedule,
                BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR,
                (publicInputs, localProbeInputs) -> upotSender.probeBatch(publicInputs, localProbeInputs),
                backendConfig.getOnlineBatchSize(), phaseRecorder
            );
        } finally {
            if (phaseRecorder != null) {
                phaseRecorder.finishOnline(rpc);
            }
        }
    }

    private static BiUpsuPartyOutput runShadowEndpoint(
        Rpc rpc, Party otherParty, FixedInput localInput, BaSsuIbltOprfTagOutput tagOutput,
        BaSsuIbltBiUpsuParams params, BaSsuIbltUpBaUpotOfflineSchedule schedule,
        BaSsuIbltProductionUnionProbeBackendConfig backendConfig, int taskId, boolean parallel,
        PhaseRecorder phaseRecorder)
        throws MpcAbortException {
        BaSsuIbltRpcUpBaUpotReceiver upotReceiver = new BaSsuIbltRpcUpBaUpotReceiver(
            rpc, otherParty, backendConfig
        );
        upotReceiver.setTaskId(taskId);
        upotReceiver.setParallel(parallel);
        upotReceiver.init(schedule);
        upotReceiver.prepareOfflineMaterial();
        if (phaseRecorder != null) {
            phaseRecorder.finishOffline(rpc);
        }
        try {
            return runLocalQueuePeel(
                rpc, otherParty, taskId, localInput, tagOutput, params, schedule,
                BaSsuIbltProductionUnionProbeLocalLayer.SHADOW,
                (publicInputs, localProbeInputs) -> upotReceiver.probeBatch(publicInputs, localProbeInputs),
                backendConfig.getOnlineBatchSize(), phaseRecorder
            );
        } finally {
            if (phaseRecorder != null) {
                phaseRecorder.finishOnline(rpc);
            }
        }
    }

    private static BiUpsuPartyOutput runLocalQueuePeel(
        Rpc rpc, Party otherParty, int taskId, FixedInput localInput, BaSsuIbltOprfTagOutput tagOutput,
        BaSsuIbltBiUpsuParams params, BaSsuIbltUpBaUpotOfflineSchedule schedule,
        BaSsuIbltProductionUnionProbeLocalLayer localLayer, ProbeEndpoint probeEndpoint,
        int probeBatchSize, PhaseRecorder phaseRecorder) throws MpcAbortException {
        if (probeBatchSize <= 0) {
            throw new IllegalArgumentException("probeBatchSize must be positive");
        }
        Map<ByteKey, TagPair> tagMap = tagMap(localInput, tagOutput);
        TreeSet<ByteKey> peeled = new TreeSet<>();
        boolean success = false;
        for (int retryIndex = 0; retryIndex < params.getRetryCount(); retryIndex++) {
            RetryResult retryResult = runRetry(
                localInput, tagOutput, tagMap, params, schedule, retryIndex, localLayer, probeEndpoint, probeBatchSize,
                phaseRecorder
            );
            RetryStatus remoteStatus = exchangeRetryStatus(
                rpc, otherParty, taskId, retryIndex, retryResult.success, retryResult.probeCount
            );
            MpcAbortPreconditions.checkArgument(
                remoteStatus.success == retryResult.success, "queue-peel retry success status mismatch"
            );
            MpcAbortPreconditions.checkArgument(
                remoteStatus.probeCount == retryResult.probeCount, "queue-peel retry probe count mismatch"
            );
            if (phaseRecorder != null) {
                phaseRecorder.recordRetryStatus(retryIndex, retryResult.success, retryResult.probeCount);
            }
            if (retryResult.success) {
                peeled.addAll(retryResult.peeled);
                success = true;
                break;
            }
        }
        MpcAbortPreconditions.checkArgument(success, "queue-peel endpoint failed without a complete union");
        TreeSet<ByteKey> union = new TreeSet<>(localInput.activeSet);
        union.addAll(peeled);
        return new BiUpsuPartyOutput(
            union.stream().map(ByteKey::toByteBuffer).collect(Collectors.toCollection(LinkedHashSet::new)),
            BiUpsuPartyOutput.UNKNOWN_PSICA
        );
    }

    private static RetryResult runRetry(
        FixedInput localInput, BaSsuIbltOprfTagOutput tagOutput, Map<ByteKey, TagPair> tagMap,
        BaSsuIbltBiUpsuParams params, BaSsuIbltUpBaUpotOfflineSchedule schedule, int retryIndex,
        BaSsuIbltProductionUnionProbeLocalLayer localLayer, ProbeEndpoint probeEndpoint, int probeBatchSize,
        PhaseRecorder phaseRecorder) throws MpcAbortException {
        BaSsuIbltSecureLayerBuilder builder = BaSsuIbltSecureLayerBuilder.fromParams(
            params, retryIndex, localInput.elementByteLength
        );
        if (localLayer == BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR) {
            builder.insertAnchors(localInput.fixedInputs, localInput.activeFlags, tagOutput);
        } else {
            builder.insertShadows(localInput.fixedInputs, localInput.activeFlags, tagOutput);
        }
        Set<ByteKey> localRemaining = new HashSet<>(localInput.activeSet);
        TreeSet<ByteKey> peeled = new TreeSet<>();
        HashSet<ByteKey> deleted = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>(params.getTableLength());
        for (int bucketIndex = 0; bucketIndex < params.getTableLength(); bucketIndex++) {
            queue.addLast(bucketIndex);
        }
        int[] bucketVersions = new int[params.getTableLength()];
        int[] lastProbedVersions = new int[params.getTableLength()];
        Arrays.fill(lastProbedVersions, BaSsuIbltQueuePeelLocalSkipPolicy.NEVER_PROBED);
        List<BaSsuIbltUpBaUpotPublicInput> publicInputs = new ArrayList<>(probeBatchSize);
        List<BaSsuIbltUpBaUpotLocalInput> localProbeInputs = new ArrayList<>(probeBatchSize);
        HashSet<Integer> scheduledInCurrentBatch = new HashSet<>(probeBatchSize * 2);
        long probeCount = 0L;
        while (!queue.isEmpty()) {
            publicInputs.clear();
            localProbeInputs.clear();
            scheduledInCurrentBatch.clear();
            while (!queue.isEmpty() && publicInputs.size() < probeBatchSize) {
                int bucketIndex = queue.removeFirst();
                BaSsuIbltQueuePeelLocalSkipPolicy.Decision decision =
                    BaSsuIbltQueuePeelLocalSkipPolicy.decide(
                        bucketIndex, bucketVersions[bucketIndex], lastProbedVersions[bucketIndex],
                        scheduledInCurrentBatch
                    );
                if (decision != BaSsuIbltQueuePeelLocalSkipPolicy.Decision.PROBE_REMOTE) {
                    if (phaseRecorder != null) {
                        phaseRecorder.recordPublicSkip(decision);
                    }
                    continue;
                }
                if (probeCount + publicInputs.size() == schedule.getMaxProbeNum()) {
                    if (!publicInputs.isEmpty()) {
                        break;
                    }
                    return new RetryResult(false, peeled, Math.toIntExact(probeCount));
                }
                int probeOrdinal = Math.toIntExact(probeCount + publicInputs.size());
                BaSsuIbltQueuePeelProbeContext context = new BaSsuIbltQueuePeelProbeContext(
                    retryIndex, bucketIndex, probeOrdinal
                );
                BaSsuIbltSecureCellView localCellView = localLayer == BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR
                    ? builder.getAnchorCellView(bucketIndex)
                    : builder.getShadowCellView(bucketIndex);
                BaSsuIbltProductionQueuePeelPartyLocalProbeInput localProbe =
                    BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                        schedule, context, localCellView, localLayer, BaSsuIbltQueuePeelEndpoint::authMaterial
                    );
                publicInputs.add(localProbe.getPublicInput());
                localProbeInputs.add(localProbe.getOwnLocalInput());
                scheduledInCurrentBatch.add(bucketIndex);
                lastProbedVersions[bucketIndex] = bucketVersions[bucketIndex];
            }
            if (publicInputs.isEmpty()) {
                continue;
            }
            List<BaSsuIbltProductionUnionProbeOutput> outputs = probeEndpoint.probeBatch(
                publicInputs, localProbeInputs
            );
            int currentBatchSize = publicInputs.size();
            MpcAbortPreconditions.checkArgument(outputs.size() == currentBatchSize);
            if (phaseRecorder != null) {
                phaseRecorder.recordProbeBatch(currentBatchSize);
            }
            probeCount += currentBatchSize;
            for (BaSsuIbltProductionUnionProbeOutput output : outputs) {
                if (!output.isSingleton()) {
                    continue;
                }
                ByteKey outputKey = new ByteKey(output.getElement());
                boolean localDeleted = deleteLocalIfPresent(
                    outputKey, deleted, localRemaining, tagMap, builder, localLayer
                );
                if (localDeleted) {
                    peeled.add(outputKey);
                    touchAndEnqueuePositions(queue, builder, outputKey, bucketVersions);
                } else if (!localInput.activeSet.contains(outputKey) && peeled.add(outputKey)) {
                    touchAndEnqueuePositions(queue, builder, outputKey, bucketVersions);
                }
            }
        }
        return new RetryResult(localRemaining.isEmpty(), peeled, Math.toIntExact(probeCount));
    }

    private static RetryStatus exchangeRetryStatus(
        Rpc rpc, Party otherParty, int taskId, int retryIndex, boolean success, int probeCount
    ) throws MpcAbortException {
        byte[] localStatus = encodeRetryStatus(success, probeCount);
        DataPacketHeader sendHeader = new DataPacketHeader(
            taskId, BaSsuIbltBiUpsuPtoDesc.getInstance().getPtoId(),
            BaSsuIbltBiUpsuPtoDesc.PtoStep.RETRY_STATUS.ordinal(), retryIndex,
            rpc.ownParty().getPartyId(), otherParty.getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(sendHeader, List.of(localStatus)));
        DataPacketHeader receiveHeader = new DataPacketHeader(
            taskId, BaSsuIbltBiUpsuPtoDesc.getInstance().getPtoId(),
            BaSsuIbltBiUpsuPtoDesc.PtoStep.RETRY_STATUS.ordinal(), retryIndex,
            otherParty.getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(receiveHeader).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == 1);
        return decodeRetryStatus(payload.get(0));
    }

    private static byte[] encodeRetryStatus(boolean success, int probeCount) {
        if (probeCount < 0) {
            throw new IllegalArgumentException("probeCount must be non-negative");
        }
        return new byte[]{
            (byte) (success ? 1 : 0),
            (byte) (probeCount >>> 24),
            (byte) (probeCount >>> 16),
            (byte) (probeCount >>> 8),
            (byte) probeCount,
        };
    }

    private static RetryStatus decodeRetryStatus(byte[] payload) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(payload != null && payload.length == 5);
        MpcAbortPreconditions.checkArgument(payload[0] == 0 || payload[0] == 1);
        int probeCount = ((payload[1] & 0xFF) << 24)
            | ((payload[2] & 0xFF) << 16)
            | ((payload[3] & 0xFF) << 8)
            | (payload[4] & 0xFF);
        MpcAbortPreconditions.checkArgument(probeCount >= 0);
        return new RetryStatus(payload[0] == 1, probeCount);
    }

    private static boolean deleteLocalIfPresent(
        ByteKey element, Set<ByteKey> deleted, Set<ByteKey> localRemaining, Map<ByteKey, TagPair> tagMap,
        BaSsuIbltSecureLayerBuilder builder, BaSsuIbltProductionUnionProbeLocalLayer localLayer) {
        if (deleted.contains(element) || !localRemaining.remove(element)) {
            return false;
        }
        TagPair tagPair = tagMap.get(element);
        if (tagPair == null) {
            throw new IllegalStateException("missing local tag/check for peeled element");
        }
        if (localLayer == BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR) {
            builder.deleteAnchor(element.bytes, tagPair.tag, tagPair.check);
        } else {
            builder.deleteShadow(element.bytes, tagPair.tag, tagPair.check);
        }
        deleted.add(element);
        return true;
    }

    private static void touchAndEnqueuePositions(
        ArrayDeque<Integer> queue, BaSsuIbltSecureLayerBuilder builder, ByteKey element, int[] bucketVersions
    ) {
        for (int position : builder.positions(element.bytes)) {
            bucketVersions[position]++;
            queue.addLast(position);
        }
    }

    private static Map<ByteKey, TagPair> tagMap(FixedInput fixedInput, BaSsuIbltOprfTagOutput tagOutput) {
        if (tagOutput == null || tagOutput.getBatchSize() != fixedInput.fixedInputs.length) {
            throw new IllegalArgumentException("tagOutput must match fixed input length");
        }
        Map<ByteKey, TagPair> map = new HashMap<>();
        for (int index = 0; index < fixedInput.fixedInputs.length; index++) {
            if (fixedInput.activeFlags[index]) {
                ByteKey key = new ByteKey(fixedInput.fixedInputs[index]);
                if (map.put(key, new TagPair(tagOutput.getTag(index), tagOutput.getCheck(index))) != null) {
                    throw new IllegalArgumentException("active fixed inputs must not contain duplicates");
                }
            }
        }
        return map;
    }

    private static byte[] authMaterial(BaSsuIbltUpBaUpotPublicInput publicInput,
                                       BaSsuIbltProductionUnionProbeLocalLayer ownLayer,
                                       BaSsuIbltSecureCellView ownCellView) {
        if (publicInput == null || ownLayer == null || ownCellView == null) {
            throw new IllegalArgumentException("auth inputs must be non-null");
        }
        byte[] output = new byte[publicInput.getAuthTagByteLength()];
        int offset = 0;
        int counter = 0;
        while (offset < output.length) {
            MessageDigest digest = digest();
            digest.update(QUEUE_PEEL_AUTH_DOMAIN);
            updateBytes(digest, publicInput.getProfileId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            updateInt(digest, publicInput.getRetryId());
            updateInt(digest, publicInput.getBucketIndex());
            updateInt(digest, publicInput.getProbeOrdinal());
            updateInt(digest, publicInput.getElementByteLength());
            updateInt(digest, publicInput.getTagBitLength());
            updateInt(digest, publicInput.getCheckBitLength());
            updateInt(digest, publicInput.getAuthTagBitLength());
            updateInt(digest, ownCellView.getCount());
            updateBytes(digest, ownCellView.getKeyXorReference());
            updateBytes(digest, ownCellView.getTagXorReference());
            updateBytes(digest, ownCellView.getCheckXorReference());
            updateInt(digest, counter);
            byte[] block = digest.digest();
            int copyLength = Math.min(block.length, output.length - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        return output;
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] toBytes(ByteBuffer buffer, int elementByteLength) {
        if (buffer == null) {
            throw new IllegalArgumentException("element must be non-null");
        }
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        if (bytes.length != elementByteLength) {
            throw new IllegalArgumentException("element byte length must be " + elementByteLength);
        }
        return bytes;
    }

    /**
     * fixed local input.
     */
    private static class FixedInput {
        /**
         * element byte length.
         */
        private final int elementByteLength;
        /**
         * fixed inputs.
         */
        private final byte[][] fixedInputs;
        /**
         * active flags.
         */
        private final boolean[] activeFlags;
        /**
         * active set.
         */
        private final Set<ByteKey> activeSet;

        private FixedInput(int elementByteLength, byte[][] fixedInputs, boolean[] activeFlags,
                           Set<ByteKey> activeSet) {
            this.elementByteLength = elementByteLength;
            this.fixedInputs = fixedInputs;
            this.activeFlags = activeFlags;
            this.activeSet = activeSet;
        }

        private static FixedInput fromSet(Set<ByteBuffer> set, int capacity, int elementByteLength,
                                          boolean localAnchor, boolean localIsProtocolSender) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            if (set.size() > capacity) {
                throw new IllegalArgumentException("local input size exceeds public capacity");
            }
            byte[][] fixedInputs = new byte[capacity][];
            boolean[] activeFlags = new boolean[capacity];
            TreeSet<ByteKey> active = new TreeSet<>();
            int index = 0;
            for (ByteBuffer element : set) {
                ByteKey key = new ByteKey(toBytes(element, elementByteLength));
                if (!active.add(key)) {
                    throw new IllegalArgumentException("input set must not contain duplicate byte strings");
                }
                fixedInputs[index] = Arrays.copyOf(key.bytes, key.bytes.length);
                activeFlags[index] = true;
                index++;
            }
            HashSet<ByteKey> used = new HashSet<>(active);
            int dummyCounter = 0;
            while (index < capacity) {
                ByteKey dummy = dummy(localAnchor, localIsProtocolSender, index, dummyCounter, elementByteLength);
                dummyCounter++;
                if (used.add(dummy)) {
                    fixedInputs[index] = Arrays.copyOf(dummy.bytes, dummy.bytes.length);
                    index++;
                }
            }
            return new FixedInput(
                elementByteLength, fixedInputs, activeFlags, java.util.Collections.unmodifiableSet(active)
            );
        }

        private static ByteKey dummy(boolean localAnchor, boolean localIsProtocolSender, int index, int counter,
                                     int elementByteLength) {
            byte[] output = new byte[elementByteLength];
            int offset = 0;
            int blockCounter = 0;
            while (offset < output.length) {
                MessageDigest digest = digest();
                digest.update(DUMMY_DOMAIN);
                updateInt(digest, localAnchor ? 1 : 0);
                updateInt(digest, localIsProtocolSender ? 1 : 0);
                updateInt(digest, index);
                updateInt(digest, counter);
                updateInt(digest, blockCounter);
                byte[] block = digest.digest();
                int copyLength = Math.min(block.length, output.length - offset);
                System.arraycopy(block, 0, output, offset, copyLength);
                offset += copyLength;
                blockCounter++;
            }
            return new ByteKey(output);
        }
    }

    /**
     * tag/check pair.
     */
    private static class TagPair {
        /**
         * tag.
         */
        private final byte[] tag;
        /**
         * check.
         */
        private final byte[] check;

        TagPair(byte[] tag, byte[] check) {
            this.tag = Arrays.copyOf(tag, tag.length);
            this.check = Arrays.copyOf(check, check.length);
        }
    }

    /**
     * retry result.
     */
    private static class RetryResult {
        /**
         * success.
         */
        private final boolean success;
        /**
         * peeled elements.
         */
        private final Set<ByteKey> peeled;
        /**
         * public probe count consumed by this retry.
         */
        private final int probeCount;

        RetryResult(boolean success, Set<ByteKey> peeled, int probeCount) {
            this.success = success;
            this.peeled = peeled;
            this.probeCount = probeCount;
        }
    }

    /**
     * remote retry status.
     */
    private static class RetryStatus {
        /**
         * success.
         */
        private final boolean success;
        /**
         * probe count.
         */
        private final int probeCount;

        RetryStatus(boolean success, int probeCount) {
            this.success = success;
            this.probeCount = probeCount;
        }
    }

    /**
     * endpoint probe callback.
     */
    @FunctionalInterface
    private interface ProbeEndpoint {
        /**
         * Probes one public bucket.
         *
         * @param publicInput public probe input.
         * @param localProbeInput local probe input.
         * @return public output.
         * @throws MpcAbortException if protocol aborts.
         */
        List<BaSsuIbltProductionUnionProbeOutput> probeBatch(
            List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
            List<BaSsuIbltUpBaUpotLocalInput> localProbeInputs
        ) throws MpcAbortException;
    }

    /**
     * Optional per-party phase recorder for measured candidate endpoint runs.
     */
    static final class PhaseRecorder {
        /**
         * offline start.
         */
        private long offlineStartNanos;
        /**
         * online start.
         */
        private long onlineStartNanos;
        /**
         * offline time.
         */
        private long offlineTimeNanos;
        /**
         * online time.
         */
        private long onlineTimeNanos;
        /**
         * offline send bytes.
         */
        private long offlineSendBytes;
        /**
         * online send bytes.
         */
        private long onlineSendBytes;
        /**
         * offline payload bytes.
         */
        private long offlinePayloadBytes;
        /**
         * online payload bytes.
         */
        private long onlinePayloadBytes;
        /**
         * offline packets.
         */
        private long offlinePacketNum;
        /**
         * online packets.
         */
        private long onlinePacketNum;
        /**
         * phase barrier time.
         */
        private long phaseBarrierTimeNanos;
        /**
         * phase barrier send bytes.
         */
        private long phaseBarrierSendBytes;
        /**
         * phase barrier payload bytes.
         */
        private long phaseBarrierPayloadBytes;
        /**
         * phase barrier packets.
         */
        private long phaseBarrierPacketNum;
        /**
         * public retry index.
         */
        private int retryIndex;
        /**
         * public retry success flag.
         */
        private boolean retrySuccess;
        /**
         * public probe count.
         */
        private int probeCount;
        /**
         * logical public probes sent through online batches.
         */
        private long logicalProbeCount;
        /**
         * online probe batches.
         */
        private long onlineBatchCount;
        /**
         * maximum logical probes in one online batch.
         */
        private int maxProbeBatchSize;
        /**
         * public duplicate queue entries skipped inside a batch.
         */
        private long publicDuplicateSkipCount;
        /**
         * public unchanged re-probes skipped by bucket version.
         */
        private long publicUnchangedSkipCount;
        /**
         * whether retry status was recorded.
         */
        private boolean hasRetryStatus;

        private void startOffline() {
            offlineStartNanos = System.nanoTime();
        }

        private void finishOffline(Rpc rpc) {
            offlineTimeNanos = System.nanoTime() - offlineStartNanos;
            offlineSendBytes = rpc.getSendByteLength();
            offlinePayloadBytes = rpc.getPayloadByteLength();
            offlinePacketNum = rpc.getSendDataPacketNum();
            long barrierStartNanos = System.nanoTime();
            rpc.synchronize();
            phaseBarrierTimeNanos = System.nanoTime() - barrierStartNanos;
            phaseBarrierSendBytes = rpc.getSendByteLength() - offlineSendBytes;
            phaseBarrierPayloadBytes = rpc.getPayloadByteLength() - offlinePayloadBytes;
            phaseBarrierPacketNum = rpc.getSendDataPacketNum() - offlinePacketNum;
            rpc.reset();
            onlineStartNanos = System.nanoTime();
        }

        private void finishOnline(Rpc rpc) {
            onlineTimeNanos = System.nanoTime() - onlineStartNanos;
            onlineSendBytes = rpc.getSendByteLength();
            onlinePayloadBytes = rpc.getPayloadByteLength();
            onlinePacketNum = rpc.getSendDataPacketNum();
        }

        private void recordRetryStatus(int retryIndex, boolean retrySuccess, int probeCount) {
            this.retryIndex = retryIndex;
            this.retrySuccess = retrySuccess;
            this.probeCount = probeCount;
            hasRetryStatus = true;
        }

        private void recordProbeBatch(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be positive");
            }
            logicalProbeCount += batchSize;
            onlineBatchCount++;
            maxProbeBatchSize = Math.max(maxProbeBatchSize, batchSize);
        }

        private void recordPublicSkip(BaSsuIbltQueuePeelLocalSkipPolicy.Decision decision) {
            if (decision == BaSsuIbltQueuePeelLocalSkipPolicy.Decision.SKIP_DUPLICATE_PUBLIC) {
                publicDuplicateSkipCount++;
            } else if (decision == BaSsuIbltQueuePeelLocalSkipPolicy.Decision.SKIP_UNCHANGED_PUBLIC) {
                publicUnchangedSkipCount++;
            } else if (decision != BaSsuIbltQueuePeelLocalSkipPolicy.Decision.PROBE_REMOTE) {
                throw new IllegalArgumentException("unknown public skip decision: " + decision);
            }
        }

        long getOfflineTimeNanos() {
            return offlineTimeNanos;
        }

        long getOnlineTimeNanos() {
            return onlineTimeNanos;
        }

        long getOfflineSendBytes() {
            return offlineSendBytes;
        }

        long getOnlineSendBytes() {
            return onlineSendBytes;
        }

        long getOfflinePayloadBytes() {
            return offlinePayloadBytes;
        }

        long getOnlinePayloadBytes() {
            return onlinePayloadBytes;
        }

        long getOfflinePacketNum() {
            return offlinePacketNum;
        }

        long getOnlinePacketNum() {
            return onlinePacketNum;
        }

        long getPhaseBarrierTimeNanos() {
            return phaseBarrierTimeNanos;
        }

        long getPhaseBarrierSendBytes() {
            return phaseBarrierSendBytes;
        }

        long getPhaseBarrierPayloadBytes() {
            return phaseBarrierPayloadBytes;
        }

        long getPhaseBarrierPacketNum() {
            return phaseBarrierPacketNum;
        }

        int getRetryIndex() {
            return retryIndex;
        }

        boolean isRetrySuccess() {
            return retrySuccess;
        }

        int getProbeCount() {
            return probeCount;
        }

        long getLogicalProbeCount() {
            return logicalProbeCount;
        }

        long getOnlineBatchCount() {
            return onlineBatchCount;
        }

        int getMaxProbeBatchSize() {
            return maxProbeBatchSize;
        }

        double getAverageProbeBatchSize() {
            if (onlineBatchCount == 0L) {
                return 0.0;
            }
            return (double) logicalProbeCount / (double) onlineBatchCount;
        }

        long getPublicDuplicateSkipCount() {
            return publicDuplicateSkipCount;
        }

        long getPublicUnchangedSkipCount() {
            return publicUnchangedSkipCount;
        }

        long getPublicSkipCount() {
            return Math.addExact(publicDuplicateSkipCount, publicUnchangedSkipCount);
        }

        boolean hasRetryStatus() {
            return hasRetryStatus;
        }
    }

    /**
     * byte-array key.
     */
    private static class ByteKey implements Comparable<ByteKey> {
        /**
         * bytes.
         */
        private final byte[] bytes;
        /**
         * whether the key can use the 8-byte fast path.
         */
        private final boolean longFastPath;
        /**
         * exact big-endian 8-byte value for equality / hashing.
         */
        private final long longValue;
        /**
         * sortable 8-byte value preserving the signed-byte lexicographic order used by compareTo.
         */
        private final long sortableLongValue;

        ByteKey(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            longFastPath = bytes.length == Long.BYTES;
            if (longFastPath) {
                long exact = 0L;
                long sortable = 0L;
                for (byte value : this.bytes) {
                    exact = (exact << Byte.SIZE) | (value & 0xFFL);
                    sortable = (sortable << Byte.SIZE) | ((value ^ 0x80) & 0xFFL);
                }
                longValue = exact;
                sortableLongValue = sortable;
            } else {
                longValue = 0L;
                sortableLongValue = 0L;
            }
        }

        private ByteBuffer toByteBuffer() {
            return ByteBuffer.wrap(Arrays.copyOf(bytes, bytes.length));
        }

        @Override
        public int compareTo(ByteKey other) {
            if (longFastPath && other.longFastPath) {
                return Long.compareUnsigned(sortableLongValue, other.sortableLongValue);
            }
            for (int i = 0; i < bytes.length && i < other.bytes.length; i++) {
                int compare = Byte.compare(bytes[i], other.bytes[i]);
                if (compare != 0) {
                    return compare;
                }
            }
            return Integer.compare(bytes.length, other.bytes.length);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ByteKey)) {
                return false;
            }
            ByteKey that = (ByteKey) obj;
            if (longFastPath && that.longFastPath) {
                return longValue == that.longValue;
            }
            return Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            if (longFastPath) {
                return Long.hashCode(longValue);
            }
            return Arrays.hashCode(bytes);
        }
    }
}
