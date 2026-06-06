package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Party-local input bundle for one production queue-peel probe.
 *
 * <p>The bundle deliberately contains only one party's local bucket input. It must not be extended into a combined
 * anchor/shadow bucket holder, since production UP-BA-UPOT must not give either party a local opener for the other
 * party's bucket state.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltProductionQueuePeelPartyLocalProbeInput {
    /**
     * public queue probe context.
     */
    private final BaSsuIbltQueuePeelProbeContext context;
    /**
     * public UP-BA-UPOT input.
     */
    private final BaSsuIbltUpBaUpotPublicInput publicInput;
    /**
     * this party's local input.
     */
    private final BaSsuIbltUpBaUpotLocalInput ownLocalInput;
    /**
     * this party's local layer role. Internal domain-separation metadata only.
     */
    private final BaSsuIbltProductionUnionProbeLocalLayer ownLayer;

    private BaSsuIbltProductionQueuePeelPartyLocalProbeInput(
        BaSsuIbltQueuePeelProbeContext context, BaSsuIbltUpBaUpotPublicInput publicInput,
        BaSsuIbltUpBaUpotLocalInput ownLocalInput, BaSsuIbltProductionUnionProbeLocalLayer ownLayer) {
        if (context == null) {
            throw new IllegalArgumentException("context must be non-null");
        }
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (ownLocalInput == null) {
            throw new IllegalArgumentException("ownLocalInput must be non-null");
        }
        if (ownLayer == null) {
            throw new IllegalArgumentException("ownLayer must be non-null");
        }
        if (context.getRetryIndex() != publicInput.getRetryId()
            || context.getBucketIndex() != publicInput.getBucketIndex()
            || context.getProbeOrdinal() != publicInput.getProbeOrdinal()) {
            throw new IllegalArgumentException("context must match publicInput");
        }
        this.context = context;
        this.publicInput = publicInput;
        this.ownLocalInput = ownLocalInput;
        this.ownLayer = ownLayer;
    }

    static BaSsuIbltProductionQueuePeelPartyLocalProbeInput of(
        BaSsuIbltQueuePeelProbeContext context, BaSsuIbltUpBaUpotPublicInput publicInput,
        BaSsuIbltUpBaUpotLocalInput ownLocalInput, BaSsuIbltProductionUnionProbeLocalLayer ownLayer) {
        return new BaSsuIbltProductionQueuePeelPartyLocalProbeInput(
            context, publicInput, ownLocalInput, ownLayer
        );
    }

    BaSsuIbltQueuePeelProbeContext getContext() {
        return context;
    }

    BaSsuIbltUpBaUpotPublicInput getPublicInput() {
        return publicInput;
    }

    BaSsuIbltUpBaUpotLocalInput getOwnLocalInput() {
        return ownLocalInput;
    }

    BaSsuIbltProductionUnionProbeLocalLayer getOwnLayer() {
        return ownLayer;
    }
}
