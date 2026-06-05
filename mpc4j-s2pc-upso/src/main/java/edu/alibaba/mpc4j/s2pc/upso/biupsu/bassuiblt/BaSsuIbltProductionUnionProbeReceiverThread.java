package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Production union-probe receiver test thread.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionUnionProbeReceiverThread extends Thread {
    /**
     * receiver.
     */
    private final BaSsuIbltProductionUnionProbeReceiver receiver;
    /**
     * bucket index.
     */
    private final int bucketIndex;
    /**
     * bucket input.
     */
    private final BaSsuIbltSecureBucketInput bucketInput;
    /**
     * sender capsule.
     */
    private final BaSsuIbltProductionUnionProbeCapsule senderCapsule;
    /**
     * max probe number.
     */
    private final int maxProbeNum;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * output.
     */
    private BaSsuIbltProductionUnionProbeOutput output;
    /**
     * exception.
     */
    private Exception exception;

    BaSsuIbltProductionUnionProbeReceiverThread(BaSsuIbltProductionUnionProbeReceiver receiver, int bucketIndex,
                                                BaSsuIbltSecureBucketInput bucketInput,
                                                BaSsuIbltProductionUnionProbeCapsule senderCapsule,
                                                int maxProbeNum, int elementByteLength) {
        this.receiver = receiver;
        this.bucketIndex = bucketIndex;
        this.bucketInput = bucketInput;
        this.senderCapsule = senderCapsule;
        this.maxProbeNum = maxProbeNum;
        this.elementByteLength = elementByteLength;
    }

    @Override
    public void run() {
        try {
            receiver.init(maxProbeNum, elementByteLength);
            output = receiver.probeProduction(bucketIndex, bucketInput, senderCapsule);
        } catch (MpcAbortException | RuntimeException e) {
            exception = e;
        }
    }

    BaSsuIbltProductionUnionProbeOutput getOutput() {
        return output;
    }

    Exception getException() {
        return exception;
    }
}
