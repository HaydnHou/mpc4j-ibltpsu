package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuPtoDesc;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Subset-seed PRSS for degree-t Shamir sharing over F_(2^61-1).
 *
 * <p>For every t-party complement T, the remaining p-t parties share a seed and generate a random coefficient r_T.
 * Their contribution is r_T * product_{j in T}(X - x_j). Parties in T contribute zero. Summing all terms gives a
 * degree-t sharing. For any coalition of at most t parties, the term whose complement is that coalition is driven by an
 * all-honest seed and vanishes at every corrupted evaluation point.</p>
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public final class PrssShamirRandomnessProvider implements ShamirRandomnessProvider {
    private static final byte[] PROTOCOL_VERSION = "MP-SOGS-SSM-PRSS-V1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FIELD_ID = "MERSENNE61".getBytes(StandardCharsets.US_ASCII);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/NoPadding";
    private static final int SEED_BYTE_LENGTH = 32;
    private static final int AES_KEY_BYTE_LENGTH = 16;
    private static final int STEP_PRSS_SETUP = 44;

    private final long taskId;
    private final int attemptId;
    private final int partyNum;
    private final int threshold;
    private final int ownPartyIndex;
    private final int[] partyIds;
    private final List<SeedTerm> seedTerms;
    private final Set<ShamirRandomnessDomain> consumedDomains;
    private final boolean useUncheckedHotPath;
    private final MessageDigest digest;
    private final byte[] numberBuffer;
    private final long setupSendBytes;
    private long generatedElementCount;

    public PrssShamirRandomnessProvider(Rpc rpc, long taskId, int attemptId) {
        this(rpc, taskId, attemptId, false);
    }

    public PrssShamirRandomnessProvider(Rpc rpc, long taskId, int attemptId, boolean useUncheckedHotPath) {
        this.taskId = taskId;
        this.attemptId = attemptId;
        this.useUncheckedHotPath = useUncheckedHotPath;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        numberBuffer = new byte[Long.BYTES];
        Party[] parties = rpc.getPartySet().stream()
            .sorted(Comparator.comparingInt(Party::getPartyId))
            .toArray(Party[]::new);
        partyNum = parties.length;
        threshold = (partyNum - 1) / 2;
        if (partyNum < 3 || partyNum <= 2 * threshold) {
            throw new IllegalArgumentException("PRSS requires an honest majority: p=" + partyNum
                + ", t=" + threshold);
        }
        partyIds = Arrays.stream(parties).mapToInt(Party::getPartyId).toArray();
        for (int index = 0; index < partyIds.length; index++) {
            if (partyIds[index] != index) {
                throw new IllegalArgumentException("PRSS requires contiguous party IDs starting at zero");
            }
        }
        ownPartyIndex = rpc.ownParty().getPartyId();
        consumedDomains = new HashSet<>();
        long sendBefore = rpc.getSendByteLength();
        seedTerms = establishSubsetSeeds(rpc, parties);
        setupSendBytes = Math.max(0L, rpc.getSendByteLength() - sendBefore);
    }

    @Override
    public long[] randomDegreeT(ShamirRandomnessDomain domain) {
        register(domain);
        generatedElementCount += domain.vectorLength();
        long[] result = new long[domain.vectorLength()];
        for (SeedTerm seedTerm : seedTerms) {
            ExpansionStream coefficients = expansion(seedTerm, domain, 0);
            for (int index = 0; index < result.length; index++) {
                result[index] = add(
                    result[index], mul(seedTerm.basisAtOwnPoint, coefficients.next())
                );
            }
        }
        return result;
    }

    @Override
    public ShamirDoubleShare randomDoubleShare(ShamirRandomnessDomain domain) {
        register(domain);
        generatedElementCount += domain.vectorLength();
        long[] degreeT = new long[domain.vectorLength()];
        long[] degree2T = new long[domain.vectorLength()];
        long ownPoint = ownPartyIndex + 1L;
        for (SeedTerm seedTerm : seedTerms) {
            ExpansionStream constants = expansion(seedTerm, domain, 0);
            ExpansionStream[] highCoefficients = new ExpansionStream[threshold];
            for (int degree = 0; degree < threshold; degree++) {
                highCoefficients[degree] = expansion(seedTerm, domain, degree + 1);
            }
            for (int index = 0; index < degreeT.length; index++) {
                long constant = constants.next();
                long baseContribution = mul(seedTerm.basisAtOwnPoint, constant);
                degreeT[index] = add(degreeT[index], baseContribution);
                long highAtOwnPoint = 0L;
                for (int degree = threshold - 1; degree >= 0; degree--) {
                    highAtOwnPoint = add(
                        mul(highAtOwnPoint, ownPoint), highCoefficients[degree].next()
                    );
                }
                long liftedCoefficient = add(
                    constant, mul(ownPoint, highAtOwnPoint)
                );
                degree2T[index] = add(
                    degree2T[index], mul(seedTerm.basisAtOwnPoint, liftedCoefficient)
                );
            }
        }
        return new ShamirDoubleShare(degreeT, degree2T);
    }

    @Override
    public long[] randomDegree2TZero(ShamirRandomnessDomain domain) {
        register(domain);
        generatedElementCount += domain.vectorLength();
        long[] degree2TZero = new long[domain.vectorLength()];
        long ownPoint = ownPartyIndex + 1L;
        for (SeedTerm seedTerm : seedTerms) {
            ExpansionStream[] coefficients = new ExpansionStream[threshold];
            for (int degree = 0; degree < threshold; degree++) {
                coefficients[degree] = expansion(seedTerm, domain, degree);
            }
            for (int index = 0; index < degree2TZero.length; index++) {
                long polynomialAtOwnPoint = 0L;
                for (int degree = threshold - 1; degree >= 0; degree--) {
                    polynomialAtOwnPoint = add(
                        mul(polynomialAtOwnPoint, ownPoint), coefficients[degree].next()
                    );
                }
                long contribution = mul(
                    seedTerm.basisAtOwnPoint, mul(ownPoint, polynomialAtOwnPoint)
                );
                degree2TZero[index] = add(degree2TZero[index], contribution);
            }
        }
        return degree2TZero;
    }

    @Override
    public long getRequestCount() {
        return consumedDomains.size();
    }

    @Override
    public long getGeneratedElementCount() {
        return generatedElementCount;
    }

    @Override
    public long getSetupSendBytes() {
        return setupSendBytes;
    }

    private List<SeedTerm> establishSubsetSeeds(Rpc rpc, Party[] parties) {
        List<int[]> complements = combinations(partyNum, threshold);
        List<SeedTerm> terms = new ArrayList<>();
        SecureRandom secureRandom = new SecureRandom();
        for (int subsetId = 0; subsetId < complements.size(); subsetId++) {
            int[] complement = complements.get(subsetId);
            if (contains(complement, ownPartyIndex)) {
                continue;
            }
            byte[] ownContribution = new byte[SEED_BYTE_LENGTH];
            secureRandom.nextBytes(ownContribution);
            for (Party party : parties) {
                int receiver = party.getPartyId();
                if (receiver != ownPartyIndex && !contains(complement, receiver)) {
                    DataPacketHeader header = new DataPacketHeader(
                        taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_PRSS_SETUP, subsetId,
                        ownPartyIndex, receiver
                    );
                    rpc.send(DataPacket.fromByteArrayList(header, List.of(ownContribution)));
                }
            }
            byte[] subsetSeed = Arrays.copyOf(ownContribution, ownContribution.length);
            for (Party party : parties) {
                int sender = party.getPartyId();
                if (sender != ownPartyIndex && !contains(complement, sender)) {
                    DataPacketHeader header = new DataPacketHeader(
                        taskId, MpSogsMpsuPtoDesc.getInstance().getPtoId(), STEP_PRSS_SETUP, subsetId,
                        sender, ownPartyIndex
                    );
                    List<byte[]> payload = rpc.receive(header).getPayload();
                    if (payload.size() != 1 || payload.get(0).length != SEED_BYTE_LENGTH) {
                        throw new IllegalStateException("invalid PRSS subset-seed contribution");
                    }
                    xorInto(subsetSeed, payload.get(0));
                }
            }
            terms.add(new SeedTerm(
                subsetSeed, basisAtPoint(complement, ownPartyIndex + 1L),
                useUncheckedHotPath ? threshold + 1 : 0
            ));
        }
        return terms;
    }

    private ExpansionStream expansion(SeedTerm seedTerm, ShamirRandomnessDomain domain, int streamId) {
        byte[] streamKey = new byte[SEED_BYTE_LENGTH];
        try {
            Mac mac = seedTerm.mac;
            mac.reset();
            mac.update(PROTOCOL_VERSION);
            updateLong(mac, taskId);
            updateInt(mac, attemptId);
            mac.update(FIELD_ID);
            updateInt(mac, threshold);
            for (int partyId : partyIds) {
                updateInt(mac, partyId);
            }
            updateInt(mac, domain.tier().ordinal());
            updateInt(mac, domain.phase().ordinal());
            updateInt(mac, domain.roundId());
            updateLong(mac, domain.batchId());
            updateLong(mac, domain.operationId());
            updateInt(mac, domain.repetition());
            updateInt(mac, domain.vectorOffset());
            updateInt(mac, domain.vectorLength());
            updateInt(mac, streamId);
            mac.doFinal(streamKey, 0);
        } catch (ShortBufferException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
        return useUncheckedHotPath
            ? new ExpansionStream(streamKey, seedTerm.initAesCipher(streamId, streamKey))
            : new ExpansionStream(streamKey);
    }

    private void fillBlock(ExpansionStream stream) {
        if (stream.aesCipher != null) {
            fillAesBlock(stream);
            return;
        }
        try {
            digest.reset();
            digest.update(stream.streamKey);
            putLong(numberBuffer, stream.blockIndex++);
            digest.update(numberBuffer, 0, Long.BYTES);
            digest.digest(stream.block, 0, stream.block.length);
            stream.offset = 0;
        } catch (DigestException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void fillAesBlock(ExpansionStream stream) {
        putLong(stream.counterBlocks, Long.BYTES, stream.blockIndex++);
        putLong(stream.counterBlocks, 3 * Long.BYTES, stream.blockIndex++);
        try {
            int outputLength = stream.aesCipher.update(
                stream.counterBlocks, 0, stream.counterBlocks.length, stream.block, 0
            );
            if (outputLength != stream.block.length) {
                throw new IllegalStateException("AES PRG produced an unexpected block length: " + outputLength);
            }
            stream.offset = 0;
        } catch (ShortBufferException e) {
            throw new IllegalStateException("AES PRG output buffer is too short", e);
        }
    }

    private void register(ShamirRandomnessDomain domain) {
        if (!consumedDomains.add(domain)) {
            throw new IllegalStateException("Shamir PRSS domain reused: " + domain);
        }
    }

    private static long basisAtPoint(int[] roots, long point) {
        long result = 1L;
        for (int rootIndex : roots) {
            result = Mersenne61Field.mul(result, Mersenne61Field.sub(point, rootIndex + 1L));
        }
        return result;
    }

    private static List<int[]> combinations(int universeSize, int selectedSize) {
        List<int[]> result = new ArrayList<>();
        collectCombinations(result, new int[selectedSize], 0, 0, universeSize);
        return result;
    }

    private static void collectCombinations(List<int[]> output, int[] current, int depth, int next,
                                            int universeSize) {
        if (depth == current.length) {
            output.add(Arrays.copyOf(current, current.length));
            return;
        }
        for (int value = next; value <= universeSize - (current.length - depth); value++) {
            current[depth] = value;
            collectCombinations(output, current, depth + 1, value + 1, universeSize);
        }
    }

    private static boolean contains(int[] sortedValues, int value) {
        return Arrays.binarySearch(sortedValues, value) >= 0;
    }

    private static void xorInto(byte[] target, byte[] source) {
        for (int index = 0; index < target.length; index++) {
            target[index] ^= source[index];
        }
    }

    private long add(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.addUnchecked(left, right)
            : Mersenne61Field.add(left, right);
    }

    private long mul(long left, long right) {
        return useUncheckedHotPath
            ? Mersenne61Field.mulUnchecked(left, right)
            : Mersenne61Field.mul(left, right);
    }

    private void updateInt(Mac mac, int value) {
        numberBuffer[0] = (byte) (value >>> 24);
        numberBuffer[1] = (byte) (value >>> 16);
        numberBuffer[2] = (byte) (value >>> 8);
        numberBuffer[3] = (byte) value;
        mac.update(numberBuffer, 0, Integer.BYTES);
    }

    private void updateLong(Mac mac, long value) {
        putLong(numberBuffer, value);
        mac.update(numberBuffer, 0, Long.BYTES);
    }

    private static void putLong(byte[] output, long value) {
        putLong(output, 0, value);
    }

    private static void putLong(byte[] output, int offset, long value) {
        for (int index = Long.BYTES - 1; index >= 0; index--) {
            output[offset + index] = (byte) value;
            value >>>= Byte.SIZE;
        }
    }

    private static long readLong(byte[] input, int offset) {
        return ((input[offset] & 0xFFL) << 56)
            | ((input[offset + 1] & 0xFFL) << 48)
            | ((input[offset + 2] & 0xFFL) << 40)
            | ((input[offset + 3] & 0xFFL) << 32)
            | ((input[offset + 4] & 0xFFL) << 24)
            | ((input[offset + 5] & 0xFFL) << 16)
            | ((input[offset + 6] & 0xFFL) << 8)
            | (input[offset + 7] & 0xFFL);
    }

    private static final class SeedTerm {
        private final long basisAtOwnPoint;
        private final Mac mac;
        private final Cipher[] aesCiphers;

        private SeedTerm(byte[] seed, long basisAtOwnPoint, int aesStreamNum) {
            this.basisAtOwnPoint = basisAtOwnPoint;
            try {
                mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(Arrays.copyOf(seed, seed.length), HMAC_ALGORITHM));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("HmacSHA256 is unavailable", e);
            }
            aesCiphers = new Cipher[aesStreamNum];
        }

        private Cipher initAesCipher(int streamId, byte[] streamKey) {
            try {
                Cipher cipher = aesCiphers[streamId];
                if (cipher == null) {
                    cipher = Cipher.getInstance(AES_TRANSFORMATION);
                    aesCiphers[streamId] = cipher;
                }
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(streamKey, 0, AES_KEY_BYTE_LENGTH, AES_ALGORITHM)
                );
                return cipher;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("AES PRG is unavailable", e);
            }
        }
    }

    private final class ExpansionStream {
        private final byte[] streamKey;
        private final byte[] block;
        private final Cipher aesCipher;
        private final byte[] counterBlocks;
        private int offset;
        private long blockIndex;

        private ExpansionStream(byte[] streamKey) {
            this.streamKey = streamKey;
            block = new byte[digest.getDigestLength()];
            if (useUncheckedHotPath) {
                throw new IllegalStateException("AES expansion stream requires a reusable Cipher");
            }
            aesCipher = null;
            counterBlocks = null;
            offset = block.length;
        }

        private ExpansionStream(byte[] streamKey, Cipher aesCipher) {
            this.streamKey = streamKey;
            block = new byte[digest.getDigestLength()];
            this.aesCipher = aesCipher;
            if (aesCipher != null) {
                counterBlocks = new byte[block.length];
            } else {
                counterBlocks = null;
            }
            offset = block.length;
        }

        private long next() {
            while (true) {
                if (offset >= block.length) {
                    fillBlock(this);
                }
                long candidate = readLong(block, offset) & Mersenne61Field.PRIME;
                offset += Long.BYTES;
                if (candidate != Mersenne61Field.PRIME) {
                    return candidate;
                }
            }
        }
    }
}
