package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Production UP-BA-UPOT sender-side local capsule builder.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeSender implements BaSsuIbltUnionProbeSender {
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

    BaSsuIbltProductionUnionProbeSender(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed) {
        this(config, seed, BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR);
    }

    BaSsuIbltProductionUnionProbeSender(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed,
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
    public BaSsuIbltUnionProbeCapsule probe(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput)
        throws MpcAbortException {
        return probeProduction(bucketIndex, bucketInput).toUnionProbeCapsule();
    }

    BaSsuIbltProductionUnionProbeCapsule probeProduction(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput)
        throws MpcAbortException {
        checkReady(bucketIndex, bucketInput);
        probeNum++;
        return codec.encode(bucketIndex, localLayer.select(bucketInput));
    }

    private void checkReady(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput) throws MpcAbortException {
        if (!initialized) {
            throw new MpcAbortException("production sender is not initialized");
        }
        if (probeNum == maxProbeNum) {
            throw new MpcAbortException("production sender exceeded maxProbeNum");
        }
        if (bucketInput == null || bucketInput.getBucketIndex() != bucketIndex) {
            throw new IllegalArgumentException("bucketInput index must match public bucketIndex");
        }
    }
}
