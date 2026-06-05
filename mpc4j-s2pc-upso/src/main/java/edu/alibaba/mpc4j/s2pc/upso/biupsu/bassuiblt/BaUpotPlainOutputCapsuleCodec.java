package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed-shape plain BA-UPOT output capsule codec.
 *
 * <p>This codec binds an ideal BA-UPOT bucket output to a fixed-length byte representation. It is a payload format and
 * correctness harness only: it reveals the bucket case in the clear and is not the final case-hiding BA-UPOT
 * realization.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainOutputCapsuleCodec {
    /**
     * auth digest domain.
     */
    private static final byte DOMAIN_AUTH = 0x41;
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
     * Creates a codec.
     *
     * @param elementByteLength element byte length.
     * @param checkByteLength check byte length.
     * @param tagByteLength auth tag byte length.
     */
    public BaUpotPlainOutputCapsuleCodec(int elementByteLength, int checkByteLength, int tagByteLength) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        if (checkByteLength <= 0 || checkByteLength > 32) {
            throw new IllegalArgumentException("checkByteLength must be in range [1, 32]");
        }
        if (tagByteLength <= 0 || tagByteLength > 32) {
            throw new IllegalArgumentException("tagByteLength must be in range [1, 32]");
        }
        this.elementByteLength = elementByteLength;
        this.checkByteLength = checkByteLength;
        this.tagByteLength = tagByteLength;
    }

    /**
     * Creates a codec from BA-UPOT config.
     *
     * @param config BA-UPOT config.
     * @return codec.
     */
    public static BaUpotPlainOutputCapsuleCodec fromConfig(BaUpotConfig config) {
        return new BaUpotPlainOutputCapsuleCodec(config.getElementByteLength(), config.checkByteLength(),
            config.tagByteLength());
    }

    /**
     * Returns the fixed capsule byte length.
     *
     * @return fixed capsule byte length.
     */
    public int capsuleByteLength() {
        return 1 + elementByteLength + checkByteLength + tagByteLength;
    }

    /**
     * Encodes one output.
     *
     * @param output bucket output.
     * @return fixed-length capsule.
     */
    public byte[] encode(BaUpotBucketOutput output) {
        byte[] capsule = new byte[capsuleByteLength()];
        byte caseCode = caseCode(output.getCaseType());
        capsule[0] = caseCode;
        int offset = 1;
        byte[] element = new byte[elementByteLength];
        byte[] check = new byte[checkByteLength];
        if (output.isSingleton()) {
            byte[] outputElement = output.getElementReference();
            if (outputElement.length != elementByteLength) {
                throw new IllegalArgumentException("Unexpected element byte length: " + outputElement.length);
            }
            System.arraycopy(outputElement, 0, element, 0, element.length);
            System.arraycopy(BaUpotIdeal.digest(element, checkByteLength), 0, check, 0, check.length);
        }
        System.arraycopy(element, 0, capsule, offset, element.length);
        offset += element.length;
        System.arraycopy(check, 0, capsule, offset, check.length);
        offset += check.length;
        byte[] tag = authTag(output.getBucketIndex(), caseCode, element, check);
        System.arraycopy(tag, 0, capsule, offset, tag.length);
        return capsule;
    }

    /**
     * Decodes and verifies one capsule.
     *
     * @param bucketIndex expected bucket index.
     * @param capsule capsule.
     * @return bucket output.
     */
    public BaUpotBucketOutput decode(int bucketIndex, byte[] capsule) {
        if (capsule.length != capsuleByteLength()) {
            throw new IllegalArgumentException("Invalid capsule byte length: " + capsule.length);
        }
        byte caseCode = capsule[0];
        int offset = 1;
        byte[] element = Arrays.copyOfRange(capsule, offset, offset + elementByteLength);
        offset += elementByteLength;
        byte[] check = Arrays.copyOfRange(capsule, offset, offset + checkByteLength);
        offset += checkByteLength;
        byte[] tag = Arrays.copyOfRange(capsule, offset, offset + tagByteLength);
        if (!Arrays.equals(tag, authTag(bucketIndex, caseCode, element, check))) {
            throw new IllegalArgumentException("Invalid output capsule auth tag");
        }
        BaUpotBucketOutput.CaseType caseType = caseType(caseCode);
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

    /**
     * Encodes a batch of outputs.
     *
     * @param outputs bucket outputs.
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
     * Decodes a consecutive batch of capsules.
     *
     * @param firstBucketIndex first bucket index.
     * @param capsules capsules.
     * @return outputs.
     */
    public List<BaUpotBucketOutput> decodeConsecutiveBatch(int firstBucketIndex, List<byte[]> capsules) {
        List<BaUpotBucketOutput> outputs = new ArrayList<>(capsules.size());
        for (int index = 0; index < capsules.size(); index++) {
            outputs.add(decode(firstBucketIndex + index, capsules.get(index)));
        }
        return outputs;
    }

    /**
     * Decodes a batch of capsules bound to arbitrary bucket indices.
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

    /**
     * Encodes all outputs in a plain BA-SSU trace.
     *
     * @param bucketTrace bucket trace.
     * @return capsules.
     */
    public List<byte[]> encodeTraceOutputs(List<BaSsuIbltBucketTrace> bucketTrace) {
        List<byte[]> capsules = new ArrayList<>(bucketTrace.size());
        for (BaSsuIbltBucketTrace trace : bucketTrace) {
            capsules.add(encode(trace.getOutput()));
        }
        return capsules;
    }

    /**
     * Decodes capsules using the bucket indices from a plain BA-SSU trace.
     *
     * @param bucketTrace bucket trace.
     * @param capsules capsules.
     * @return outputs.
     */
    public List<BaUpotBucketOutput> decodeTraceOutputs(List<BaSsuIbltBucketTrace> bucketTrace,
                                                       List<byte[]> capsules) {
        if (bucketTrace.size() != capsules.size()) {
            throw new IllegalArgumentException("bucketTrace and capsules must have the same size");
        }
        List<BaUpotBucketOutput> outputs = new ArrayList<>(capsules.size());
        for (int index = 0; index < capsules.size(); index++) {
            outputs.add(decode(bucketTrace.get(index).getBucketIndex(), capsules.get(index)));
        }
        return outputs;
    }

    private byte[] authTag(int bucketIndex, byte caseCode, byte[] element, byte[] check) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN_AUTH);
            digest.update(intToBytes(bucketIndex));
            digest.update(caseCode);
            digest.update(element);
            digest.update(check);
            return Arrays.copyOf(digest.digest(), tagByteLength);
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
            default -> throw new IllegalArgumentException("Unknown case code: " + caseCode);
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
