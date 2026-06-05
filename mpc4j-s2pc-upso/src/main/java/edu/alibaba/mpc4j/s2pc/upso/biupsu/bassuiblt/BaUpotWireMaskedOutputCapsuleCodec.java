package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wire-masked BA-UPOT output capsule codec.
 *
 * <p>The codec removes the clear case byte from the network representation by encrypting
 * {@code case || element || check} with a bucket-bound hash stream and authenticating the ciphertext. This is still not
 * the final receiver-hidden BA-UPOT realization: the party that holds the mask seed can decode the bucket case. It is a
 * line-format step between the plain payload bridge and a true case-hiding evaluator.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotWireMaskedOutputCapsuleCodec {
    /**
     * mask domain.
     */
    private static final byte DOMAIN_MASK = 0x61;
    /**
     * auth domain.
     */
    private static final byte DOMAIN_AUTH = 0x62;
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
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * auth tag byte length.
     */
    private final int tagByteLength;
    /**
     * mask seed.
     */
    private final byte[] maskSeed;

    /**
     * Creates the codec.
     *
     * @param elementByteLength element byte length.
     * @param checkByteLength check byte length.
     * @param tagByteLength auth tag byte length.
     * @param maskSeed mask seed.
     */
    public BaUpotWireMaskedOutputCapsuleCodec(int elementByteLength, int checkByteLength, int tagByteLength,
                                              byte[] maskSeed) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (checkByteLength <= 0 || checkByteLength > 32) {
            throw new IllegalArgumentException("checkByteLength must be in range [1, 32]");
        }
        if (tagByteLength <= 0 || tagByteLength > 32) {
            throw new IllegalArgumentException("tagByteLength must be in range [1, 32]");
        }
        if (maskSeed == null || maskSeed.length == 0) {
            throw new IllegalArgumentException("maskSeed must be non-empty");
        }
        this.elementByteLength = elementByteLength;
        this.checkByteLength = checkByteLength;
        this.tagByteLength = tagByteLength;
        this.maskSeed = Arrays.copyOf(maskSeed, maskSeed.length);
    }

    /**
     * Creates a codec from BA-UPOT config.
     *
     * @param config config.
     * @param maskSeed mask seed.
     * @return codec.
     */
    public static BaUpotWireMaskedOutputCapsuleCodec fromConfig(BaUpotConfig config, byte[] maskSeed) {
        return new BaUpotWireMaskedOutputCapsuleCodec(config.getElementByteLength(), config.checkByteLength(),
            config.tagByteLength(), maskSeed);
    }

    /**
     * Returns the fixed capsule byte length.
     *
     * @return capsule byte length.
     */
    public int capsuleByteLength() {
        return plaintextByteLength() + tagByteLength;
    }

    /**
     * Encodes one output.
     *
     * @param output bucket output.
     * @return capsule.
     */
    public byte[] encode(BaUpotBucketOutput output) {
        if (output == null) {
            throw new IllegalArgumentException("output must be non-null");
        }
        return encode(output, output.getBucketIndex());
    }

    /**
     * Encodes one output with a wire-domain index. The wire-domain index is the fixed transcript ordinal, so retries
     * that reuse the same retry-local bucket index still receive independent mask/auth domains.
     *
     * @param output bucket output.
     * @param wireIndex wire-domain index.
     * @return capsule.
     */
    public byte[] encode(BaUpotBucketOutput output, int wireIndex) {
        if (output == null) {
            throw new IllegalArgumentException("output must be non-null");
        }
        if (wireIndex < 0) {
            throw new IllegalArgumentException("wireIndex must be non-negative");
        }
        byte[] plaintext = outputPlaintext(output);
        byte[] mask = mask(wireIndex, plaintext.length);
        byte[] ciphertext = new byte[plaintext.length];
        for (int i = 0; i < plaintext.length; i++) {
            ciphertext[i] = (byte) (plaintext[i] ^ mask[i]);
        }
        byte[] tag = authTag(wireIndex, ciphertext);
        byte[] capsule = new byte[capsuleByteLength()];
        System.arraycopy(ciphertext, 0, capsule, 0, ciphertext.length);
        System.arraycopy(tag, 0, capsule, ciphertext.length, tag.length);
        return capsule;
    }

    /**
     * Decodes one output.
     *
     * @param bucketIndex expected bucket index.
     * @param capsule capsule.
     * @return output.
     */
    public BaUpotBucketOutput decode(int bucketIndex, byte[] capsule) {
        return decode(bucketIndex, bucketIndex, capsule);
    }

    /**
     * Decodes one output with a wire-domain index.
     *
     * @param bucketIndex expected decoded bucket index.
     * @param wireIndex wire-domain index.
     * @param capsule capsule.
     * @return output.
     */
    public BaUpotBucketOutput decode(int bucketIndex, int wireIndex, byte[] capsule) {
        if (wireIndex < 0) {
            throw new IllegalArgumentException("wireIndex must be non-negative");
        }
        if (capsule == null) {
            throw new IllegalArgumentException("capsule must be non-null");
        }
        if (capsule.length != capsuleByteLength()) {
            throw new IllegalArgumentException("Invalid capsule byte length: " + capsule.length);
        }
        int plaintextByteLength = plaintextByteLength();
        byte[] ciphertext = Arrays.copyOfRange(capsule, 0, plaintextByteLength);
        byte[] tag = Arrays.copyOfRange(capsule, plaintextByteLength, capsule.length);
        if (!Arrays.equals(tag, authTag(wireIndex, ciphertext))) {
            throw new IllegalArgumentException("Invalid masked capsule auth tag");
        }
        byte[] mask = mask(wireIndex, plaintextByteLength);
        byte[] plaintext = new byte[plaintextByteLength];
        for (int i = 0; i < plaintext.length; i++) {
            plaintext[i] = (byte) (ciphertext[i] ^ mask[i]);
        }
        return parsePlaintext(bucketIndex, plaintext);
    }

    /**
     * Encodes a batch of outputs.
     *
     * @param outputs outputs.
     * @return capsules.
     */
    public List<byte[]> encodeBatch(List<BaUpotBucketOutput> outputs) {
        List<byte[]> capsules = new ArrayList<>(outputs.size());
        for (BaUpotBucketOutput output : outputs) {
            capsules.add(encode(output));
        }
        return capsules;
    }

    /**
     * Decodes a batch of outputs.
     *
     * @param bucketIndices expected bucket indices.
     * @param capsules capsules.
     * @return outputs.
     */
    public List<BaUpotBucketOutput> decodeBatch(List<Integer> bucketIndices, List<byte[]> capsules) {
        if (bucketIndices.size() != capsules.size()) {
            throw new IllegalArgumentException("bucketIndices and capsules must have the same size");
        }
        List<BaUpotBucketOutput> outputs = new ArrayList<>(capsules.size());
        for (int index = 0; index < capsules.size(); index++) {
            outputs.add(decode(bucketIndices.get(index), capsules.get(index)));
        }
        return outputs;
    }

    private byte[] outputPlaintext(BaUpotBucketOutput output) {
        byte[] plaintext = new byte[plaintextByteLength()];
        plaintext[0] = caseCode(output.getCaseType());
        int offset = 1;
        if (output.isSingleton()) {
            byte[] element = output.getElementReference();
            if (element.length != elementByteLength) {
                throw new IllegalArgumentException("Unexpected element byte length: " + element.length);
            }
            System.arraycopy(element, 0, plaintext, offset, element.length);
            offset += element.length;
            System.arraycopy(BaUpotIdeal.digest(element, checkByteLength), 0, plaintext, offset, checkByteLength);
        }
        return plaintext;
    }

    private BaUpotBucketOutput parsePlaintext(int bucketIndex, byte[] plaintext) {
        BaUpotBucketOutput.CaseType caseType = caseType(plaintext[0]);
        byte[] element = Arrays.copyOfRange(plaintext, 1, 1 + elementByteLength);
        byte[] check = Arrays.copyOfRange(plaintext, 1 + elementByteLength, plaintext.length);
        if (isSingleton(caseType)) {
            if (!Arrays.equals(BaUpotIdeal.digest(element, checkByteLength), check)) {
                throw new IllegalArgumentException("Invalid singleton check");
            }
            return BaUpotBucketOutput.singleton(bucketIndex, caseType, element);
        } else {
            if (!isZero(element) || !isZero(check)) {
                throw new IllegalArgumentException("Non-singleton capsule must carry zero payload");
            }
            return caseType == BaUpotBucketOutput.CaseType.EMPTY
                ? BaUpotBucketOutput.empty(bucketIndex)
                : BaUpotBucketOutput.blocked(bucketIndex);
        }
    }

    private int plaintextByteLength() {
        return 1 + elementByteLength + checkByteLength;
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

    private byte[] authTag(int bucketIndex, byte[] ciphertext) {
        return Arrays.copyOf(digest(DOMAIN_AUTH, bucketIndex, 0, maskSeed, ciphertext), tagByteLength);
    }

    private static byte[] digest(byte domain, int bucketIndex, int counter, byte[] seed, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain);
            digest.update(intToBytes(bucketIndex));
            digest.update(intToBytes(counter));
            digest.update(seed);
            digest.update(input);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte caseCode(BaUpotBucketOutput.CaseType caseType) {
        return switch (caseType) {
            case EMPTY -> CODE_EMPTY;
            case ANCHOR_SINGLETON -> CODE_ANCHOR_SINGLETON;
            case SHADOW_SINGLETON -> CODE_SHADOW_SINGLETON;
            case SHARED_SINGLETON -> CODE_SHARED_SINGLETON;
            case BLOCKED -> CODE_BLOCKED;
        };
    }

    private static BaUpotBucketOutput.CaseType caseType(byte caseCode) {
        return switch (caseCode) {
            case CODE_EMPTY -> BaUpotBucketOutput.CaseType.EMPTY;
            case CODE_ANCHOR_SINGLETON -> BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON;
            case CODE_SHADOW_SINGLETON -> BaUpotBucketOutput.CaseType.SHADOW_SINGLETON;
            case CODE_SHARED_SINGLETON -> BaUpotBucketOutput.CaseType.SHARED_SINGLETON;
            case CODE_BLOCKED -> BaUpotBucketOutput.CaseType.BLOCKED;
            default -> throw new IllegalArgumentException("Unknown masked case code: " + caseCode);
        };
    }

    private static boolean isSingleton(BaUpotBucketOutput.CaseType caseType) {
        return caseType == BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON
            || caseType == BaUpotBucketOutput.CaseType.SHADOW_SINGLETON
            || caseType == BaUpotBucketOutput.CaseType.SHARED_SINGLETON;
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value,
        };
    }
}
