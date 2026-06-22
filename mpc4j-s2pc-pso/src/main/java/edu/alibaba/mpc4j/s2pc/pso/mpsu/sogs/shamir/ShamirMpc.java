package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.shamir;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight semi-honest Shamir MPC helper over a 31-bit prime field.
 *
 * <p>This is intentionally scoped to MP-SOGS secure-uPeel. It supports vector sharing, vector multiplication with
 * BGW-style degree reduction, and vector opening. The threshold is {@code floor((n - 1) / 2)}, so 4 parties tolerate
 * one passive corruption and 5 parties tolerate two passive corruptions.</p>
 *
 * @author donghai hou
 * @date 2026/06/21
 */
class ShamirMpc {
    /**
     * Mersenne prime 2^31 - 1. Products of two field elements fit in a signed long.
     */
    static final long PRIME = 2_147_483_647L;
    /**
     * Protocol step for input sharing.
     */
    private static final int STEP_SHARE = 10;
    /**
     * Protocol step for multiplication resharing.
     */
    private static final int STEP_MUL_RESHARE = 11;
    /**
     * Protocol step for opening.
     */
    private static final int STEP_OPEN = 12;

    private final Rpc rpc;
    private final Party[] parties;
    private final int partyNum;
    private final int ownPartyId;
    private final int threshold;
    private final long taskId;
    private final SecureRandom secureRandom;
    private final long[] degreeReductionLambdas;
    private final long[] openLambdas;
    private long extraInfo;
    private int networkRoundCount;

    ShamirMpc(Rpc rpc, long taskId) {
        this.rpc = rpc;
        parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        partyNum = parties.length;
        if (partyNum < 3) {
            throw new IllegalArgumentException("Shamir MP-SOGS backend requires at least 3 parties: " + partyNum);
        }
        ownPartyId = rpc.ownParty().getPartyId();
        threshold = (partyNum - 1) / 2;
        if (partyNum <= 2 * threshold) {
            throw new IllegalArgumentException("Shamir MP-SOGS requires honest majority: n=" + partyNum
                + ", threshold=" + threshold);
        }
        this.taskId = taskId;
        secureRandom = new SecureRandom();
        degreeReductionLambdas = lagrangeAtZero(partyNum);
        openLambdas = lagrangeAtZero(partyNum);
    }

    int getPartyNum() {
        return partyNum;
    }

    int getNetworkRoundCount() {
        return networkRoundCount;
    }

    void resetNetworkRoundCount() {
        networkRoundCount = 0;
    }

    /**
     * Secret-shares this party's vector and receives all other parties' shared vectors.
     *
     * @param ownSecrets own secret vector.
     * @return one share vector per owner party.
     */
    long[][] shareOwnAndReceiveAll(long[] ownSecrets) {
        return shareOwnAndReceiveAll(ownSecrets, STEP_SHARE);
    }

    /**
     * Multiplies two secret-shared vectors.
     *
     * @param left left share vector.
     * @param right right share vector.
     * @return product share vector.
     */
    long[] mul(long[] left, long[] right) {
        checkSameLength(left, right);
        long[] localProduct = new long[left.length];
        for (int i = 0; i < left.length; i++) {
            localProduct[i] = mul(left[i], right[i]);
        }
        long[][] resharedProducts = shareOwnAndReceiveAll(localProduct, STEP_MUL_RESHARE);
        long[] reduced = new long[left.length];
        for (int dealerId = 0; dealerId < partyNum; dealerId++) {
            long lambda = degreeReductionLambdas[dealerId];
            for (int i = 0; i < reduced.length; i++) {
                reduced[i] = add(reduced[i], mul(lambda, resharedProducts[dealerId][i]));
            }
        }
        return reduced;
    }

