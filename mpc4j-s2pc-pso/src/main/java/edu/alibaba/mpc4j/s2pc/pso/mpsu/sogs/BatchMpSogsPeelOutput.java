package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public batch output for one MP-SOGS union-peel layer.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class BatchMpSogsPeelOutput {
    /**
     * Public peel results in the same order as input cells.
     */
    private final List<MpSogsPeelResult> results;
    /**
     * Sent bytes for accounting.
     */
    private final long sendBytes;
    /**
     * Received bytes for accounting.
     */
    private final long receiveBytes;
    /**
     * Network rounds for accounting.
     */
    private final int roundCount;

    public BatchMpSogsPeelOutput(List<MpSogsPeelResult> results, long sendBytes, long receiveBytes, int roundCount) {
        if (sendBytes < 0 || receiveBytes < 0) {
            throw new IllegalArgumentException("byte counters must be non-negative");
        }
        if (roundCount < 0) {
            throw new IllegalArgumentException("roundCount must be non-negative");
        }
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.sendBytes = sendBytes;
        this.receiveBytes = receiveBytes;
        this.roundCount = roundCount;
    }

    public List<MpSogsPeelResult> getResults() {
        return results;
    }

    public long getSendBytes() {
        return sendBytes;
    }

    public long getReceiveBytes() {
        return receiveBytes;
    }

    public int getRoundCount() {
        return roundCount;
    }
}
