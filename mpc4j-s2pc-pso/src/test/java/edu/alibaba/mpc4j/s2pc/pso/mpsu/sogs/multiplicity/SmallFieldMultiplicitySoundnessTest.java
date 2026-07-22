package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

/**
 * Small-field checks for the multiplicity predicate's statistical error scaling.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class SmallFieldMultiplicitySoundnessTest {
    private static final int PRIME = 17;
    private static final int TRIALS = 1_000_000;

    @Test
    public void testIndependentMomentDomains() {
        Random random = new Random(2026072201L);
        int[] falseAccepts = new int[3];
        for (int trial = 0; trial < TRIALS; trial++) {
            boolean accepted = true;
            for (int domain = 0; domain < 3; domain++) {
                int leftHash = random.nextInt(PRIME);
                int rightHash = random.nextInt(PRIME);
                accepted &= leftHash == rightHash;
                if (accepted) {
                    falseAccepts[domain]++;
                }
            }
        }

        assertNearExpected(falseAccepts[0], TRIALS / (double) PRIME, 0.03);
        assertNearExpected(falseAccepts[1], TRIALS / Math.pow(PRIME, 2), 0.12);
        assertNearExpected(falseAccepts[2], TRIALS / Math.pow(PRIME, 3), 0.30);
        Assert.assertTrue(falseAccepts[0] > falseAccepts[1]);
        Assert.assertTrue(falseAccepts[1] > falseAccepts[2]);
    }

    @Test
    public void testCompressedMaskedZeroTest() {
        Random random = new Random(2026072202L);
        int falseAccepts = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            boolean accepted = true;
            for (int repetition = 0; repetition < 3; repetition++) {
                int compression = random.nextInt(PRIME);
                int mask = random.nextInt(PRIME);
                accepted &= mod(compression * mask) == 0;
            }
            if (accepted) {
                falseAccepts++;
            }
        }

        double oneRepetitionError = (2.0 * PRIME - 1.0) / (PRIME * PRIME);
        assertNearExpected(falseAccepts, TRIALS * Math.pow(oneRepetitionError, 3), 0.10);
    }

    @Test
    public void testCombinedTwoDistinctPredicate() {
        Random random = new Random(2026072203L);
        int falseAccepts = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            int[] errors = new int[3];
            for (int domain = 0; domain < errors.length; domain++) {
                int leftHash = random.nextInt(PRIME);
                int rightHash = random.nextInt(PRIME);
                int difference = mod(leftHash - rightHash);
                // Multiplicities 1 and 2 give E = 2 * (h(x) - h(y))^2.
                errors[domain] = mod(2 * difference * difference);
            }

            boolean accepted = true;
            for (int repetition = 0; repetition < 3; repetition++) {
                int compressed = 0;
                for (int error : errors) {
                    compressed = mod(compressed + random.nextInt(PRIME) * error);
                }
                int mask = random.nextInt(PRIME);
                accepted &= mod(compressed * mask) == 0;
            }
            if (accepted) {
                falseAccepts++;
            }
        }

        double conservativeBound = 16.0 * TRIALS / Math.pow(PRIME, 3);
        Assert.assertTrue(
            "observed=" + falseAccepts + ", bound=" + conservativeBound,
            falseAccepts < conservativeBound
        );
    }

    private static int mod(int value) {
        int reduced = value % PRIME;
        return reduced < 0 ? reduced + PRIME : reduced;
    }

    private static void assertNearExpected(int actual, double expected, double relativeTolerance) {
        double difference = Math.abs(actual - expected);
        Assert.assertTrue(
            "actual=" + actual + ", expected=" + expected,
            difference <= expected * relativeTolerance
        );
    }
}
