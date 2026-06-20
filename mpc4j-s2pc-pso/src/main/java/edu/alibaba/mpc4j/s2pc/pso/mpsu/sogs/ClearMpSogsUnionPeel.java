package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Clear multi-party union-peel evaluator. This is not a secure protocol.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class ClearMpSogsUnionPeel {
    private ClearMpSogsUnionPeel() {
        // empty
    }

    public static MpSogsPeelResult uPeel(List<MpSogsSketch> sketches, int cellIndex) {
        if (sketches.isEmpty()) {
            throw new IllegalArgumentException("sketches must be non-empty");
        }
        return MpSogsUnionPeelFunction.evaluate(
            sketches.stream()
                .map(sketch -> sketch.localCellView(cellIndex))
                .collect(Collectors.toList())
        );
    }
}
