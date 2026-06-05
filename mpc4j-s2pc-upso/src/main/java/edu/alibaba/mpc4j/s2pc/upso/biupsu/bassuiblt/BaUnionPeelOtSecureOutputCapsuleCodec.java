package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * BA-UnionPeel-OT fixed-shape masked output capsule codec.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtSecureOutputCapsuleCodec {
    /**
     * mask domain.
     */
    private static final byte DOMAIN_MASK = 0x51;
    /**
     * auth domain.
     */
    private static final byte DOMAIN_AUTH = 0x52;
    /**
     * empty case code.
     */
    private static final byte CODE_EMPTY = 0x00;
    /**
     * anchor singleton case code.
     */
    private static final byte CODE_ANCHOR_SINGLETON = 0x01;
    /**
     * shadow singleton case code.
     */
    private static final byte CODE_SHADOW_SINGLETON = 0x02;
    /**
     * shared singleton case code.
     */
    private static final byte CODE_SHARED_SINGLETON = 0x03;
    /**
     * blocked case code.
     */
    private static final byte CODE_BLOCKED = 0x04;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * auth tag byte length.
     */
    private final int authTagByteLength;
    /**
     * COT-derived mask seed.
     */
    private final byte[] maskSeed;

    BaUnionPeelOtSecureOutputCapsuleCodec(int elementByteLength, int authTagByteLength, byte[] maskSeed) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (authTagByteLength <= 0 || authTagByteLength > 32) {
            throw new IllegalArgumentException("authTagByteLength must be in range [1, 32]");
        }
        if (maskSeed == null || maskSeed.length == 0) {
            throw new IllegalArgumentException("maskSeed must be non-empty");
        }
        this.elementByteLength = elementByteLength;
        this.authTagByteLength = authTagByteLength;
        this.maskSeed = Arrays.copyOf(maskSeed, maskSeed.length);
    }

    int capsuleByteLength() {
        return plaintextByteLength() + authTagByteLength;
    }

    byte[] encode(int wireIndex, BaUpotBucketOutput output) {
        if (wireIndex < 0) {
            throw new IllegalArgumentException("wireIndex must be non-negative");
        }
        if (output == null) {
            throw new IllegalArgumentException("output must be non-null");
        }
        byte[] plaintext = outputPlaintext(output);
        byte[] ciphertext = xor(plaintext, mask(wireIndex, plaintext.length));
        byte[] authTag = authTag(wireIndex, ciphertext);
        byte[] capsule = new byte[capsuleByteLength()];
        System.arraycopy(ciphertext, 0, capsule, 0, ciphertext.length);
        System.arraycopy(authTag, 0, capsule, ciphertext.length, authTag.length);
        return capsule;
    }

    BaUpotBucketOutput decode(int bucketIndex, int wireIndex, byte[] capsule) {
        if (bucketIndex < 0 || wireIndex < 0) {
            throw new IllegalArgumentException("bucketIndex and wireIndex must be non-negative");
        }
        if (capsule == null || capsule.length != capsuleByteLength()) {
            throw new IllegalArgumentException("invalid capsule length");
        }
        int plaintextByteLength = plaintextByteLength();
        byte[] ciphertext = Arrays.copyOfRange(capsule, 0, plaintextByteLength);
        byte[] authTag = Arrays.copyOfRange(capsule, plaintextByteLength, capsule.length);
        if (!Arrays.equals(authTag, authTag(wireIndex, ciphertext))) {
            throw new IllegalArgumentException("invalid capsule auth tag");
        }
        byte[] plaintext = xor(ciphertext, mask(wireIndex, plaintextByteLength));
        return parsePlaintext(bucketIndex, plaintext);
    }

    private byte[] outputPlaintext(BaUpotBucketOutput output) {
        byte[] plaintext = new byte[plaintextByteLength()];
        plaintext[0] = caseCode(output.getCaseType());
        if (output.isSingleton()) {
            byte[] element = output.getElementReference();
            if (element.length != elementByteLength) {
                throw new IllegalArgumentException("unexpected element byte length");
            }
            System.arraycopy(element, 0, plaintext, 1, element.length);
        }
        return plaintext;
    }

    private BaUpotBucketOutput parsePlaintext(int bucketIndex, byte[] plaintext) {
        BaUpotBucketOutput.CaseType caseType = caseType(plaintext[0]);
        byte[] element = Arrays.copyOfRange(plaintext, 1, plaintext.length);
        switch (caseType) {
            case EMPTY:
                requireZeroPayload(element);
                return BaUpotBucketOutput.empty(bucketIndex);
            case BLOCKED:
                requireZeroPayload(element);
                return BaUpotBucketOutput.blocked(bucketIndex);
            case ANCHOR_SINGLETON:
            case SHADOW_SINGLETON:
            case SHARED_SINGLETON:
                return BaUpotBucketOutput.singleton(bucketIndex, caseType, element);
            default:
                throw new IllegalArgumentException("unsupported output case");
        }
    }

    private int plaintextByteLength() {
        return 1 + elementByteLength;
    }

    private byte[] mask(int wireIndex, int byteLength) {
        byte[] output = new byte[byteLength];
        int offset = 0;
        int counter = 0;
        while (offset < byteLength) {
            byte[] block = digest(DOMAIN_MASK, wireIndex, counter, maskSeed, new byte[0]);
            int copyLength = Math.min(block.length, byteLength - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        return output;
    }

    private byte[] authTag(int wireIndex, byte[] ciphertext) {
        return Arrays.copyOf(digest(DOMAIN_AUTH, wireIndex, 0, maskSeed, ciphertext), authTagByteLength);
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

    private static byte caseCode(BaUpotBucketOutput.CaseType caseType) {
        switch (caseType) {
            case EMPTY:
                return CODE_EMPTY;
            case ANCHOR_SINGLETON:
                return CODE_ANCHOR_SINGLETON;
            case SHADOW_SINGLETON:
                return CODE_SHADOW_SINGLETON;
            case SHARED_SINGLETON:
                return CODE_SHARED_SINGLETON;
            case BLOCKED:
                return CODE_BLOCKED;
            default:
                throw new IllegalArgumentException("unknown case type");
        }
    }

    private static BaUpotBucketOutput.CaseType caseType(byte code) {
        switch (code) {
            case CODE_EMPTY:
                return BaUpotBucketOutput.CaseType.EMPTY;
            case CODE_ANCHOR_SINGLETON:
                return BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON;
            case CODE_SHADOW_SINGLETON:
                return BaUpotBucketOutput.CaseType.SHADOW_SINGLETON;
            case CODE_SHARED_SINGLETON:
                return BaUpotBucketOutput.CaseType.SHARED_SINGLETON;
            case CODE_BLOCKED:
                return BaUpotBucketOutput.CaseType.BLOCKED;
            default:
                throw new IllegalArgumentException("invalid output case code");
        }
    }

    private static void requireZeroPayload(byte[] element) {
        for (byte value : element) {
            if (value != 0) {
                throw new IllegalArgumentException("non-singleton capsule must carry zero payload");
            }
        }
    }

    static byte[] deriveSeed(byte[][] cotBlocks) {
        if (cotBlocks == null || cotBlocks.length == 0) {
            throw new IllegalArgumentException("cotBlocks must be non-empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((byte) 0x53);
            for (byte[] block : cotBlocks) {
                if (block == null || block.length == 0) {
                    throw new IllegalArgumentException("cotBlocks must contain non-empty entries");
                }
                digest.update(block);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] digest(byte domain, int wireIndex, int counter, byte[] seed, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            digest.update(intToBytes(wireIndex));
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
