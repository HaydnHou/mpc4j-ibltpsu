package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * BA-SSU-IBLT bi-output UPSU factory.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltBiUpsuFactory {
    /**
     * private constructor.
     */
    private BaSsuIbltBiUpsuFactory() {
        // empty
    }

    /**
     * Creates a default config.
     *
     * @return default config.
     */
    public static BaSsuIbltBiUpsuConfig createDefaultConfig() {
        return new BaSsuIbltBiUpsuConfig.Builder().build();
    }

    public static boolean isProductionReady(BaSsuIbltBiUpsuConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        return config.isProductionReady();
    }

    public static String productionReadinessReason(BaSsuIbltBiUpsuConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        return config.getProductionReadinessReason();
    }

    /**
     * Creates a sender.
     *
     * <p>The current endpoint is a plain reference wrapper for API and correctness tests. It is not a secure network
     * realization of BA-SSU-IBLT; use {@link BaSsuIbltTwoPartyBenchmark} for the costed BA-UPOT path.</p>
     *
     * @param senderRpc sender RPC.
     * @param receiverParty receiver party.
     * @param config config.
     * @return sender.
     */
    public static BaSsuIbltBiUpsuSender createSender(Rpc senderRpc, Party receiverParty,
                                                     BaSsuIbltBiUpsuConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        return new BaSsuIbltBiUpsuSender(senderRpc, receiverParty, config);
    }

    /**
     * Creates a receiver.
     *
     * <p>The current endpoint is a plain reference wrapper for API and correctness tests. It is not a secure network
     * realization of BA-SSU-IBLT; use {@link BaSsuIbltTwoPartyBenchmark} for the costed BA-UPOT path.</p>
     *
     * @param receiverRpc receiver RPC.
     * @param senderParty sender party.
     * @param config config.
     * @return receiver.
     */
    public static BaSsuIbltBiUpsuReceiver createReceiver(Rpc receiverRpc, Party senderParty,
                                                         BaSsuIbltBiUpsuConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        return new BaSsuIbltBiUpsuReceiver(receiverRpc, senderParty, config);
    }

    /**
     * Runs the plain reference protocol.
     *
     * @param leftSet left input.
     * @param rightSet right input.
     * @param elementByteLength element byte length.
     * @param config config.
     * @return plain result.
     */
    public static BaSsuIbltPlainResult runPlainReference(Set<ByteBuffer> leftSet, Set<ByteBuffer> rightSet,
                                                         int elementByteLength,
                                                         BaSsuIbltBiUpsuConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        if (config.getProtocolMode() == BaSsuIbltProtocolMode.SECURE_SEMI_HONEST) {
            throw new IllegalArgumentException("secure mode must not use the plain reference runner");
        }
        BaSsuIbltBiUpsuParams params = config.createParams(leftSet.size(), rightSet.size());
        if (config.getEvaluatorMode() == BaUpotBucketEvaluatorMode.CASE_GATE) {
            return BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
                leftSet, rightSet, elementByteLength, params, new BaUpotCaseGateEvaluator()
            );
        } else if (config.getEvaluatorMode() == BaUpotBucketEvaluatorMode.CASE_GATE_WIRE_MASKED) {
            BaUpotConfig upotConfig = new BaUpotConfig.Builder()
                .setElementByteLength(elementByteLength)
                .setCheckBits(params.getCheckBits())
                .setTagBits(params.getTagBits())
                .build();
            BaUpotCaseGateWireMaskedPayloadTransducer transducer =
                BaUpotCaseGateWireMaskedPayloadTransducer.fromConfig(
                    upotConfig, new byte[]{0x42, 0x41, 0x2D, 0x53, 0x53, 0x55}
                );
            return BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
                leftSet, rightSet, elementByteLength, params, transducer
            );
        } else if (config.isCollectTrace()) {
            return BaSsuIbltPlainProtocol.runBiOutputWithTrace(leftSet, rightSet, elementByteLength, params);
        } else {
            return BaSsuIbltPlainProtocol.runBiOutput(leftSet, rightSet, elementByteLength, params);
        }
    }
}
