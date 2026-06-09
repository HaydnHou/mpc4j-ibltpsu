package edu.alibaba.mpc4j.common.structure.sogs;

/**
 * Parameters for source-oblivious graph sketch.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsGraphParams {
    /**
     * Minimum subtable length for small deterministic tests and tiny protocol instances.
     */
    private static final int MIN_SUBTABLE_LENGTH = 64;
    /**
     * Expected peeled item count.
     */
    private final int expectedItemSize;
    /**
     * Vertex count.
     */
    private final int vertexCount;
    /**
     * Edge degree.
     */
    private final int degree;
    /**
     * Layout.
     */
    private final SogsGraphLayout layout;
    /**
     * Position seed.
     */
    private final long seed;
    /**
     * Check seed.
     */
    private final long checkSeed;

    private SogsGraphParams(int expectedItemSize, int vertexCount, int degree, SogsGraphLayout layout, long seed) {
        if (expectedItemSize <= 0) {
            throw new IllegalArgumentException("expectedItemSize must be positive: " + expectedItemSize);
        }
        if (vertexCount <= 0) {
            throw new IllegalArgumentException("vertexCount must be positive: " + vertexCount);
        }
        if (degree < 2) {
            throw new IllegalArgumentException("degree must be at least 2: " + degree);
        }
        if (vertexCount < degree) {
            throw new IllegalArgumentException("vertexCount must be at least degree");
        }
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        if (layout != SogsGraphLayout.SUBTABLE) {
            throw new IllegalArgumentException("unsupported layout: " + layout);
        }
        if (vertexCount % degree != 0) {
            throw new IllegalArgumentException("SUBTABLE layout requires vertexCount % degree == 0");
        }
        this.expectedItemSize = expectedItemSize;
        this.vertexCount = vertexCount;
        this.degree = degree;
        this.layout = layout;
        this.seed = seed;
        checkSeed = SogsHashUtils.mix64(seed ^ 0x6A09E667F3BCC909L);
    }

    /**
     * Creates parameters from expected item count and vertex multiplier.
     *
     * @param expectedItemSize expected item count.
     * @param alpha            vertex multiplier.
     * @param degree           edge degree.
     * @param seed             seed.
     * @return parameters.
     */
    public static SogsGraphParams fromExpectedItemSize(int expectedItemSize, double alpha, int degree, long seed) {
        if (!Double.isFinite(alpha) || alpha <= 0.0) {
            throw new IllegalArgumentException("alpha must be positive: " + alpha);
        }
        int minVertexCount = Math.multiplyExact(degree, MIN_SUBTABLE_LENGTH);
        int rawVertexCount = Math.max(minVertexCount, (int) Math.ceil(expectedItemSize * alpha));
        int vertexCount = roundUp(rawVertexCount, degree);
        return new SogsGraphParams(expectedItemSize, vertexCount, degree, SogsGraphLayout.SUBTABLE, seed);
    }

    private static int roundUp(int value, int factor) {
        long result = ((long) value + factor - 1) / factor * factor;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("rounded value is too large: " + result);
        }
        return (int) result;
    }

    /**
     * Gets expected item count.
     *
     * @return expected item count.
     */
    public int getExpectedItemSize() {
        return expectedItemSize;
    }

    /**
     * Gets vertex count.
     *
     * @return vertex count.
     */
    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * Gets degree.
     *
     * @return degree.
     */
    public int getDegree() {
        return degree;
    }

    /**
     * Gets layout.
     *
     * @return layout.
     */
    public SogsGraphLayout getLayout() {
        return layout;
    }

    /**
     * Gets subtable length.
     *
     * @return subtable length.
     */
    int getSubTableLength() {
        return vertexCount / degree;
    }

    /**
     * Gets position seed.
     *
     * @return seed.
     */
    long getSeed() {
        return seed;
    }

    /**
     * Gets check seed.
     *
     * @return check seed.
     */
    long getCheckSeed() {
        return checkSeed;
    }
}
