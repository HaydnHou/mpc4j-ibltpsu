package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.tool.EnvType;

/**
 * PSU sketch backend factory.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchBackendFactory {
    private SogsPsuSketchBackendFactory() {
        // empty
    }

    /**
     * Creates an H5-IBLT backend.
     *
     * @param envType           environment.
     * @param threshold         threshold.
     * @param payloadByteLength payload byte length.
     * @param hashKey           hash key.
     * @return backend.
     */
    public static SogsPsuSketchBackend createH5Backend(
        EnvType envType, int threshold, int payloadByteLength, byte[] hashKey
    ) {
        return new H5LongIbltPsuSketchBackend(
            envType, threshold, H5LongIblt.DEFAULT_MULTIPLIER, payloadByteLength, hashKey
        );
    }

    /**
     * Creates an H5-IBLT backend.
     *
     * @param envType           environment.
     * @param threshold         threshold.
     * @param multiplier        multiplier.
     * @param payloadByteLength payload byte length.
     * @param hashKey           hash key.
     * @return backend.
     */
    public static SogsPsuSketchBackend createH5Backend(
        EnvType envType, int threshold, double multiplier, int payloadByteLength, byte[] hashKey
    ) {
        return new H5LongIbltPsuSketchBackend(envType, threshold, multiplier, payloadByteLength, hashKey);
    }

    /**
     * Creates a SOGS backend.
     *
     * @param expectedItemSize  expected item count.
     * @param alpha             vertex multiplier.
     * @param degree            graph degree.
     * @param payloadByteLength payload byte length.
     * @param seed              seed.
     * @return backend.
     */
    public static SogsPsuSketchBackend createSogsBackend(
        int expectedItemSize, double alpha, int degree, int payloadByteLength, long seed
    ) {
        SogsGraphParams params = SogsGraphParams.fromExpectedItemSize(expectedItemSize, alpha, degree, seed);
        return new SogsGraphPsuSketchBackend(params, payloadByteLength);
    }
}
