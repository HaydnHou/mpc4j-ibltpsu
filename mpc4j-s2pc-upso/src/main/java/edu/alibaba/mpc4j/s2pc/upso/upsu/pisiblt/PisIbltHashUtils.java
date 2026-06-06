package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;

import java.nio.ByteBuffer;

/**
 * PISF-IBLT public profile hash utilities.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class PisIbltHashUtils {
    /**
     * private constructor.
     */
    private PisIbltHashUtils() {
        // empty
    }

    static byte[] key(byte[] element) {
        return BytesUtils.clone(element);
    }

    static byte[] fastKey(EnvType envType, byte[] salt, byte[] token, int keyByteLength) {
        Hash hash = HashFactory.createInstance(envType, keyByteLength);
        return hash.digestToBytes(hashInput((byte) 0x11, salt, token, 0, 0));
    }

    static byte[] fastTag(EnvType envType, byte[] salt, byte[] key, int tagByteLength) {
        Hash hash = HashFactory.createInstance(envType, tagByteLength);
        return hash.digestToBytes(hashInput((byte) 0x12, salt, key, 0, 0));
    }

    static byte[] fingerprint(EnvType envType, byte[] salt, byte[] key, int tagByteLength) {
        Hash hash = HashFactory.createInstance(envType, tagByteLength);
        return hash.digestToBytes(hashInput((byte) 0x01, salt, key, 0, 0));
    }

    static int degree(EnvType envType, byte[] salt, byte[] key, int dMax) {
        Hash hash = HashFactory.createInstance(envType, Integer.BYTES);
        int sample = Math.floorMod(IntUtils.byteArrayToInt(hash.digestToBytes(hashInput((byte) 0x02, salt, key, 0, 0))), 10);
        int degree;
        if (sample < 5) {
            degree = 3;
        } else if (sample < 8) {
            degree = 4;
        } else {
            degree = 5;
        }
        return Math.max(2, Math.min(dMax, degree));
    }

    static int[] positions(EnvType envType, byte[] salt, byte[] key, PisIbltUpsuParams params, int repetitionIndex) {
        int dMax = params.getDMax();
        int degree = degree(envType, salt, key, dMax);
        int tableLength = params.getTableLength();
        int[] positions = new int[dMax];
        Hash hash = HashFactory.createInstance(envType, Integer.BYTES);
        for (int i = 0; i < degree; i++) {
            int partitionStart = (int) (((long) i * tableLength) / dMax);
            int partitionEnd = (int) (((long) (i + 1) * tableLength) / dMax);
            int partitionSize = Math.max(1, partitionEnd - partitionStart);
            int offset = Math.floorMod(
                IntUtils.byteArrayToInt(hash.digestToBytes(hashInput((byte) 0x03, salt, key, repetitionIndex, i))),
                partitionSize
            );
            positions[i] = partitionStart + offset + 1;
        }
        return positions;
    }

    static int[] fastPositions(EnvType envType, byte[] salt, byte[] key, PisIbltUpsuParams params) {
        int degree = params.getDMax();
        int tableLength = params.getTableLength();
        int[] positions = new int[degree];
        Hash hash = HashFactory.createInstance(envType, Integer.BYTES);
        for (int i = 0; i < degree; i++) {
            int partitionStart = (int) (((long) i * tableLength) / degree);
            int partitionEnd = (int) (((long) (i + 1) * tableLength) / degree);
            int partitionSize = Math.max(1, partitionEnd - partitionStart);
            int offset = Math.floorMod(
                IntUtils.byteArrayToInt(hash.digestToBytes(hashInput((byte) 0x13, salt, key, 0, i))),
                partitionSize
            );
            positions[i] = partitionStart + offset;
        }
        return positions;
    }

    static boolean containsPosition(int[] positions, int position) {
        for (int candidate : positions) {
            if (candidate == position) {
                return true;
            }
        }
        return false;
    }

    static byte[] defaultSalt(int taskId, PisIbltUpsuParams params) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 6);
        byteBuffer.putInt(taskId);
        byteBuffer.putInt(params.getSenderCapacity());
        byteBuffer.putInt(params.getReceiverCapacity());
        byteBuffer.putInt(params.getTableLength());
        byteBuffer.putInt(params.getDMax());
        byteBuffer.putInt(params.getRepetitionNum());
        return byteBuffer.array();
    }

    private static byte[] hashInput(byte domain, byte[] salt, byte[] key, int repetitionIndex, int index) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + salt.length + key.length + Integer.BYTES * 2);
        byteBuffer.put(domain);
        byteBuffer.put(salt);
        byteBuffer.put(key);
        byteBuffer.putInt(repetitionIndex);
        byteBuffer.putInt(index);
        return byteBuffer.array();
    }
}
