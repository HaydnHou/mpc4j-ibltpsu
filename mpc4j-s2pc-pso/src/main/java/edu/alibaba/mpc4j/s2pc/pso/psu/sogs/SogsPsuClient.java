package edu.alibaba.mpc4j.s2pc.pso.psu.sogs;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.structure.sogs.SogsPsuSketchBackend;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SOGS-PSU client.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuClient extends AbstractPsuClient {
    /**
     * MP-OPRF receiver.
     */
    private final MpOprfReceiver mpOprfReceiver;
    /**
     * COT sender for client singleton elements.
     */
    private final CoreCotSender singletonCotSender;
    /**
     * COT receiver for union-peel messages.
     */
    private final CoreCotReceiver unionCotReceiver;
    /**
     * config.
     */
    private final SogsPsuConfig config;
    /**
     * client main element sketch.
     */
    private SogsPsuSketchBackend clientMainSketch;
    /**
     * client main OPRF seed sketch.
     */
    private SogsPsuSketchBackend clientMainSeedSketch;
    /**
     * client fixed auxiliary element sketch.
     */
    private SogsPsuSketchBackend clientAuxiliarySketch;
    /**
     * client fixed auxiliary OPRF seed sketch.
     */
    private SogsPsuSketchBackend clientAuxiliarySeedSketch;
    /**
     * client elements.
     */
    private Set<ByteBuffer> clientElementSet;
    /**
     * remaining client-owned elements.
     */
    private Set<ByteBuffer> clientRemainSet;
    /**
     * client OPRF seeds.
     */
    private Map<ByteBuffer, byte[]> clientSeedMap;

    public SogsPsuClient(Rpc clientRpc, Party serverParty, SogsPsuConfig config) {
        super(SogsPsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        mpOprfReceiver = OprfFactory.createMpOprfReceiver(clientRpc, serverParty, config.getMpOprfConfig());
        addSubPto(mpOprfReceiver);
        singletonCotSender = CoreCotFactory.createSender(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(singletonCotSender);
        unionCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(unionCotReceiver);
        this.config = config;
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        mpOprfReceiver.init(maxClientElementSize);
        singletonCotSender.init(BlockUtils.randomBlock(secureRandom));
        unionCotReceiver.init();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        initProtocolState();
        stopWatch.stop();
        long initStateTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, initStateTime, "Client initializes OPRF seeds and SOGS sketches");

        stopWatch.start();
        Set<ByteBuffer> union = runUnionPeel();
        int psica = serverElementSize + clientElementSize - union.size();
        stopWatch.stop();
        long peelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, peelTime, "Client runs SOGS union peel");

        clientMainSketch = null;
        clientMainSeedSketch = null;
        clientAuxiliarySketch = null;
        clientAuxiliarySeedSketch = null;
        this.clientElementSet = null;
        clientRemainSet = null;
        clientSeedMap = null;
        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }

    private void initProtocolState() throws MpcAbortException {
        byte[] sketchKey = receiveSketchKey();
        byte[][] clientInputs = clientElementArrayList.stream()
            .map(ByteBuffer::array)
            .map(BytesUtils::clone)
            .toArray(byte[][]::new);
        MpOprfReceiverOutput mpOprfReceiverOutput = mpOprfReceiver.oprf(clientInputs);
        int threshold = Math.addExact(serverElementSize, clientElementSize);
        clientMainSketch = SogsPsuUtils.createMainSketchBackend(config, threshold, elementByteLength, sketchKey);
        clientMainSeedSketch = SogsPsuUtils.createMainSketchBackend(
            config, threshold, CommonConstants.BLOCK_BYTE_LENGTH, sketchKey
        );
        if (config.isTwoTier()) {
            clientAuxiliarySketch = SogsPsuUtils.createAuxiliarySketchBackend(
                config, threshold, elementByteLength, sketchKey
            );
            clientAuxiliarySeedSketch = SogsPsuUtils.createAuxiliarySketchBackend(
                config, threshold, CommonConstants.BLOCK_BYTE_LENGTH, sketchKey
            );
        } else {
            clientAuxiliarySketch = null;
            clientAuxiliarySeedSketch = null;
        }
        clientElementSet = new HashSet<>(clientElementSize);
        clientRemainSet = new HashSet<>(clientElementSize);
        clientSeedMap = new HashMap<>(clientElementSize);
        for (int index = 0; index < clientInputs.length; index++) {
            byte[] element = clientInputs[index];
            byte[] seed = SogsPsuUtils.seed(envType, mpOprfReceiverOutput.getPrf(index));
            long key = SogsPsuUtils.elementKey(envType, element);
            clientMainSketch.add(key, element);
            clientMainSeedSketch.add(key, seed);
            if (config.isTwoTier()) {
                clientAuxiliarySketch.add(key, element);
                clientAuxiliarySeedSketch.add(key, seed);
            }
            ByteBuffer elementBuffer = ByteBuffer.wrap(element);
            clientElementSet.add(elementBuffer);
            clientRemainSet.add(ByteBuffer.wrap(BytesUtils.clone(element)));
            clientSeedMap.put(ByteBuffer.wrap(BytesUtils.clone(element)), seed);
        }
    }

    private byte[] receiveSketchKey() throws MpcAbortException {
        DataPacketHeader keyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_SKETCH_KEY.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> keyPayload = rpc.receive(keyHeader).getPayload();
        MpcAbortPreconditions.checkArgument(keyPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(keyPayload.get(0).length == CommonConstants.BLOCK_BYTE_LENGTH);
        return keyPayload.get(0);
    }

    private Set<ByteBuffer> runUnionPeel() throws MpcAbortException {
        Set<ByteBuffer> union = new HashSet<>(serverElementSize + clientElementSize);
        PeelPhase phase = PeelPhase.MAIN;
        boolean[] mainPeeled = new boolean[clientMainSketch.tableSize()];
        boolean[] auxiliaryPeeled = config.isTwoTier() ? new boolean[clientAuxiliarySketch.tableSize()] : null;
        int maxRound = Math.max(
            1, clientMainSketch.tableSize() + (config.isTwoTier() ? clientAuxiliarySketch.tableSize() : 0)
        );
        long nextExtraInfo = extraInfo;
        try {
            int[] probeIndexes = SogsPsuUtils.allUnpeeledPositions(mainPeeled);
            for (int round = 0; round <= maxRound; round++) {
                long roundExtraInfo = nextExtraInfo++;
                if (probeIndexes.length == 0) {
                    PhaseBoundaryResult boundary = handlePhaseBoundary(roundExtraInfo, phase, union);
                    if (boundary == PhaseBoundaryResult.FINISH) {
                        return union;
                    }
                    phase = PeelPhase.AUXILIARY;
                    probeIndexes = SogsPsuUtils.allUnpeeledPositions(auxiliaryPeeled);
                    continue;
                }
                sendClientSingletonElements(roundExtraInfo, phase, probeIndexes);
                byte[][] unionMessages = receiveUnionPeelMessages(roundExtraInfo, phase, probeIndexes);
                List<byte[]> peeledPayload = handleUnionMessages(
                    round, phase, probeIndexes, unionMessages, union, activePeeled(phase, mainPeeled, auxiliaryPeeled)
                );
                sendPeeledElements(roundExtraInfo, peeledPayload);
                if (peeledPayload.isEmpty()) {
                    PhaseBoundaryResult boundary = handlePhaseBoundary(roundExtraInfo, phase, union);
                    if (boundary == PhaseBoundaryResult.FINISH) {
                        return union;
                    }
                    phase = PeelPhase.AUXILIARY;
                    probeIndexes = SogsPsuUtils.allUnpeeledPositions(auxiliaryPeeled);
                    continue;
                }
                probeIndexes = nextProbeIndexes(phase, peeledPayload, activePeeled(phase, mainPeeled, auxiliaryPeeled));
            }
            throw new MpcAbortException("SOGS union peel exceeds the maximum round number");
        } finally {
            extraInfo = nextExtraInfo;
        }
    }

    private void sendClientSingletonElements(long roundExtraInfo, PeelPhase phase, int[] probeIndexes)
        throws MpcAbortException {
        int probeNum = probeIndexes.length;
        SogsPsuSketchBackend activeSketch = activeSketch(phase);
        int[] counts = activeSketch.counts();
        boolean[] pureSingletons = activeSketch.pureSingletons();
        byte[][] values = activeSketch.valueSums();
        byte[][] message0 = new byte[probeNum][elementByteLength];
        byte[][] message1 = new byte[probeNum][elementByteLength];
        for (int i = 0; i < probeNum; i++) {
            int index = probeIndexes[i];
            if (counts[index] == 1 && pureSingletons[index]) {
                message1[i] = SogsPsuUtils.encodeOptionalElement(values[index], elementByteLength);
            }
        }
        CotSenderOutput cotSenderOutput = singletonCotSender.send(probeNum);
        List<byte[]> singletonPayload = SogsPsuUtils.generateCotPayload(
            envType, cotSenderOutput, message0, message1, Byte.BYTES + elementByteLength
        );
        DataPacketHeader singletonHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_SINGLETON_ELEMENTS.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(singletonHeader, singletonPayload));
    }

    private byte[][] receiveUnionPeelMessages(long roundExtraInfo, PeelPhase phase, int[] probeIndexes)
        throws MpcAbortException {
        int probeNum = probeIndexes.length;
        SogsPsuSketchBackend activeSketch = activeSketch(phase);
        int[] counts = activeSketch.counts();
        boolean[] pureSingletons = activeSketch.pureSingletons();
        boolean[] choices = new boolean[probeNum];
        for (int i = 0; i < probeNum; i++) {
            int index = probeIndexes[i];
            choices[i] = counts[index] == 1 && pureSingletons[index];
        }
        CotReceiverOutput cotReceiverOutput = unionCotReceiver.receive(choices);
        DataPacketHeader unionHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_UNION_PEEL_MESSAGES.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> unionPayload = rpc.receive(unionHeader).getPayload();
        int messageByteLength = Math.max(Byte.BYTES + elementByteLength, CommonConstants.BLOCK_BYTE_LENGTH);
        return SogsPsuUtils.handleCotPayload(envType, cotReceiverOutput, unionPayload, messageByteLength);
    }

    private List<byte[]> handleUnionMessages(int round, PeelPhase phase, int[] probeIndexes, byte[][] unionMessages,
                                             Set<ByteBuffer> union, boolean[] peeled) {
        SogsPsuSketchBackend activeSketch = activeSketch(phase);
        SogsPsuSketchBackend activeSeedSketch = activeSeedSketch(phase);
        int[] counts = activeSketch.counts();
        boolean[] pureSingletons = activeSketch.pureSingletons();
        byte[][] values = activeSketch.valueSums();
        byte[][] seedValues = activeSeedSketch.valueSums();
        List<byte[]> peeledPayload = new ArrayList<>();
        for (int i = 0; i < probeIndexes.length; i++) {
            int index = probeIndexes[i];
            int globalIndex = toGlobalIndex(phase, index);
            byte[] element = null;
            if (counts[index] == 0) {
                element = SogsPsuUtils.decodeOptionalElement(unionMessages[i], elementByteLength);
            } else if (counts[index] == 1 && pureSingletons[index]) {
                byte[] expectTag = SogsPsuUtils.seedTag(envType, seedValues[index], round, globalIndex);
                byte[] actualTag = Arrays.copyOf(unionMessages[i], CommonConstants.BLOCK_BYTE_LENGTH);
                if (Arrays.equals(expectTag, actualTag)) {
                    element = values[index];
                }
            }
            if (element != null) {
                peeled[index] = true;
                union.add(ByteBuffer.wrap(BytesUtils.clone(element)));
                peeledPayload.add(SogsPsuUtils.encodePeeledElement(globalIndex, element));
                removeClientElement(element);
            }
        }
        return peeledPayload;
    }

    private void removeClientElement(byte[] element) {
        ByteBuffer elementBuffer = ByteBuffer.wrap(element);
        if (clientElementSet.contains(elementBuffer) && clientRemainSet.remove(elementBuffer)) {
            byte[] seed = clientSeedMap.get(elementBuffer);
            long key = SogsPsuUtils.elementKey(envType, element);
            clientMainSketch.remove(key, element);
            clientMainSeedSketch.remove(key, seed);
            if (config.isTwoTier()) {
                clientAuxiliarySketch.remove(key, element);
                clientAuxiliarySeedSketch.remove(key, seed);
            }
        }
    }

    private int[] nextProbeIndexes(PeelPhase phase, List<byte[]> peeledPayload, boolean[] peeled) {
        long[] peeledKeys = SogsPsuUtils.peeledElementKeys(envType, peeledPayload, elementByteLength);
        return activeSketch(phase).uniquePositions(peeledKeys, peeled);
    }

    private void sendPeeledElements(long roundExtraInfo, List<byte[]> peeledPayload) {
        DataPacketHeader peeledHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PEELED_ELEMENTS.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(peeledHeader, peeledPayload));
    }

    private void receiveFinish(long roundExtraInfo) throws MpcAbortException {
        DataPacketHeader finishHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_FINISH.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> finishPayload = rpc.receive(finishHeader).getPayload();
        MpcAbortPreconditions.checkArgument(finishPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(finishPayload.get(0).length == 1 && finishPayload.get(0)[0] == 1);
        MpcAbortPreconditions.checkArgument(clientRemainSet.isEmpty());
    }

    private PhaseBoundaryResult handlePhaseBoundary(long roundExtraInfo, PeelPhase phase, Set<ByteBuffer> union)
        throws MpcAbortException {
        if (!config.isTwoTier()) {
            receiveFinish(roundExtraInfo);
            union.addAll(clientElementSet);
            return PhaseBoundaryResult.FINISH;
        }
        sendPhaseStatus(roundExtraInfo);
        PhaseBoundaryResult decision = receivePhaseDecision(roundExtraInfo);
        if (decision == PhaseBoundaryResult.FINISH) {
            MpcAbortPreconditions.checkArgument(clientRemainSet.isEmpty());
            union.addAll(clientElementSet);
        } else if (decision == PhaseBoundaryResult.FAIL) {
            throw new MpcAbortException("two-tier SOGS peel failed");
        } else if (phase == PeelPhase.AUXILIARY) {
            throw new MpcAbortException("two-tier SOGS returned auxiliary decision in auxiliary phase");
        }
        return decision;
    }

    private void sendPhaseStatus(long roundExtraInfo) {
        List<byte[]> statusPayload = new ArrayList<>(1);
        statusPayload.add(new byte[]{(byte) (clientRemainSet.isEmpty() ? 1 : 0)});
        DataPacketHeader statusHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PHASE_STATUS.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(statusHeader, statusPayload));
    }

    private PhaseBoundaryResult receivePhaseDecision(long roundExtraInfo) throws MpcAbortException {
        DataPacketHeader decisionHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PHASE_DECISION.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> decisionPayload = rpc.receive(decisionHeader).getPayload();
        MpcAbortPreconditions.checkArgument(decisionPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(decisionPayload.get(0).length == 1);
        return PhaseBoundaryResult.fromCode(decisionPayload.get(0)[0]);
    }

    private SogsPsuSketchBackend activeSketch(PeelPhase phase) {
        return phase == PeelPhase.MAIN ? clientMainSketch : clientAuxiliarySketch;
    }

    private SogsPsuSketchBackend activeSeedSketch(PeelPhase phase) {
        return phase == PeelPhase.MAIN ? clientMainSeedSketch : clientAuxiliarySeedSketch;
    }

    private boolean[] activePeeled(PeelPhase phase, boolean[] mainPeeled, boolean[] auxiliaryPeeled) {
        return phase == PeelPhase.MAIN ? mainPeeled : auxiliaryPeeled;
    }

    private int toGlobalIndex(PeelPhase phase, int localIndex) {
        return SogsPsuUtils.toGlobalIndex(phase, localIndex, clientMainSketch.tableSize());
    }

    private enum PhaseBoundaryResult {
        FINISH(0),
        AUXILIARY(1),
        FAIL(2);

        private final int code;

        PhaseBoundaryResult(int code) {
            this.code = code;
        }

        private static PhaseBoundaryResult fromCode(byte code) throws MpcAbortException {
            for (PhaseBoundaryResult result : values()) {
                if (result.code == code) {
                    return result;
                }
            }
            throw new MpcAbortException("invalid two-tier phase decision: " + code);
        }
    }
}
