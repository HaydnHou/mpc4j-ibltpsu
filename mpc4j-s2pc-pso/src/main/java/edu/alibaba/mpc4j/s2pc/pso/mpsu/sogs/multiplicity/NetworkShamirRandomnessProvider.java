package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import java.util.HashSet;
import java.util.Set;

/**
 * Regression provider that creates random shares with the original all-dealer network path.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class NetworkShamirRandomnessProvider implements ShamirRandomnessProvider {
    private final Mersenne61ShamirMpc mpc;
    private final Set<ShamirRandomnessDomain> consumedDomains;
    private long generatedElementCount;

    public NetworkShamirRandomnessProvider(Mersenne61ShamirMpc mpc) {
        this.mpc = mpc;
        consumedDomains = new HashSet<>();
    }

    @Override
    public long[] randomDegreeT(ShamirRandomnessDomain domain) {
        register(domain);
        generatedElementCount += domain.vectorLength();
        return mpc.shareOwnAndAggregate(mpc.randomFieldVector(domain.vectorLength()));
    }

    @Override
    public ShamirDoubleShare randomDoubleShare(ShamirRandomnessDomain domain) {
        register(domain);
        generatedElementCount += domain.vectorLength();
        long[] ownRandomness = mpc.randomFieldVector(domain.vectorLength());
        long[] degreeT = mpc.shareOwnAndAggregateAtDegree(ownRandomness, mpc.getThreshold());
        long[] degree2T = mpc.shareOwnAndAggregateAtDegree(ownRandomness, 2 * mpc.getThreshold());
        return new ShamirDoubleShare(degreeT, degree2T);
    }

    @Override
    public long[] randomDegree2TZero(ShamirRandomnessDomain domain) {
        register(domain);
        generatedElementCount += domain.vectorLength();
        return mpc.shareOwnAndAggregateAtDegree(new long[domain.vectorLength()], 2 * mpc.getThreshold());
    }

    @Override
    public long getRequestCount() {
        return consumedDomains.size();
    }

    @Override
    public long getGeneratedElementCount() {
        return generatedElementCount;
    }

    @Override
    public long getSetupSendBytes() {
        return 0L;
    }

    private void register(ShamirRandomnessDomain domain) {
        if (!consumedDomains.add(domain)) {
            throw new IllegalStateException("Shamir randomness domain reused: " + domain);
        }
    }
}
