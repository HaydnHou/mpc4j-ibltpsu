package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Test-only local decoder for the production union-probe truth table.
 *
 * <p>This class intentionally lives in test sources. Main production code must not contain or depend on a helper that
 * decodes the remote capsule locally.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeReferenceCodec {
    /**
     * local-state mask domain.
     */
    private static final byte DOMAIN_MASK = 0x71;
    /**
     * auth domain.
     */
    private static final byte DOMAIN_AUTH = 0x72;
    /**
     * seed domain.
     */
    private static final byte DOMAIN_SEED = 0x73;
    /**
     * empty state code.
     */
    private static final byte STATE_EMPTY = 0x00;
    /**
     * singleton state code.
     */
    private static final byte STATE_SINGLETON = 0x01;
    /**
     * blocked state code.
     */
    private static final byte STATE_BLOCKED = 0x02;
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
     * mask seed.
     */
    private final byte[] maskSeed;

    BaSsuIbltProductionUnionProbeReferenceCodec(BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed) {
        elementByteLength = config.getElementByteLength();
        tagByteLength = config.getTagByteLength();
        checkByteLength = config.getCheckByteLength();
        authTagByteLength = config.getAuthTagByteLength();
        maskSeed = digest(DOMAIN_SEED, 0, 0, seed, new byte[]{(byte) elementByteLength, (byte) tagByteLength,
            (byte) checkByteLength, (byte) authTagByteLength});
    }

    BaSsuIbltProductionUnionProbeOutput open(int bucketIndex, BaSsuIbltProductionUnionProbeCapsule anchorCapsule,
                                             BaSsuIbltProductionUnionProbeCapsule shadowCapsule) {
        if (anchorCapsule == null || shadowCapsule == null) {
            throw new IllegalArgumentException("capsules must be non-null");
        }
        if (anchorCapsule.getBucketIndex() != bucketIndex || shadowCapsule.getBucketIndex() != bucketIndex) {
            throw new IllegalArgumentException("capsule bucket index must match public bucketIndex");
        }
        DecodedLocal anchor = decode(bucketIndex, anchorCapsule);
        DecodedLocal shadow = decode(bucketIndex, shadowCapsule);
        if (anchor.state == STATE_SINGLETON && shadow.state == STATE_EMPTY) {
            return BaSsuIbltProductionUnionProbeOutput.singleton(bucketIndex, anchor.element, elementByteLength);
        }
        if (anchor.state == STATE_EMPTY && shadow.state == STATE_SINGLETON) {
            return BaSsuIbltProductionUnionProbeOutput.singleton(bucketIndex, shadow.element, elementByteLength);
        }
        if (anchor.state == STATE_SINGLETON && shadow.state == STATE_SINGLETON
            && Arrays.equals(anchor.element, shadow.element) && Arrays.equals(anchor.tag, shadow.tag)
            && Arrays.equals(anchor.check, shadow.check)) {
            return BaSsuIbltProductionUnionProbeOutput.singleton(bucketIndex, anchor.element, elementByteLength);
        }
        return BaSsuIbltProductionUnionProbeOutput.bottom(bucketIndex, elementByteLength);
    }

    private DecodedLocal decode(int bucketIndex, BaSsuIbltProductionUnionProbeCapsule capsule) {
        byte[] encoded = capsule.getEncoded();
        int expectedByteLength = BaSsuIbltProductionUnionProbeCodec.capsuleByteLength(
            elementByteLength, tagByteLength, checkByteLength, authTagByteLength
        );
        if (encoded.length != expectedByteLength) {
            throw new IllegalArgumentException("invalid capsule length");
        }
        int plaintextByteLength = encoded.length - authTagByteLength;
        byte[] ciphertext = Arrays.copyOfRange(encoded, 0, plaintextByteLength);
        byte[] authTag = Arrays.copyOfRange(encoded, plaintextByteLength, encoded.length);
        if (!Arrays.equals(authTag, Arrays.copyOf(digest(DOMAIN_AUTH, bucketIndex, 0, maskSeed, ciphertext),
            authTagByteLength))) {
            throw new IllegalArgumentException("invalid capsule auth tag");
        }
        return parsePlaintext(xor(ciphertext, mask(bucketIndex, plaintextByteLength)));
    }

    private DecodedLocal parsePlaintext(byte[] plaintext) {
        byte state = plaintext[0];
        byte[] element = Arrays.copyOfRange(plaintext, 1, 1 + elementByteLength);
        byte[] tag = Arrays.copyOfRange(plaintext, 1 + elementByteLength, 1 + elementByteLength + tagByteLength);
        byte[] check = Arrays.copyOfRange(
            plaintext, 1 + elementByteLength + tagByteLength, plaintext.length
        );
        if (state == STATE_EMPTY || state == STATE_BLOCKED) {
            if (!isZero(element) || !isZero(tag) || !isZero(check)) {
                throw new IllegalArgumentException("non-singleton state must carry dummy-zero internals");
            }
            return new DecodedLocal(state, element, tag, check);
        }
        if (state == STATE_SINGLETON) {
            if (!Arrays.equals(BaSsuIbltOprfTagPipeline.checkFromTag(tag, checkByteLength), check)) {
                throw new IllegalArgumentException("invalid singleton check");
            }
            return new DecodedLocal(state, element, tag, check);
        }
        throw new IllegalArgumentException("invalid local-state wire");
    }

    private byte[] mask(int bucketIndex, int byteLength) {
        byte[] output = new byte[byteLength];
        int offset = 0;
        int counter = 0;
        while (offset < byteLength) {
            byte[] block = digest(DOMAIN_MASK, bucketIndex, counter, maskSeed, new byte[0]);
            int copyLength = Math.min(block.length, byteLength - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        return output;
    }

    private static byte[] xor(byte[] left, byte[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("xor inputs must have equal length");
        }
        byte[] output = new byte[left.length];
        for (int i = 0; i < left.length; i++) {
            output[i] = (byte) (left[i] ^ right[i]);
        }
        return output;
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
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

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    /**
     * decoded local state.
     */
    private static class DecodedLocal {
        /**
         * state wire.
         */
        private final byte state;
        /**
         * element or dummy.
         */
        private final byte[] element;
        /**
         * tag or dummy.
         */
        private final byte[] tag;
        /**
         * check or dummy.
         */
        private final byte[] check;

        DecodedLocal(byte state, byte[] element, byte[] tag, byte[] check) {
            this.state = state;
            this.element = element;
            this.tag = tag;
            this.check = check;
        }
    }
}
