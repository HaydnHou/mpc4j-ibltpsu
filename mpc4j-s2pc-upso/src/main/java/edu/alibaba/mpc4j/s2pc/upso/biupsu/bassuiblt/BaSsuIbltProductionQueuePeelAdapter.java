package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;

/**
 * Production queue-peel secure-core adapter.
 *
 * <p>This adapter is the only entry point from the secure core into the remote-state-hiding UP-BA-UPOT backend.
 * It remains fail-closed until the online queue-peel loop is wired to the COT-backed bucket-probe evaluator and the
 * adaptive queue transcript leakage policy is explicitly accepted. Reference queue-peel code must stay outside this
 * adapter.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltProductionQueuePeelAdapter {
    /**
     * randomness for the local secure-core transport bridge.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * private constructor.
     */
    private BaSsuIbltProductionQueuePeelAdapter() {
        // empty
    }

    static BaSsuIbltSecureProtocolResult run(
        Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet, int elementByteLength, BaSsuIbltBiUpsuParams params,
        byte[][] leftFixedInputs, boolean[] leftActiveFlags, BaSsuIbltOprfTagOutput leftTagOutput,
        byte[][] rightFixedInputs, boolean[] rightActiveFlags, BaSsuIbltOprfTagOutput rightTagOutput,
        BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        BaSsuIbltProductionUnionProbeBackendConfig productionConfig = requireProductionBackend(
            unionProbeBackendConfig
        );
        offlineSchedule(params, elementByteLength, productionConfig);
        throw new UnsupportedOperationException(
            "production queue-peel execution requires a true two-party RPC/Core-COT endpoint; "
                + "the local secure-core bridge is not a production endpoint"
        );
    }

    static BaSsuIbltUpBaUpotOfflineSchedule offlineSchedule(
        BaSsuIbltBiUpsuParams params, int elementByteLength, BaSsuIbltProductionUnionProbeBackendConfig config) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (elementByteLength != config.getElementByteLength()) {
            throw new IllegalArgumentException("elementByteLength must match production config");
        }
        if (BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()) != config.getTagByteLength()
            || BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits()) != config.getCheckByteLength()) {
            throw new IllegalArgumentException("params tag/check shape must match production config");
        }
        long retryProbeCap = perRetryQueuePeelProbeCap(params);
        if (retryProbeCap > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("queue-peel retry probe cap exceeds UP-BA-UPOT schedule range");
        }
        return new BaSsuIbltUpBaUpotOfflineSchedule(
            params.getProfileId(), params.getRetryCount(), Math.toIntExact(retryProbeCap),
            params.getTableLength(), elementByteLength, config.getTagByteLength(), config.getCheckByteLength(),
            config.getAuthTagByteLength()
        );
    }

    static BaSsuIbltUpBaUpotPublicInput publicInput(
        BaSsuIbltUpBaUpotOfflineSchedule schedule, BaSsuIbltQueuePeelProbeContext context) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must be non-null");
        }
        return schedule.publicInput(context.getRetryIndex(), context.getBucketIndex(), context.getProbeOrdinal());
    }

    static BaSsuIbltProductionQueuePeelPartyLocalProbeInput partyLocalProbeInput(
        BaSsuIbltUpBaUpotOfflineSchedule schedule, BaSsuIbltQueuePeelProbeContext context,
        BaSsuIbltSecureCellView ownCellView, BaSsuIbltProductionUnionProbeLocalLayer ownLayer,
        BaSsuIbltUpBaUpotAuthMaterialProvider authProvider) {
        if (ownCellView == null) {
            throw new IllegalArgumentException("ownCellView must be non-null");
        }
        if (ownLayer == null) {
            throw new IllegalArgumentException("ownLayer must be non-null");
        }
        if (authProvider == null) {
            throw new IllegalArgumentException("authProvider must be non-null");
        }
        BaSsuIbltUpBaUpotPublicInput publicInput = publicInput(schedule, context);
        byte[] auth = authProvider.authMaterial(publicInput, ownLayer, ownCellView);
        BaSsuIbltUpBaUpotLocalInput ownLocalInput = BaSsuIbltUpBaUpotLocalInput.fromCellView(
            publicInput, ownCellView, auth
        );
        return BaSsuIbltProductionQueuePeelPartyLocalProbeInput.of(
            context, publicInput, ownLocalInput, ownLayer
        );
    }

    static BaSsuIbltProductionUnionProbeOutput executeProbe(
        BaSsuIbltUpBaUpotOfflineSchedule schedule,
        BaSsuIbltProductionQueuePeelPartyLocalProbeInput anchorInput,
        BaSsuIbltProductionQueuePeelPartyLocalProbeInput shadowInput,
        BaSsuIbltProductionUnionProbeBackendConfig config) {
        if (schedule == null || anchorInput == null || shadowInput == null || config == null) {
            throw new IllegalArgumentException("probe inputs and config must be non-null");
        }
        BaSsuIbltUpBaUpotPublicInput publicInput = anchorInput.getPublicInput();
        schedule.validate(publicInput);
        if (!samePublicInput(publicInput, shadowInput.getPublicInput())) {
            throw new IllegalArgumentException("party-local probe inputs must share one public input");
        }
        if (anchorInput.getOwnLayer() != BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR
            || shadowInput.getOwnLayer() != BaSsuIbltProductionUnionProbeLocalLayer.SHADOW) {
            throw new IllegalArgumentException("probe layers must be anchor/shadow ordered");
        }
        int cotNumPerProbe = config.getCotNumPerProbe();
        CotSenderOutput cotSenderOutput = CotSenderOutput.createRandom(
            cotNumPerProbe, BlockUtils.randomBlock(SECURE_RANDOM), SECURE_RANDOM
        );
        BaSsuIbltUpBaUpotMaskedProbeRows rows = BaSsuIbltUpBaUpotBucketProbeGadget.encodeRows(
            schedule, publicInput, anchorInput.getOwnLocalInput(), cotSenderOutput, 0, cotNumPerProbe
        );
        boolean[] choices = BaSsuIbltUpBaUpotBucketProbeGadget.receiverChoices(
            shadowInput.getOwnLocalInput(), cotNumPerProbe
        );
        CotReceiverOutput cotReceiverOutput = cotReceiverOutput(cotSenderOutput, choices);
        return BaSsuIbltUpBaUpotBucketProbeGadget.decodeSelectedRow(
            schedule, publicInput, shadowInput.getOwnLocalInput(), rows, cotReceiverOutput, 0, cotNumPerProbe
        );
    }

    static BaSsuIbltProductionUnionProbeBackendConfig requireProductionBackend(
        BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        BaSsuIbltProductionUnionProbeBackendConfig productionConfig = requireExactProductionBackend(
            unionProbeBackendConfig
        );
        if (!productionConfig.isQueuePeelProductionReady()) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires a production-ready backend; "
                    + productionConfig.getUnionProbeBackendName() + " is fail-closed: "
                    + productionConfig.getProductionReadinessReason()
            );
        }
        return productionConfig;
    }

    static BaSsuIbltProductionUnionProbeBackendConfig requireExactProductionBackend(
        BaSsuIbltUnionProbeBackendConfig unionProbeBackendConfig) {
        if (unionProbeBackendConfig == null) {
            throw new IllegalArgumentException("unionProbeBackendConfig must be non-null");
        }
        if (unionProbeBackendConfig.getClass() != BaSsuIbltProductionUnionProbeBackendConfig.class) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires the exact trusted production union-probe backend type; "
                    + unionProbeBackendConfig.getUnionProbeBackendName() + " is not trusted"
            );
        }
        BaSsuIbltProductionUnionProbeBackendConfig productionConfig =
            (BaSsuIbltProductionUnionProbeBackendConfig) unionProbeBackendConfig;
        if (!productionConfig.isSpecializedBucketProbe()) {
            throw new IllegalArgumentException(
                "production queue-peel secure core requires a specialized bucket-probe backend"
            );
        }
        return productionConfig;
    }

    private static long perRetryQueuePeelProbeCap(BaSsuIbltBiUpsuParams params) {
        return Math.addExact(
            params.getTableLength(),
            Math.multiplyExact((long) params.getDegree(), Math.addExact(params.getNLarge(), params.getNShadow()))
        );
    }

    private static CotReceiverOutput cotReceiverOutput(CotSenderOutput cotSenderOutput, boolean[] choices) {
        byte[][] rbArray = new byte[choices.length][];
        for (int index = 0; index < choices.length; index++) {
            byte[] rb = choices[index] ? cotSenderOutput.getR1(index) : cotSenderOutput.getR0(index);
            rbArray[index] = Arrays.copyOf(rb, rb.length);
        }
        return CotReceiverOutput.create(choices, rbArray);
    }

    private static boolean samePublicInput(BaSsuIbltUpBaUpotPublicInput left,
                                           BaSsuIbltUpBaUpotPublicInput right) {
        return right != null
            && left.getProfileId().equals(right.getProfileId())
            && left.getRetryId() == right.getRetryId()
            && left.getBucketIndex() == right.getBucketIndex()
            && left.getProbeOrdinal() == right.getProbeOrdinal()
            && left.getElementByteLength() == right.getElementByteLength()
            && left.getTagBitLength() == right.getTagBitLength()
            && left.getCheckBitLength() == right.getCheckBitLength()
            && left.getAuthTagBitLength() == right.getAuthTagBitLength();
    }
}
