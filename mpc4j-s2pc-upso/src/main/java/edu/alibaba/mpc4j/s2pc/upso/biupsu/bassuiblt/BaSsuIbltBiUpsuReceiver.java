package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuReceiver;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import static edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt.BaSsuIbltBiUpsuPtoDesc.PtoStep;

/**
 * BA-SSU-IBLT bi-output UPSU receiver.
 *
 * <p>The fixed-layer reference endpoint is disabled by default and must be explicitly enabled for tests or local
 * measurements. In {@link BaSsuIbltProtocolMode#SECURE_SEMI_HONEST}, this class must pass the production gate and then
 * enter the queue-peel endpoint; it must not fall back to the fixed-layer reference path.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltBiUpsuReceiver extends AbstractTwoPartyPto implements BiUpsuReceiver {
    /**
     * config.
     */
    private final BaSsuIbltBiUpsuConfig config;
    /**
     * receiver element size.
     */
    private int receiverElementSize;
    /**
     * max sender element size.
     */
    private int maxSenderElementSize;
    /**
     * element byte length.
     */
    private int elementByteLength;

    BaSsuIbltBiUpsuReceiver(Rpc receiverRpc, Party senderParty, BaSsuIbltBiUpsuConfig config) {
        super(BaSsuIbltBiUpsuPtoDesc.getInstance(), receiverRpc, senderParty, config);
        this.config = config;
    }

    @Override
    public void init(int receiverElementSize, int maxSenderElementSize, int elementByteLength) {
        MathPreconditions.checkPositive("receiverElementSize", receiverElementSize);
        MathPreconditions.checkPositive("maxSenderElementSize", maxSenderElementSize);
        MathPreconditions.checkPositiveInRangeClosed(
            "elementByteLength", elementByteLength, config.getMaxElementByteLength()
        );
        this.receiverElementSize = receiverElementSize;
        this.maxSenderElementSize = maxSenderElementSize;
        this.elementByteLength = elementByteLength;
        initState();
    }

    @Override
    public BiUpsuPartyOutput psu(Set<ByteBuffer> receiverElementSet) throws MpcAbortException {
        checkInitialized();
        if (config.getProtocolMode() == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST) {
            BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config);
            return runSecureSemiHonest(receiverElementSet);
        }
        if (receiverElementSet == null) {
            throw new IllegalArgumentException("receiverElementSet must be non-null");
        }
        MathPreconditions.checkPositiveInRangeClosed(
            "receiverElementSize", receiverElementSet.size(), receiverElementSize
        );
        MpcAbortPreconditions.checkArgument(
            config.isEnableFixedLayerReferenceEndpoint(),
            "fixed-layer reference endpoint is disabled; enable it only for tests"
        );
        extraInfo++;
        boolean senderAnchor = maxSenderElementSize >= receiverElementSize;
        boolean receiverAnchor = !senderAnchor;
        BaSsuIbltBiUpsuParams params = config.createParams(maxSenderElementSize, receiverElementSize);
        MpcAbortPreconditions.checkArgument(
            params.getRetryCount() == 1,
            "fixed-layer reference endpoint supports retryCount = 1 until safe retry selection is implemented"
        );
        List<byte[]> receiverLayerPayload = BaSsuIbltFixedLayerEndpoint.encodeOwnLayer(
            receiverElementSet, receiverAnchor, elementByteLength, params
        );
        sendOtherPartyPayload(PtoStep.RECEIVER_SEND_FIXED_LAYER.ordinal(), receiverLayerPayload);
        List<byte[]> senderLayerPayload = receiveOtherPartyPayload(PtoStep.SENDER_SEND_FIXED_LAYER.ordinal());
        return BaSsuIbltFixedLayerEndpoint.decodeAndPeel(
            receiverElementSet, receiverAnchor, receiverLayerPayload, senderLayerPayload, elementByteLength, params
        );
    }

    private BiUpsuPartyOutput runSecureSemiHonest(Set<ByteBuffer> receiverElementSet) throws MpcAbortException {
        if (receiverElementSet == null) {
            throw new IllegalArgumentException("receiverElementSet must be non-null");
        }
        MathPreconditions.checkPositiveInRangeClosed(
            "receiverElementSize", receiverElementSet.size(), receiverElementSize
        );
        extraInfo++;
        return BaSsuIbltQueuePeelEndpoint.runProductionEndpoint(
            rpc, otherParty(), false, receiverElementSet, maxSenderElementSize, receiverElementSize,
            elementByteLength, config, secureEndpointTaskId(), parallel
        );
    }

    private int secureEndpointTaskId() {
        return (int) (encodeTaskId & Integer.MAX_VALUE);
    }
}
