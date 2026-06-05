package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Production union-probe sender test thread.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeSenderThread extends Thread {
    /**
     * sender.
     */
    private final BaSsuIbltProductionUnionProbeSender sender;
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * bucket input.
     */
    private final BaSsuIbltSecureBucketInput bucketInput;
    /**
     * max probe number.
     */
    private final int maxProbeNum;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * output capsule.
     */
    private BaSsuIbltProductionUnionProbeCapsule capsule;
    /**
     * exception.
     */
    private Exception exception;

    BaSsuIbltProductionUnionProbeSenderThread(BaSsuIbltProductionUnionProbeSender sender, int bucketIndex,
                                              BaSsuIbltSecureBucketInput bucketInput, int maxProbeNum,
                                              int elementByteLength) {
        this.sender = sender;
        this.bucketIndex = bucketIndex;
        this.bucketInput = bucketInput;
        this.maxProbeNum = maxProbeNum;
        this.elementByteLength = elementByteLength;
    }

    @Override
    public void run() {
        try {
            sender.init(maxProbeNum, elementByteLength);
            capsule = sender.probeProduction(bucketIndex, bucketInput);
        } catch (MpcAbortException | RuntimeException e) {
            exception = e;
        }
    }

    BaSsuIbltProductionUnionProbeCapsule getCapsule() {
        return capsule;
    }

    Exception getException() {
        return exception;
    }
}
