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
     * client element sketch.
     */
    private SogsPsuSketchBackend clientSketch;
    /**
     * client OPRF seed sketch.
     */
    private SogsPsuSketchBackend clientSeedSketch;
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

        clientSketch = null;
        clientSeedSketch = null;
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
        clientSketch = SogsPsuUtils.createSketchBackend(config, threshold, elementByteLength, sketchKey);
        clientSeedSketch = SogsPsuUtils.createSketchBackend(
            config, threshold, CommonConstants.BLOCK_BYTE_LENGTH, sketchKey
        );
        clientElementSet = new HashSet<>(clientElementSize);
        clientRemainSet = new HashSet<>(clientElementSize);
        clientSeedMap = new HashMap<>(clientElementSize);
        for (int index = 0; index < clientInputs.length; index++) {
            byte[] element = clientInputs[index];
            byte[] seed = SogsPsuUtils.seed(envType, mpOprfReceiverOutput.getPrf(index));
            long key = SogsPsuUtils.elementKey(envType, element);
            clientSketch.add(key, element);
            clientSeedSketch.add(key, seed);
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
        boolean[] peeled = new boolean[clientSketch.tableSize()];
        int maxRound = Math.max(1, clientSketch.tableSize());
        long nextExtraInfo = extraInfo;
        try {
            int[] probeIndexes = SogsPsuUtils.allUnpeeledPositions(peeled);
            for (int round = 0; round <= maxRound; round++) {
                long roundExtraInfo = nextExtraInfo++;
                if (probeIndexes.length == 0) {
                    receiveFinish(roundExtraInfo);
                    union.addAll(clientElementSet);
                    return union;
                }
                sendClientSingletonElements(roundExtraInfo, probeIndexes);
                byte[][] unionMessages = receiveUnionPeelMessages(roundExtraInfo, probeIndexes);
                List<byte[]> peeledPayload = handleUnionMessages(round, probeIndexes, unionMessages, union, peeled);
                sendPeeledElements(roundExtraInfo, peeledPayload);
                if (peeledPayload.isEmpty()) {
                    receiveFinish(roundExtraInfo);
                    union.addAll(clientElementSet);
                    return union;
                }
                probeIndexes = nextProbeIndexes(peeledPayload, peeled);
            }
            throw new MpcAbortException("SOGS union peel exceeds the maximum round number");
        } finally {
            extraInfo = nextExtraInfo;
        }
    }

    private void sendClientSingletonElements(long roundExtraInfo, int[] probeIndexes) throws MpcAbortException {
        int probeNum = probeIndexes.length;
        int[] counts = clientSketch.counts();
        boolean[] pureSingletons = clientSketch.pureSingletons();
        byte[][] values = clientSketch.valueSums();
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

    private byte[][] receiveUnionPeelMessages(long roundExtraInfo, int[] probeIndexes) throws MpcAbortException {
        int probeNum = probeIndexes.length;
        int[] counts = clientSketch.counts();
        boolean[] pureSingletons = clientSketch.pureSingletons();
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

    private List<byte[]> handleUnionMessages(int round, int[] probeIndexes, byte[][] unionMessages,
                                             Set<ByteBuffer> union, boolean[] peeled) {
        int[] counts = clientSketch.counts();
        boolean[] pureSingletons = clientSketch.pureSingletons();
        byte[][] values = clientSketch.valueSums();
        byte[][] seedValues = clientSeedSketch.valueSums();
        List<byte[]> peeledPayload = new ArrayList<>();
        for (int i = 0; i < probeIndexes.length; i++) {
            int index = probeIndexes[i];
            byte[] element = null;
            if (counts[index] == 0) {
                element = SogsPsuUtils.decodeOptionalElement(unionMessages[i], elementByteLength);
            } else if (counts[index] == 1 && pureSingletons[index]) {
                byte[] expectTag = SogsPsuUtils.seedTag(envType, seedValues[index], round, index);
                byte[] actualTag = Arrays.copyOf(unionMessages[i], CommonConstants.BLOCK_BYTE_LENGTH);
                if (Arrays.equals(expectTag, actualTag)) {
                    element = values[index];
                }
            }
            if (element != null) {
                peeled[index] = true;
                union.add(ByteBuffer.wrap(BytesUtils.clone(element)));
                peeledPayload.add(SogsPsuUtils.encodePeeledElement(index, element));
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
            clientSketch.remove(key, element);
            clientSeedSketch.remove(key, seed);
        }
    }

    private int[] nextProbeIndexes(List<byte[]> peeledPayload, boolean[] peeled) {
        long[] peeledKeys = SogsPsuUtils.peeledElementKeys(envType, peeledPayload, elementByteLength);
        return clientSketch.uniquePositions(peeledKeys, peeled);
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
}
