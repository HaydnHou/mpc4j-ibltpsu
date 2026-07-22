package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

/**
 * Arithmetic in the Mersenne prime field F_(2^61 - 1).
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class Mersenne61Field {
    /** Prime modulus. */
    public static final long PRIME = (1L << 61) - 1L;

    private Mersenne61Field() {
        // empty
    }

    public static long add(long left, long right) {
        checkElement(left);
        checkElement(right);
        return addUnchecked(left, right);
    }

    public static long sub(long left, long right) {
        checkElement(left);
        checkElement(right);
        return subUnchecked(left, right);
    }

    public static long neg(long value) {
        checkElement(value);
        return value == 0L ? 0L : PRIME - value;
    }

    public static long mul(long left, long right) {
        checkElement(left);
        checkElement(right);
        return mulUnchecked(left, right);
    }

    public static long square(long value) {
        return mul(value, value);
    }

    public static long pow(long value, long exponent) {
        checkElement(value);
        if (exponent < 0L) {
            throw new IllegalArgumentException("exponent must be non-negative: " + exponent);
        }
        long result = 1L;
        long base = value;
        long remaining = exponent;
        while (remaining != 0L) {
            if ((remaining & 1L) != 0L) {
                result = mul(result, base);
            }
            base = square(base);
            remaining >>>= 1;
        }
        return result;
    }

    public static long inv(long value) {
        checkElement(value);
        if (value == 0L) {
            throw new ArithmeticException("zero has no inverse");
        }
        return pow(value, PRIME - 2L);
    }

    public static long div(long numerator, long denominator) {
        return mul(numerator, inv(denominator));
    }

    public static long fromUnsignedInt(int value) {
        return Integer.toUnsignedLong(value);
    }

    public static long fromUnsignedLong(long value) {
        return Long.remainderUnsigned(value, PRIME);
    }

    public static boolean isElement(long value) {
        return value >= 0L && value < PRIME;
    }

    static long addUnchecked(long left, long right) {
        long sum = left + right;
        return sum >= PRIME ? sum - PRIME : sum;
    }

    static long subUnchecked(long left, long right) {
        long difference = left - right;
        return difference < 0L ? difference + PRIME : difference;
    }

    static long mulUnchecked(long left, long right) {
        long low = left * right;
        long high = Math.multiplyHigh(left, right);
        long folded = (low & PRIME) + (high << 3) + (low >>> 61);
        return folded >= PRIME ? folded - PRIME : folded;
    }

    private static void checkElement(long value) {
        if (!isElement(value)) {
            throw new IllegalArgumentException("not a Mersenne-61 field element: " + value);
        }
    }
}
