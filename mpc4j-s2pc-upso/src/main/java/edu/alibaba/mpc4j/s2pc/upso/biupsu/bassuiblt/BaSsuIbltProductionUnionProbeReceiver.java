package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Production UP-BA-UPOT receiver-side fail-closed placeholder.
 *
 * <p>The previous candidate decoded the sender capsule locally. P36 removes that path from production code. This class
 * remains fail-closed until a true remote-state-hiding UP-BA-UPOT evaluator is implemented.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeReceiver implements BaSsuIbltUnionProbeReceiver {
    /**
     * config.
     */
    private final BaSsuIbltProductionUnionProbeBackendConfig config;
    /**
     * codec.
     */
    private final BaSsuIbltProductionUnionProbeCodec codec;
    /**
     * local source layer.
     */
    private final BaSsuIbltProductionUnionProbeLocalLayer localLayer;
    /**
     * max public probes.
     */
    private int maxProbeNum;
    /**
     * executed probes.
     */
    private int probeNum;
    /**
     * initialized.
     */
    private boolean initialized;

    BaSsuIbltProductionUnionProbeReceiver(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed) {
        this(config, seed, BaSsuIbltProductionUnionProbeLocalLayer.SHADOW);
    }

    BaSsuIbltProductionUnionProbeReceiver(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed,
                                          BaSsuIbltProductionUnionProbeLocalLayer localLayer) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (localLayer == null) {
            throw new IllegalArgumentException("localLayer must be non-null");
        }
        this.config = config;
        codec = new BaSsuIbltProductionUnionProbeCodec(config, seed);
        this.localLayer = localLayer;
    }

    @Override
    public void init(int maxProbeNum, int elementByteLength) {
        if (maxProbeNum <= 0) {
            throw new IllegalArgumentException("maxProbeNum must be positive");
        }
        if (elementByteLength != config.getElementByteLength()) {
            throw new IllegalArgumentException("elementByteLength must match config");
        }
        this.maxProbeNum = maxProbeNum;
        probeNum = 0;
        initialized = true;
    }

    @Override
    public BaSsuIbltUnionProbeOutput probe(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput,
                                           BaSsuIbltUnionProbeCapsule senderCapsule) throws MpcAbortException {
        BaSsuIbltProductionUnionProbeCapsule productionSenderCapsule =
            BaSsuIbltProductionUnionProbeCapsule.fromUnionProbeCapsule(senderCapsule, config.capsuleByteLength());
        return probeProduction(bucketIndex, bucketInput, productionSenderCapsule).toUnionProbeOutput();
    }

    BaSsuIbltProductionUnionProbeOutput probeProduction(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput,
                                                        BaSsuIbltProductionUnionProbeCapsule senderCapsule)
        throws MpcAbortException {
        checkReady(bucketIndex, bucketInput);
        if (senderCapsule == null || senderCapsule.getBucketIndex() != bucketIndex) {
            throw new IllegalArgumentException("senderCapsule index must match public bucketIndex");
        }
        probeNum++;
        codec.encode(bucketIndex, localLayer.select(bucketInput));
        throw new MpcAbortException(
            "production union-probe requires true remote-state-hiding UP-BA-UPOT; local capsule decoding is disabled"
        );
    }

    private void checkReady(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput) throws MpcAbortException {
        if (!initialized) {
            throw new MpcAbortException("production receiver is not initialized");
        }
        if (probeNum == maxProbeNum) {
            throw new MpcAbortException("production receiver exceeded maxProbeNum");
        }
        if (bucketInput == null || bucketInput.getBucketIndex() != bucketIndex) {
            throw new IllegalArgumentException("bucketInput index must match public bucketIndex");
        }
    }
}
