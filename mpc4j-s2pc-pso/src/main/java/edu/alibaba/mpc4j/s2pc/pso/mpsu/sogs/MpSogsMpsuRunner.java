package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.List;
import java.util.Set;

/**
 * Local MP-SOGS MPSU runner interface.
 *
 * <p>This is a development-facing interface for the all-output protocol core. A real networked participant
 * implementation will keep only one local input set and call a concrete secure uPeel backend.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public interface MpSogsMpsuRunner {
    /**
     * Gets the config.
     *
     * @return config.
     */
    MpSogsMpsuConfig getConfig();

    /**
     * Runs the local all-output evaluator.
     *
     * @param partyInputs all party input sets.
     * @return public transcript.
     */
    MpSogsTranscript run(List<Set<Long>> partyInputs);
}
