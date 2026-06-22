package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss;

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
 * 5-party packed replicated Boolean backend with PRSS-compressed sharing and compact opening.
 *
 * <p>AND still uses the same multiply-reshare formula as the first REP5 backend. The optimization here is that the
 * resharing path sends only correction deltas instead of explicit full replicated views.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep5PrssPackedBooleanBackend implements PackedBooleanBackend {
    /**
     * Party number.
     */
    public static final int PARTY_NUM = 5;
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
    /**
     * Domain bit for compact child backends. This keeps PRSS extraInfo disjoint from the parent backend.
     */
    private static final long COMPACT_EXTRA_INFO_DOMAIN = 1L << 60;

    private final Rpc rpc;
    private final Party[] parties;
    private final int ownPartyId;
    private final int batchSize;
    private final int blockNum;
    private final long lastBlockMask;
    private final long taskId;
    private final Rep5PrssSeedManager seedManager;
    private final int[] networkRoundCounter;
    private long extraInfo;

    public Rep5PrssPackedBooleanBackend(Rpc rpc, int batchSize, long taskId) {
        this(rpc, batchSize, taskId, null, new int[]{0}, 0L);
    }

    private Rep5PrssPackedBooleanBackend(Rpc rpc, int batchSize, long taskId, Rep5PrssSeedManager seedManager,
                                         int[] networkRoundCounter, long extraInfo) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.rpc = rpc;
        parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        if (parties.length != PARTY_NUM) {
            throw new IllegalArgumentException("REP5 PRSS packed backend requires exactly 5 parties: "
                + parties.length);
        }
        ownPartyId = rpc.ownParty().getPartyId();
        this.batchSize = batchSize;
        blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
        int lastBits = batchSize & (Long.SIZE - 1);
        lastBlockMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
        this.taskId = taskId;
        this.seedManager = seedManager == null ? new Rep5PrssSeedManager(rpc, taskId) : seedManager;
        this.networkRoundCounter = networkRoundCounter;
        this.extraInfo = extraInfo;
    }

    public int getNetworkRoundCount() {
        return networkRoundCounter[0];
    }

    public void resetNetworkRoundCount() {
        networkRoundCounter[0] = 0;
    }

    @Override
    public int blockNum() {
        return blockNum;
    }

    @Override
    public int batchSize() {
        return batchSize;
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
    public Rep5PrssPackedBooleanShare[] shareOwnAndReceiveAll(long[] ownBits) {
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
        return new Rep5PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare one() {
        Rep5PrssPackedBooleanShare zero = rep5(zero());
        long[][] components = zero.copyComponents();
        if (ownPartyId != 0) {
            Arrays.fill(components[0], -1L);
            components[0][blockNum - 1] &= lastBlockMask;
        }
        return new Rep5PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare xor(PackedBooleanShare x, PackedBooleanShare y) {
        Rep5PrssPackedBooleanShare left = rep5(x);
        Rep5PrssPackedBooleanShare right = rep5(y);
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != ownPartyId) {
                components[componentIndex] = xorBlocks(left.component(componentIndex), right.component(componentIndex));
            }
        }
        return new Rep5PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare not(PackedBooleanShare x) {
        return xor(x, one());
    }

    @Override
    public PackedBooleanShare and(PackedBooleanShare x, PackedBooleanShare y) {
        Rep5PrssPackedBooleanShare left = rep5(x);
        Rep5PrssPackedBooleanShare right = rep5(y);
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
        Rep5PrssPackedBooleanShare[] resharedProducts = shareOwnAndReceiveAll(localProduct, STEP_AND_RESHARE);
        return xorShares(resharedProducts);
    }

    @Override
    public PackedBooleanShare[] andMany(PackedBooleanShare[] xs, PackedBooleanShare[] ys) {
        if (xs.length != ys.length) {
            throw new IllegalArgumentException("xs and ys length mismatch: " + xs.length + " != " + ys.length);
        }
        int num = xs.length;
        PackedBooleanShare[] results = new PackedBooleanShare[num];
        if (num == 0) {
            return results;
        }
        long[][] localProducts = new long[num][blockNum];
        for (int itemIndex = 0; itemIndex < num; itemIndex++) {
            Rep5PrssPackedBooleanShare left = rep5(xs[itemIndex]);
            Rep5PrssPackedBooleanShare right = rep5(ys[itemIndex]);
            for (int leftComponent = 0; leftComponent < PARTY_NUM; leftComponent++) {
                for (int rightComponent = 0; rightComponent < PARTY_NUM; rightComponent++) {
                    if (andComputingParty(leftComponent, rightComponent) == ownPartyId) {
                        xorInPlace(localProducts[itemIndex], andBlocks(
                            left.component(leftComponent), right.component(rightComponent)
                        ));
                    }
                }
            }
        }
        Rep5PrssPackedBooleanShare[][] resharedProducts = shareOwnAndReceiveAllMany(localProducts, STEP_AND_RESHARE);
        for (int itemIndex = 0; itemIndex < num; itemIndex++) {
            results[itemIndex] = xorShares(resharedProducts[itemIndex]);
        }
        return results;
    }

    private Rep5PrssPackedBooleanShare xorShares(Rep5PrssPackedBooleanShare[] shares) {
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != ownPartyId) {
                components[componentIndex] = new long[blockNum];
            }
        }
        for (Rep5PrssPackedBooleanShare share : shares) {
            for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
                if (componentIndex != ownPartyId) {
                    xorInPlace(components[componentIndex], share.component(componentIndex));
                }
            }
        }
        return new Rep5PrssPackedBooleanShare(components, blockNum);
    }

    @Override
    public long[] open(PackedBooleanShare x) {
        Rep5PrssPackedBooleanShare share = rep5(x);
        return reconstruct(openCompact(share, blockNum), blockNum, lastBlockMask);
    }

    @Override
    public long[] openSelected(PackedBooleanShare x, int[] selectedIndexes) {
        int compactBlockNum = (selectedIndexes.length + Long.SIZE - 1) / Long.SIZE;
        if (compactBlockNum == 0) {
            return new long[0];
        }
        Rep5PrssPackedBooleanShare share = rep5(x);
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
            openCompact(new Rep5PrssPackedBooleanShare(compactComponents, compactBlockNum), compactBlockNum),
            compactBlockNum, compactLastMask
        );
    }

    @Override
    public PackedBooleanShare compact(PackedBooleanShare x, int[] selectedIndexes) {
        int compactBlockNum = (selectedIndexes.length + Long.SIZE - 1) / Long.SIZE;
        if (compactBlockNum == 0) {
            throw new IllegalArgumentException("selectedIndexes must be non-empty");
        }
        Rep5PrssPackedBooleanShare share = rep5(x);
        long[][] compactComponents = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            long[] component = share.component(componentIndex);
            if (component != null) {
                compactComponents[componentIndex] = select(component, selectedIndexes, compactBlockNum);
            }
        }
        return new Rep5PrssPackedBooleanShare(compactComponents, compactBlockNum);
    }

    @Override
    public PackedBooleanBackend derive(int compactBatchSize) {
        long compactBaseInfo = COMPACT_EXTRA_INFO_DOMAIN | (extraInfo++ << 20);
        return new Rep5PrssPackedBooleanBackend(
            rpc, compactBatchSize, taskId, seedManager, networkRoundCounter, compactBaseInfo
        );
    }

    private Rep5PrssPackedBooleanShare[] shareOwnAndReceiveAll(long[] ownBits, int stepId) {
        checkBlockNum(ownBits);
        long info = extraInfo++;
        long[] ownDelta = correctionDelta(maskValidBits(ownBits), ownPartyId, stepId, info, info);
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
        Rep5PrssPackedBooleanShare[] sharesByDealer = new Rep5PrssPackedBooleanShare[PARTY_NUM];
        for (int dealerId = 0; dealerId < PARTY_NUM; dealerId++) {
            sharesByDealer[dealerId] = buildShareForDealer(
                dealerId, stepId, info, info, correctionDeltas[dealerId]
            );
        }
        networkRoundCounter[0]++;
        return sharesByDealer;
    }

    private Rep5PrssPackedBooleanShare[][] shareOwnAndReceiveAllMany(long[][] ownBitsArray, int stepId) {
        int num = ownBitsArray.length;
        if (num == 0) {
            return new Rep5PrssPackedBooleanShare[0][];
        }
        for (long[] ownBits : ownBitsArray) {
            checkBlockNum(ownBits);
        }
        long info = extraInfo++;
        long[][] ownDeltas = new long[num][];
        for (int itemIndex = 0; itemIndex < num; itemIndex++) {
            ownDeltas[itemIndex] = correctionDelta(
                maskValidBits(ownBitsArray[itemIndex]), ownPartyId, stepId, info, itemInfo(info, itemIndex)
            );
        }
        int ownCorrectionComponent = correctionComponent(ownPartyId, info);
        sendCorrectionDeltas(ownDeltas, stepId, info, ownCorrectionComponent);
        long[][][] correctionDeltas = new long[PARTY_NUM][][];
        correctionDeltas[ownPartyId] = ownDeltas;
        for (Party party : parties) {
            int dealerId = party.getPartyId();
            if (dealerId != ownPartyId && holdsCorrectionComponent(dealerId, info)) {
                correctionDeltas[dealerId] = receiveCorrectionDeltas(dealerId, stepId, info, num);
            }
        }
        Rep5PrssPackedBooleanShare[][] sharesByItemDealer = new Rep5PrssPackedBooleanShare[num][PARTY_NUM];
        for (int itemIndex = 0; itemIndex < num; itemIndex++) {
            long prssInfo = itemInfo(info, itemIndex);
            for (int dealerId = 0; dealerId < PARTY_NUM; dealerId++) {
                long[] correctionDelta = correctionDeltas[dealerId] == null
                    ? null
                    : correctionDeltas[dealerId][itemIndex];
                sharesByItemDealer[itemIndex][dealerId] = buildShareForDealer(
                    dealerId, stepId, prssInfo, info, correctionDelta
                );
            }
        }
        networkRoundCounter[0]++;
        return sharesByItemDealer;
    }

    private long[] correctionDelta(long[] bits, int dealerId, int stepId, long correctionInfo, long prssInfo) {
        long[] delta = Arrays.copyOf(bits, blockNum);
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != dealerId) {
                xorInPlace(delta, prssComponent(componentIndex, stepId, prssInfo, dealerId));
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

    private void sendCorrectionDeltas(long[][] deltas, int stepId, long info, int correctionComponent) {
        for (Party party : parties) {
            int partyId = party.getPartyId();
            if (partyId != ownPartyId && partyId != correctionComponent) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    ownPartyId, partyId
                );
                rpc.send(DataPacket.fromByteArrayList(header, List.of(encodeManyBlocks(deltas))));
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
            throw new IllegalStateException("invalid REP5 PRSS correction payload size: " + payload.size());
        }
        return decodeBlocks(payload.get(0), blockNum);
    }

    private long[][] receiveCorrectionDeltas(int dealerId, int stepId, long info, int num) {
        DataPacketHeader header = new DataPacketHeader(
            taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
            dealerId, ownPartyId
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        if (payload.size() != 1) {
            throw new IllegalStateException("invalid REP5 PRSS batched correction payload size: " + payload.size());
        }
        return decodeManyBlocks(payload.get(0), num);
    }

    private boolean holdsCorrectionComponent(int dealerId, long info) {
        int correctionComponent = correctionComponent(dealerId, info);
        return ownPartyId != dealerId && ownPartyId != correctionComponent;
    }

    private Rep5PrssPackedBooleanShare buildShareForDealer(int dealerId, int stepId, long prssInfo,
                                                           long correctionInfo, long[] correctionDelta) {
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex == ownPartyId) {
                components[componentIndex] = null;
            } else if (componentIndex == dealerId) {
                components[componentIndex] = new long[blockNum];
            } else {
                components[componentIndex] = prssComponent(componentIndex, stepId, prssInfo, dealerId);
            }
        }
        int correctionComponent = correctionComponent(dealerId, correctionInfo);
        if (correctionComponent != ownPartyId) {
            if (correctionDelta == null) {
                throw new IllegalStateException("missing REP5 PRSS correction delta from dealer " + dealerId);
            }
            xorInPlace(components[correctionComponent], correctionDelta);
            components[correctionComponent][blockNum - 1] &= lastBlockMask;
        }
        return new Rep5PrssPackedBooleanShare(components, blockNum);
    }

    private long itemInfo(long info, int itemIndex) {
        return info ^ (((long) itemIndex + 1) << 32);
    }

    private long[][] openCompact(Rep5PrssPackedBooleanShare share, int openBlockNum) {
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
                throw new IllegalStateException("invalid REP5 PRSS open payload size: " + payload.size());
            }
            components[ownPartyId] = decodeBlocks(payload.get(0), openBlockNum);
        }
        networkRoundCounter[0]++;
        return components;
    }

    private long[] reconstruct(long[][] components, int componentBlockNum, long finalMask) {
        long[] result = new long[componentBlockNum];
        for (long[] component : components) {
            if (component == null) {
                throw new IllegalStateException("missing REP5 PRSS component during reconstruct");
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
            throw new IllegalStateException("invalid REP5 PRSS correction component");
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
            throw new IllegalArgumentException("invalid REP5 PRSS block length: " + blocks.length
                + ", expected " + componentBlockNum);
        }
        ByteBuffer buffer = ByteBuffer.allocate(componentBlockNum * Long.BYTES);
        for (long block : blocks) {
            buffer.putLong(block);
        }
        return buffer.array();
    }

    private byte[] encodeManyBlocks(long[][] blocksArray) {
        ByteBuffer buffer = ByteBuffer.allocate(blocksArray.length * blockNum * Long.BYTES);
        for (long[] blocks : blocksArray) {
            if (blocks.length != blockNum) {
                throw new IllegalArgumentException("invalid REP5 PRSS block length: " + blocks.length
                    + ", expected " + blockNum);
            }
            for (long block : blocks) {
                buffer.putLong(block);
            }
        }
        return buffer.array();
    }

    private long[] decodeBlocks(byte[] encoded, int componentBlockNum) {
        if (encoded.length != componentBlockNum * Long.BYTES) {
            throw new IllegalArgumentException("invalid REP5 PRSS block payload length: " + encoded.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        long[] blocks = new long[componentBlockNum];
        for (int blockIndex = 0; blockIndex < componentBlockNum; blockIndex++) {
            blocks[blockIndex] = buffer.getLong();
        }
        return blocks;
    }

    private long[][] decodeManyBlocks(byte[] encoded, int num) {
        if (encoded.length != num * blockNum * Long.BYTES) {
            throw new IllegalArgumentException("invalid REP5 PRSS batched payload length: " + encoded.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        long[][] blocksArray = new long[num][blockNum];
        for (int itemIndex = 0; itemIndex < num; itemIndex++) {
            for (int blockIndex = 0; blockIndex < blockNum; blockIndex++) {
                blocksArray[itemIndex][blockIndex] = buffer.getLong();
            }
        }
        return blocksArray;
    }

    private int andComputingParty(int leftComponent, int rightComponent) {
        for (int partyId = 0; partyId < PARTY_NUM; partyId++) {
            if (partyId != leftComponent && partyId != rightComponent) {
                return partyId;
            }
        }
        throw new IllegalStateException("no REP5 PRSS party holds components " + leftComponent
            + " and " + rightComponent);
    }

    private Rep5PrssPackedBooleanShare rep5(PackedBooleanShare share) {
        if (!(share instanceof Rep5PrssPackedBooleanShare rep5Share)) {
            throw new IllegalArgumentException("expected Rep5PrssPackedBooleanShare: "
                + share.getClass().getName());
        }
        return rep5Share;
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
