package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * BA-SSU-IBLT OPRF tag/check derivation scaffold.
 *
 * <p>The final semi-honest protocol must replace {@link #referenceTag(byte[], int)} with a two-party OPRF output. This
 * class fixes the local domain-separated data flow used by the prototype: public placement hashes stay independent,
 * element tags are derived in a tag domain, checks are derived only from tags, and BA-UPOT-local masks use a separate
 * domain. Tags and checks are local values and must not be exposed as plaintext membership-test material.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
final class BaSsuIbltOprfTagPipeline {
    /**
     * tag domain.
     */
    static final byte DOMAIN_TAG = 0x54;
    /**
     * check domain.
     */
    static final byte DOMAIN_CHECK = 0x43;
    /**
     * BA-UPOT local domain.
     */
    static final byte DOMAIN_UPOT = 0x55;
    /**
     * test-only alternate tag domain.
     */
    static final byte DOMAIN_TEST_TAG = 0x6A;
    /**
     * fixed reference label.
     */
    private static final byte[] REFERENCE_LABEL = new byte[]{
        0x42, 0x41, 0x2D, 0x53, 0x53, 0x55, 0x2D, 0x49, 0x42, 0x4C, 0x54, 0x2D, 0x4F, 0x50, 0x52, 0x46
    };

    /**
     * private constructor.
     */
    private BaSsuIbltOprfTagPipeline() {
        // empty
    }

    /**
     * Derives a reference OPRF tag. This is not a network OPRF.
     *
     * @param params BA-SSU parameters.
     * @param element element bytes.
     * @return tag.
     */
    static byte[] referenceTag(BaSsuIbltBiUpsuParams params, byte[] element) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return referenceTag(element, byteLength(params.getTagBits()));
    }

    /**
     * Derives a reference OPRF tag. This is not a network OPRF.
     *
     * @param element element bytes.
     * @param tagByteLength tag byte length.
     * @return tag.
     */
    static byte[] referenceTag(byte[] element, int tagByteLength) {
        return derive(DOMAIN_TAG, 0, 0, element, tagByteLength);
    }

    /**
     * Derives a singleton check from a reference tag.
     *
     * @param tag OPRF tag.
     * @param checkByteLength check byte length.
     * @return check.
     */
    static byte[] checkFromTag(byte[] tag, int checkByteLength) {
        return derive(DOMAIN_CHECK, 0, 0, tag, checkByteLength);
    }

    /**
     * Derives an element tag from a real two-party OPRF PRF output.
     *
     * @param prf OPRF PRF output.
     * @param tagByteLength tag byte length.
     * @return tag.
     */
    static byte[] tagFromPrf(byte[] prf, int tagByteLength) {
        return derive(DOMAIN_TAG, 0, 0, prf, tagByteLength);
    }

    /**
     * Derives a singleton check directly from an element through tag -> check.
     *
     * @param params BA-SSU parameters.
     * @param element element.
     * @return check.
     */
    static byte[] referenceCheck(BaSsuIbltBiUpsuParams params, byte[] element) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return referenceCheck(element, byteLength(params.getCheckBits()));
    }

    /**
     * Derives a singleton check directly from an element through tag -> check.
     *
     * @param element element.
     * @param checkByteLength check byte length.
     * @return check.
     */
    static byte[] referenceCheck(byte[] element, int checkByteLength) {
        byte[] tag = referenceTag(element, checkByteLength);
        return checkFromTag(tag, checkByteLength);
    }

    /**
     * Derives BA-UPOT-local bytes from a tag.
     *
     * @param bucketIndex bucket index.
     * @param branch branch.
     * @param tag tag.
     * @param outputByteLength output byte length.
     * @return derived bytes.
     */
    static byte[] upotLocal(int bucketIndex, int branch, byte[] tag, int outputByteLength) {
        return derive(DOMAIN_UPOT, bucketIndex, branch, tag, outputByteLength);
    }

    static byte[] referenceTagWithDomain(byte domain, byte[] element, int tagByteLength) {
        return derive(domain, 0, 0, element, tagByteLength);
    }

    static int byteLength(int bitLength) {
        if (bitLength <= 0) {
            throw new IllegalArgumentException("bitLength must be positive");
        }
        return (bitLength + Byte.SIZE - 1) / Byte.SIZE;
    }

    private static byte[] derive(byte domain, int bucketIndex, int branch, byte[] input, int outputByteLength) {
        if (input == null) {
            throw new IllegalArgumentException("input must be non-null");
        }
        if (outputByteLength <= 0) {
            throw new IllegalArgumentException("outputByteLength must be positive");
        }
        byte[] output = new byte[outputByteLength];
        int offset = 0;
        int counter = 0;
        while (offset < output.length) {
            byte[] block = digest(domain, bucketIndex, branch, counter, input);
            int copyLength = Math.min(block.length, output.length - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        return output;
    }

    private static byte[] digest(byte domain, int bucketIndex, int branch, int counter, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(REFERENCE_LABEL);
            digest.update(domain);
            digest.update(ByteBuffer.allocate(Integer.BYTES * 3)
                .putInt(bucketIndex)
                .putInt(branch)
                .putInt(counter)
                .array());
            digest.update(input);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static boolean equalCheck(byte[] element, byte[] check) {
        if (check == null) {
            throw new IllegalArgumentException("check must be non-null");
        }
        return Arrays.equals(referenceCheck(element, check.length), check);
    }
}
