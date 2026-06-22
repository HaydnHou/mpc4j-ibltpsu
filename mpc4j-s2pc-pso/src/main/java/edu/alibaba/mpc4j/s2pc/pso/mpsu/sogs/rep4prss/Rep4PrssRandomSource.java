package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Fast domain-separated PRSS random source for REP4 packed Boolean sharing.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
class Rep4PrssRandomSource {
    /**
     * AES block byte length.
     */
    private static final int AES_BLOCK_BYTE_LENGTH = 16;
    /**
     * AES/CTR mode.
     */
    private static final String AES_CTR_MODE = "AES/CTR/NoPadding";
    /**
     * AES algorithm.
     */
    private static final String AES_ALGORITHM = "AES";
    /**
     * Zero IV. Domain separation is already folded into the AES key.
     */
    private static final IvParameterSpec ZERO_IV = new IvParameterSpec(new byte[AES_BLOCK_BYTE_LENGTH]);

    private final long taskId;

    Rep4PrssRandomSource(long taskId) {
        this.taskId = taskId;
    }

    long[] componentRandom(byte[] componentSeed, int componentId, int stepId, long extraInfo, int dealerId,
                           int blockNum) {
        if (blockNum <= 0) {
            throw new IllegalArgumentException("blockNum must be positive: " + blockNum);
        }
        byte[] aesKey = deriveAesKey(componentSeed, componentId, stepId, extraInfo, dealerId);
        byte[] randomBytes = expandAesCtr(aesKey, blockNum * Long.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(randomBytes);
        long[] blocks = new long[blockNum];
        for (int blockIndex = 0; blockIndex < blockNum; blockIndex++) {
            blocks[blockIndex] = buffer.getLong();
        }
        return blocks;
    }

    private byte[] deriveAesKey(byte[] componentSeed, int componentId, int stepId, long extraInfo, int dealerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer context = ByteBuffer.allocate(componentSeed.length + Integer.BYTES * 3 + Long.BYTES * 2);
            context.put(componentSeed);
            context.putInt(componentId);
            context.putInt(stepId);
            context.putLong(extraInfo);
            context.putInt(dealerId);
            context.putLong(taskId);
            byte[] hash = digest.digest(context.array());
            return Arrays.copyOf(hash, AES_BLOCK_BYTE_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private byte[] expandAesCtr(byte[] aesKey, int outputByteLength) {
        try {
            Cipher cipher = Cipher.getInstance(AES_CTR_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, AES_ALGORITHM), ZERO_IV);
            return cipher.doFinal(new byte[outputByteLength]);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("invalid AES key length: " + aesKey.length, e);
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException
                 | NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("system does not support " + AES_CTR_MODE, e);
        }
    }
}
