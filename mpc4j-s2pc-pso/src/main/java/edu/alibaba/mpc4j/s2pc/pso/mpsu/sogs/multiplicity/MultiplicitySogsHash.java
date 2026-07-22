package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTier;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Domain-separated encodings for multiplicity SOGS cells.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class MultiplicitySogsHash {
    /** Number of independent moment coordinates. */
    public static final int MOMENT_DOMAIN_NUM = 3;
    private static final byte[] DOMAIN = "MP-SOGS-SSM-MOMENT-V1".getBytes(StandardCharsets.US_ASCII);
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    });

    private MultiplicitySogsHash() {
        // empty
    }

    public static long[] moments(long value, long sessionSeed, MpSogsTier tier) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        digest.update(DOMAIN);
        ByteBuffer input = ByteBuffer.allocate(Long.BYTES * 2 + Integer.BYTES);
        input.putLong(sessionSeed);
        input.putInt(tier.ordinal());
        input.putLong(value);
        byte[] output = digest.digest(input.array());
        ByteBuffer buffer = ByteBuffer.wrap(output);
        long[] moments = new long[MOMENT_DOMAIN_NUM];
        for (int domainIndex = 0; domainIndex < MOMENT_DOMAIN_NUM; domainIndex++) {
            moments[domainIndex] = Mersenne61Field.fromUnsignedLong(buffer.getLong());
        }
        return moments;
    }

    public static long lowLimb(long value) {
        return value & 0xFFFF_FFFFL;
    }

    public static long highLimb(long value) {
        return value >>> Integer.SIZE;
    }

    public static long joinLimbs(long low, long high) {
        if ((low >>> Integer.SIZE) != 0L || (high >>> Integer.SIZE) != 0L) {
            throw new IllegalArgumentException("payload limb exceeds 32 bits");
        }
        return (high << Integer.SIZE) | low;
    }
}
