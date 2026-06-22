package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;

/**
 * PRSS seed manager for 5-party replicated packed Boolean sharing.
 *
 * <p>Component {@code c} is held by all parties except {@code P_c}. The seed for component {@code c} is distributed
 * only to those holders, so {@code P_c} cannot derive {@code r_c}.</p>
 *
 * @author donghai hou
 * @date 2026/06/22
 */
class Rep5PrssSeedManager {
    /**
     * Party number.
     */
    static final int PARTY_NUM = 5;
    /**
     * Seed byte length.
     */
    private static final int SEED_BYTE_LENGTH = 32;
    /**
     * Protocol step for seed setup.
     */
    private static final int STEP_SEED_SETUP = 30;

    private final Rpc rpc;
    private final Party[] parties;
    private final int ownPartyId;
    private final long taskId;
    private final byte[][] componentSeeds;
    private final Rep5PrssRandomSource randomSource;

    Rep5PrssSeedManager(Rpc rpc, long taskId) {
        this.rpc = rpc;
        this.taskId = taskId;
        parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        if (parties.length != PARTY_NUM) {
            throw new IllegalArgumentException("REP5 PRSS requires exactly 5 parties: " + parties.length);
        }
        ownPartyId = rpc.ownParty().getPartyId();
        componentSeeds = new byte[PARTY_NUM][];
        randomSource = new Rep5PrssRandomSource(taskId);
        setupSeeds();
    }

    long[] componentRandom(int componentId, int stepId, long extraInfo, int dealerId, int blockNum) {
        if (componentId == ownPartyId) {
            throw new IllegalArgumentException("party " + ownPartyId + " cannot derive missing component "
                + componentId);
        }
        byte[] seed = componentSeeds[componentId];
        if (seed == null) {
            throw new IllegalStateException("missing PRSS seed for component " + componentId);
        }
        return randomSource.componentRandom(seed, componentId, stepId, extraInfo, dealerId, blockNum);
    }

    private void setupSeeds() {
        SecureRandom secureRandom = new SecureRandom();
        for (int componentId = 0; componentId < PARTY_NUM; componentId++) {
            int ownerId = seedOwner(componentId);
            if (ownPartyId == ownerId) {
                byte[] seed = new byte[SEED_BYTE_LENGTH];
                secureRandom.nextBytes(seed);
                componentSeeds[componentId] = seed;
                for (Party party : parties) {
                    int partyId = party.getPartyId();
                    if (partyId != componentId && partyId != ownerId) {
                        DataPacketHeader header = new DataPacketHeader(
                            taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_SEED_SETUP, componentId,
                            ownPartyId, partyId
                        );
                        rpc.send(DataPacket.fromByteArrayList(header, List.of(seed)));
                    }
                }
            } else if (ownPartyId != componentId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_SEED_SETUP, componentId,
                    ownerId, ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1 || payload.get(0).length != SEED_BYTE_LENGTH) {
                    throw new IllegalStateException("invalid REP5 PRSS seed payload for component " + componentId);
                }
                componentSeeds[componentId] = payload.get(0);
            }
        }
    }

    private static int seedOwner(int componentId) {
        return componentId == 0 ? 1 : 0;
    }
}
