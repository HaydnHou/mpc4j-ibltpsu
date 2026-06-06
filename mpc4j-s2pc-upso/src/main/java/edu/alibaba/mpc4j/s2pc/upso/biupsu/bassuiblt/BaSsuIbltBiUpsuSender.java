package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuPartyOutput;
import edu.alibaba.mpc4j.s2pc.upso.biupsu.BiUpsuSender;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import static edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt.BaSsuIbltBiUpsuPtoDesc.PtoStep;

/**
 * BA-SSU-IBLT bi-output UPSU sender.
 *
 * <p>The fixed-layer reference endpoint is disabled by default and must be explicitly enabled for tests or local
 * measurements. In {@link BaSsuIbltProtocolMode#SECURE_SEMI_HONEST}, this class must pass the production gate and then
 * enter the queue-peel endpoint; it must not fall back to the fixed-layer reference path.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltBiUpsuSender extends AbstractTwoPartyPto implements BiUpsuSender {
    /**
     * config.
     */
    private final BaSsuIbltBiUpsuConfig config;
    /**
     * max sender element size.
     */
    private int maxSenderElementSize;
    /**
     * receiver element size.
     */
    private int receiverElementSize;
    /**
     * element byte length.
     */
    private int elementByteLength;

    BaSsuIbltBiUpsuSender(Rpc senderRpc, Party receiverParty, BaSsuIbltBiUpsuConfig config) {
        super(BaSsuIbltBiUpsuPtoDesc.getInstance(), senderRpc, receiverParty, config);
        this.config = config;
    }

    @Override
    public void init(int maxSenderElementSize, int receiverElementSize, int elementByteLength) {
        MathPreconditions.checkPositive("maxSenderElementSize", maxSenderElementSize);
        MathPreconditions.checkPositive("receiverElementSize", receiverElementSize);
        MathPreconditions.checkPositiveInRangeClosed(
            "elementByteLength", elementByteLength, config.getMaxElementByteLength()
        );
        this.maxSenderElementSize = maxSenderElementSize;
        this.receiverElementSize = receiverElementSize;
        this.elementByteLength = elementByteLength;
        initState();
    }

    @Override
    public BiUpsuPartyOutput psu(Set<ByteBuffer> senderElementSet) throws MpcAbortException {
        checkInitialized();
        if (config.getProtocolMode() == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST) {
            BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config);
            return runSecureSemiHonest(senderElementSet);
        }
        if (senderElementSet == null) {
            throw new IllegalArgumentException("senderElementSet must be non-null");
        }
        MathPreconditions.checkPositiveInRangeClosed(
            "senderElementSize", senderElementSet.size(), maxSenderElementSize
        );
        Preconditions.checkArgument(receiverElementSize > 0);
        MpcAbortPreconditions.checkArgument(
            config.isEnableFixedLayerReferenceEndpoint(),
            "fixed-layer reference endpoint is disabled; enable it only for tests"
        );
        extraInfo++;
        boolean senderAnchor = maxSenderElementSize >= receiverElementSize;
        BaSsuIbltBiUpsuParams params = config.createParams(maxSenderElementSize, receiverElementSize);
        MpcAbortPreconditions.checkArgument(
            params.getRetryCount() == 1,
            "fixed-layer reference endpoint supports retryCount = 1 until safe retry selection is implemented"
        );
        List<byte[]> senderLayerPayload = BaSsuIbltFixedLayerEndpoint.encodeOwnLayer(
            senderElementSet, senderAnchor, elementByteLength, params
        );
        sendOtherPartyPayload(PtoStep.SENDER_SEND_FIXED_LAYER.ordinal(), senderLayerPayload);
        List<byte[]> receiverLayerPayload = receiveOtherPartyPayload(PtoStep.RECEIVER_SEND_FIXED_LAYER.ordinal());
        return BaSsuIbltFixedLayerEndpoint.decodeAndPeel(
            senderElementSet, senderAnchor, senderLayerPayload, receiverLayerPayload, elementByteLength, params
        );
    }

    private BiUpsuPartyOutput runSecureSemiHonest(Set<ByteBuffer> senderElementSet) throws MpcAbortException {
        if (senderElementSet == null) {
            throw new IllegalArgumentException("senderElementSet must be non-null");
        }
        MathPreconditions.checkPositiveInRangeClosed(
            "senderElementSize", senderElementSet.size(), maxSenderElementSize
        );
        Preconditions.checkArgument(receiverElementSize > 0);
        extraInfo++;
        return BaSsuIbltQueuePeelEndpoint.runProductionEndpoint(
            rpc, otherParty(), true, senderElementSet, maxSenderElementSize, receiverElementSize,
            elementByteLength, config, secureEndpointTaskId(), parallel
        );
    }

    private int secureEndpointTaskId() {
        return (int) (encodeTaskId & Integer.MAX_VALUE);
    }
}