    /**
     * Opens a secret-shared vector to all parties.
     *
     * @param share local share vector.
     * @return opened field vector.
     */
    long[] open(long[] share) {
        long info = extraInfo++;
        byte[] ownPayload = encodeLongArray(share);
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                    ownPartyId, party.getPartyId()
                );
                rpc.send(DataPacket.fromByteArrayList(header, List.of(ownPayload)));
            }
        }
        long[][] allShares = new long[partyNum][];
        allShares[ownPartyId] = share;
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1) {
                    throw new IllegalStateException("invalid Shamir open payload size: " + payload.size());
                }
                allShares[party.getPartyId()] = decodeLongArray(payload.get(0));
            }
        }
        networkRoundCount++;
        return reconstruct(allShares, openLambdas, share.length);
    }

    private long[][] shareOwnAndReceiveAll(long[] ownSecrets, int stepId) {
        long info = extraInfo++;
        long[][] ownShares = shareVector(ownSecrets);
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    ownPartyId, party.getPartyId()
                );
                rpc.send(DataPacket.fromByteArrayList(header, List.of(encodeLongArray(ownShares[party.getPartyId()]))));
            }
        }
        long[][] sharesByOwner = new long[partyNum][];
        sharesByOwner[ownPartyId] = ownShares[ownPartyId];
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1) {
                    throw new IllegalStateException("invalid Shamir share payload size: " + payload.size());
                }
                sharesByOwner[party.getPartyId()] = decodeLongArray(payload.get(0));
            }
        }
        networkRoundCount++;
        return sharesByOwner;
    }

    private long[][] shareVector(long[] secrets) {
        long[][] shares = new long[partyNum][secrets.length];
        long[] coefficients = new long[threshold + 1];
        for (int valueIndex = 0; valueIndex < secrets.length; valueIndex++) {
            coefficients[0] = mod(secrets[valueIndex]);
            for (int degree = 1; degree <= threshold; degree++) {
                coefficients[degree] = randomFieldElement();
            }
            for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
                shares[partyIndex][valueIndex] = evaluatePolynomial(coefficients, partyIndex + 1L);
            }
        }
        return shares;
    }

    private long randomFieldElement() {
        return secureRandom.nextInt((int) PRIME);
    }

    private static long evaluatePolynomial(long[] coefficients, long x) {
        long result = 0L;
        for (int degree = coefficients.length - 1; degree >= 0; degree--) {
            result = add(mul(result, x), coefficients[degree]);
        }
        return result;
    }

    private static long[] reconstruct(long[][] shares, long[] lambdas, int length) {
        long[] values = new long[length];
        for (int partyIndex = 0; partyIndex < shares.length; partyIndex++) {
            if (shares[partyIndex] == null || shares[partyIndex].length != length) {
                throw new IllegalStateException("invalid Shamir share from party " + partyIndex);
            }
            long lambda = lambdas[partyIndex];
            for (int i = 0; i < length; i++) {
                values[i] = add(values[i], mul(lambda, shares[partyIndex][i]));
            }
        }
        return values;
    }

    private static long[] lagrangeAtZero(int pointNum) {
        long[] lambdas = new long[pointNum];
        for (int i = 0; i < pointNum; i++) {
            long xi = i + 1L;
            long numerator = 1L;
            long denominator = 1L;
            for (int j = 0; j < pointNum; j++) {
                if (i == j) {
                    continue;
                }
                long xj = j + 1L;
                numerator = mul(numerator, neg(xj));
                denominator = mul(denominator, sub(xi, xj));
            }
            lambdas[i] = mul(numerator, inv(denominator));
        }
        return lambdas;
    }

    static long[] zeros(int length) {
        return new long[length];
    }

    static long[] ones(int length) {
        long[] ones = new long[length];
        Arrays.fill(ones, 1L);
        return ones;
    }

    static long[] constant(int length, long value) {
        long[] result = new long[length];
        Arrays.fill(result, mod(value));
        return result;
    }

    static long[] add(long[] left, long[] right) {
        checkSameLength(left, right);
        long[] result = new long[left.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = add(left[i], right[i]);
        }
        return result;
    }

    static long[] sub(long[] left, long[] right) {
        checkSameLength(left, right);
        long[] result = new long[left.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = sub(left[i], right[i]);
        }
        return result;
    }

    static long[] not(long[] vector) {
        long[] result = new long[vector.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = sub(1L, vector[i]);
        }
        return result;
    }

    static long add(long left, long right) {
        long result = left + right;
        return result >= PRIME ? result - PRIME : result;
    }

    static long sub(long left, long right) {
        long result = left - right;
        return result < 0 ? result + PRIME : result;
    }

    static long neg(long value) {
        return value == 0 ? 0 : PRIME - value;
    }

    static long mul(long left, long right) {
        return (mod(left) * mod(right)) % PRIME;
    }

    static long mod(long value) {
        long result = value % PRIME;
        return result < 0 ? result + PRIME : result;
    }

    private static long inv(long value) {
        if (value == 0) {
            throw new ArithmeticException("zero has no inverse");
        }
        long base = value;
        long exponent = PRIME - 2;
        long result = 1L;
        while (exponent > 0) {
            if ((exponent & 1L) == 1L) {
                result = mul(result, base);
            }
            base = mul(base, base);
            exponent >>>= 1;
        }
        return result;
    }

    private static byte[] encodeLongArray(long[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Long.BYTES);
        for (long value : values) {
            buffer.putLong(value);
        }
        return buffer.array();
    }

    private static long[] decodeLongArray(byte[] payload) {
        if (payload.length % Long.BYTES != 0) {
            throw new IllegalArgumentException("invalid long-array payload length: " + payload.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        long[] values = new long[payload.length / Long.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getLong();
        }
        return values;
    }

    private static void checkSameLength(long[] left, long[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("length mismatch: " + left.length + " != " + right.length);
        }
    }
}
