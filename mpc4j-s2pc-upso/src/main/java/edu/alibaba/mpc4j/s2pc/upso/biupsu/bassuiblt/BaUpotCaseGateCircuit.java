package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * BA-UPOT fixed case-gate circuit specification.
 *
 * <p>The circuit models the case table after singleton validity bits have already been authenticated. It is a local
 * gate-counting specification for the fixed BA-UPOT selector and must be replaced by a real Z2/GC backend for a secure
 * implementation.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotCaseGateCircuit {
    /**
     * private constructor.
     */
    private BaUpotCaseGateCircuit() {
        // empty
    }

    /**
     * Evaluates the case gate on a bucket input.
     *
     * @param input bucket input.
     * @return evaluation.
     */
    static Evaluation evaluate(BaUpotBucketInput input) {
        InputSignals signals = InputSignals.from(input);
        GateStats stats = new GateStats();
        Wire anchorOnly = signals.anchorSingletonValid.and(signals.shadowEmpty, stats);
        Wire shadowOnly = signals.shadowSingletonValid.and(signals.anchorEmpty, stats);
        Wire shared = signals.anchorSingletonValid
            .and(signals.shadowSingletonValid, stats)
            .and(signals.sameSingleton, stats);
        Wire empty = signals.anchorEmpty.and(signals.shadowEmpty, stats);
        Wire singleton = anchorOnly.or(shadowOnly, stats).or(shared, stats);
        Wire recognized = singleton.or(empty, stats);
        Wire blocked = recognized.not(stats);

        BaUpotBucketOutput output;
        if (anchorOnly.value || shared.value) {
            output = BaUpotBucketOutput.singleton(
                input.getBucketIndex(),
                shared.value ? BaUpotBucketOutput.CaseType.SHARED_SINGLETON
                    : BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON,
                input.getAnchorKeyXorReference()
            );
        } else if (shadowOnly.value) {
            output = BaUpotBucketOutput.singleton(
                input.getBucketIndex(), BaUpotBucketOutput.CaseType.SHADOW_SINGLETON,
                input.getShadowKeyXorReference()
            );
        } else if (empty.value && !blocked.value) {
            output = BaUpotBucketOutput.empty(input.getBucketIndex());
        } else {
            output = BaUpotBucketOutput.blocked(input.getBucketIndex());
        }
        return new Evaluation(output, stats);
    }

    /**
     * Input boolean signals for the case table.
     */
    private static class InputSignals {
        /**
         * anchor empty.
         */
        private final Wire anchorEmpty;
        /**
         * shadow empty.
         */
        private final Wire shadowEmpty;
        /**
         * anchor singleton valid.
         */
        private final Wire anchorSingletonValid;
        /**
         * shadow singleton valid.
         */
        private final Wire shadowSingletonValid;
        /**
         * same singleton.
         */
        private final Wire sameSingleton;

        InputSignals(Wire anchorEmpty, Wire shadowEmpty, Wire anchorSingletonValid, Wire shadowSingletonValid,
                     Wire sameSingleton) {
            this.anchorEmpty = anchorEmpty;
            this.shadowEmpty = shadowEmpty;
            this.anchorSingletonValid = anchorSingletonValid;
            this.shadowSingletonValid = shadowSingletonValid;
            this.sameSingleton = sameSingleton;
        }

        static InputSignals from(BaUpotBucketInput input) {
            boolean anchorEmpty = input.getAnchorCount() == 0
                && isZero(input.getAnchorKeyXorReference())
                && isZero(input.getAnchorCheckXorReference());
            boolean shadowEmpty = input.getShadowCount() == 0
                && isZero(input.getShadowKeyXorReference())
                && isZero(input.getShadowCheckXorReference());
            boolean anchorSingletonValid = input.getAnchorCount() == 1
                && Arrays.equals(
                BaUpotIdeal.digest(input.getAnchorKeyXorReference(), input.getCheckByteLength()),
                input.getAnchorCheckXorReference()
            );
            boolean shadowSingletonValid = input.getShadowCount() == 1
                && Arrays.equals(
                BaUpotIdeal.digest(input.getShadowKeyXorReference(), input.getCheckByteLength()),
                input.getShadowCheckXorReference()
            );
            boolean sameSingleton = Arrays.equals(input.getAnchorKeyXorReference(), input.getShadowKeyXorReference());
            return new InputSignals(
                Wire.input(anchorEmpty),
                Wire.input(shadowEmpty),
                Wire.input(anchorSingletonValid),
                Wire.input(shadowSingletonValid),
                Wire.input(sameSingleton)
            );
        }
    }

    /**
     * One boolean wire.
     */
    private static class Wire {
        /**
         * value.
         */
        private final boolean value;

        private Wire(boolean value) {
            this.value = value;
        }

        static Wire input(boolean value) {
            return new Wire(value);
        }

        Wire and(Wire that, GateStats stats) {
            stats.andGateCount++;
            return new Wire(this.value && that.value);
        }

        Wire xor(Wire that, GateStats stats) {
            stats.xorGateCount++;
            return new Wire(this.value ^ that.value);
        }

        Wire not(GateStats stats) {
            stats.notGateCount++;
            return new Wire(!value);
        }

        Wire or(Wire that, GateStats stats) {
            return this.xor(that, stats).xor(this.and(that, stats), stats);
        }
    }

    /**
     * Gate statistics.
     */
    public static class GateStats {
        /**
         * AND gates.
         */
        private long andGateCount;
        /**
         * XOR gates.
         */
        private long xorGateCount;
        /**
         * NOT gates.
         */
        private long notGateCount;

        public long getAndGateCount() {
            return andGateCount;
        }

        public long getXorGateCount() {
            return xorGateCount;
        }

        public long getNotGateCount() {
            return notGateCount;
        }

        public long getTotalGateCount() {
            return andGateCount + xorGateCount + notGateCount;
        }

        void add(GateStats that) {
            andGateCount += that.andGateCount;
            xorGateCount += that.xorGateCount;
            notGateCount += that.notGateCount;
        }
    }

    /**
     * Circuit evaluation.
     */
    static class Evaluation {
        /**
         * output.
         */
        private final BaUpotBucketOutput output;
        /**
         * gate stats.
         */
        private final GateStats gateStats;

        Evaluation(BaUpotBucketOutput output, GateStats gateStats) {
            this.output = output;
            this.gateStats = gateStats;
        }

        BaUpotBucketOutput getOutput() {
            return output;
        }

        GateStats getGateStats() {
            return gateStats;
        }
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
