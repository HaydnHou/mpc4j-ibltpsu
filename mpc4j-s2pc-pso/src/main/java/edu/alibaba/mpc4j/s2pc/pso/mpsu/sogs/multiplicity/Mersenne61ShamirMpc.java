package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Honest-majority Shamir/BGW helper over the Mersenne-61 field.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class Mersenne61ShamirMpc {
    private static final int STEP_SHARE = 40;
    private static final int STEP_MUL_RESHARE = 41;
    private static final int STEP_OPEN = 42;
    private static final int STEP_BALANCED_OPEN_SHARE = 45;
    private static final int STEP_BALANCED_OPEN_VALUE = 46;
    private static final int STEP_DOUBLE_SHARE_T = 47;
    private static final int STEP_DOUBLE_SHARE_2T = 48;

    private final Rpc rpc;
    private final Party[] parties;
    private final int partyNum;
    private final int ownPartyId;
    private final int threshold;
    private final long taskId;
    private final Mersenne61WireFormat wireFormat;
    private final boolean useUncheckedHotPath;
    private final SecureRandom secureRandom;
    private final long[] degreeReductionLambdas;
    private final long[] openLambdas;
    private long extraInfo;
    private int networkRoundCount;

    public Mersenne61ShamirMpc(Rpc rpc, long taskId) {
        this(rpc, taskId, Mersenne61WireFormat.LONG_64);
    }

    public Mersenne61ShamirMpc(Rpc rpc, long taskId, Mersenne61WireFormat wireFormat) {
        this.rpc = rpc;
        parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        partyNum = parties.length;
        if (partyNum < 3) {
            throw new IllegalArgumentException("multiplicity Shamir backend requires at least 3 parties: " + partyNum);
        }
        ownPartyId = rpc.ownParty().getPartyId();
        threshold = (partyNum - 1) / 2;
        if (partyNum <= 2 * threshold) {
            throw new IllegalArgumentException("multiplicity Shamir backend requires an honest majority: n="
                + partyNum + ", threshold=" + threshold);
        }
        this.taskId = taskId;
        if (wireFormat == null) {
            throw new IllegalArgumentException("wire format must be set");
        }
        this.wireFormat = wireFormat;
        useUncheckedHotPath = wireFormat == Mersenne61WireFormat.PACKED_61;
        secureRandom = new SecureRandom();
        degreeReductionLambdas = lagrangeAtZero(partyNum);
        openLambdas = lagrangeAtZero(partyNum);
    }

    public int getPartyNum() {
        return partyNum;
    }

    public int getThreshold() {
        return threshold;
    }

    public Mersenne61WireFormat getWireFormat() {
        return wireFormat;
    }

    public int getNetworkRoundCount() {
        return networkRoundCount;
    }

    public void resetNetworkRoundCount() {
        networkRoundCount = 0;
    }

    /**
     * Shares this party's vector, receives every other dealer's shares, and locally adds them.
     */
    public long[] shareOwnAndAggregate(long[] ownSecrets) {
        long[][] sharesByOwner = shareOwnAndReceiveAll(ownSecrets, STEP_SHARE, threshold);
        long[] aggregate = new long[ownSecrets.length];
        for (long[] ownerShares : sharesByOwner) {
            for (int index = 0; index < aggregate.length; index++) {
                aggregate[index] = addInternal(aggregate[index], ownerShares[index]);
            }
        }
        return aggregate;
    }

    /**
     * Shares and aggregates the same dealer secrets at a requested polynomial degree.
     */
    public long[] shareOwnAndAggregateAtDegree(long[] ownSecrets, int degree) {
        if (degree != threshold && degree != 2 * threshold) {
            throw new IllegalArgumentException("supported sharing degrees are t and 2t: " + degree);
        }
        if (degree >= partyNum) {
            throw new IllegalArgumentException("sharing degree requires more evaluation points: " + degree);
        }
        int stepId = degree == threshold ? STEP_DOUBLE_SHARE_T : STEP_DOUBLE_SHARE_2T;
        long[][] sharesByOwner = shareOwnAndReceiveAll(ownSecrets, stepId, degree);
        long[] aggregate = new long[ownSecrets.length];
        for (long[] ownerShares : sharesByOwner) {
            for (int index = 0; index < aggregate.length; index++) {
                aggregate[index] = addInternal(aggregate[index], ownerShares[index]);
            }
        }
        return aggregate;
    }

    /**
     * Multiplies a flattened batch of independent shared values in one degree-reduction round.
     */
    public long[] mul(long[] left, long[] right) {
        checkSameLength(left, right);
        long[] localProducts = new long[left.length];
        for (int index = 0; index < localProducts.length; index++) {
            localProducts[index] = mulInternal(left[index], right[index]);
        }
        long[][] resharedProducts = shareOwnAndReceiveAll(localProducts, STEP_MUL_RESHARE, threshold);
        long[] reduced = new long[left.length];
        for (int dealerId = 0; dealerId < partyNum; dealerId++) {
            long lambda = degreeReductionLambdas[dealerId];
            for (int index = 0; index < reduced.length; index++) {
                reduced[index] = addInternal(
                    reduced[index], mulInternal(lambda, resharedProducts[dealerId][index])
                );
            }
        }
        return reduced;
    }

    /**
     * Reduces degree-2t local products with a fresh correlated degree-t / degree-2t random sharing.
     */
    public long[] mulWithDoubleShare(long[] left, long[] right, ShamirDoubleShare doubleShare) {
        return mulWithDoubleShare(
            left, right, doubleShare, SsmOpeningMode.BALANCED_TWO_PHASE, 0.0, 0.0
        );
    }

    /**
     * Reduces degree-2t products with the selected public collective-opening policy.
     */
    public long[] mulWithDoubleShare(long[] left, long[] right, ShamirDoubleShare doubleShare,
                                     SsmOpeningMode openingMode, double rttMillis, double bandwidthMbps) {
        checkSameLength(left, right);
        if (doubleShare.degreeT().length != left.length || doubleShare.degree2T().length != left.length) {
            throw new IllegalArgumentException("double-share vector length mismatch");
        }
        long[] maskedProducts = new long[left.length];
        for (int index = 0; index < maskedProducts.length; index++) {
            long product = mulInternal(left[index], right[index]);
            maskedProducts[index] = subInternal(product, doubleShare.degree2T()[index]);
        }
        long[] openedDifferences = openRttAware(maskedProducts, openingMode, rttMillis, bandwidthMbps);
        long[] reduced = new long[left.length];
        for (int index = 0; index < reduced.length; index++) {
            reduced[index] = addInternal(doubleShare.degreeT()[index], openedDifferences[index]);
        }
        return reduced;
    }

    /**
     * Opens a terminal product after rerandomizing its degree-2t sharing polynomial with a fresh zero sharing.
     */
    public long[] openTerminalProduct(long[] left, long[] right, long[] degree2TZero,
                                      SsmOpeningMode openingMode, double rttMillis, double bandwidthMbps) {
        checkSameLength(left, right);
        if (degree2TZero.length != left.length) {
            throw new IllegalArgumentException("degree-2t zero-share vector length mismatch");
        }
        long[] rerandomizedProducts = new long[left.length];
        for (int index = 0; index < rerandomizedProducts.length; index++) {
            rerandomizedProducts[index] = addInternal(
                mulInternal(left[index], right[index]), degree2TZero[index]
            );
        }
        return openRttAware(rerandomizedProducts, openingMode, rttMillis, bandwidthMbps);
    }

    /**
     * Opens with a public bandwidth-delay policy.
     */
    public long[] openRttAware(long[] shares, SsmOpeningMode openingMode,
                               double rttMillis, double bandwidthMbps) {
        if (openingMode == null) {
            throw new IllegalArgumentException("opening mode must be set");
        }
        return openingMode.useAllToAll(partyNum, shares.length, rttMillis, bandwidthMbps)
            ? open(shares)
            : openBalanced(shares);
    }

    public long[] open(long[] shares) {
        long info = extraInfo++;
        byte[] ownPayload = encodeVector(shares);
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
        allShares[ownPartyId] = shares;
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_OPEN, info,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1) {
                    throw new IllegalStateException("invalid Mersenne-61 Shamir open payload size: " + payload.size());
                }
                allShares[party.getPartyId()] = decodeVector(payload.get(0), shares.length);
            }
        }
        networkRoundCount++;
        return reconstruct(allShares, openLambdas, shares.length);
    }

    /**
     * Opens a public vector through rotating reconstruction roots with balanced send volume.
     */
    public long[] openBalanced(long[] shares) {
        if (shares.length == 0) {
            return new long[0];
        }
        long info = extraInfo++;
        int rotation = (int) Math.floorMod(info, (long) partyNum);
        long[][] openedChunks = new long[partyNum][];

        // Phase 1: every party sends one share chunk to each non-local reconstruction root.
        for (int chunkIndex = 0; chunkIndex < partyNum; chunkIndex++) {
            int from = chunkStart(shares.length, chunkIndex);
            int to = chunkStart(shares.length, chunkIndex + 1);
            if (from == to) {
                continue;
            }
            int rootId = (rotation + chunkIndex) % partyNum;
            if (rootId != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_BALANCED_OPEN_SHARE, info,
                    ownPartyId, rootId
                );
                rpc.send(DataPacket.fromByteArrayList(
                    header, List.of(encodeVector(shares, from, to - from))
                ));
            }
        }

        int ownChunkIndex = Math.floorMod(ownPartyId - rotation, partyNum);
        int ownFrom = chunkStart(shares.length, ownChunkIndex);
        int ownTo = chunkStart(shares.length, ownChunkIndex + 1);
        if (ownFrom != ownTo) {
            int chunkLength = ownTo - ownFrom;
            long[][] allShares = new long[partyNum][];
            allShares[ownPartyId] = Arrays.copyOfRange(shares, ownFrom, ownTo);
            for (Party party : parties) {
                if (party.getPartyId() == ownPartyId) {
                    continue;
                }
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_BALANCED_OPEN_SHARE, info,
                    party.getPartyId(), ownPartyId
                );
                List<byte[]> payload = rpc.receive(header).getPayload();
                if (payload.size() != 1) {
                    throw new IllegalStateException("invalid balanced-open share payload size: " + payload.size());
                }
                long[] remoteShares = decodeVector(payload.get(0), chunkLength);
                if (remoteShares.length != chunkLength) {
                    throw new IllegalStateException("invalid balanced-open share vector length");
                }
                allShares[party.getPartyId()] = remoteShares;
            }
            openedChunks[ownChunkIndex] = reconstruct(allShares, openLambdas, chunkLength);
        } else {
            openedChunks[ownChunkIndex] = new long[0];
        }

        // Phase 2: each root broadcasts its reconstructed public chunk.
        if (ownFrom != ownTo) {
            byte[] ownOpenedPayload = encodeVector(openedChunks[ownChunkIndex]);
            for (Party party : parties) {
                if (party.getPartyId() != ownPartyId) {
                    DataPacketHeader header = new DataPacketHeader(
                        taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_BALANCED_OPEN_VALUE, info,
                        ownPartyId, party.getPartyId()
                    );
                    rpc.send(DataPacket.fromByteArrayList(header, List.of(ownOpenedPayload)));
                }
            }
        }
        for (int chunkIndex = 0; chunkIndex < partyNum; chunkIndex++) {
            int from = chunkStart(shares.length, chunkIndex);
            int to = chunkStart(shares.length, chunkIndex + 1);
            if (from == to) {
                openedChunks[chunkIndex] = new long[0];
                continue;
            }
            int rootId = (rotation + chunkIndex) % partyNum;
            if (rootId == ownPartyId) {
                continue;
            }
            DataPacketHeader header = new DataPacketHeader(
                taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_BALANCED_OPEN_VALUE, info,
                rootId, ownPartyId
            );
            List<byte[]> payload = rpc.receive(header).getPayload();
            if (payload.size() != 1) {
                throw new IllegalStateException("invalid balanced-open value payload size: " + payload.size());
            }
            openedChunks[chunkIndex] = decodeVector(payload.get(0), to - from);
            if (openedChunks[chunkIndex].length != to - from) {
                throw new IllegalStateException("invalid balanced-open value vector length");
            }
        }
        long[] opened = new long[shares.length];
        for (int chunkIndex = 0; chunkIndex < partyNum; chunkIndex++) {
            System.arraycopy(
                openedChunks[chunkIndex], 0, opened, chunkStart(shares.length, chunkIndex),
                openedChunks[chunkIndex].length
            );
        }
        networkRoundCount += 2;
        return opened;
    }

    public long[] randomFieldVector(int length) {
        long[] result = new long[length];
        for (int index = 0; index < length; index++) {
            result[index] = randomFieldElement();
        }
        return result;
    }

    public static long[] add(long[] left, long[] right) {
        checkSameLength(left, right);
        long[] result = new long[left.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = Mersenne61Field.add(left[index], right[index]);
        }
        return result;
    }

    public static long[] sub(long[] left, long[] right) {
        checkSameLength(left, right);
        long[] result = new long[left.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = Mersenne61Field.sub(left[index], right[index]);
        }
        return result;
    }

    public static long[] subPublic(long[] shares, long value) {
        long[] result = Arrays.copyOf(shares, shares.length);
        for (int index = 0; index < result.length; index++) {
            result[index] = Mersenne61Field.sub(result[index], value);
        }
        return result;
    }

    public static long[] mulPublic(long[] shares, long value) {
        long[] result = new long[shares.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = Mersenne61Field.mul(shares[index], value);
        }
        return result;
    }

    private long[][] shareOwnAndReceiveAll(long[] ownSecrets, int stepId, int degree) {
        long info = extraInfo++;
        long[][] ownShares = shareVector(ownSecrets, degree);
        for (Party party : parties) {
            if (party.getPartyId() != ownPartyId) {
                DataPacketHeader header = new DataPacketHeader(
                    taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), stepId, info,
                    ownPartyId, party.getPartyId()
                );
                rpc.send(DataPacket.fromByteArrayList(
                    header, List.of(encodeVector(ownShares[party.getPartyId()]))
                ));
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
                    throw new IllegalStateException("invalid Mersenne-61 Shamir share payload size: " + payload.size());
                }
                sharesByOwner[party.getPartyId()] = decodeVector(payload.get(0), ownSecrets.length);
            }
        }
        networkRoundCount++;
        return sharesByOwner;
    }

    private long[][] shareVector(long[] secrets, int degree) {
        long[][] shares = new long[partyNum][secrets.length];
        long[] coefficients = new long[degree + 1];
        for (int valueIndex = 0; valueIndex < secrets.length; valueIndex++) {
            if (!Mersenne61Field.isElement(secrets[valueIndex])) {
                throw new IllegalArgumentException("secret is outside the Mersenne-61 field");
            }
            coefficients[0] = secrets[valueIndex];
            for (int coefficientIndex = 1; coefficientIndex <= degree; coefficientIndex++) {
                coefficients[coefficientIndex] = randomFieldElement();
            }
            for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
                shares[partyIndex][valueIndex] = evaluatePolynomial(coefficients, partyIndex + 1L);
            }
        }
        return shares;
    }

    private long randomFieldElement() {
        return Mersenne61Field.fromUnsignedLong(secureRandom.nextLong());
    }

    private long evaluatePolynomial(long[] coefficients, long x) {
        long result = 0L;
        for (int degree = coefficients.length - 1; degree >= 0; degree--) {
            result = addInternal(mulInternal(result, x), coefficients[degree]);
        }
        return result;
    }

    private long[] reconstruct(long[][] shares, long[] lambdas, int length) {
        long[] values = new long[length];
        for (int partyIndex = 0; partyIndex < shares.length; partyIndex++) {
            if (shares[partyIndex] == null || shares[partyIndex].length != length) {
                throw new IllegalStateException("invalid Mersenne-61 Shamir share from party " + partyIndex);
            }
            long lambda = lambdas[partyIndex];
            for (int index = 0; index < length; index++) {
                values[index] = addInternal(
                    values[index], mulInternal(lambda, shares[partyIndex][index])
                );
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
                numerator = Mersenne61Field.mul(numerator, Mersenne61Field.neg(xj));
                denominator = Mersenne61Field.mul(denominator, Mersenne61Field.sub(xi, xj));
            }
            lambdas[i] = Mersenne61Field.div(numerator, denominator);
        }
        return lambdas;
    }

    private byte[] encodeVector(long[] values) {
        return Mersenne61VectorCodec.encode(values, wireFormat);
    }

    private byte[] encodeVector(long[] values, int offset, int length) {
        return Mersenne61VectorCodec.encode(values, offset, length, wireFormat);
    }

    private long[] decodeVector(byte[] payload, int elementCount) {
        return Mersenne61VectorCodec.decode(payload, elementCount, wireFormat);
    }

    private long addInternal(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.addUnchecked(left, right)
            : Mersenne61Field.add(left, right);
    }

    private long subInternal(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.subUnchecked(left, right)
            : Mersenne61Field.sub(left, right);
    }

    private long mulInternal(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.mulUnchecked(left, right)
            : Mersenne61Field.mul(left, right);
    }

    private static void checkSameLength(long[] left, long[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("length mismatch: " + left.length + " != " + right.length);
        }
    }

    private int chunkStart(int length, int chunkIndex) {
        return (int) ((long) length * chunkIndex / partyNum);
    }
}
