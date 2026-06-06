package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Domain-separated fixed-count UP-BA-UPOT offline material derivation.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltUpBaUpotOfflineMaterial {
    /**
     * domain byte.
     */
    private static final byte DOMAIN = 0x45;
    /**
     * schedule.
     */
    private final BaSsuIbltUpBaUpotOfflineSchedule schedule;
    /**
     * party role.
     */
    private final BaSsuIbltUpBaUpotOfflineRole role;
    /**
     * root seed.
     */
    private final byte[] seed;

    BaSsuIbltUpBaUpotOfflineMaterial(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                      BaSsuIbltUpBaUpotOfflineRole role, byte[] seed) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must be non-null");
        }
        if (seed == null || seed.length == 0) {
            throw new IllegalArgumentException("seed must be non-empty");
        }
        this.schedule = schedule;
        this.role = role;
        this.seed = Arrays.copyOf(seed, seed.length);
    }

    byte[] derive(BaSsuIbltUpBaUpotOfflineLabel label, int retryId, int bucketIndex, int probeOrdinal) {
        if (label == null) {
            throw new IllegalArgumentException("label must be non-null");
        }
        if (retryId < 0 || retryId >= schedule.getRetryNum()) {
            throw new IllegalArgumentException("retryId must be in range");
        }
        if (bucketIndex < 0 || bucketIndex >= schedule.getTableLength()) {
            throw new IllegalArgumentException("bucketIndex must be in table range");
        }
        if (probeOrdinal < 0 || probeOrdinal >= schedule.getMaxProbeNum()) {
            throw new IllegalArgumentException("probeOrdinal must be in range");
        }
        int byteLength = label.byteLength(schedule);
        byte[] context = context(label, retryId, bucketIndex, probeOrdinal);
        byte[] output = new byte[byteLength];
        int offset = 0;
        int counter = 0;
        while (offset < byteLength) {
            byte[] block = digest(counter, context);
            int copyLength = Math.min(block.length, byteLength - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        return output;
    }

    private byte[] context(BaSsuIbltUpBaUpotOfflineLabel label, int retryId, int bucketIndex, int probeOrdinal) {
        byte[] profileId = schedule.getProfileId().getBytes(StandardCharsets.UTF_8);
        byte[] labelName = label.name().getBytes(StandardCharsets.UTF_8);
        byte[] roleName = role.name().getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[
            Integer.BYTES * 13 + profileId.length + labelName.length + roleName.length
        ];
        int offset = 0;
        offset = putBytes(output, offset, profileId);
        offset = putBytes(output, offset, roleName);
        offset = putBytes(output, offset, labelName);
        offset = putInt(output, offset, retryId);
        offset = putInt(output, offset, bucketIndex);
        offset = putInt(output, offset, probeOrdinal);
        offset = putInt(output, offset, schedule.getRetryNum());
        offset = putInt(output, offset, schedule.getMaxProbeNum());
        offset = putInt(output, offset, schedule.getTableLength());
        offset = putInt(output, offset, schedule.getElementByteLength());
        offset = putInt(output, offset, schedule.getTagByteLength());
        offset = putInt(output, offset, schedule.getCheckByteLength());
        putInt(output, offset, schedule.getAuthTagByteLength());
        return output;
    }

    private byte[] digest(int counter, byte[] context) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            digest.update(intToBytes(counter));
            digest.update(seed);
            digest.update(context);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static int putBytes(byte[] output, int offset, byte[] input) {
        offset = putInt(output, offset, input.length);
        System.arraycopy(input, 0, output, offset, input.length);
        return offset + input.length;
    }

    private static int putInt(byte[] output, int offset, int value) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
        return offset + Integer.BYTES;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }
}
