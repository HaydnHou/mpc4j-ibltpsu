package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.List;

/**
 * Dummy union-peel adapter around the clear evaluator.
 *
 * <p>This class is not secure. It exists only to pin down the batch interface and protocol shape before
 * implementing a real semi-honest secure uPeel_t.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class DummySecureMpSogsUnionPeel implements SecureMpSogsUnionPeel {
    /**
     * Local sketches for all parties, available only in the clear prototype.
     */
    private final List<MpSogsSketch> sketches;

    public DummySecureMpSogsUnionPeel(List<MpSogsSketch> sketches) {
        this.sketches = new ArrayList<>(sketches);
    }

    @Override
    public BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input) {
        List<MpSogsPeelResult> results = new ArrayList<>(input.size());
        for (int cellIndex : input.getCellIndexes()) {
            results.add(ClearMpSogsUnionPeel.uPeel(sketches, cellIndex));
        }
        return new BatchMpSogsPeelOutput(results, 0L, 0L, 0);
    }
}
