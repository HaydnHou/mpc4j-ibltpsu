package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Conservative EGUP simulator for PISF-IBLT.
 *
 * <p>This class is a local audit/simulation helper. It must not be used to exchange peel traces in the online protocol.
 * The online protocol keeps singleton, group, winner, success, and output bits inside MPC/fallback state.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class PisIbltConservativePeelSimulator {
    /**
     * private constructor.
     */
    private PisIbltConservativePeelSimulator() {
        // empty
    }

    /**
     * Runs fixed-round conservative EGUP simulation.
     *
     * @param table      source-split table.
     * @param senderReal sender real flags, indexed by owner - 1.
     * @return audit result.
     */
    public static Result simulate(PisIbltSourceSplitTable table, boolean[] senderReal) {
        PisIbltUpsuParams params = table.getParams();
        boolean[] emitted = new boolean[senderReal.length];
        boolean[] absorbed = new boolean[senderReal.length];
        boolean malformed = false;
        for (int round = 0; round < params.getPeelRound(); round++) {
            Map<ByteBuffer, EvidenceGroup> groups = collectEvidence(table);
            for (EvidenceGroup group : groups.values()) {
                if (!group.consistent()) {
                    malformed = true;
                    continue;
                }
                if (group.seenSender() && group.seenReceiver()) {
                    RawEvidence senderEvidence = group.firstSenderEvidence();
                    RawEvidence receiverEvidence = group.firstReceiverEvidence();
                    delete(table, senderEvidence, true);
                    delete(table, receiverEvidence, false);
                    if (senderEvidence.owner > 0 && senderEvidence.owner <= senderReal.length
                        && senderReal[senderEvidence.owner - 1]) {
                        absorbed[senderEvidence.owner - 1] = true;
                    }
                } else if (group.seenSender() && group.hasAbsentReceiver()) {
                    RawEvidence senderEvidence = group.firstSenderEvidence();
                    delete(table, senderEvidence, true);
                    if (senderEvidence.owner > 0 && senderEvidence.owner <= senderReal.length
                        && senderReal[senderEvidence.owner - 1]) {
                        emitted[senderEvidence.owner - 1] = true;
                    }
                } else if (group.seenReceiver() && group.hasAbsentSender()) {
                    RawEvidence receiverEvidence = group.firstReceiverEvidence();
                    delete(table, receiverEvidence, false);
                }
            }
        }
        boolean stateConsistent = true;
        for (int i = 0; i < senderReal.length; i++) {
            stateConsistent &= !(emitted[i] && absorbed[i]);
        }
        return new Result(table.tableAllZero() && !malformed && stateConsistent, malformed, emitted, absorbed);
    }

    private static Map<ByteBuffer, EvidenceGroup> collectEvidence(PisIbltSourceSplitTable table) {
        Map<ByteBuffer, EvidenceGroup> groups = new HashMap<>();
        PisIbltSourceSplitTable.Bucket[] buckets = table.getBuckets();
        for (PisIbltSourceSplitTable.Bucket bucket : buckets) {
            PisIbltSourceSplitTable.Side senderSide = bucket.senderSide;
            PisIbltSourceSplitTable.Side receiverSide = bucket.receiverSide;
            boolean senderSingleton = senderSide.getCount() == 1;
            boolean receiverSingleton = receiverSide.getCount() == 1;
            if (senderSingleton) {
                RawEvidence evidence = RawEvidence.create(senderSide, receiverSide.getCount() == 0, true);
                groups.computeIfAbsent(evidence.groupKey(), key -> new EvidenceGroup()).senderEvidence.add(evidence);
            }
            if (receiverSingleton) {
                RawEvidence evidence = RawEvidence.create(receiverSide, senderSide.getCount() == 0, false);
                groups.computeIfAbsent(evidence.groupKey(), key -> new EvidenceGroup()).receiverEvidence.add(evidence);
            }
        }
        return groups;
    }

    private static void delete(PisIbltSourceSplitTable table, RawEvidence evidence, boolean senderSide) {
        for (int encodedAddress : evidence.posList) {
            if (encodedAddress != 0) {
                PisIbltSourceSplitTable.Side side = senderSide
                    ? table.getBucket(encodedAddress).senderSide
                    : table.getBucket(encodedAddress).receiverSide;
                side.delete(evidence.key, evidence.fp, evidence.posList, evidence.owner, evidence.payloadKey);
            }
        }
    }

    /**
     * Simulation result.
     */
    public static class Result {
        /**
         * success flag.
         */
        private final boolean success;
        /**
         * malformed flag.
         */
        private final boolean malformed;
        /**
         * emitted sender owners.
         */
        private final boolean[] emitted;
        /**
         * absorbed sender owners.
         */
        private final boolean[] absorbed;

        Result(boolean success, boolean malformed, boolean[] emitted, boolean[] absorbed) {
            this.success = success;
            this.malformed = malformed;
            this.emitted = emitted;
            this.absorbed = absorbed;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isMalformed() {
            return malformed;
        }

        public boolean[] getEmitted() {
            return Arrays.copyOf(emitted, emitted.length);
        }

        public boolean[] getAbsorbed() {
            return Arrays.copyOf(absorbed, absorbed.length);
        }
    }

    /**
     * Evidence group.
     */
    private static class EvidenceGroup {
        /**
         * sender evidence list.
         */
        private final List<RawEvidence> senderEvidence;
        /**
         * receiver evidence list.
         */
        private final List<RawEvidence> receiverEvidence;

        EvidenceGroup() {
            senderEvidence = new ArrayList<>();
            receiverEvidence = new ArrayList<>();
        }

        boolean seenSender() {
            return !senderEvidence.isEmpty();
        }

        boolean seenReceiver() {
            return !receiverEvidence.isEmpty();
        }

        boolean hasAbsentReceiver() {
            return senderEvidence.stream().anyMatch(evidence -> evidence.certAbsentOther);
        }

        boolean hasAbsentSender() {
            return receiverEvidence.stream().anyMatch(evidence -> evidence.certAbsentOther);
        }

        RawEvidence firstSenderEvidence() {
            return senderEvidence.get(0);
        }

        RawEvidence firstReceiverEvidence() {
            return receiverEvidence.get(0);
        }

        boolean consistent() {
            return sameSourceConsistent(senderEvidence) && sameSourceConsistent(receiverEvidence);
        }

        private boolean sameSourceConsistent(List<RawEvidence> evidenceList) {
            if (evidenceList.size() <= 1) {
                return true;
            }
            RawEvidence first = evidenceList.get(0);
            return evidenceList.stream().allMatch(first::sameSourceState);
        }
    }

    /**
     * Raw singleton evidence.
     */
    private static class RawEvidence {
        /**
         * key.
         */
        private final byte[] key;
        /**
         * fingerprint.
         */
        private final byte[] fp;
        /**
         * pos list.
         */
        private final int[] posList;
        /**
         * owner.
         */
        private final int owner;
        /**
         * payload key.
         */
        private final int payloadKey;
        /**
         * conservative absence certificate for the other side.
         */
        private final boolean certAbsentOther;
        /**
         * sender source.
         */
        private final boolean senderSource;

        private RawEvidence(byte[] key, byte[] fp, int[] posList, int owner, int payloadKey,
                            boolean certAbsentOther, boolean senderSource) {
            this.key = key;
            this.fp = fp;
            this.posList = posList;
            this.owner = owner;
            this.payloadKey = payloadKey;
            this.certAbsentOther = certAbsentOther;
            this.senderSource = senderSource;
        }

        static RawEvidence create(PisIbltSourceSplitTable.Side side, boolean certAbsentOther, boolean senderSource) {
            return new RawEvidence(
                BytesUtils.clone(side.getKeyXor()),
                BytesUtils.clone(side.getFpXor()),
                Arrays.copyOf(side.getPosXor(), side.getPosXor().length),
                side.getOwnerXor(),
                side.getPayloadKeyXor(),
                certAbsentOther,
                senderSource
            );
        }

        ByteBuffer groupKey() {
            byte[] groupKey = new byte[key.length + fp.length];
            System.arraycopy(key, 0, groupKey, 0, key.length);
            System.arraycopy(fp, 0, groupKey, key.length, fp.length);
            return ByteBuffer.wrap(groupKey);
        }

        boolean sameSourceState(RawEvidence that) {
            return senderSource == that.senderSource
                && Arrays.equals(key, that.key)
                && Arrays.equals(fp, that.fp)
                && Arrays.equals(posList, that.posList)
                && owner == that.owner
                && payloadKey == that.payloadKey;
        }
    }
}
