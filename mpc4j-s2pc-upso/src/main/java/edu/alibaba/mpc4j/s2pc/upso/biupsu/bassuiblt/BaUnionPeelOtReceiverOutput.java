package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.List;

/**
 * BA-UnionPeel-OT receiver output.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaUnionPeelOtReceiverOutput {
    /**
     * COT count.
     */
    private final int cotNum;
    /**
     * decoded bucket outputs.
     */
    private final List<BaUpotBucketOutput> outputs;
    /**
     * checksum.
     */
    private final long checksum;

    BaUnionPeelOtReceiverOutput(int cotNum, List<BaUpotBucketOutput> outputs, long checksum) {
        if (cotNum <= 0) {
            throw new IllegalArgumentException("cotNum must be positive");
        }
        if (outputs == null || outputs.isEmpty()) {
            throw new IllegalArgumentException("outputs must be non-empty");
        }
        this.cotNum = cotNum;
        this.outputs = List.copyOf(outputs);
        this.checksum = checksum;
    }

    public int getCotNum() {
        return cotNum;
    }

    public List<BaUpotBucketOutput> getOutputs() {
        return outputs;
    }

    public long getChecksum() {
        return checksum;
    }
}
