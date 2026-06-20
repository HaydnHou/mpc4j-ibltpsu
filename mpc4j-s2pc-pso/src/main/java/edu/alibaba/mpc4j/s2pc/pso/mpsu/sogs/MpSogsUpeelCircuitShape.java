package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Fixed secure-uPeel circuit shape for one public batch.
 *
 * <p>The numbers are implementation targets for a semi-honest backend. They are intentionally independent of
 * local cell states, holder sets, and whether the output is an element or {@code bottom}.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsUpeelCircuitShape {
    /**
     * Number of participants.
     */
    private final int partyNum;
    /**
     * Element bit length.
     */
    private final int elementBitLength;
    /**
     * Public batch size.
     */
    private final int batchSize;

    public MpSogsUpeelCircuitShape(int partyNum, int elementBitLength, int batchSize) {
        if (partyNum < 2) {
            throw new IllegalArgumentException("partyNum must be at least 2: " + partyNum);
        }
        if (elementBitLength <= 0) {
            throw new IllegalArgumentException("elementBitLength must be positive: " + elementBitLength);
        }
        if (batchSize < 0) {
            throw new IllegalArgumentException("batchSize must be non-negative: " + batchSize);
        }
        this.partyNum = partyNum;
        this.elementBitLength = elementBitLength;
        this.batchSize = batchSize;
    }

    public int getPartyNum() {
        return partyNum;
    }

    public int getElementBitLength() {
        return elementBitLength;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Equality checks needed per cell. After a priority candidate is selected, every participant's
     * singleton value is conditionally compared with the candidate.
     *
     * @return equality checks per cell.
     */
    public int getEqualityChecksPerCell() {
        return partyNum;
    }

    /**
     * Candidate muxes needed per cell for deterministic priority selection.
     *
     * @return muxes per cell.
     */
    public int getCandidateMuxesPerCell() {
        return partyNum;
    }

    /**
     * Private input bit-vectors shared by each participant for one batch: singleton flag, heavy flag, and element bits.
     *
     * @return private input bit-vectors per participant.
     */
    public int getPrivateInputVectorsPerParty() {
        return 2 + elementBitLength;
    }

    /**
     * Public output bit-vectors opened for one batch: open flag and masked element bits.
     *
     * @return public output bit-vectors per batch.
     */
    public int getOpenedOutputVectors() {
        return 1 + elementBitLength;
    }

    /**
     * AND depth for one equality check using tree reduction.
     *
     * @return equality AND depth.
     */
    public int getEqualityAndDepth() {
        int depth = 0;
        int remaining = elementBitLength;
        while (remaining > 1) {
            remaining = (remaining + 1) / 2;
            depth++;
        }
        return depth;
    }

    /**
     * AND depth proxy for candidate muxing.
     *
     * @return candidate mux AND depth.
     */
    public int getCandidateMuxAndDepth() {
        return partyNum;
    }

    /**
     * Total private input bits shared by all participants for the batch.
     *
     * @return shared private input bit count.
     */
    public long getTotalPrivateInputBits() {
        return (long) partyNum * getPrivateInputVectorsPerParty() * batchSize;
    }

    /**
     * Total public output bits opened for the batch.
     *
     * @return opened public output bit count.
     */
    public long getTotalOpenedOutputBits() {
        return (long) getOpenedOutputVectors() * batchSize;
    }

    /**
     * Conservative AND-gate proxy per cell for bit-level MPC planning.
     *
     * @return AND-gate proxy per cell.
     */
    public long getAndGateProxyPerCell() {
        long equalityAnds = (long) getEqualityChecksPerCell() * elementBitLength;
        long candidateMuxAnds = (long) getCandidateMuxesPerCell() * elementBitLength;
        long stateLogicAnds = 4L * partyNum;
        long outputMaskAnds = elementBitLength;
        return equalityAnds + candidateMuxAnds + stateLogicAnds + outputMaskAnds;
    }

    public long getTotalAndGateProxy() {
        return getAndGateProxyPerCell() * batchSize;
    }
}
