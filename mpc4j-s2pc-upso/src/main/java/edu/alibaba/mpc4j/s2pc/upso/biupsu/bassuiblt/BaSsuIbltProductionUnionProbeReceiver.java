package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Legacy receiver-side fail-closed capsule validator.
 *
 * <p>The previous candidate decoded the sender capsule locally. P36 removes that path from production code. This class
 * remains fail-closed for regression tests and deliberately does not implement {@link BaSsuIbltUpBaUpotReceiver}.
 * The production UP-BA-UPOT receiver API is the RPC-backed {@link BaSsuIbltRpcUpBaUpotReceiver}.</p>
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
     * configured fixed offline schedule.
     */
    private final BaSsuIbltUpBaUpotOfflineSchedule configuredSchedule;
    /**
     * active fixed offline schedule after init.
     */
    private BaSsuIbltUpBaUpotOfflineSchedule activeSchedule;
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

    BaSsuIbltProductionUnionProbeReceiver(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed,
                                          BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        this(config, seed, BaSsuIbltProductionUnionProbeLocalLayer.SHADOW, schedule);
    }

    BaSsuIbltProductionUnionProbeReceiver(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed,
                                          BaSsuIbltProductionUnionProbeLocalLayer localLayer,
                                          BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (localLayer == null) {
            throw new IllegalArgumentException("localLayer must be non-null");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("explicit finite offline schedule is required");
        }
        this.config = config;
        codec = new BaSsuIbltProductionUnionProbeCodec(config, seed);
        this.localLayer = localLayer;
        configuredSchedule = schedule;
    }

    @Override
    public void init(int maxProbeNum, int elementByteLength) {
        if (maxProbeNum <= 0) {
            throw new IllegalArgumentException("maxProbeNum must be positive");
        }
        if (elementByteLength != config.getElementByteLength()) {
            throw new IllegalArgumentException("elementByteLength must match config");
        }
        activeSchedule = configuredSchedule;
        validateSchedule(activeSchedule, maxProbeNum, elementByteLength);
        this.maxProbeNum = activeSchedule.getMaterialCount();
        probeNum = 0;
        initialized = true;
    }

    public void init(int maxProbeNum) {
        init(maxProbeNum, config.getElementByteLength());
    }

    public void init(BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        activeSchedule = schedule;
        validateSchedule(activeSchedule, schedule.getMaxProbeNum(), config.getElementByteLength());
        maxProbeNum = schedule.getMaterialCount();
        probeNum = 0;
        initialized = true;
    }

    @Override
    public BaSsuIbltUnionProbeOutput probe(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput,
                                           BaSsuIbltUnionProbeCapsule senderCapsule) throws MpcAbortException {
        throw new MpcAbortException(
            "legacy bucket-only production union-probe path is disabled; use UP-BA-UPOT public input"
        );
    }

    BaSsuIbltProductionUnionProbeOutput probeProduction(int bucketIndex, BaSsuIbltSecureBucketInput bucketInput,
                                                        BaSsuIbltProductionUnionProbeCapsule senderCapsule)
        throws MpcAbortException {
        throw new MpcAbortException(
            "legacy bucket-only production union-probe path is disabled; use UP-BA-UPOT public input"
        );
    }

    public BaSsuIbltProductionUnionProbeOutput probe(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                     BaSsuIbltUpBaUpotLocalInput localInput)
        throws MpcAbortException {
        checkReady(publicInput, localInput);
        probeNum++;
        throw new MpcAbortException(
            "production UP-BA-UPOT receiver fixed-result step requires an RPC-backed remote-state-hiding evaluator"
        );
    }

    BaSsuIbltProductionUnionProbeOutput validateCapsuleAndFailClosed(
        BaSsuIbltUpBaUpotPublicInput publicInput, BaSsuIbltUpBaUpotLocalInput localInput,
        BaSsuIbltProductionUnionProbeCapsule senderCapsule)
        throws MpcAbortException {
        checkReady(publicInput, localInput);
        if (senderCapsule == null || senderCapsule.getBucketIndex() != publicInput.getBucketIndex()) {
            throw new IllegalArgumentException("senderCapsule index must match public bucketIndex");
        }
        codec.validate(activeSchedule, publicInput, senderCapsule);
        probeNum++;
        codec.encode(activeSchedule, publicInput, localInput.toCellView());
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

    private void checkReady(BaSsuIbltUpBaUpotPublicInput publicInput, BaSsuIbltUpBaUpotLocalInput localInput)
        throws MpcAbortException {
        if (!initialized) {
            throw new MpcAbortException("production receiver is not initialized");
        }
        if (probeNum == maxProbeNum) {
            throw new MpcAbortException("production receiver exceeded maxProbeNum");
        }
        checkPublicInput(publicInput);
        if (materialOrdinal(publicInput) != probeNum) {
            throw new IllegalArgumentException("publicInput material ordinal must equal the current public probe counter");
        }
        if (localInput == null) {
            throw new IllegalArgumentException("localInput must be non-null");
        }
        localInput.validatePublicInput(publicInput);
    }

    private void checkPublicInput(BaSsuIbltUpBaUpotPublicInput publicInput) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        activeSchedule.validate(publicInput);
        if (publicInput.getElementByteLength() != config.getElementByteLength()
            || publicInput.getTagByteLength() != config.getTagByteLength()
            || publicInput.getCheckByteLength() != config.getCheckByteLength()
            || publicInput.getAuthTagByteLength() != config.getAuthTagByteLength()) {
            throw new IllegalArgumentException("publicInput lengths must match config");
        }
    }

    private void validateSchedule(BaSsuIbltUpBaUpotOfflineSchedule schedule, int maxProbeNum, int elementByteLength) {
        if (schedule.getMaxProbeNum() != maxProbeNum) {
            throw new IllegalArgumentException("offline schedule maxProbeNum must match init maxProbeNum");
        }
        if (schedule.getElementByteLength() != elementByteLength
            || schedule.getTagByteLength() != config.getTagByteLength()
            || schedule.getCheckByteLength() != config.getCheckByteLength()
            || schedule.getAuthTagByteLength() != config.getAuthTagByteLength()) {
            throw new IllegalArgumentException("offline schedule shape must match config");
        }
    }

    private int materialOrdinal(BaSsuIbltUpBaUpotPublicInput publicInput) {
        return Math.addExact(
            Math.multiplyExact(publicInput.getRetryId(), activeSchedule.getMaxProbeNum()),
            publicInput.getProbeOrdinal()
        );
    }
}
