package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Receiver fixed-count offline material for queue-peel UP-BA-UPOT.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotOfflineReceiverOutput {
    /**
     * public schedule.
     */
    private final BaSsuIbltUpBaUpotOfflineSchedule schedule;
    /**
     * material derivation helper.
     */
    private final BaSsuIbltUpBaUpotOfflineMaterial material;
    /**
     * private fixed-shape branch choices.
     */
    private final boolean[] branchChoices;
    /**
     * offline send bytes.
     */
    private final long offlineSendBytes;
    /**
     * offline time.
     */
    private final long offlineTimeNanos;

    private BaSsuIbltUpBaUpotOfflineReceiverOutput(BaSsuIbltUpBaUpotOfflineSchedule schedule, byte[] seed,
                                                   boolean[] branchChoices, long offlineSendBytes,
                                                   long offlineTimeNanos) {
        this.schedule = schedule;
        material = new BaSsuIbltUpBaUpotOfflineMaterial(
            schedule, BaSsuIbltUpBaUpotOfflineRole.RECEIVER, seed
        );
        this.branchChoices = Arrays.copyOf(branchChoices, branchChoices.length);
        this.offlineSendBytes = offlineSendBytes;
        this.offlineTimeNanos = offlineTimeNanos;
    }

    public static BaSsuIbltUpBaUpotOfflineReceiverOutput create(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                                byte[] seed, boolean[] branchChoices,
                                                                long offlineTimeNanos) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (seed == null || seed.length == 0) {
            throw new IllegalArgumentException("seed must be non-empty");
        }
        if (branchChoices == null || branchChoices.length != schedule.getMaterialCount()) {
            throw new IllegalArgumentException("branchChoices length must equal fixed material count");
        }
        if (offlineTimeNanos < 0) {
            throw new IllegalArgumentException("offlineTimeNanos must be non-negative");
        }
        return new BaSsuIbltUpBaUpotOfflineReceiverOutput(
            schedule, Arrays.copyOf(seed, seed.length), branchChoices, schedule.getTotalMaterialByteLength(),
            offlineTimeNanos
        );
    }

    public BaSsuIbltUpBaUpotOfflineSchedule getSchedule() {
        return schedule;
    }

    public int getMaterialCount() {
        return schedule.getMaterialCount();
    }

    public long getOfflineSendBytes() {
        return offlineSendBytes;
    }

    public long getOfflineTimeNanos() {
        return offlineTimeNanos;
    }

    boolean branchChoice(int retryId, int probeOrdinal) {
        if (retryId < 0 || retryId >= schedule.getRetryNum()) {
            throw new IllegalArgumentException("retryId must be in range");
        }
        if (probeOrdinal < 0 || probeOrdinal >= schedule.getMaxProbeNum()) {
            throw new IllegalArgumentException("probeOrdinal must be in range");
        }
        return branchChoices[retryId * schedule.getMaxProbeNum() + probeOrdinal];
    }

    byte[] derive(BaSsuIbltUpBaUpotOfflineLabel label, int retryId, int bucketIndex, int probeOrdinal) {
        return material.derive(label, retryId, bucketIndex, probeOrdinal);
    }
}
