package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fixed-layer endpoint bridge for BA-SSU-IBLT bi-output UPSU.
 *
 * <p>The bridge exchanges fixed source-layer bucket payloads, not raw element sets. It is still a reference endpoint:
 * source-layer bucket summaries are visible to the peer, and later milestones must replace this bridge with
 * protocol-level BA-UPOT before making a full security claim.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltFixedLayerEndpoint {
    /**
     * private constructor.
     */
    private BaSsuIbltFixedLayerEndpoint() {
        // empty
    }

    static List<byte[]> encodeOwnLayer(Set<ByteBuffer> ownSet, boolean ownAnchor, int elementByteLength,
                                       BaSsuIbltBiUpsuParams params) {
        if (params.getRetryCount() != 1) {
            throw new IllegalArgumentException(
                "fixed-layer reference endpoint supports retryCount = 1 until safe retry selection is implemented"
            );
        }
        return BaSsuIbltLayerPayloadCodec.encodeLayer(ownSet, ownAnchor, elementByteLength, params);
    }

    static BiUpsuPartyOutput decodeAndPeel(Set<ByteBuffer> ownSet, boolean ownAnchor, List<byte[]> ownLayerPayload,
                                           List<byte[]> otherLayerPayload, int elementByteLength,
                                           BaSsuIbltBiUpsuParams params) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(
            params.getRetryCount() == 1,
            "fixed-layer reference endpoint supports retryCount = 1 until safe retry selection is implemented"
        );
        BaSsuIbltLayerPayloadCodec.LayerCell[] ownCells = BaSsuIbltLayerPayloadCodec.decodeLayer(
            ownLayerPayload, elementByteLength, params
        );
        BaSsuIbltLayerPayloadCodec.LayerCell[] otherCells = BaSsuIbltLayerPayloadCodec.decodeLayer(
            otherLayerPayload, elementByteLength, params
        );
        BaSsuIbltLayerPeeler.PeelResult peelResult = ownAnchor
            ? BaSsuIbltLayerPeeler.peel(ownCells, otherCells, params, elementByteLength)
            : BaSsuIbltLayerPeeler.peel(otherCells, ownCells, params, elementByteLength);
        MpcAbortPreconditions.checkArgument(peelResult.isSuccess(), "fixed-layer BA-SSU-IBLT peel failed");
        Set<ByteBuffer> union = new LinkedHashSet<>(ownSet.size());
        for (ByteBuffer element : ownSet) {
            union.add(ByteBuffer.wrap(toBytes(element)));
        }
        Set<byte[]> receivedDifference = ownAnchor
            ? peelResult.getShadowOnlyElements()
            : peelResult.getAnchorOnlyElements();
        for (byte[] element : receivedDifference) {
            union.add(ByteBuffer.wrap(Arrays.copyOf(element, element.length)));
        }
        return new BiUpsuPartyOutput(union, BiUpsuPartyOutput.UNKNOWN_PSICA);
    }

    private static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
