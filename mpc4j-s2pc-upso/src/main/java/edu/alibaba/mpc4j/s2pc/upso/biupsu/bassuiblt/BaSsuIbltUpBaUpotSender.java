package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

import java.util.List;

/**
 * Sender-facing API for production queue-peel UP-BA-UPOT.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
interface BaSsuIbltUpBaUpotSender {
    /**
     * Initializes the fixed public probe schedule.
     *
     * @param schedule fixed offline schedule.
     * @throws MpcAbortException if the protocol aborts.
     */
    void init(BaSsuIbltUpBaUpotOfflineSchedule schedule) throws MpcAbortException;

    /**
     * Probes one public bucket and returns the synchronized fixed-result output.
     *
     * @param publicInput public input.
     * @param localInput  private local input.
     * @return public fixed-result output.
     * @throws MpcAbortException if the protocol aborts.
     */
    BaSsuIbltProductionUnionProbeOutput probe(BaSsuIbltUpBaUpotPublicInput publicInput,
                                              BaSsuIbltUpBaUpotLocalInput localInput)
        throws MpcAbortException;

    /**
     * Probes a public batch in logical probe order.
     *
     * @param publicInputs public inputs.
     * @param localInputs  private local inputs.
     * @return public fixed-result outputs in the same order.
     * @throws MpcAbortException if the protocol aborts.
     */
    List<BaSsuIbltProductionUnionProbeOutput> probeBatch(
        List<BaSsuIbltUpBaUpotPublicInput> publicInputs,
        List<BaSsuIbltUpBaUpotLocalInput> localInputs
    ) throws MpcAbortException;
}
