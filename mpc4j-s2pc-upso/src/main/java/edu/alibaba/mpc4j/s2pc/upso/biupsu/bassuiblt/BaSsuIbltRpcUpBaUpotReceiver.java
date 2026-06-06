package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RPC-backed shell for receiver-side production UP-BA-UPOT bucket probes.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltRpcUpBaUpotReceiver extends AbstractTwoPartyPto implements BaSsuIbltUpBaUpotReceiver {
    /**
     * config.
     */
    private final BaSsuIbltProductionUnionProbeBackendConfig config;
    /**
     * core COT receiver.
     */
    private final CoreCotReceiver coreCotReceiver;
    /**
     * fixed public result codec.
     */
    private final BaSsuIbltProductionUnionProbeResultCodec resultCodec;
    /**
     * active public schedule.
     */
    private BaSsuIbltUpBaUpotOfflineSchedule schedule;
    /**
     * precomputed material count placeholder.
     */
    private long precomputedCotNum;
    /**
     * next global material ordinal.
     */
    private int nextMaterialOrdinal;
    /**
     * batched COT receiver material.
     */
    private CotReceiverOutput batchedCotReceiverOutput;
    /**
     * whether the fixed offline material has been prepared.
     */
    private boolean offlineMaterialPrepared;

    BaSsuIbltRpcUpBaUpotReceiver(Rpc receiverRpc, Party senderParty,
                                 BaSsuIbltProductionUnionProbeBackendConfig config) {
        super(BaSsuIbltProductionUnionProbePtoDesc.getInstance(), receiverRpc, senderParty, config);
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        this.config = config;
        coreCotReceiver = CoreCotFactory.createReceiver(receiverRpc, senderParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
        resultCodec = new BaSsuIbltProductionUnionProbeResultCodec(config.getElementByteLength());
    }

    @Override
    public void init(BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        validateSchedule(schedule);
        logPhaseInfo(PtoState.INIT_BEGIN);
        this.schedule = schedule;
        precomputedCotNum = Math.multiplyExact((long) schedule.getMaterialCount(), config.getCotNumPerProbe());
        nextMaterialOrdinal = 0;
        batchedCotReceiverOutput = null;
        offlineMaterialPrepared = false;
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public BaSsuIbltProductionUnionProbeOutput probe(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                     BaSsuIbltUpBaUpotLocalInput localInput)
        throws MpcAbortException {
        return probeBatch(List.of(publicInput), List.of(localInput)).get(0);
    }

    @Override
    public List<BaSsuIbltProductionUnionProbeOutput> probeBatch(
        List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
        List<BaSsuIbltUpBaUpotLocalInput> localInputs
    ) throws MpcAbortException {
        int batchSize = checkBatchInputs(publicInputs, localInputs);
        ensureOfflineMaterialPrepared();
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        List<byte[]> choiceCorrectionPayload = correctBatch(publicInputs, localInputs);
        sendOtherPartyPayload(
            BaSsuIbltProductionUnionProbePtoDesc.PtoStep.ONLINE_CHOICE_CORRECTION_BATCH.ordinal(),
            BaSsuIbltFixedLengthBatchPayloadCodec.pack(
                choiceCorrectionPayload, getFixedChoiceCorrectionByteLength()
            )
        );
        List<byte[]> rowPayload = receiveOtherPartyPayload(
            BaSsuIbltProductionUnionProbePtoDesc.PtoStep.ONLINE_PROBE_CAPSULE_BATCH.ordinal()
        );
        rowPayload = unpackPeerPayload(
            rowPayload, Math.multiplyExact(batchSize, BaSsuIbltUpBaUpotMaskedProbeRows.ROW_NUM),
            rowPayloadByteLength()
        );
        List<BaSsuIbltUpBaUpotMaskedProbeRows> maskedRowsBatch;
        try {
            maskedRowsBatch = BaSsuIbltUpBaUpotMaskedProbeRows.fromBatchPayload(
                rowPayload, batchSize, rowPayloadByteLength()
            );
        } catch (IllegalArgumentException e) {
            throw new MpcAbortException("malformed UP-BA-UPOT masked row payload", e);
        }
        List<BaSsuIbltProductionUnionProbeOutput> outputs = new ArrayList<>(batchSize);
        List<byte[]> fixedResultPayload = new ArrayList<>(batchSize);
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            byte[] choiceCorrection = choiceCorrectionPayload.get(batchIndex);
            int cotOffset = Math.multiplyExact(
                materialOrdinal(publicInputs.get(batchIndex)), config.getCotNumPerProbe()
            );
            boolean[] choiceCorrectionBits = BaSsuIbltUpBaUpotBucketProbeGadget.decodeChoiceCorrectionPayload(
                choiceCorrection, config.getCotNumPerProbe()
            );
            BaSsuIbltProductionUnionProbeOutput output;
            try {
                output = BaSsuIbltUpBaUpotBucketProbeGadget.decodeSelectedRow(
                    schedule, publicInputs.get(batchIndex), localInputs.get(batchIndex),
                    maskedRowsBatch.get(batchIndex), batchedCotReceiverOutput, cotOffset,
                    config.getCotNumPerProbe(), choiceCorrectionBits
                );
            } catch (IllegalArgumentException e) {
                throw new MpcAbortException("malformed UP-BA-UPOT masked row content", e);
            }
            outputs.add(output);
            fixedResultPayload.add(resultCodec.encode(output));
        }
        sendOtherPartyPayload(
            BaSsuIbltProductionUnionProbePtoDesc.PtoStep.ONLINE_FIXED_RESULT_BATCH.ordinal(),
            BaSsuIbltFixedLengthBatchPayloadCodec.pack(fixedResultPayload, resultCodec.byteLength())
        );
        nextMaterialOrdinal = Math.addExact(materialOrdinal(publicInputs.get(batchSize - 1)), 1);
        stopWatch.stop();
        long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, time,
            "receiver exchanged one precomputed-COT fixed-shape UP-BA-UPOT probe batch: " + batchSize
        );
        logPhaseInfo(PtoState.PTO_END);
        return outputs;
    }

    void prepareOfflineMaterial() throws MpcAbortException {
        if (offlineMaterialPrepared) {
            return;
        }
        if (schedule == null) {
            throw new IllegalStateException("receiver must be initialized before preparing offline material");
        }
        int totalCotNum = Math.toIntExact(precomputedCotNum);
        boolean[] randomChoices = new boolean[totalCotNum];
        for (int i = 0; i < totalCotNum; i++) {
            randomChoices[i] = secureRandom.nextBoolean();
        }
        coreCotReceiver.init();
        batchedCotReceiverOutput = coreCotReceiver.receive(randomChoices);
        offlineMaterialPrepared = true;
    }

    List<byte[]> correctBatch(List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
                              List<BaSsuIbltUpBaUpotLocalInput> localInputs) {
        ensureOfflineMaterialPrepared();
        checkBatchInputs(publicInputs, localInputs);
        if (publicInputs == null || localInputs == null || publicInputs.size() != localInputs.size()) {
            throw new IllegalArgumentException("publicInputs and localInputs must be non-null and equally sized");
        }
        List<byte[]> correctionPayload = new ArrayList<>(publicInputs.size());
        for (int i = 0; i < publicInputs.size(); i++) {
            BaSsuIbltUpBaUpotPublicInput publicInput = publicInputs.get(i);
            BaSsuIbltUpBaUpotLocalInput localInput = localInputs.get(i);
            if (publicInput == null || localInput == null) {
                throw new IllegalArgumentException("batch inputs must be non-null");
            }
            schedule.validate(publicInput);
            localInput.validatePublicInput(publicInput);
            int cotOffset = Math.multiplyExact(materialOrdinal(publicInput), config.getCotNumPerProbe());
            correctionPayload.add(BaSsuIbltUpBaUpotBucketProbeGadget.choiceCorrectionPayload(
                localInput, batchedCotReceiverOutput, cotOffset, config.getCotNumPerProbe()
            ));
        }
        return correctionPayload;
    }

    long getPrecomputedCotNum() {
        return precomputedCotNum;
    }

    boolean isOfflineMaterialPrepared() {
        return offlineMaterialPrepared;
    }

    int getFixedProbeCapsuleByteLength() {
        return Math.multiplyExact(BaSsuIbltUpBaUpotMaskedProbeRows.ROW_NUM, rowPayloadByteLength());
    }

    int getFixedChoiceCorrectionByteLength() {
        return BaSsuIbltUpBaUpotBucketProbeGadget.choiceCorrectionByteLength(config.getCotNumPerProbe());
    }

    int getFixedResultByteLength() {
        return resultCodec.byteLength();
    }

    private void checkProbeInput(BaSsuIbltUpBaUpotPublicInput publicInput,
                                 BaSsuIbltUpBaUpotLocalInput localInput) {
        checkBatchInputs(List.of(publicInput), List.of(localInput));
    }

    private int checkBatchInputs(List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
                                 List<BaSsuIbltUpBaUpotLocalInput> localInputs) {
        if (schedule == null) {
            throw new IllegalStateException("receiver must be initialized before probing");
        }
        if (publicInputs == null || localInputs == null || publicInputs.isEmpty()
            || publicInputs.size() != localInputs.size()) {
            throw new IllegalArgumentException("publicInputs and localInputs must be non-empty and equally sized");
        }
        if (publicInputs.get(0) == null || localInputs.get(0) == null) {
            throw new IllegalArgumentException("batch inputs must be non-null");
        }
        int expectedRetryId = publicInputs.get(0).getRetryId();
        int firstMaterialOrdinal = materialOrdinal(publicInputs.get(0));
        validateFirstMaterialOrdinal(firstMaterialOrdinal);
        for (int i = 0; i < publicInputs.size(); i++) {
            BaSsuIbltUpBaUpotPublicInput publicInput = publicInputs.get(i);
            BaSsuIbltUpBaUpotLocalInput localInput = localInputs.get(i);
            if (publicInput == null || localInput == null) {
                throw new IllegalArgumentException("batch inputs must be non-null");
            }
            schedule.validate(publicInput);
            if (publicInput.getRetryId() != expectedRetryId) {
                throw new IllegalArgumentException("batch publicInput values must not cross retry boundaries");
            }
            if (materialOrdinal(publicInput) != Math.addExact(firstMaterialOrdinal, i)) {
                throw new IllegalArgumentException("batch publicInput material ordinals must be contiguous");
            }
            localInput.validatePublicInput(publicInput);
        }
        return publicInputs.size();
    }

    private void validateSchedule(BaSsuIbltUpBaUpotOfflineSchedule inputSchedule) {
        if (inputSchedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (inputSchedule.getElementByteLength() != config.getElementByteLength()
            || inputSchedule.getTagByteLength() != config.getTagByteLength()
            || inputSchedule.getCheckByteLength() != config.getCheckByteLength()
            || inputSchedule.getAuthTagByteLength() != config.getAuthTagByteLength()) {
            throw new IllegalArgumentException("offline schedule shape must match config");
        }
    }

    private int materialOrdinal(BaSsuIbltUpBaUpotPublicInput publicInput) {
        return Math.addExact(
            Math.multiplyExact(publicInput.getRetryId(), schedule.getMaxProbeNum()),
            publicInput.getProbeOrdinal()
        );
    }

    private void validateFirstMaterialOrdinal(int firstMaterialOrdinal) {
        if (firstMaterialOrdinal < nextMaterialOrdinal) {
            throw new IllegalArgumentException("batch publicInput material ordinal is stale");
        }
        if (firstMaterialOrdinal == nextMaterialOrdinal) {
            return;
        }
        if (nextMaterialOrdinal % schedule.getMaxProbeNum() == 0) {
            throw new IllegalArgumentException("batch publicInput material ordinals may not skip an entire retry");
        }
        int nextRetryBoundary = Math.multiplyExact(
            Math.addExact(nextMaterialOrdinal / schedule.getMaxProbeNum(), 1),
            schedule.getMaxProbeNum()
        );
        if (firstMaterialOrdinal != nextRetryBoundary) {
            throw new IllegalArgumentException("batch publicInput material ordinals may only skip at retry boundary");
        }
    }

    private void ensureOfflineMaterialPrepared() {
        if (!offlineMaterialPrepared) {
            throw new IllegalStateException("receiver offline COT material must be prepared before probing");
        }
    }

    private static List<byte[]> unpackPeerPayload(List<byte[]> payload, int chunkNum, int chunkByteLength)
        throws MpcAbortException {
        try {
            return BaSsuIbltFixedLengthBatchPayloadCodec.unpack(payload, chunkNum, chunkByteLength);
        } catch (IllegalArgumentException e) {
            throw new MpcAbortException("malformed packed UP-BA-UPOT peer payload", e);
        }
    }

    private int rowPayloadByteLength() {
        return Math.addExact(1, Math.addExact(config.getElementByteLength(), config.getAuthTagByteLength()));
    }
}
