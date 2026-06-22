package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanBackend;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PackedBooleanShare;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 4-party packed replicated Boolean backend.
 *
 * <p>This is the first secure REP4 backend for MP-SOGS uPeel. AND currently uses packed multiply-reshare; later work
 * can replace it with packed Beaver triples without changing the packed uPeel circuit.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class Rep4PackedBooleanBackend implements PackedBooleanBackend {
    /**
     * Party number.
     */
    public static final int PARTY_NUM = 4;
    /**
     * Protocol step for input sharing.
     */
    private static final int STEP_SHARE = 20;
    /**
     * Protocol step for opening.
     */
    private static final int STEP_OPEN = 21;
    /**
     * Protocol step for AND resharing.
     */
    private static final int STEP_AND_RESHARE = 22;

    private final Rpc rpc;
    private final Party[] parties;
    private final int ownPartyId;
    private final int batchSize;
    private final int blockNum;
    private final long lastBlockMask;
    private final long taskId;
    private final SecureRandom secureRandom;
    private long extraInfo;
    private int networkRoundCount;

    public Rep4PackedBooleanBackend(Rpc rpc, int batchSize, long taskId) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        this.rpc = rpc;
        parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        if (parties.length != PARTY_NUM) {
            throw new IllegalArgumentException("REP4 packed backend requires exactly 4 parties: " + parties.length);
        }
        ownPartyId = rpc.ownParty().getPartyId();
        this.batchSize = batchSize;
        blockNum = (batchSize + Long.SIZE - 1) / Long.SIZE;
        int lastBits = batchSize & (Long.SIZE - 1);
        lastBlockMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
        this.taskId = taskId;
        secureRandom = new SecureRandom();
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
    public Rep4PackedBooleanShare[] shareOwnAndReceiveAll(long[] ownBits) {
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
        return new Rep4PackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare one() {
        Rep4PackedBooleanShare zero = rep4(zero());
        long[][] components = zero.copyComponents();
        if (ownPartyId != 0) {
            Arrays.fill(components[0], -1L);
            components[0][blockNum - 1] &= lastBlockMask;
        }
        return new Rep4PackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare xor(PackedBooleanShare x, PackedBooleanShare y) {
        Rep4PackedBooleanShare left = rep4(x);
        Rep4PackedBooleanShare right = rep4(y);
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != ownPartyId) {
                components[componentIndex] = xorBlocks(left.component(componentIndex), right.component(componentIndex));
            }
        }
        return new Rep4PackedBooleanShare(components, blockNum);
    }

    @Override
    public PackedBooleanShare not(PackedBooleanShare x) {
        return xor(x, one());
    }

    @Override
    public PackedBooleanShare and(PackedBooleanShare x, PackedBooleanShare y) {
        Rep4PackedBooleanShare left = rep4(x);
        Rep4PackedBooleanShare right = rep4(y);
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
        Rep4PackedBooleanShare[] resharedProducts = shareOwnAndReceiveAll(localProduct, STEP_AND_RESHARE);
        PackedBooleanShare result = zero();
        for (Rep4PackedBooleanShare productShare : resharedProducts) {
            result = xor(result, productShare);
        }
        return result;
    }

    @Override
    public long[] open(PackedBooleanShare x) {
        Rep4PackedBooleanShare share = rep4(x);
        long[][] openedComponents = openComponents(share, blockNum);
        long[] result = new long[blockNum];
        for (long[] component : openedComponents) {
            xorInPlace(result, component);
        }
        result[blockNum - 1] &= lastBlockMask;
        return result;
    }

    @Override
    public long[] openSelected(PackedBooleanShare x, int[] selectedIndexes) {
        int compactBlockNum = (selectedIndexes.length + Long.SIZE - 1) / Long.SIZE;
        if (compactBlockNum == 0) {
            return new long[0];
        }
        Rep4PackedBooleanShare share = rep4(x);
        long[][] compactComponents = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            long[] component = share.component(componentIndex);
            if (component != null) {
                compactComponents[componentIndex] = select(component, selectedIndexes, compactBlockNum);
            }
        }
        long[][] openedComponents = openComponents(
            new Rep4PackedBooleanShare(compactComponents, compactBlockNum), compactBlockNum
        );
        long[] result = new long[compactBlockNum];
        for (long[] component : openedComponents) {
            xorInPlace(result, component);
        }
        int lastBits = selectedIndexes.length & (Long.SIZE - 1);
        long compactLastMask = lastBits == 0 ? -1L : (1L << lastBits) - 1L;
        result[compactBlockNum - 1] &= compactLastMask;
        return result;
    }

    private Rep4PackedBooleanShare[] shareOwnAndReceiveAll(long[] ownBits, int stepId) {
        checkBlockNum(ownBits);
        long info = extraInfo++;
        long[][] dealtComponents = sampleComponents(maskValidBits(ownBits));
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    ownPartyId, party.getPartyId()
                );
                rpc.send(DataPacket.fromByteArrayList(
                    header, List.of(encodeComponents(viewForRecipient(dealtComponents, party.getPartyId()), blockNum))
                ));
            }
        }
        Rep4PackedBooleanShare[] sharesByDealer = new Rep4PackedBooleanShare[PARTY_NUM];
        sharesByDealer[ownPartyId] = new Rep4PackedBooleanShare(
            viewForRecipient(dealtComponents, ownPartyId), blockNum
        );
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1) {
                    throw new IllegalStateException("invalid REP4 share payload size: " + payload.size());
                }
                sharesByDealer[party.getPartyId()] = new Rep4PackedBooleanShare(
                    decodeComponents(payload.get(0), ownPartyId), blockNum
                );
            }
        }
        networkRoundCount++;
        return sharesByDealer;
    }

    private long[][] openComponents(Rep4PackedBooleanShare share, int openBlockNum) {
        long info = extraInfo++;
        byte[] ownPayload = encodeComponents(share.copyComponents(), openBlockNum);
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                    ownPartyId, party.getPartyId()
                );
                rpc.send(DataPacket.fromByteArrayList(header, List.of(ownPayload)));
            }
        }
        long[][][] sharesByParty = new long[PARTY_NUM][][];
        sharesByParty[ownPartyId] = share.copyComponents();
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1) {
                    throw new IllegalStateException("invalid REP4 open payload size: " + payload.size());
                }
                sharesByParty[party.getPartyId()] = decodeComponents(payload.get(0), party.getPartyId(), openBlockNum);
            }
        }
        networkRoundCount++;
        long[][] openedComponents = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            for (int partyIndex = 0; partyIndex < PARTY_NUM; partyIndex++) {
                if (partyIndex != componentIndex && sharesByParty[partyIndex][componentIndex] != null) {
                    openedComponents[componentIndex] = sharesByParty[partyIndex][componentIndex];
                    break;
                }
            }
            if (openedComponents[componentIndex] == null) {
                throw new IllegalStateException("missing REP4 opened component " + componentIndex);
            }
        }
        return openedComponents;
    }

    private long[][] sampleComponents(long[] bits) {
        long[][] components = new long[PARTY_NUM][blockNum];
        for (int componentIndex = 0; componentIndex < PARTY_NUM - 1; componentIndex++) {
            for (int blockIndex = 0; blockIndex < blockNum; blockIndex++) {
                components[componentIndex][blockIndex] = secureRandom.nextLong();
            }
            components[componentIndex][blockNum - 1] &= lastBlockMask;
        }
        System.arraycopy(bits, 0, components[PARTY_NUM - 1], 0, blockNum);
        for (int componentIndex = 0; componentIndex < PARTY_NUM - 1; componentIndex++) {
            xorInPlace(components[PARTY_NUM - 1], components[componentIndex]);
        }
        components[PARTY_NUM - 1][blockNum - 1] &= lastBlockMask;
        return components;
    }

    private long[][] viewForRecipient(long[][] components, int recipientId) {
        long[][] view = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            if (componentIndex != recipientId) {
                view[componentIndex] = Arrays.copyOf(components[componentIndex], blockNum);
            }
        }
        return view;
    }

    private byte[] encodeComponents(long[][] components, int componentBlockNum) {
        ByteBuffer buffer = ByteBuffer.allocate(PARTY_NUM * componentBlockNum * Long.BYTES);
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            long[] component = components[componentIndex];
            for (int blockIndex = 0; blockIndex < componentBlockNum; blockIndex++) {
                buffer.putLong(component == null ? 0L : component[blockIndex]);
            }
        }
        return buffer.array();
    }

    private long[][] decodeComponents(byte[] encoded, int missingComponentId) {
        return decodeComponents(encoded, missingComponentId, blockNum);
    }

    private long[][] decodeComponents(byte[] encoded, int missingComponentId, int componentBlockNum) {
        if (encoded.length != PARTY_NUM * componentBlockNum * Long.BYTES) {
            throw new IllegalArgumentException("invalid REP4 component payload length: " + encoded.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        long[][] components = new long[PARTY_NUM][];
        for (int componentIndex = 0; componentIndex < PARTY_NUM; componentIndex++) {
            long[] component = new long[componentBlockNum];
            for (int blockIndex = 0; blockIndex < componentBlockNum; blockIndex++) {
                component[blockIndex] = buffer.getLong();
            }
            components[componentIndex] = componentIndex == missingComponentId ? null : component;
        }
        return components;
    }

    private int andComputingParty(int leftComponent, int rightComponent) {
        for (int partyId = 0; partyId < PARTY_NUM; partyId++) {
            if (partyId != leftComponent && partyId != rightComponent) {
                return partyId;
            }
        }
        throw new IllegalStateException("no REP4 party holds components " + leftComponent + " and " + rightComponent);
    }

    private Rep4PackedBooleanShare rep4(PackedBooleanShare share) {
        if (!(share instanceof Rep4PackedBooleanShare rep4Share)) {
            throw new IllegalArgumentException("expected Rep4PackedBooleanShare: " + share.getClass().getName());
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
