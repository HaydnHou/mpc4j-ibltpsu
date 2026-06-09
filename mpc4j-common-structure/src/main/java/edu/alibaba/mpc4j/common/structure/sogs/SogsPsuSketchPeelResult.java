package edu.alibaba.mpc4j.common.structure.sogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PSU sketch peel result.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchPeelResult {
    /**
     * Success.
     */
    private final boolean success;
    /**
     * Entries.
     */
    private final List<SogsPsuSketchEntry> entries;
    /**
     * Residual edge count estimate.
     */
    private final int residualEdgeCount;
    /**
     * Queue polls.
     */
    private final long queuePolls;
    /**
     * Bottom polls.
     */
    private final long bottomCount;
    /**
     * Peel time.
     */
    private final long peelNanos;

    SogsPsuSketchPeelResult(
        boolean success, List<SogsPsuSketchEntry> entries, int residualEdgeCount,
        long queuePolls, long bottomCount, long peelNanos
    ) {
        this.success = success;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.residualEdgeCount = residualEdgeCount;
        this.queuePolls = queuePolls;
        this.bottomCount = bottomCount;
        this.peelNanos = peelNanos;
    }

    /**
     * Gets success.
     *
     * @return success.
     */
    public boolean success() {
        return success;
    }

    /**
     * Gets entries.
     *
     * @return entries.
     */
    public List<SogsPsuSketchEntry> entries() {
        return entries;
    }

    /**
     * Gets positive entries.
     *
     * @return positive entries.
     */
    public List<SogsPsuSketchEntry> positiveEntries() {
        List<SogsPsuSketchEntry> positiveEntries = new ArrayList<>();
        for (SogsPsuSketchEntry entry : entries) {
            if (entry.sign() > 0) {
                positiveEntries.add(entry);
            }
        }
        return positiveEntries;
    }

    /**
     * Gets negative entries.
     *
     * @return negative entries.
     */
    public List<SogsPsuSketchEntry> negativeEntries() {
        List<SogsPsuSketchEntry> negativeEntries = new ArrayList<>();
        for (SogsPsuSketchEntry entry : entries) {
            if (entry.sign() < 0) {
                negativeEntries.add(entry);
            }
        }
        return negativeEntries;
    }

    /**
     * Gets residual edge count.
     *
     * @return residual edge count.
     */
    public int residualEdgeCount() {
        return residualEdgeCount;
    }

    /**
     * Gets queue polls.
     *
     * @return queue polls.
     */
    public long queuePolls() {
        return queuePolls;
    }

    /**
     * Gets bottom count.
     *
     * @return bottom count.
     */
    public long bottomCount() {
        return bottomCount;
    }

    /**
     * Gets peel time in nanoseconds.
     *
     * @return peel time.
     */
    public long peelNanos() {
        return peelNanos;
    }
}
