package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     * seed domain.
     */
    private static final byte DOMAIN_SEED = 0x73;
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
        byte[] encoded = opaqueCapsule(bucketIndex);
        return new BaSsuIbltProductionUnionProbeCapsule(bucketIndex, encoded, encoded.length);
    }

    private void checkLengths(BaSsuIbltSecureCellView cellView) {
        if (cellView.getElementByteLength() != elementByteLength || cellView.getTagByteLength() != tagByteLength
            || cellView.getCheckByteLength() != checkByteLength) {
            throw new IllegalArgumentException("cellView byte lengths must match codec config");
        }
    }

    private byte[] opaqueCapsule(int bucketIndex) {
        byte[] output = new byte[capsuleByteLength()];
        int offset = 0;
        int counter = 0;
        while (offset < output.length) {
            byte[] block = digest(DOMAIN_OPAQUE_CAPSULE, bucketIndex, counter, opaqueSeed, new byte[0]);
            int copyLength = Math.min(block.length, output.length - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
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

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

}
