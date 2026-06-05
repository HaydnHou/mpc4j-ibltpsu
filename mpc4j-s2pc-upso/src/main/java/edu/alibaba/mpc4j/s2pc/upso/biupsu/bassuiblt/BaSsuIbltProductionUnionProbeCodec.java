package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Fixed-shape production UP-BA-UPOT local capsule encoder.
 *
 * <p>This main-code codec deliberately does not expose a local opener/decoder for remote capsules. Truth-table
 * regression helpers that decode both sides belong in test/reference code only; production code must use a real
 * remote-state-hiding UP-BA-UPOT evaluator before {@code isQueuePeelProductionReady()} can become true.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeCodec {
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
     * domain-separated mask seed.
     */
    private final byte[] maskSeed;

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
        maskSeed = digest(DOMAIN_SEED, 0, 0, seed, new byte[]{(byte) elementByteLength, (byte) tagByteLength,
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
        byte[] plaintext = localPlaintext(cellView);
        byte[] ciphertext = xor(plaintext, mask(bucketIndex, plaintext.length));
        byte[] authTag = Arrays.copyOf(digest(DOMAIN_AUTH, bucketIndex, 0, maskSeed, ciphertext), authTagByteLength);
        byte[] encoded = new byte[capsuleByteLength()];
        System.arraycopy(ciphertext, 0, encoded, 0, ciphertext.length);
        System.arraycopy(authTag, 0, encoded, ciphertext.length, authTag.length);
        return new BaSsuIbltProductionUnionProbeCapsule(bucketIndex, encoded, encoded.length);
    }

    private byte[] localPlaintext(BaSsuIbltSecureCellView cellView) {
        byte[] plaintext = new byte[1 + elementByteLength + tagByteLength + checkByteLength];
        if (cellView.getCount() == 0 && isZero(cellView.getKeyXorReference())
            && isZero(cellView.getTagXorReference()) && isZero(cellView.getCheckXorReference())) {
            plaintext[0] = STATE_EMPTY;
            return plaintext;
        }
        if (cellView.isValidSingleton()) {
            plaintext[0] = STATE_SINGLETON;
            int offset = 1;
            System.arraycopy(cellView.getKeyXorReference(), 0, plaintext, offset, elementByteLength);
            offset += elementByteLength;
            System.arraycopy(cellView.getTagXorReference(), 0, plaintext, offset, tagByteLength);
            offset += tagByteLength;
            System.arraycopy(cellView.getCheckXorReference(), 0, plaintext, offset, checkByteLength);
            return plaintext;
        }
        plaintext[0] = STATE_BLOCKED;
        return plaintext;
    }

    private void checkLengths(BaSsuIbltSecureCellView cellView) {
        if (cellView.getElementByteLength() != elementByteLength || cellView.getTagByteLength() != tagByteLength
            || cellView.getCheckByteLength() != checkByteLength) {
            throw new IllegalArgumentException("cellView byte lengths must match codec config");
        }
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

}
