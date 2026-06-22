package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanBackend;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 4-party packed replicated Boolean backend with PRSS-compressed sharing and compact opening.
 *
 * <p>AND still uses the same multiply-reshare formula as the first REP4 backend. The optimization here is that the
 * resharing path sends only correction deltas instead of explicit full replicated views.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep4PrssPackedBooleanBackend implements PackedBooleanBackend {
    /**
     * Party number.
     */
    public static final int PARTY_NUM = 4;
    /**
     * Protocol step for input sharing.
     */
    private static final int STEP_SHARE = 31;
    /**
     * Protocol step for opening.
     */
    private static final int STEP_OPEN = 32;
    /**
     * Protocol step for AND resharing.
     */
    private static final int STEP_AND_RESHARE = 33;

    private final Rpc rpc;
    private final Party[] parties;
    private final int ownPartyId;
    private final int batchSize;
    private final int blockNum;
    private final long lastBlockMask;
    private final long taskId;
    private final Rep4PrssSeedManager seedManager;
    private long extraInfo;
    private int networkRoundCount;

    public Rep4PrssPackedBooleanBackend(Rpc rpc, int batchSize, long taskId) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.rpc = rpc;
        parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        if (parties.length != PARTY_NUM) {
            throw new IllegalArgumentException("REP4 PRSS packed backend requires exactly 4 parties: "
                + parties.length);
        }
        ownPartyId = rpc.ownParty().getPartyId();
        this.batchSize = batchSize;
        blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
        int lastBits = batchSize & (Long.SIZE - 1);
        lastBlockMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
        this.taskId = taskId;
        seedManager = new Rep4PrssSeedManager(rpc, taskId);
    }

    public int getNetworkRoundCount() {
        return networkRoundCount;
    }

    public void resetNetworkRoundCount() {
        networkRoundCount = 0;
    }

    @Override
    public int blockNum() {
        return blockNum;
    }

    @Override
    public PackedBooleanShare shareOwn(long[] bits) {
        return shareOwnAndReceiveAll(bits)[ownPartyId];
    }

    /**
     * Each party shares its own packed vector and receives shares of every party's vector.
     *
     * @param ownBits own packed bits.
     * @return shares indexed by dealer party id.
     */
    public Rep4PrssPackedBooleanShare[] shareOwnAndReceiveAll(long[] ownBits) {
        return shareOwnAndReceiveAll(ownBits, STEP_SHARE);
    }

    @Override
    public PackedBooleanShare zero() {
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != ownPartyId) {
                components[componentIndex] = new long[blockNum];
            }
        }
        return new Rep4PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare one() {
        Rep4PrssPackedBooleanShare zero = rep4(zero());
        long[][] components = zero.copyComponents();
        if (ownPartyId != 0) {
            Arrays.fill(components[0], -1L);
            components[0][blockNum - 1] &= lastBlockMask;
        }
        return new Rep4PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare xor(PackedBooleanShare x, PackedBooleanShare y) {
        Rep4PrssPackedBooleanShare left = rep4(x);
        Rep4PrssPackedBooleanShare right = rep4(y);
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != ownPartyId) {
                components[componentIndex] = xorBlocks(left.component(componentIndex), right.component(componentIndex));
            }
        }
        return new Rep4PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare not(PackedBooleanShare x) {
        return xor(x, one());
    }

    @Override
    public PackedBooleanShare and(PackedBooleanShare x, PackedBooleanShare y) {
        Rep4PrssPackedBooleanShare left = rep4(x);
        Rep4PrssPackedBooleanShare right = rep4(y);
        long[] localProduct = new long[blockNum];
        for (int leftComponent = 0; leftComponent < PARTY_NUM; leftComponent++) {
            for (int rightComponent = 0; rightComponent < PARTY_NUM; rightComponent++) {
                if (andComputingParty(leftComponent, rightComponent) == ownPartyId) {
                    xorInPlace(localProduct, andBlocks(
                        left.component(leftComponent), right.component(rightComponent)
                    ));
                }
            }
        }
        Rep4PrssPackedBooleanShare[] resharedProducts = shareOwnAndReceiveAll(localProduct, STEP_AND_RESHARE);
        PackedBooleanShare result = zero();
        for (Rep4PrssPackedBooleanShare productShare : resharedProducts) {
            result = xor(result, productShare);
        }
        return result;
    }

    @Override
    public long[] open(PackedBooleanShare x) {
        Rep4PrssPackedBooleanShare share = rep4(x);
        return reconstruct(openCompact(share, blockNum), blockNum, lastBlockMask);
    }

    @Override
    public long[] openSelected(PackedBooleanShare x, int[] selectedIndexes) {
        int compactBlockNum = (selectedIndexes.length + Long.SIZE - 1) / Long.SIZE;
        if (compactBlockNum == 0) {
            return new long[0];
        }
        Rep4PrssPackedBooleanShare share = rep4(x);
        long[][] compactComponents = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            long[] component = share.component(componentIndex);
            if (component != null) {
                compactComponents[componentIndex] = select(component, selectedIndexes, compactBlockNum);
            }
        }
        int lastBits = selectedIndexes.length & (Long.SIZE - 1);
        long compactLastMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
        return reconstruct(
            openCompact(new Rep4PrssPackedBooleanShare(compactComponents, compactBlockNum), compactBlockNum),
            compactBlockNum, compactLastMask
        );
    }

    private Rep4PrssPackedBooleanShare[] shareOwnAndReceiveAll(long[] ownBits, int stepId) {
        checkBlockNum(ownBits);
        long info = extraInfo++;
        long[] ownDelta = correctionDelta(maskValidBits(ownBits), ownPartyId, stepId, info);
        int ownCorrectionComponent = correctionComponent(ownPartyId, info);
        sendCorrectionDelta(ownDelta, stepId, info, ownCorrectionComponent);
        long[][] correctionDeltas = new long[PARTY_NUM][];
        correctionDeltas[ownPartyId] = ownDelta;
        for (Party party : parties) {
            int dealerId = party.getPartyId();
            if (dealerId != ownPartyId && holdsCorrectionComponent(dealerId, info)) {
                correctionDeltas[dealerId] = receiveCorrectionDelta(dealerId, stepId, info);
            }
        }
        Rep4PrssPackedBooleanShare[] sharesByDealer = new Rep4PrssPackedBooleanShare[PARTY_NUM];
        for (int dealerId = 0; dealerId < PARTY_NUM; dealerId++) {
            sharesByDealer[dealerId] = buildShareForDealer(dealerId, stepId, info, correctionDeltas[dealerId]);
        }
        networkRoundCount++;
        return sharesByDealer;
    }

    private long[] correctionDelta(long[] bits, int dealerId, int stepId, long info) {
        long[] delta = Arrays.copyOf(bits, blockNum);
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != dealerId) {
                xorInPlace(delta, prssComponent(componentIndex, stepId, info, dealerId));
            }
        }
        delta[blockNum - 1] &= lastBlockMask;
        return delta;
    }

    private void sendCorrectionDelta(long[] delta, int stepId, long info, int correctionComponent) {
        for (Party party : parties) {
            int partyId = party.getPartyId();
            if (partyId != ownPartyId && partyId != correctionComponent) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    ownPartyId, partyId
                );
                rpc.send(DataPacket.fromByteArrayList(header, List.of(encodeBlocks(delta, blockNum))));
            }
        }
    }

    private long[] receiveCorrectionDelta(int dealerId, int stepId, long info) {
        DataPacketHeader header = new DataPacketHeader(
            taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
            dealerId, ownPartyId
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        if (payload.size() != 1) {
            throw new IllegalStateException("invalid REP4 PRSS correction payload size: " + payload.size());
        }
        return decodeBlocks(payload.get(0), blockNum);
    }

    private boolean holdsCorrectionComponent(int dealerId, long info) {
        int correctionComponent = correctionComponent(dealerId, info);
        return ownPartyId != dealerId && ownPartyId != correctionComponent;
    }

    private Rep4PrssPackedBooleanShare buildShareForDealer(int dealerId, int stepId, long info,
                                                          long[] correctionDelta) {
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex == ownPartyId) {
                components[componentIndex] = null;
            } else if (componentIndex == dealerId) {
                components[componentIndex] = new long[blockNum];
            } else {
                components[componentIndex] = prssComponent(componentIndex, stepId, info, dealerId);
            }
        }
        int correctionComponent = correctionComponent(dealerId, info);
        if (correctionComponent != ownPartyId) {
            if (correctionDelta == null) {
                throw new IllegalStateException("missing REP4 PRSS correction delta from dealer " + dealerId);
            }
            xorInPlace(components[correctionComponent], correctionDelta);
            components[correctionComponent][blockNum - 1] &= lastBlockMask;
        }
        return new Rep4PrssPackedBooleanShare(components, blockNum);
    }

    private long[][] openCompact(Rep4PrssPackedBooleanShare share, int openBlockNum) {
        long info = extraInfo++;
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (openHolder(componentIndex) == ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                    ownPartyId, componentIndex
                );
                rpc.send(DataPacket.fromByteArrayList(
                    header, List.of(encodeBlocks(share.component(componentIndex), openBlockNum))
                ));
            }
        }
        long[][] components = share.copyComponents();
        if (components[ownPartyId] == null) {
            DataPacketHeader header = new DataPacketHeader(
                taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                openHolder(ownPartyId), ownPartyId
            );
            List<byte[]> payload = rpc.receive(header).getPayload();
            if (payload.size() != 1) {
                throw new IllegalStateException("invalid REP4 PRSS open payload size: " + payload.size());
            }
            components[ownPartyId] = decodeBlocks(payload.get(0), openBlockNum);
        }
        networkRoundCount++;
        return components;
    }

    private long[] reconstruct(long[][] components, int componentBlockNum, long finalMask) {
        long[] result = new long[componentBlockNum];
        for (long[] component : components) {
            if (component == null) {
                throw new IllegalStateException("missing REP4 PRSS component during reconstruct");
            }
            xorInPlace(result, component);
        }
        result[componentBlockNum - 1] &= finalMask;
        return result;
    }

    private int correctionComponent(int dealerId, long info) {
        int offset = (int) (Math.floorMod(info, PARTY_NUM - 1) + 1);
        int component = (dealerId + offset) % PARTY_NUM;
        if (component == dealerId) {
            throw new IllegalStateException("invalid REP4 PRSS correction component");
        }
        return component;
    }

    private int openHolder(int componentId) {
        return (componentId + 1) % PARTY_NUM;
    }

    private long[] prssComponent(int componentIndex, int stepId, long info, int dealerId) {
        long[] component = seedManager.componentRandom(componentIndex, stepId, info, dealerId, blockNum);
        component[blockNum - 1] &= lastBlockMask;
        return component;
    }

    private byte[] encodeBlocks(long[] blocks, int componentBlockNum) {
        if (blocks.length != componentBlockNum) {
            throw new IllegalArgumentException("invalid REP4 PRSS block length: " + blocks.length
                + ", expected " + componentBlockNum);
        }
        ByteBuffer buffer = ByteBuffer.allocate(componentBlockNum * Long.BYTES);
        for (long block : blocks) {
            buffer.putLong(block);
        }
        return buffer.array();
    }

    private long[] decodeBlocks(byte[] encoded, int componentBlockNum) {
        if (encoded.length != componentBlockNum * Long.BYTES) {
            throw new IllegalArgumentException("invalid REP4 PRSS block payload length: " + encoded.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        long[] blocks = new long[componentBlockNum];
        for (int blockIndex = 0; blockIndex < componentBlockNum; blockIndex++) {
            blocks[blockIndex] = buffer.getLong();
        }
        return blocks;
    }

    private int andComputingParty(int leftComponent, int rightComponent) {
        for (int partyId = 0; partyId < PARTY_NUM; partyId++) {
            if (partyId != leftComponent && partyId != rightComponent) {
                return partyId;
            }
        }
        throw new IllegalStateException("no REP4 PRSS party holds components " + leftComponent
            + " and " + rightComponent);
    }

    private Rep4PrssPackedBooleanShare rep4(PackedBooleanShare share) {
        if (!(share instanceof Rep4PrssPackedBooleanShare rep4Share)) {
            throw new IllegalArgumentException("expected Rep4PrssPackedBooleanShare: "
                + share.getClass().getName());
        }
        return rep4Share;
    }

    private long[] maskValidBits(long[] blocks) {
        long[] copy = Arrays.copyOf(blocks, blocks.length);
        copy[blockNum - 1] &= lastBlockMask;
        return copy;
    }

    private void checkBlockNum(long[] blocks) {
        if (blocks.length != blockNum) {
            throw new IllegalArgumentException("invalid block length: " + blocks.length + ", expected " + blockNum);
        }
    }

    private long[] xorBlocks(long[] left, long[] right) {
        checkBlockNum(left);
        checkBlockNum(right);
        long[] result = new long[blockNum];
        for (int i = 0; i < blockNum; i++) {
            result[i] = left[i] ^ right[i];
        }
        result[blockNum - 1] &= lastBlockMask;
        return result;
    }

    private long[] andBlocks(long[] left, long[] right) {
        checkBlockNum(left);
        checkBlockNum(right);
        long[] result = new long[blockNum];
        for (int i = 0; i < blockNum; i++) {
            result[i] = left[i] & right[i];
        }
        result[blockNum - 1] &= lastBlockMask;
        return result;
    }

    private long[] select(long[] blocks, int[] selectedIndexes, int compactBlockNum) {
        long[] selected = new long[compactBlockNum];
        for (int selectedIndex = 0; selectedIndex < selectedIndexes.length; selectedIndex++) {
            int laneIndex = selectedIndexes[selectedIndex];
            if (laneIndex < 0 || laneIndex >= batchSize) {
                throw new IllegalArgumentException("selected lane out of range: " + laneIndex);
            }
            if (((blocks[laneIndex >>> 6] >>> (laneIndex & (Long.SIZE - 1))) & 1L) != 0L) {
                selected[selectedIndex >>> 6] |= 1L << (selectedIndex & (Long.SIZE - 1));
            }
        }
        return selected;
    }

    private static void xorInPlace(long[] target, long[] other) {
        for (int i = 0; i < target.length; i++) {
            target[i] ^= other[i];
        }
    }
}
