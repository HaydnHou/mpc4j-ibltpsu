package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * BA-SSU-IBLT secure source-layer cell view.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltSecureCellView {
    /**
     * source count.
     */
    private final int count;
    /**
     * key xor.
     */
    private final byte[] keyXor;
    /**
     * OPRF tag xor.
     */
    private final byte[] tagXor;
    /**
     * check xor.
     */
    private final byte[] checkXor;

    private BaSsuIbltSecureCellView(int count, byte[] keyXor, byte[] tagXor, byte[] checkXor) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        if (keyXor == null || keyXor.length == 0) {
            throw new IllegalArgumentException("keyXor must be non-empty");
        }
        if (tagXor == null || tagXor.length == 0) {
            throw new IllegalArgumentException("tagXor must be non-empty");
        }
        if (checkXor == null || checkXor.length == 0) {
            throw new IllegalArgumentException("checkXor must be non-empty");
        }
        this.count = count;
        this.keyXor = Arrays.copyOf(keyXor, keyXor.length);
        this.tagXor = Arrays.copyOf(tagXor, tagXor.length);
        this.checkXor = Arrays.copyOf(checkXor, checkXor.length);
    }

    static BaSsuIbltSecureCellView of(int count, byte[] keyXor, byte[] tagXor, byte[] checkXor) {
        return new BaSsuIbltSecureCellView(count, keyXor, tagXor, checkXor);
    }

    static BaSsuIbltSecureCellView empty(int elementByteLength, int tagByteLength, int checkByteLength) {
        return new BaSsuIbltSecureCellView(
            0, new byte[elementByteLength], new byte[tagByteLength], new byte[checkByteLength]
        );
    }

    public int getCount() {
        return count;
    }

    public int getElementByteLength() {
        return keyXor.length;
    }

    public int getTagByteLength() {
        return tagXor.length;
    }

    public int getCheckByteLength() {
        return checkXor.length;
    }

    public byte[] getKeyXor() {
        return Arrays.copyOf(keyXor, keyXor.length);
    }

    byte[] getTagXor() {
        return Arrays.copyOf(tagXor, tagXor.length);
    }

    byte[] getCheckXor() {
        return Arrays.copyOf(checkXor, checkXor.length);
    }

    byte[] getKeyXorReference() {
        return keyXor;
    }

    byte[] getTagXorReference() {
        return tagXor;
    }

    byte[] getCheckXorReference() {
        return checkXor;
    }

    public boolean isValidSingleton() {
        return count == 1 && Arrays.equals(
            BaSsuIbltOprfTagPipeline.checkFromTag(tagXor, checkXor.length), checkXor
        );
    }
}
