package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.util.Arrays;

/**
 * Source-split PISF-IBLT table used by the conservative profile simulator.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltSourceSplitTable {
    /**
     * public profile.
     */
    private final PisIbltUpsuParams params;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * public salt.
     */
    private final byte[] salt;
    /**
     * flattened table.
     */
    private final Bucket[] buckets;

    public PisIbltSourceSplitTable(PisIbltUpsuParams params, int elementByteLength, byte[] salt) {
        this.params = params;
        this.elementByteLength = elementByteLength;
        this.salt = BytesUtils.clone(salt);
        buckets = new Bucket[params.getTableLength()];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new Bucket(elementByteLength, params.getTagByteLength(), params.getDMax());
        }
    }

    public PisIbltUpsuParams getParams() {
        return params;
    }

    public Bucket getBucket(int encodedAddress) {
        return buckets[encodedAddress - 1];
    }

    public Bucket[] getBuckets() {
        return buckets;
    }

    public void insertSender(EnvType envType, byte[] element, int owner, int payloadKey, int repetitionIndex) {
        byte[] key = PisIbltHashUtils.key(element);
        byte[] fp = PisIbltHashUtils.fingerprint(envType, salt, key, params.getTagByteLength());
        int[] posList = PisIbltHashUtils.positions(envType, salt, key, params, repetitionIndex);
        for (int encodedAddress : posList) {
            if (encodedAddress != 0) {
                buckets[encodedAddress - 1].senderSide.insert(key, fp, posList, owner, payloadKey);
            }
        }
    }

    public void insertReceiver(EnvType envType, byte[] element, int owner, int repetitionIndex) {
        byte[] key = PisIbltHashUtils.key(element);
        byte[] fp = PisIbltHashUtils.fingerprint(envType, salt, key, params.getTagByteLength());
        int[] posList = PisIbltHashUtils.positions(envType, salt, key, params, repetitionIndex);
        for (int encodedAddress : posList) {
            if (encodedAddress != 0) {
                buckets[encodedAddress - 1].receiverSide.insert(key, fp, posList, owner, 0);
            }
        }
    }

    boolean tableAllZero() {
        return Arrays.stream(buckets)
            .allMatch(bucket -> bucket.senderSide.isZero() && bucket.receiverSide.isZero());
    }

    /**
     * PISF source-split bucket.
     */
    public static class Bucket {
        /**
         * sender side.
         */
        final Side senderSide;
        /**
         * receiver side.
         */
        final Side receiverSide;

        Bucket(int elementByteLength, int tagByteLength, int dMax) {
            senderSide = new Side(elementByteLength, tagByteLength, dMax);
            receiverSide = new Side(elementByteLength, tagByteLength, dMax);
        }
    }

    /**
     * One source side of a PISF bucket.
     */
    static class Side {
        /**
         * count.
         */
        private int count;
        /**
         * key xor.
         */
        private final byte[] keyXor;
        /**
         * fingerprint xor.
         */
        private final byte[] fpXor;
        /**
         * position xor.
         */
        private final int[] posXor;
        /**
         * owner xor.
         */
        private int ownerXor;
        /**
         * payload key xor.
         */
        private int payloadKeyXor;

        Side(int elementByteLength, int tagByteLength, int dMax) {
            count = 0;
            keyXor = new byte[elementByteLength];
            fpXor = new byte[tagByteLength];
            posXor = new int[dMax];
            ownerXor = 0;
            payloadKeyXor = 0;
        }

        int getCount() {
            return count;
        }

        byte[] getKeyXor() {
            return keyXor;
        }

        byte[] getFpXor() {
            return fpXor;
        }

        int[] getPosXor() {
            return posXor;
        }

        int getOwnerXor() {
            return ownerXor;
        }

        int getPayloadKeyXor() {
            return payloadKeyXor;
        }

        void insert(byte[] key, byte[] fp, int[] posList, int owner, int payloadKey) {
            count++;
            BytesUtils.xori(keyXor, key);
            BytesUtils.xori(fpXor, fp);
            xorPositions(posList);
            ownerXor ^= owner;
            payloadKeyXor ^= payloadKey;
        }

        void delete(byte[] key, byte[] fp, int[] posList, int owner, int payloadKey) {
            count--;
            BytesUtils.xori(keyXor, key);
            BytesUtils.xori(fpXor, fp);
            xorPositions(posList);
            ownerXor ^= owner;
            payloadKeyXor ^= payloadKey;
        }

        boolean isZero() {
            return count == 0
                && isZero(keyXor)
                && isZero(fpXor)
                && Arrays.stream(posXor).allMatch(x -> x == 0)
                && ownerXor == 0
                && payloadKeyXor == 0;
        }

        private void xorPositions(int[] posList) {
            for (int i = 0; i < posXor.length; i++) {
                posXor[i] ^= posList[i];
            }
        }

        private boolean isZero(byte[] bytes) {
            for (byte b : bytes) {
                if (b != 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
