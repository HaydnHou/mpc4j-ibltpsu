package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Batch secure union-peel interface for MP-SOGS.
 *
 * <p>The clear prototype only provides a dummy adapter. A real semi-honest implementation must keep
 * transcript shape fixed and reveal only {@code x / bottom} per public cell.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public interface SecureMpSogsUnionPeel {
    /**
     * Evaluates a public batch of cells.
     *
     * @param input public batch input.
     * @return public batch output.
     */
    BatchMpSogsPeelOutput peelBatch(BatchMpSogsPeelInput input);
}
