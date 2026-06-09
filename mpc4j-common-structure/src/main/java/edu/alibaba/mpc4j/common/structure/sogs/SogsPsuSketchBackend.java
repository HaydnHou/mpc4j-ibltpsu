package edu.alibaba.mpc4j.common.structure.sogs;

/**
 * Common PSU sketch backend interface for comparing H5-IBLT and SOGS.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public interface SogsPsuSketchBackend {
    /**
     * Gets backend type.
     *
     * @return backend type.
     */
    SogsPsuSketchBackendType type();

    /**
     * Adds a positive item.
     *
     * @param label   label.
     * @param payload payload.
     */
    void add(long label, byte[] payload);

    /**
     * Adds a negative item.
     *
     * @param label   label.
     * @param payload payload.
     */
    void remove(long label, byte[] payload);

    /**
     * Peels without modifying the backend.
     *
     * @return peel result.
     */
    SogsPsuSketchPeelResult peel();

    /**
     * Gets positions of a label.
     *
     * @param label label.
     * @return positions.
     */
    int[] positions(long label);

    /**
     * Returns deduplicated positions hit by labels, excluding positions marked by the bitmap.
     *
     * @param labels   labels.
     * @param excluded excluded bitmap.
     * @return unique positions.
     */
    int[] uniquePositions(long[] labels, boolean[] excluded);

    /**
     * Gets cloned cell counts/degrees.
     *
     * @return counts.
     */
    int[] counts();

    /**
     * Gets cloned payload/value XOR sums.
     *
     * @return payload sums.
     */
    byte[][] valueSums();

    /**
     * Gets pure singleton indicators.
     *
     * @return pure singleton indicators.
     */
    boolean[] pureSingletons();

    /**
     * Gets storage table size.
     *
     * @return storage size.
     */
    int tableSize();

    /**
     * Gets hash count / edge degree.
     *
     * @return hash count.
     */
    int hashNum();

    /**
     * Gets payload byte length.
     *
     * @return payload byte length.
     */
    int payloadByteLength();

    /**
     * Gets net item count.
     *
     * @return net item count.
     */
    int itemCount();
}
