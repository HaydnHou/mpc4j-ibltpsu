package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Signed source-oblivious graph sketch with payload XOR.
 *
 * <p>The layout mirrors the payload-bearing fields of H5LongIblt, but uses an explicit graph degree instead of the
 * fixed five-hash IBLT layout. Positive and negative singleton entries are both supported so that the structure can be
 * used as a candidate backend for IBLT-PSU-style residual peeling.</p>
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPayloadGraphSketch {
    /**
     * Parameters.
     */
    private final SogsGraphParams params;
    /**
     * Payload byte length.
     */
    private final int payloadByteLength;
    /**
     * Vertex summaries.
     */
    private final SogsPayloadVertexSummary[] vertices;
    /**
     * Net item count.
     */
    private int netItemCount;

    /**
     * Creates an empty graph sketch.
     *
     * @param params            parameters.
     * @param payloadByteLength payload byte length.
     */
    public SogsPayloadGraphSketch(SogsGraphParams params, int payloadByteLength) {
        if (params == null) {
            throw new NullPointerException("params");
        }
        if (payloadByteLength < 0) {
            throw new IllegalArgumentException("payloadByteLength must be non-negative");
        }
        this.params = params;
        this.payloadByteLength = payloadByteLength;
        vertices = new SogsPayloadVertexSummary[params.getVertexCount()];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new SogsPayloadVertexSummary(payloadByteLength);
        }
        netItemCount = 0;
    }

    private SogsPayloadGraphSketch(SogsPayloadGraphSketch that) {
        params = that.params;
        payloadByteLength = that.payloadByteLength;
        vertices = new SogsPayloadVertexSummary[that.vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            vertices[i] = new SogsPayloadVertexSummary(that.vertices[i]);
        }
        netItemCount = that.netItemCount;
    }

    /**
     * Creates a deep copy.
     *
     * @return copy.
     */
    public SogsPayloadGraphSketch copy() {
        return new SogsPayloadGraphSketch(this);
    }

    /**
     * Inserts a positive item.
     *
     * @param label   label.
     * @param payload payload.
     */
    public void insert(long label, byte[] payload) {
        update(label, payload, 1);
    }

    /**
     * Inserts a negative item.
     *
     * @param label   label.
     * @param payload payload.
     */
    public void delete(long label, byte[] payload) {
        update(label, payload, -1);
    }

    private void update(long label, byte[] payload, int signDelta) {
        checkPayload(payload);
        long check = SogsHashUtils.check(label, params.getCheckSeed());
        for (int position : positions(label)) {
            vertices[position].update(label, check, payload, signDelta);
        }
        netItemCount += signDelta;
    }

    /**
     * Peels without modifying this instance.
     *
     * @return peel result.
     */
    public SogsPsuSketchPeelResult peel() {
        SogsPayloadGraphSketch work = copy();
        long start = System.nanoTime();
        ArrayDeque<Integer> queue = new ArrayDeque<>(work.vertices.length);
        for (int i = 0; i < work.vertices.length; i++) {
            if (work.vertices[i].isSingleton(params.getCheckSeed())) {
                queue.add(i);
            }
        }
        List<SogsPsuSketchEntry> entries = new ArrayList<>();
        long queuePolls = 0L;
        long bottomCount = 0L;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            queuePolls++;
            SogsPayloadVertexSummary vertex = work.vertices[index];
            if (!vertex.isSingleton(params.getCheckSeed())) {
                bottomCount++;
                continue;
            }
            int sign = vertex.getSign();
            long label = vertex.getLabelXor();
            byte[] payload = vertex.getPayloadXor();
            entries.add(new SogsPsuSketchEntry(label, payload, sign));
            work.update(label, payload, -sign);
            for (int position : work.positions(label)) {
                if (work.vertices[position].isSingleton(params.getCheckSeed())) {
                    queue.add(position);
                }
            }
        }
        long peelNanos = System.nanoTime() - start;
        return new SogsPsuSketchPeelResult(
            work.isEmpty(), entries, work.residualEdgeCount(), queuePolls, bottomCount, peelNanos
        );
    }

    /**
     * Returns whether the sketch is empty.
     *
     * @return true if empty.
     */
    public boolean isEmpty() {
        for (SogsPayloadVertexSummary vertex : vertices) {
            if (!vertex.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets positions for a label.
     *
     * @param label label.
     * @return positions.
     */
    public int[] positions(long label) {
        return SogsHashUtils.positions(label, params);
    }

    /**
     * Returns deduplicated positions hit by labels, excluding positions marked by the bitmap.
     *
     * @param labels   labels.
     * @param excluded excluded bitmap.
     * @return unique positions.
     */
    public int[] uniquePositions(long[] labels, boolean[] excluded) {
        if (labels == null) {
            throw new NullPointerException("labels");
        }
        if (excluded != null && excluded.length != vertices.length) {
            throw new IllegalArgumentException("excluded length mismatch");
        }
        boolean[] seen = new boolean[vertices.length];
        int[] uniquePositions = new int[Math.multiplyExact(labels.length, params.getDegree())];
        int uniquePositionNum = 0;
        for (long label : labels) {
            for (int position : positions(label)) {
                if (!seen[position] && (excluded == null || !excluded[position])) {
                    seen[position] = true;
                    uniquePositions[uniquePositionNum] = position;
                    uniquePositionNum++;
                }
            }
        }
        return Arrays.copyOf(uniquePositions, uniquePositionNum);
    }

    /**
     * Gets cloned signed degrees.
     *
     * @return degrees.
     */
    public int[] counts() {
        int[] counts = new int[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            counts[i] = vertices[i].getDegree();
        }
        return counts;
    }

    /**
     * Gets cloned payload XOR sums.
     *
     * @return payload XOR sums.
     */
    public byte[][] valueSums() {
        byte[][] valueSums = new byte[vertices.length][];
        for (int i = 0; i < vertices.length; i++) {
            valueSums[i] = BytesUtils.clone(vertices[i].getPayloadXor());
        }
        return valueSums;
    }

    /**
     * Gets pure singleton indicators.
     *
     * @return pure singleton indicators.
     */
    public boolean[] pureSingletons() {
        boolean[] pureSingletons = new boolean[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            pureSingletons[i] = vertices[i].isSingleton(params.getCheckSeed());
        }
        return pureSingletons;
    }

    /**
     * Gets parameters.
     *
     * @return parameters.
     */
    public SogsGraphParams getParams() {
        return params;
    }

    /**
     * Gets payload byte length.
     *
     * @return payload byte length.
     */
    public int getPayloadByteLength() {
        return payloadByteLength;
    }

    /**
     * Gets net item count.
     *
     * @return net item count.
     */
    public int getNetItemCount() {
        return netItemCount;
    }

    private int residualEdgeCount() {
        int absDegreeSum = 0;
        for (SogsPayloadVertexSummary vertex : vertices) {
            absDegreeSum += Math.abs(vertex.getDegree());
        }
        return absDegreeSum / params.getDegree();
    }

    private void checkPayload(byte[] payload) {
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        if (payload.length != payloadByteLength) {
            throw new IllegalArgumentException("payload length mismatch");
        }
    }
}
