package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Plain payload-bound BA-UPOT receiver.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class BaUpotPlainPayloadReceiver extends AbstractTwoPartyPto {
    /**
     * config.
     */
    private final BaUpotConfig config;
    /**
     * bucket count.
     */
    private int bucketNum;
    /**
     * decoded outputs.
     */
    private List<BaUpotBucketOutput> outputs;
    /**
     * decoded output count.
     */
    private int decodedOutputCount;

    public BaUpotPlainPayloadReceiver(Rpc receiverRpc, Party senderParty, BaUpotConfig config) {
        super(BaUpotPlainPayloadPtoDesc.getInstance(), receiverRpc, senderParty, config);
        this.config = config;
    }

    /**
     * Initializes the plain transport.
     *
     * @param bucketNum bucket count.
     */
    public void init(int bucketNum) {
        if (bucketNum <= 0) {
            throw new IllegalArgumentException("bucketNum must be positive");
        }
        this.bucketNum = bucketNum;
        outputs = null;
        decodedOutputCount = 0;
        logPhaseInfo(PtoState.INIT_BEGIN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /**
     * Receives and decodes fixed-shape output capsules.
     *
     * @param bucketIndices expected bucket indices.
     * @return checksum.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public long execute(List<Integer> bucketIndices) throws MpcAbortException {
        if (bucketIndices == null) {
            throw new IllegalArgumentException("bucketIndices must be non-null");
        }
        return execute(bucketIndices.iterator(), true);
    }

    /**
     * Receives and decodes fixed-shape output capsules.
     *
     * @param bucketIndices expected bucket indices.
     * @param retainOutputs true if decoded outputs should be retained.
     * @return checksum.
     * @throws MpcAbortException the protocol failure aborts.
     */
    public long execute(Iterator<Integer> bucketIndices, boolean retainOutputs) throws MpcAbortException {
        checkInitialized();
        if (bucketIndices == null) {
            throw new IllegalArgumentException("bucketIndices must be non-null");
        }
        logPhaseInfo(PtoState.PTO_BEGIN);
        stopWatch.start();
        BaUpotPlainOutputCapsuleCodec codec = BaUpotPlainOutputCapsuleCodec.fromConfig(config);
        outputs = retainOutputs ? new ArrayList<>(bucketNum) : null;
        decodedOutputCount = 0;
        int batchSize = config.getOnlineBatchSize();
        long checksum = 0L;
        for (int batchStart = 0; batchStart < bucketNum; batchStart += batchSize) {
            int batchEnd = Math.min(bucketNum, batchStart + batchSize);
            List<byte[]> payload = receiveOtherPartyPayload(
                BaUpotPlainPayloadPtoDesc.PtoStep.SENDER_SEND_OUTPUT_CAPSULE.ordinal()
            );
            MpcAbortPreconditions.checkArgument(payload.size() == batchEnd - batchStart);
            for (int offset = 0; offset < payload.size(); offset++) {
                if (!bucketIndices.hasNext()) {
                    throw new IllegalArgumentException("bucketIndices shorter than bucketNum");
                }
                Integer boxedBucketIndex = bucketIndices.next();
                if (boxedBucketIndex == null) {
                    throw new IllegalArgumentException("bucketIndices must not contain null entries");
                }
                int bucketIndex = boxedBucketIndex;
                byte[] capsule = payload.get(offset);
                MpcAbortPreconditions.checkArgument(capsule.length == codec.capsuleByteLength());
                BaUpotBucketOutput output = codec.decode(bucketIndex, capsule);
                if (retainOutputs) {
                    outputs.add(output);
                }
                decodedOutputCount++;
                checksum ^= BaUpotPlainPayloadSender.firstLong(capsule);
            }
        }
        if (bucketIndices.hasNext()) {
            throw new IllegalArgumentException("bucketIndices longer than bucketNum");
        }
        stopWatch.stop();
        long receiveTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(
            PtoState.PTO_STEP, 1, 1, receiveTime,
            "receiver decodes " + bucketNum + " plain output capsules of " + codec.capsuleByteLength() + "B"
        );
        logPhaseInfo(PtoState.PTO_END);
        return checksum;
    }

    public List<BaUpotBucketOutput> getOutputs() {
        return outputs == null ? List.of() : List.copyOf(outputs);
    }

    public int getDecodedOutputCount() {
        return decodedOutputCount;
    }
}
