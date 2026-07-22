package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Tests for the Mersenne-61 field.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class Mersenne61FieldTest {
    private static final BigInteger PRIME = BigInteger.valueOf(Mersenne61Field.PRIME);

    @Test
    public void testBoundaryArithmetic() {
        long p = Mersenne61Field.PRIME;
        Assert.assertEquals(0L, Mersenne61Field.add(p - 1, 1L));
        Assert.assertEquals(p - 1, Mersenne61Field.sub(0L, 1L));
        Assert.assertEquals(1L, Mersenne61Field.mul(p - 1, p - 1));
        Assert.assertEquals(0L, Mersenne61Field.mul(0L, p - 1));
        Assert.assertEquals(p - 1, Mersenne61Field.mul(1L, p - 1));
    }

    @Test
    public void testAgainstBigInteger() {
        SecureRandom random = new SecureRandom();
        for (int round = 0; round < 100_000; round++) {
            long left = randomFieldElement(random);
            long right = randomFieldElement(random);
            BigInteger leftBig = BigInteger.valueOf(left);
            BigInteger rightBig = BigInteger.valueOf(right);
            Assert.assertEquals(
                leftBig.add(rightBig).mod(PRIME).longValueExact(), Mersenne61Field.add(left, right)
            );
            Assert.assertEquals(
                leftBig.subtract(rightBig).mod(PRIME).longValueExact(), Mersenne61Field.sub(left, right)
            );
            Assert.assertEquals(
                leftBig.multiply(rightBig).mod(PRIME).longValueExact(), Mersenne61Field.mul(left, right)
            );
            if (left != 0L) {
                Assert.assertEquals(1L, Mersenne61Field.mul(left, Mersenne61Field.inv(left)));
            }
        }
    }

    private static long randomFieldElement(SecureRandom random) {
        long value;
        do {
            value = random.nextLong() & Mersenne61Field.PRIME;
        } while (value == Mersenne61Field.PRIME);
        return value;
    }
}
