package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Sender fixed-count offline material for queue-peel UP-BA-UPOT.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotOfflineSenderOutput {
    /**
     * public schedule.
     */
    private final BaSsuIbltUpBaUpotOfflineSchedule schedule;
    /**
     * material derivation helper.
     */
    private final BaSsuIbltUpBaUpotOfflineMaterial material;
    /**
     * offline send bytes.
     */
    private final long offlineSendBytes;
    /**
     * offline time.
     */
    private final long offlineTimeNanos;

    private BaSsuIbltUpBaUpotOfflineSenderOutput(BaSsuIbltUpBaUpotOfflineSchedule schedule, byte[] seed,
                                                 long offlineSendBytes, long offlineTimeNanos) {
        this.schedule = schedule;
        material = new BaSsuIbltUpBaUpotOfflineMaterial(
            schedule, BaSsuIbltUpBaUpotOfflineRole.SENDER, seed
        );
        this.offlineSendBytes = offlineSendBytes;
        this.offlineTimeNanos = offlineTimeNanos;
    }

    public static BaSsuIbltUpBaUpotOfflineSenderOutput create(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                              byte[] seed, long offlineTimeNanos) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (seed == null || seed.length == 0) {
            throw new IllegalArgumentException("seed must be non-empty");
        }
        if (offlineTimeNanos < 0) {
            throw new IllegalArgumentException("offlineTimeNanos must be non-negative");
        }
        return new BaSsuIbltUpBaUpotOfflineSenderOutput(
            schedule, Arrays.copyOf(seed, seed.length), schedule.getTotalMaterialByteLength(), offlineTimeNanos
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

    byte[] derive(BaSsuIbltUpBaUpotOfflineLabel label, int retryId, int bucketIndex, int probeOrdinal) {
        return material.derive(label, retryId, bucketIndex, probeOrdinal);
    }
}
