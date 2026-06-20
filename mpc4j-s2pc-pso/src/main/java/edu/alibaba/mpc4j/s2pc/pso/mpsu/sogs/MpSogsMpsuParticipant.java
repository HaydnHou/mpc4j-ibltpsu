package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;

import java.util.List;
import java.util.Set;

/**
 * Symmetric MP-SOGS MPSU participant entry point for the clear prototype.
 *
 * <p>This class intentionally has no server/client role. The current clear prototype returns the same
 * all-party union output for each participant.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuParticipant {
    /**
     * Participant index.
     */
    private final int partyIndex;

    public MpSogsMpsuParticipant(int partyIndex) {
        if (partyIndex < 0) {
            throw new IllegalArgumentException("partyIndex must be non-negative");
        }
        this.partyIndex = partyIndex;
    }

    public int getPartyIndex() {
        return partyIndex;
    }

    public Set<Long> clearMpsu(List<Set<Long>> allPartyInputs, MpSogsMpsuParams params) {
        if (partyIndex >= allPartyInputs.size()) {
            throw new IllegalArgumentException("partyIndex exceeds party input size");
        }
        return BatchMpSogsMpsu.runDummyClear(allPartyInputs, params).getUnionOutput();
    }

    public Set<Long> localMpsu(List<Set<Long>> allPartyInputs, MpSogsMpsuConfig config) {
        if (partyIndex >= allPartyInputs.size()) {
            throw new IllegalArgumentException("partyIndex exceeds party input size");
        }
        return MpSogsMpsuFactory.createLocalRunner(config).run(allPartyInputs).getUnionOutput();
    }

    public Set<Long> abb3Mpsu(TripletZ2cParty z2cParty, Set<Long> localInput, MpSogsMpsuConfig config) {
        return abb3MpsuTranscript(z2cParty, localInput, config).getUnionOutput();
    }

    public MpSogsTranscript abb3MpsuTranscript(TripletZ2cParty z2cParty, Set<Long> localInput,
                                               MpSogsMpsuConfig config) {
        if (z2cParty.ownParty().getPartyId() != partyIndex) {
            throw new IllegalArgumentException("z2c party id must match participant index");
        }
        return MpSogsMpsuFactory.createAbb3PartyRunner(z2cParty, config).run(localInput);
    }
}
