package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RPC-backed shell for sender-side production UP-BA-UPOT bucket probes.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltRpcUpBaUpotSender extends AbstractTwoPartyPto implements BaSsuIbltUpBaUpotSender {
    /**
     * config.
     */
    private final BaSsuIbltProductionUnionProbeBackendConfig config;
    /**
     * core COT sender.
     */
    private final CoreCotSender coreCotSender;
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
     * batched COT sender material.
     */
    private CotSenderOutput batchedCotSenderOutput;
    /**
     * whether the fixed offline material has been prepared.
     */
    private boolean offlineMaterialPrepared;

    BaSsuIbltRpcUpBaUpotSender(Rpc senderRpc, Party receiverParty,
                               BaSsuIbltProductionUnionProbeBackendConfig config) {
        super(BaSsuIbltProductionUnionProbePtoDesc.getInstance(), senderRpc, receiverParty, config);
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        this.config = config;
        coreCotSender = CoreCotFactory.createSender(senderRpc, receiverParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
        resultCodec = new BaSsuIbltProductionUnionProbeResultCodec(config.getElementByteLength());
    }

    @Override
    public void init(BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        validateSchedule(schedule);
        logPhaseInfo(PtoState.INIT_BEGIN);
        this.schedule = schedule;
        precomputedCotNum = Math.multiplyExact((long) schedule.getMaterialCount(), config.getCotNumPerProbe());
        nextMaterialOrdinal = 0;
        batchedCotSenderOutput = null;
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
        List<byte[]> choiceCorrectionPayload = receiveOtherPartyPayload(
            BaSsuIbltProductionUnionProbePtoDesc.PtoStep.ONLINE_CHOICE_CORRECTION_BATCH.ordinal()
        );
        MpcAbortPreconditions.checkArgument(choiceCorrectionPayload.size() == batchSize);
        List<BaSsuIbltUpBaUpotMaskedProbeRows> maskedRowsBatch = new ArrayList<>(batchSize);
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            boolean[] choiceCorrectionBits = BaSsuIbltUpBaUpotBucketProbeGadget.decodeChoiceCorrectionPayload(
                choiceCorrectionPayload.get(batchIndex), config.getCotNumPerProbe()
            );
            int cotOffset = Math.multiplyExact(
                materialOrdinal(publicInputs.get(batchIndex)), config.getCotNumPerProbe()
            );
            maskedRowsBatch.add(BaSsuIbltUpBaUpotBucketProbeGadget.encodeRows(
                schedule, publicInputs.get(batchIndex), localInputs.get(batchIndex), batchedCotSenderOutput,
                cotOffset, config.getCotNumPerProbe(), choiceCorrectionBits
            ));
        }
        sendOtherPartyPayload(
            BaSsuIbltProductionUnionProbePtoDesc.PtoStep.ONLINE_PROBE_CAPSULE_BATCH.ordinal(),
            BaSsuIbltUpBaUpotMaskedProbeRows.toBatchPayload(maskedRowsBatch)
        );
        List<byte[]> fixedResultPayload = receiveOtherPartyPayload(
            BaSsuIbltProductionUnionProbePtoDesc.PtoStep.ONLINE_FIXED_RESULT_BATCH.ordinal()
        );
        MpcAbortPreconditions.checkArgument(fixedResultPayload.size() == batchSize);
        List<BaSsuIbltProductionUnionProbeOutput> outputs = new ArrayList<>(batchSize);
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            outputs.add(resultCodec.decode(
                publicInputs.get(batchIndex).getBucketIndex(), fixedResultPayload.get(batchIndex)
            ));
        }
        nextMaterialOrdinal = Math.addExact(materialOrdinal(publicInputs.get(batchSize - 1)), 1);
        stopWatch.stop();
        long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, time,
            "sender exchanged one precomputed-COT fixed-shape UP-BA-UPOT probe batch: " + batchSize
        );
        logPhaseInfo(PtoState.PTO_END);
        return outputs;
    }

    void prepareOfflineMaterial() throws MpcAbortException {
        if (offlineMaterialPrepared) {
            return;
        }
        if (schedule == null) {
            throw new IllegalStateException("sender must be initialized before preparing offline material");
        }
        int totalCotNum = Math.toIntExact(precomputedCotNum);
        coreCotSender.init(BlockUtils.randomBlock(secureRandom));
        batchedCotSenderOutput = coreCotSender.send(totalCotNum);
        offlineMaterialPrepared = true;
    }

    long getPrecomputedCotNum() {
        return precomputedCotNum;
    }

    boolean isOfflineMaterialPrepared() {
        return offlineMaterialPrepared;
    }

    int getFixedProbeCapsuleByteLength() {
        return fixedProbePayloadByteLength();
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
            throw new IllegalStateException("sender must be initialized before probing");
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
            throw new IllegalStateException("sender offline COT material must be prepared before probing");
        }
    }

    private int fixedProbePayloadByteLength() {
        return Math.multiplyExact(
            BaSsuIbltUpBaUpotMaskedProbeRows.ROW_NUM,
            Math.addExact(1, Math.addExact(config.getElementByteLength(), config.getAuthTagByteLength()))
        );
    }
}
