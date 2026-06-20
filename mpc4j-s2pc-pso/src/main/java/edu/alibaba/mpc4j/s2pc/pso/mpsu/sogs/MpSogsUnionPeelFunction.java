package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.List;

/**
 * Ideal MP-SOGS union-peel functionality over local cell views.
 *
 * <p>The secure implementation must evaluate the same rule with hidden wires and open only the returned
 * {@link MpSogsPeelResult}.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsUnionPeelFunction {
    private MpSogsUnionPeelFunction() {
        // empty
    }

    /**
     * Evaluates the ideal uPeel_t truth table.
     *
     * @param views one local view per participant.
     * @return x or bottom.
     */
    public static MpSogsPeelResult evaluate(List<MpSogsLocalCellView> views) {
        boolean hasSingleton = false;
        long candidate = 0L;
        for (MpSogsLocalCellView view : views) {
            if (view.isHeavy()) {
                return MpSogsPeelResult.bottom();
            }
            if (view.isSingleton()) {
                long value = view.getSingletonValue();
                if (!hasSingleton) {
                    candidate = value;
                    hasSingleton = true;
                } else if (candidate != value) {
                    return MpSogsPeelResult.bottom();
                }
            }
        }
        return hasSingleton ? MpSogsPeelResult.element(candidate) : MpSogsPeelResult.bottom();
    }
}
