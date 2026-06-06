package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Domain labels for fixed-count UP-BA-UPOT offline material.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
enum BaSsuIbltUpBaUpotOfflineLabel {
    /**
     * branch mask label.
     */
    BRANCH_MASK,
    /**
     * payload mask label.
     */
    PAYLOAD_MASK,
    /**
     * authentication mask label.
     */
    AUTH_MASK,
    /**
     * retry-domain mask label.
     */
    RETRY_MASK,
    /**
     * transcript mask label.
     */
    TRANSCRIPT_MASK;

    /**
     * Returns the fixed material byte length for this label under the public schedule.
     *
     * @param schedule public schedule.
     * @return fixed byte length.
     */
    int byteLength(BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        return switch (this) {
            case BRANCH_MASK -> BaUpotMicroBenchmark.BLOCK_BYTE_LENGTH;
            case PAYLOAD_MASK ->
                Math.addExact(
                    Math.addExact(schedule.getElementByteLength(), schedule.getTagByteLength()),
                    schedule.getCheckByteLength()
                );
            case AUTH_MASK -> schedule.getAuthTagByteLength();
            case RETRY_MASK, TRANSCRIPT_MASK -> BaUpotMicroBenchmark.BLOCK_BYTE_LENGTH;
        };
    }
}
