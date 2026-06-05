package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Ideal functionality boundary for one queue-peel UP-BA-UPOT bucket probe.
 *
 * <p>The production protocol must instantiate this boundary without giving either party a local opener for the other
 * party's bucket capsule. The only public result of a probe is {@code bottom} or one source-agnostic singleton
 * element. Source labels, case labels, membership labels, raw tag/check material, OT choices, and branch selectors are
 * outside the public functionality.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltUpBaUpotFunctionality {
    /**
     * private constructor.
     */
    private BaSsuIbltUpBaUpotFunctionality() {
        // empty
    }

    /**
     * Evaluates the public source-agnostic truth table over already authenticated local-state symbols.
     *
     * <p>This method is a specification helper for tests and documentation. It is deliberately package-private and does
     * not accept or decode production capsules.</p>
     *
     * @param left local-state symbol from one party.
     * @param right local-state symbol from the other party.
     * @param sameSingleton whether both singleton symbols authenticate the same candidate.
     * @return public result symbol.
     */
    static ResultSymbol truthTable(LocalSymbol left, LocalSymbol right, boolean sameSingleton) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("local symbols must be non-null");
        }
        if (left == LocalSymbol.BLOCKED || right == LocalSymbol.BLOCKED) {
            return ResultSymbol.BOTTOM;
        }
        if (left == LocalSymbol.EMPTY && right == LocalSymbol.EMPTY) {
            return ResultSymbol.BOTTOM;
        }
        if (left == LocalSymbol.SINGLETON && right == LocalSymbol.EMPTY) {
            return ResultSymbol.SINGLETON;
        }
        if (left == LocalSymbol.EMPTY && right == LocalSymbol.SINGLETON) {
            return ResultSymbol.SINGLETON;
        }
        return sameSingleton ? ResultSymbol.SINGLETON : ResultSymbol.BOTTOM;
    }

    /**
     * Authenticated local-state symbol.
     */
    enum LocalSymbol {
        /**
         * Authenticated empty bucket.
         */
        EMPTY,
        /**
         * Authenticated singleton bucket.
         */
        SINGLETON,
        /**
         * Blocked, many, or invalid bucket.
         */
        BLOCKED
    }

    /**
     * Public source-agnostic result symbol.
     */
    enum ResultSymbol {
        /**
         * No public singleton is released.
         */
        BOTTOM,
        /**
         * A source-agnostic singleton element is released.
         */
        SINGLETON
    }
}
