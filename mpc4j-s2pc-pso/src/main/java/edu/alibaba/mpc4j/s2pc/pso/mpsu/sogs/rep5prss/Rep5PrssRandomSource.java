package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PrssPhase;

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
 * Fast domain-separated PRSS random source for REP5 packed Boolean sharing.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
class Rep5PrssRandomSource {
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

    Rep5PrssRandomSource(long taskId) {
        this.taskId = taskId;
    }

    long[] componentRandom(byte[] componentSeed, long backendId, PrssPhase phase, int componentId, int stepId,
                           long operationId, int itemIndex, int dealerId, int blockNum) {
        if (blockNum <= 0) {
            throw new IllegalArgumentException("blockNum must be positive: " + blockNum);
        }
        byte[] aesKey = deriveAesKey(
            componentSeed, backendId, phase, componentId, stepId, operationId, itemIndex, dealerId, blockNum
        );
        byte[] randomBytes = expandAesCtr(aesKey, blockNum * Long.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(randomBytes);
        long[] blocks = new long[blockNum];
        for (int blockIndex = 0; blockIndex < blockNum; blockIndex++) {
            blocks[blockIndex] = buffer.getLong();
        }
        return blocks;
    }

    private byte[] deriveAesKey(byte[] componentSeed, long backendId, PrssPhase phase, int componentId, int stepId,
                                long operationId, int itemIndex, int dealerId, int blockNum) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer context = ByteBuffer.allocate(componentSeed.length + Integer.BYTES * 7 + Long.BYTES * 3);
            context.put(componentSeed);
            context.putInt(1);
            context.putLong(taskId);
            context.putLong(backendId);
            context.putInt(phase.ordinal());
            context.putInt(componentId);
            context.putInt(stepId);
            context.putLong(operationId);
            context.putInt(itemIndex);
            context.putInt(dealerId);
            context.putInt(blockNum);
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
