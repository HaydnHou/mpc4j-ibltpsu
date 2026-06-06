package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Fixed-shape production UP-BA-UPOT local opaque capsule encoder.
 *
 * <p>This main-code codec deliberately does not expose a local opener/decoder for remote capsules. Truth-table
 * regression helpers that decode both sides belong in test/reference code only; production code must use a real
 * remote-state-hiding UP-BA-UPOT evaluator before {@code isQueuePeelProductionReady()} can become true. The fail-closed
 * placeholder capsule intentionally does not carry local state, element, tag, or check material in a reversible
 * encoding.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeCodec {
    /**
     * opaque capsule domain.
     */
    private static final byte DOMAIN_OPAQUE_CAPSULE = 0x71;
    /**
     * opaque capsule auth domain.
     */
    private static final byte DOMAIN_OPAQUE_AUTH = 0x72;
    /**
     * seed domain.
     */
    private static final byte DOMAIN_SEED = 0x73;
    /**
     * legacy opaque schedule marker.
     */
    private static final byte[] LEGACY_OPAQUE_MARKER =
        "BA_SSU_UP_BA_UPOT_OPAQUE_V2".getBytes(StandardCharsets.UTF_8);
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * auth tag byte length.
     */
    private final int authTagByteLength;
    /**
     * domain-separated opaque seed.
     */
    private final byte[] opaqueSeed;

    BaSsuIbltProductionUnionProbeCodec(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed) {
        this(
            config.getElementByteLength(), config.getTagByteLength(), config.getCheckByteLength(),
            config.getAuthTagByteLength(), seed
        );
    }

    BaSsuIbltProductionUnionProbeCodec(int elementByteLength, int tagByteLength, int checkByteLength,
                                       int authTagByteLength, byte[] seed) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (tagByteLength <= 0 || checkByteLength <= 0) {
            throw new IllegalArgumentException("tag/check byte lengths must be positive");
        }
        if (authTagByteLength <= 0 || authTagByteLength > 32) {
            throw new IllegalArgumentException("authTagByteLength must be in range [1, 32]");
        }
        if (seed == null || seed.length == 0) {
            throw new IllegalArgumentException("seed must be non-empty");
        }
        this.elementByteLength = elementByteLength;
        this.tagByteLength = tagByteLength;
        this.checkByteLength = checkByteLength;
        this.authTagByteLength = authTagByteLength;
        opaqueSeed = digest(DOMAIN_SEED, 0, 0, seed, new byte[]{(byte) elementByteLength, (byte) tagByteLength,
            (byte) checkByteLength, (byte) authTagByteLength});
    }

    static int capsuleByteLength(int elementByteLength, int tagByteLength, int checkByteLength,
                                 int authTagByteLength) {
        if (elementByteLength <= 0 || tagByteLength <= 0 || checkByteLength <= 0 || authTagByteLength <= 0) {
            throw new IllegalArgumentException("byte lengths must be positive");
        }
        return 1 + elementByteLength + tagByteLength + checkByteLength + authTagByteLength;
    }

    int capsuleByteLength() {
        return capsuleByteLength(elementByteLength, tagByteLength, checkByteLength, authTagByteLength);
    }

    BaSsuIbltProductionUnionProbeCapsule encode(int bucketIndex, BaSsuIbltSecureCellView cellView) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (cellView == null) {
            throw new IllegalArgumentException("cellView must be non-null");
        }
        checkLengths(cellView);
        byte[] encoded = authenticatedOpaqueCapsule(bucketIndex, LEGACY_OPAQUE_MARKER);
        return new BaSsuIbltProductionUnionProbeCapsule(bucketIndex, encoded, encoded.length);
    }

    BaSsuIbltProductionUnionProbeCapsule encode(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                BaSsuIbltSecureCellView cellView) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (cellView == null) {
            throw new IllegalArgumentException("cellView must be non-null");
        }
        checkLengths(cellView);
        byte[] encoded = authenticatedOpaqueCapsule(publicInput.getBucketIndex(), scheduleBytes(publicInput));
        return new BaSsuIbltProductionUnionProbeCapsule(publicInput.getBucketIndex(), encoded, encoded.length);
    }

    BaSsuIbltProductionUnionProbeCapsule encode(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                BaSsuIbltUpBaUpotPublicInput publicInput,
                                                BaSsuIbltSecureCellView cellView) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (cellView == null) {
            throw new IllegalArgumentException("cellView must be non-null");
        }
        schedule.validate(publicInput);
        checkLengths(cellView);
        byte[] encoded = authenticatedOpaqueCapsule(publicInput.getBucketIndex(), scheduleBytes(schedule, publicInput));
        return new BaSsuIbltProductionUnionProbeCapsule(publicInput.getBucketIndex(), encoded, encoded.length);
    }

    void validate(BaSsuIbltUpBaUpotPublicInput publicInput, BaSsuIbltProductionUnionProbeCapsule capsule) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (capsule == null) {
            throw new IllegalArgumentException("capsule must be non-null");
        }
        if (capsule.getBucketIndex() != publicInput.getBucketIndex()) {
            throw new IllegalArgumentException("capsule bucket index must match public bucketIndex");
        }
        byte[] encoded = capsule.getEncoded();
        if (encoded.length != capsuleByteLength()) {
            throw new IllegalArgumentException("invalid capsule length");
        }
        int bodyByteLength = encoded.length - authTagByteLength;
        byte[] body = Arrays.copyOf(encoded, bodyByteLength);
        byte[] authTag = Arrays.copyOfRange(encoded, bodyByteLength, encoded.length);
        byte[] expectedAuthTag = authTag(publicInput.getBucketIndex(), body, scheduleBytes(publicInput));
        if (!MessageDigest.isEqual(authTag, expectedAuthTag)) {
            throw new IllegalArgumentException("invalid capsule auth tag");
        }
    }

    void validate(BaSsuIbltUpBaUpotOfflineSchedule schedule, BaSsuIbltUpBaUpotPublicInput publicInput,
                  BaSsuIbltProductionUnionProbeCapsule capsule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (capsule == null) {
            throw new IllegalArgumentException("capsule must be non-null");
        }
        schedule.validate(publicInput);
        if (capsule.getBucketIndex() != publicInput.getBucketIndex()) {
            throw new IllegalArgumentException("capsule bucket index must match public bucketIndex");
        }
        byte[] encoded = capsule.getEncoded();
        if (encoded.length != capsuleByteLength()) {
            throw new IllegalArgumentException("invalid capsule length");
        }
        int bodyByteLength = encoded.length - authTagByteLength;
        byte[] body = Arrays.copyOf(encoded, bodyByteLength);
        byte[] authTag = Arrays.copyOfRange(encoded, bodyByteLength, encoded.length);
        byte[] expectedAuthTag = authTag(publicInput.getBucketIndex(), body, scheduleBytes(schedule, publicInput));
        if (!MessageDigest.isEqual(authTag, expectedAuthTag)) {
            throw new IllegalArgumentException("invalid capsule auth tag");
        }
    }

    private void checkLengths(BaSsuIbltSecureCellView cellView) {
        if (cellView.getElementByteLength() != elementByteLength || cellView.getTagByteLength() != tagByteLength
            || cellView.getCheckByteLength() != checkByteLength) {
            throw new IllegalArgumentException("cellView byte lengths must match codec config");
        }
    }

    private byte[] authenticatedOpaqueCapsule(int bucketIndex, byte[] scheduleBytes) {
        int bodyByteLength = capsuleByteLength() - authTagByteLength;
        byte[] body = new byte[bodyByteLength];
        int offset = 0;
        int counter = 0;
        while (offset < body.length) {
            byte[] block = digest(DOMAIN_OPAQUE_CAPSULE, bucketIndex, counter, opaqueSeed, scheduleBytes);
            int copyLength = Math.min(block.length, body.length - offset);
            System.arraycopy(block, 0, body, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        byte[] output = new byte[capsuleByteLength()];
        System.arraycopy(body, 0, output, 0, body.length);
        System.arraycopy(authTag(bucketIndex, body, scheduleBytes), 0, output, body.length, authTagByteLength);
        return output;
    }

    private byte[] authTag(int bucketIndex, byte[] body, byte[] scheduleBytes) {
        return Arrays.copyOf(
            digest(DOMAIN_OPAQUE_AUTH, bucketIndex, 0, opaqueSeed, concat(scheduleBytes, body)),
            authTagByteLength
        );
    }

    private static byte[] scheduleBytes(BaSsuIbltUpBaUpotPublicInput publicInput) {
        byte[] profileId = publicInput.getProfileId().getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[Integer.BYTES * 8 + profileId.length];
        int offset = 0;
        offset = putInt(output, offset, profileId.length);
        System.arraycopy(profileId, 0, output, offset, profileId.length);
        offset += profileId.length;
        offset = putInt(output, offset, publicInput.getRetryId());
        offset = putInt(output, offset, publicInput.getElementByteLength());
        offset = putInt(output, offset, publicInput.getTagBitLength());
        offset = putInt(output, offset, publicInput.getCheckBitLength());
        offset = putInt(output, offset, publicInput.getAuthTagBitLength());
        offset = putInt(output, offset, publicInput.getBucketIndex());
        putInt(output, offset, publicInput.getProbeOrdinal());
        return output;
    }

    private static byte[] scheduleBytes(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                        BaSsuIbltUpBaUpotPublicInput publicInput) {
        byte[] profileId = publicInput.getProfileId().getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[Integer.BYTES * 11 + profileId.length];
        int offset = 0;
        offset = putInt(output, offset, profileId.length);
        System.arraycopy(profileId, 0, output, offset, profileId.length);
        offset += profileId.length;
        offset = putInt(output, offset, publicInput.getRetryId());
        offset = putInt(output, offset, publicInput.getElementByteLength());
        offset = putInt(output, offset, publicInput.getTagBitLength());
        offset = putInt(output, offset, publicInput.getCheckBitLength());
        offset = putInt(output, offset, publicInput.getAuthTagBitLength());
        offset = putInt(output, offset, publicInput.getBucketIndex());
        offset = putInt(output, offset, publicInput.getProbeOrdinal());
        offset = putInt(output, offset, schedule.getRetryNum());
        offset = putInt(output, offset, schedule.getMaxProbeNum());
        putInt(output, offset, schedule.getTableLength());
        return output;
    }

    private static byte[] digest(byte domain, int index, int counter, byte[] seed, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            digest.update(intToBytes(index));
            digest.update(intToBytes(counter));
            digest.update(seed);
            digest.update(input);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] output = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, output, left.length, right.length);
        return output;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    private static int putInt(byte[] output, int offset, int value) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
        return offset + Integer.BYTES;
    }

}
