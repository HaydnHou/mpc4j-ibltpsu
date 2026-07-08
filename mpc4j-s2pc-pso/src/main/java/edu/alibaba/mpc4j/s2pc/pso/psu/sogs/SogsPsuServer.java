package edu.alibaba.mpc4j.s2pc.pso.psu.sogs;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.structure.sogs.SogsPsuSketchBackend;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SOGS-PSU server.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuServer extends AbstractPsuServer {
    /**
     * MP-OPRF sender.
     */
    private final MpOprfSender mpOprfSender;
    /**
     * COT receiver for client singleton elements.
     */
    private final CoreCotReceiver singletonCotReceiver;
    /**
     * COT sender for union-peel messages.
     */
    private final CoreCotSender unionCotSender;
    /**
     * config.
     */
    private final SogsPsuConfig config;
    /**
     * public SOGS sketch key.
     */
    private byte[] sketchKey;
    /**
     * server main element sketch.
     */
    private SogsPsuSketchBackend serverMainSketch;
    /**
     * server fixed auxiliary element sketch.
     */
    private SogsPsuSketchBackend serverAuxiliarySketch;
    /**
     * server elements indexed by byte representation.
     */
    private Set<ByteBuffer> serverElementSet;
    /**
     * remaining server-owned elements.
     */
    private Set<ByteBuffer> serverRemainSet;
    /**
     * OPRF sender output.
     */
    private MpOprfSenderOutput mpOprfSenderOutput;

    public SogsPsuServer(Rpc serverRpc, Party clientParty, SogsPsuConfig config) {
        super(SogsPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        mpOprfSender = OprfFactory.createMpOprfSender(serverRpc, clientParty, config.getMpOprfConfig());
        addSubPto(mpOprfSender);
        singletonCotReceiver = CoreCotFactory.createReceiver(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(singletonCotReceiver);
        unionCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(unionCotSender);
        this.config = config;
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        mpOprfSender.init(maxClientElementSize);
        singletonCotReceiver.init();
        unionCotSender.init(BlockUtils.randomBlock(secureRandom));
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        initProtocolState(serverElementSet);
        sendSketchKey();
        mpOprfSenderOutput = mpOprfSender.oprf(clientElementSize);
        stopWatch.stop();
        long initStateTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, initStateTime, "Server initializes SOGS sketch and runs MP-OPRF");

        stopWatch.start();
        runUnionPeel();
        stopWatch.stop();
        long peelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, peelTime, "Server runs SOGS union peel");

        mpOprfSenderOutput = null;
        serverMainSketch = null;
        serverAuxiliarySketch = null;
        this.serverElementSet = null;
        serverRemainSet = null;
        logPhaseInfo(PtoState.PTO_END);
    }

    private void initProtocolState(Set<ByteBuffer> inputServerElementSet) {
        sketchKey = BlockUtils.randomBlock(secureRandom);
        int threshold = Math.addExact(serverElementSize, clientElementSize);
        serverMainSketch = SogsPsuUtils.createMainSketchBackend(config, threshold, elementByteLength, sketchKey);
        if (config.isTwoTier()) {
            serverAuxiliarySketch = SogsPsuUtils.createAuxiliarySketchBackend(
                config, threshold, elementByteLength, sketchKey
            );
        } else {
            serverAuxiliarySketch = null;
        }
        serverElementSet = new HashSet<>(serverElementSize);
        serverRemainSet = new HashSet<>(serverElementSize);
        for (ByteBuffer element : inputServerElementSet) {
            byte[] elementBytes = BytesUtils.clone(element.array());
            ByteBuffer elementBuffer = ByteBuffer.wrap(elementBytes);
            serverElementSet.add(elementBuffer);
            serverRemainSet.add(ByteBuffer.wrap(BytesUtils.clone(elementBytes)));
            long key = SogsPsuUtils.elementKey(envType, elementBytes);
            serverMainSketch.add(key, elementBytes);
            if (config.isTwoTier()) {
                serverAuxiliarySketch.add(key, elementBytes);
            }
        }
    }

    private void sendSketchKey() {
        List<byte[]> keyPayload = new ArrayList<>(1);
        keyPayload.add(sketchKey);
        DataPacketHeader keyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_SKETCH_KEY.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(keyHeader, keyPayload));
    }

    private void runUnionPeel() throws MpcAbortException {
        PeelPhase phase = PeelPhase.MAIN;
        boolean[] mainPeeled = new boolean[serverMainSketch.tableSize()];
        boolean[] auxiliaryPeeled = config.isTwoTier() ? new boolean[serverAuxiliarySketch.tableSize()] : null;
        int maxRound = Math.max(
            1, serverMainSketch.tableSize() + (config.isTwoTier() ? serverAuxiliarySketch.tableSize() : 0)
        );
        long nextExtraInfo = extraInfo;
        try {
            int[] probeIndexes = SogsPsuUtils.allUnpeeledPositions(mainPeeled);
            for (int round = 0; round <= maxRound; round++) {
                long roundExtraInfo = nextExtraInfo++;
                if (probeIndexes.length == 0) {
                    PhaseBoundaryResult boundary = handlePhaseBoundary(roundExtraInfo, phase);
                    if (boundary == PhaseBoundaryResult.FINISH) {
                        return;
                    }
                    phase = PeelPhase.AUXILIARY;
                    probeIndexes = SogsPsuUtils.allUnpeeledPositions(auxiliaryPeeled);
                    continue;
                }
                byte[][] clientSingletonElements = receiveClientSingletonElements(roundExtraInfo, phase, probeIndexes);
                sendUnionPeelMessages(roundExtraInfo, round, phase, probeIndexes, clientSingletonElements);
                List<byte[]> peeledPayload = receivePeeledElements(roundExtraInfo);
                if (peeledPayload.isEmpty()) {
                    PhaseBoundaryResult boundary = handlePhaseBoundary(roundExtraInfo, phase);
                    if (boundary == PhaseBoundaryResult.FINISH) {
                        return;
                    }
                    phase = PeelPhase.AUXILIARY;
                    probeIndexes = SogsPsuUtils.allUnpeeledPositions(auxiliaryPeeled);
                    continue;
                }
                handlePeeledElements(peeledPayload, mainPeeled, auxiliaryPeeled);
                probeIndexes = nextProbeIndexes(phase, peeledPayload, activePeeled(phase, mainPeeled, auxiliaryPeeled));
            }
            throw new MpcAbortException("SOGS union peel exceeds the maximum round number");
        } finally {
            extraInfo = nextExtraInfo;
        }
    }

    private byte[][] receiveClientSingletonElements(long roundExtraInfo, PeelPhase phase, int[] probeIndexes)
        throws MpcAbortException {
        int probeNum = probeIndexes.length;
        int[] counts = activeSketch(phase).counts();
        boolean[] choices = new boolean[probeNum];
        for (int i = 0; i < probeNum; i++) {
            choices[i] = counts[probeIndexes[i]] == 0;
        }
        CotReceiverOutput cotReceiverOutput = singletonCotReceiver.receive(choices);
        DataPacketHeader singletonHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_SINGLETON_ELEMENTS.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> singletonPayload = rpc.receive(singletonHeader).getPayload();
        return SogsPsuUtils.handleCotPayload(
            envType, cotReceiverOutput, singletonPayload, Byte.BYTES + elementByteLength
        );
    }

    private void sendUnionPeelMessages(long roundExtraInfo, int round, PeelPhase phase, int[] probeIndexes,
                                       byte[][] clientSingletonElements)
        throws MpcAbortException {
        int probeNum = probeIndexes.length;
        MathPreconditions.checkEqual(
            "clientSingletonElements.length", "probeNum", clientSingletonElements.length, probeNum
        );
        SogsPsuSketchBackend activeSketch = activeSketch(phase);
        int[] counts = activeSketch.counts();
        boolean[] pureSingletons = activeSketch.pureSingletons();
        byte[][] values = activeSketch.valueSums();
        int messageByteLength = Math.max(Byte.BYTES + elementByteLength, CommonConstants.BLOCK_BYTE_LENGTH);
        byte[][] message0 = new byte[probeNum][messageByteLength];
        byte[][] message1 = new byte[probeNum][messageByteLength];
        for (int i = 0; i < probeNum; i++) {
            int index = probeIndexes[i];
            int globalIndex = toGlobalIndex(phase, index);
            if (counts[index] == 1 && pureSingletons[index]) {
                message0[i] = SogsPsuUtils.pad(
                    SogsPsuUtils.encodeOptionalElement(values[index], elementByteLength), messageByteLength
                );
                byte[] seed = SogsPsuUtils.seed(envType, mpOprfSenderOutput.getPrf(values[index]));
                message1[i] = SogsPsuUtils.seedTag(envType, seed, round, globalIndex);
            } else {
                byte[] clientElement = SogsPsuUtils.decodeOptionalElement(
                    clientSingletonElements[i], elementByteLength
                );
                if (clientElement == null) {
                    continue;
                }
                byte[] seed = SogsPsuUtils.seed(envType, mpOprfSenderOutput.getPrf(clientElement));
                message1[i] = SogsPsuUtils.seedTag(envType, seed, round, globalIndex);
            }
        }
        CotSenderOutput cotSenderOutput = unionCotSender.send(probeNum);
        List<byte[]> unionPayload = SogsPsuUtils.generateCotPayload(
            envType, cotSenderOutput, message0, message1, messageByteLength
        );
        DataPacketHeader unionHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_UNION_PEEL_MESSAGES.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(unionHeader, unionPayload));
    }

    private List<byte[]> receivePeeledElements(long roundExtraInfo) {
        DataPacketHeader peeledHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PEELED_ELEMENTS.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        return rpc.receive(peeledHeader).getPayload();
    }

    private void handlePeeledElements(List<byte[]> peeledPayload, boolean[] mainPeeled, boolean[] auxiliaryPeeled)
        throws MpcAbortException {
        for (byte[] encoded : peeledPayload) {
            int globalIndex = SogsPsuUtils.decodePeeledIndex(encoded);
            PeelPhase phase = SogsPsuUtils.phaseOfGlobalIndex(globalIndex, serverMainSketch.tableSize());
            int localIndex = SogsPsuUtils.toLocalIndex(globalIndex, serverMainSketch.tableSize());
            boolean[] peeled = activePeeled(phase, mainPeeled, auxiliaryPeeled);
            MpcAbortPreconditions.checkArgument(localIndex >= 0 && localIndex < peeled.length);
            peeled[localIndex] = true;
            byte[] element = SogsPsuUtils.decodePeeledElement(encoded, elementByteLength);
            removeServerElement(element);
        }
    }

    private int[] nextProbeIndexes(PeelPhase phase, List<byte[]> peeledPayload, boolean[] peeled) {
        long[] peeledKeys = SogsPsuUtils.peeledElementKeys(envType, peeledPayload, elementByteLength);
        return activeSketch(phase).uniquePositions(peeledKeys, peeled);
    }

    private void removeServerElement(byte[] element) {
        ByteBuffer elementBuffer = ByteBuffer.wrap(element);
        if (serverElementSet.contains(elementBuffer) && serverRemainSet.remove(elementBuffer)) {
            long key = SogsPsuUtils.elementKey(envType, element);
            serverMainSketch.remove(key, element);
            if (config.isTwoTier()) {
                serverAuxiliarySketch.remove(key, element);
            }
        }
    }

    private void sendFinish(long roundExtraInfo) throws MpcAbortException {
        boolean success = serverRemainSet.isEmpty();
        List<byte[]> finishPayload = new ArrayList<>(1);
        finishPayload.add(new byte[]{(byte) (success ? 1 : 0)});
        DataPacketHeader finishHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_FINISH.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(finishHeader, finishPayload));
        MpcAbortPreconditions.checkArgument(success);
    }

    private PhaseBoundaryResult handlePhaseBoundary(long roundExtraInfo, PeelPhase phase) throws MpcAbortException {
        if (!config.isTwoTier()) {
            sendFinish(roundExtraInfo);
            return PhaseBoundaryResult.FINISH;
        }
        boolean clientRemainEmpty = receivePhaseStatus(roundExtraInfo);
        if (serverRemainSet.isEmpty() && clientRemainEmpty) {
            sendPhaseDecision(roundExtraInfo, PhaseBoundaryResult.FINISH);
            return PhaseBoundaryResult.FINISH;
        }
        if (phase == PeelPhase.MAIN) {
            sendPhaseDecision(roundExtraInfo, PhaseBoundaryResult.AUXILIARY);
            return PhaseBoundaryResult.AUXILIARY;
        }
        sendPhaseDecision(roundExtraInfo, PhaseBoundaryResult.FAIL);
        throw new MpcAbortException("two-tier SOGS peel failed");
    }

    private boolean receivePhaseStatus(long roundExtraInfo) throws MpcAbortException {
        DataPacketHeader statusHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PHASE_STATUS.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> statusPayload = rpc.receive(statusHeader).getPayload();
        MpcAbortPreconditions.checkArgument(statusPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(statusPayload.get(0).length == 1);
        return statusPayload.get(0)[0] == 1;
    }

    private void sendPhaseDecision(long roundExtraInfo, PhaseBoundaryResult decision) {
        List<byte[]> decisionPayload = new ArrayList<>(1);
        decisionPayload.add(new byte[]{(byte) decision.code});
        DataPacketHeader decisionHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PHASE_DECISION.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(decisionHeader, decisionPayload));
    }

    private SogsPsuSketchBackend activeSketch(PeelPhase phase) {
        return phase == PeelPhase.MAIN ? serverMainSketch : serverAuxiliarySketch;
    }

    private boolean[] activePeeled(PeelPhase phase, boolean[] mainPeeled, boolean[] auxiliaryPeeled) {
        return phase == PeelPhase.MAIN ? mainPeeled : auxiliaryPeeled;
    }

    private int toGlobalIndex(PeelPhase phase, int localIndex) {
        return SogsPsuUtils.toGlobalIndex(phase, localIndex, serverMainSketch.tableSize());
    }

    private enum PhaseBoundaryResult {
        FINISH(0),
        AUXILIARY(1),
        FAIL(2);

        private final int code;

        PhaseBoundaryResult(int code) {
            this.code = code;
        }
    }
}
