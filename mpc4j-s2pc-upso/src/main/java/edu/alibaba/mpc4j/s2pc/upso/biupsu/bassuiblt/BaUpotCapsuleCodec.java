package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fixed-shape BA-UPOT capsule codec for standalone benchmark.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotCapsuleCodec {
    /**
     * tag domain.
     */
    private static final byte DOMAIN_TAG = 0x11;
    /**
     * check domain.
     */
    private static final byte DOMAIN_CHECK = 0x12;
    /**
     * payload domain.
     */
    private static final byte DOMAIN_PAYLOAD = 0x13;
    /**
     * auth domain.
     */
    private static final byte DOMAIN_AUTH = 0x14;

    /**
     * config.
     */
    private final BaUpotConfig config;
    /**
     * digest.
     */
    private final MessageDigest digest;
    /**
     * digest output.
     */
    private final byte[] digestOutput;
    /**
     * index buffer.
     */
    private final byte[] indexBuffer;
    /**
     * element buffer.
     */
    private final byte[] elementBuffer;
    /**
     * tag buffer.
     */
    private final byte[] tagBuffer;
    /**
     * check buffer.
     */
    private final byte[] checkBuffer;
    /**
     * payload mask.
     */
    private final byte[] payloadMask;

    BaUpotCapsuleCodec(BaUpotConfig config) {
        this.config = config;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        digestOutput = new byte[32];
        indexBuffer = new byte[Long.BYTES];
        elementBuffer = new byte[config.getElementByteLength()];
        tagBuffer = new byte[config.tagByteLength()];
        checkBuffer = new byte[config.checkByteLength()];
        payloadMask = new byte[config.getElementByteLength()];
    }

    /**
     * Encodes one sender capsule.
     *
     * @param bucketIndex bucket index.
     * @param cotBlock0 first COT block.
     * @param cotBlock1 second COT block.
     * @return capsule.
     */
    byte[] encode(int bucketIndex, byte[] cotBlock0, byte[] cotBlock1) {
        byte[] capsule = new byte[config.onlinePayloadByteLength()];
        int offset = 0;
        offset = encodeBranch(bucketIndex, 0, cotBlock0, capsule, offset);
        offset = encodeBranch(bucketIndex, 1, cotBlock1, capsule, offset);
        System.arraycopy(cotBlock0, 0, capsule, offset, BaUpotMicroBenchmark.BLOCK_BYTE_LENGTH);
        offset += BaUpotMicroBenchmark.BLOCK_BYTE_LENGTH;
        System.arraycopy(cotBlock1, 0, capsule, offset, BaUpotMicroBenchmark.BLOCK_BYTE_LENGTH);
        return capsule;
    }

    /**
     * Accumulates one received capsule.
     *
     * @param bucketIndex bucket index.
     * @param capsule capsule.
     * @param cotBlock0 first receiver COT block.
     * @param cotBlock1 second receiver COT block.
     * @return checksum contribution.
     */
    long accumulate(int bucketIndex, byte[] capsule, byte[] cotBlock0, byte[] cotBlock1) {
        if (capsule.length != config.onlinePayloadByteLength()) {
            throw new IllegalArgumentException("Invalid capsule byte length: " + capsule.length);
        }
        digest.reset();
        digest.update(DOMAIN_AUTH);
        longToBytes(bucketIndex, indexBuffer);
        digest.update(indexBuffer);
        digest.update(capsule);
        digest.update(cotBlock0);
        digest.update(cotBlock1);
        digestInto(digestOutput);
        return firstLong(digestOutput);
    }

    private int encodeBranch(int bucketIndex, int branch, byte[] cotBlock, byte[] capsule, int offset) {
        fillElement(bucketIndex, branch, cotBlock);
        digest(DOMAIN_TAG, bucketIndex, branch, elementBuffer, tagBuffer);
        digest(DOMAIN_CHECK, bucketIndex, branch, tagBuffer, checkBuffer);
        digest(DOMAIN_PAYLOAD, bucketIndex, branch, cotBlock, payloadMask);
        for (int i = 0; i < config.getElementByteLength(); i++) {
            capsule[offset + i] = (byte) (elementBuffer[i] ^ payloadMask[i]);
        }
        offset += config.getElementByteLength();
        System.arraycopy(checkBuffer, 0, capsule, offset, checkBuffer.length);
        return offset + checkBuffer.length;
    }

    private void fillElement(int bucketIndex, int branch, byte[] cotBlock) {
        digest(DOMAIN_AUTH, bucketIndex, branch, cotBlock, digestOutput);
        int offset = 0;
        while (offset < elementBuffer.length) {
            int copyLength = Math.min(digestOutput.length, elementBuffer.length - offset);
            System.arraycopy(digestOutput, 0, elementBuffer, offset, copyLength);
            offset += copyLength;
            if (offset < elementBuffer.length) {
                digest(DOMAIN_AUTH, bucketIndex, branch + offset, digestOutput, digestOutput);
            }
        }
    }

    private void digest(byte domain, int bucketIndex, int branch, byte[] input, byte[] output) {
        digest.reset();
        digest.update(domain);
        longToBytes((((long) bucketIndex) << Integer.SIZE) ^ (branch & 0xFFFFFFFFL), indexBuffer);
        digest.update(indexBuffer);
        digest.update(input);
        digestInto(digestOutput);
        System.arraycopy(digestOutput, 0, output, 0, output.length);
    }

    private void digestInto(byte[] output) {
        try {
            digest.digest(output, 0, output.length);
        } catch (DigestException e) {
            throw new IllegalStateException("SHA-256 digest failed", e);
        }
    }

    private static long firstLong(byte[] bytes) {
        long value = 0L;
        int length = Math.min(Long.BYTES, bytes.length);
        for (int i = 0; i < length; i++) {
            value |= (bytes[i] & 0xFFL) << (i * Byte.SIZE);
        }
        return value;
    }

    private static void longToBytes(long value, byte[] output) {
        for (int i = 0; i < Long.BYTES; i++) {
            output[i] = (byte) (value >>> ((Long.BYTES - 1 - i) * Byte.SIZE));
        }
    }
}
