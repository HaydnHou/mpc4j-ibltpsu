package edu.alibaba.mpc4j.common.structure.sogs;

/**
 * SOGS-backed PSU sketch backend.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsGraphPsuSketchBackend implements SogsPsuSketchBackend {
    /**
     * Graph sketch.
     */
    private final SogsPayloadGraphSketch sketch;

    /**
     * Creates a backend.
     *
     * @param params            parameters.
     * @param payloadByteLength payload byte length.
     */
    public SogsGraphPsuSketchBackend(SogsGraphParams params, int payloadByteLength) {
        sketch = new SogsPayloadGraphSketch(params, payloadByteLength);
    }

    @Override
    public SogsPsuSketchBackendType type() {
        return SogsPsuSketchBackendType.SOGS_GRAPH;
    }

    @Override
    public void add(long label, byte[] payload) {
        sketch.insert(label, payload);
    }

    @Override
    public void remove(long label, byte[] payload) {
        sketch.delete(label, payload);
    }

    @Override
    public SogsPsuSketchPeelResult peel() {
        return sketch.peel();
    }

    @Override
    public int[] positions(long label) {
        return sketch.positions(label);
    }

    @Override
    public int[] uniquePositions(long[] labels, boolean[] excluded) {
        return sketch.uniquePositions(labels, excluded);
    }

    @Override
    public int[] counts() {
        return sketch.counts();
    }

    @Override
    public byte[][] valueSums() {
        return sketch.valueSums();
    }

    @Override
    public boolean[] pureSingletons() {
        return sketch.pureSingletons();
    }

    @Override
    public int tableSize() {
        return sketch.getParams().getVertexCount();
    }

    @Override
    public int hashNum() {
        return sketch.getParams().getDegree();
    }

    @Override
    public int payloadByteLength() {
        return sketch.getPayloadByteLength();
    }

    @Override
    public int itemCount() {
        return sketch.getNetItemCount();
    }
}
