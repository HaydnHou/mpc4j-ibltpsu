package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * P56 production no-forbidden-dependency tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltProductionNoForbiddenDependencyTest {
    /**
     * package path.
     */
    private static final String PACKAGE_PATH = "edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt/";
    /**
     * forbidden production-core references.
     */
    private static final List<String> FORBIDDEN_PRODUCTION_REFERENCES = List.of(
        "BaSsuIbltProductionUnionProbeReferenceCodec",
        "BaSsuIbltSecureBucketInput",
        "runQueuePeelAlignedReference",
        "referenceTag(",
        "referenceCheck(",
        "runPlainReference",
        "BaSsuIbltFixedLayerEndpoint"
    );
    /**
     * Files that are part of the queue-peel production chain and should not contain reference helpers anywhere.
     */
    private static final List<String> PRODUCTION_CHAIN_FILES = List.of(
        "BaSsuIbltProductionQueuePeelAdapter.java",
        "BaSsuIbltProductionQueuePeelPartyLocalProbeInput.java",
        "BaSsuIbltQueuePeelEndpoint.java",
        "BaSsuIbltQueuePeelProbeContext.java",
        "BaSsuIbltBiUpsuProductionGate.java",
        "BaSsuIbltSecureLayerBuilder.java",
        "BaSsuIbltRpcUpBaUpotSender.java",
        "BaSsuIbltRpcUpBaUpotReceiver.java",
        "BaSsuIbltUpBaUpotBucketProbeGadget.java",
        "BaSsuIbltUpBaUpotFunctionality.java",
        "BaSsuIbltUpBaUpotLocalInput.java",
        "BaSsuIbltUpBaUpotMaskedProbeRows.java",
        "BaSsuIbltUpBaUpotOfflineSchedule.java",
        "BaSsuIbltUpBaUpotProbeRow.java",
        "BaSsuIbltUpBaUpotPublicInput.java",
        "BaSsuIbltProductionUnionProbeBackendConfig.java",
        "BaSsuIbltProductionUnionProbeOutput.java",
        "BaSsuIbltProductionUnionProbeResultCodec.java",
        "BaSsuIbltSourceAgnosticFrontierDelete.java",
        "DuplicateSafeUnionListCoalescer.java"
    );
    /**
     * Files that carry public production readiness / benchmark claim wording.
     */
    private static final List<String> PRODUCTION_CLAIM_SURFACE_FILES = List.of(
        "BaSsuIbltBiUpsuProductionGate.java",
        "BaSsuIbltSecureFairBenchmark.java",
        "BaSsuIbltMeasuredProductionBenchmark.java",
        "BaSsuIbltCandidateEndpointBenchmark.java",
        "BaSsuIbltQueuePeelBenchmark.java",
        "BaSsuIbltProductionUnionProbeBackendConfig.java"
    );
    /**
     * Superseded readiness wording that must not re-enter public production claims.
     */
    private static final List<String> FORBIDDEN_SUPERSEDED_CLAIM_WORDING = List.of(
        "P41-P44 production chain",
        "production union-probe BA-UPOT is not implemented",
        "true remote-state-hiding UP-BA-UPOT backend is still missing",
        "backend is still missing"
    );

    @Test
    public void testProductionCoreDoesNotDependOnReferenceHelpers() throws IOException {
        for (String fileName : PRODUCTION_CHAIN_FILES) {
            String source = codeWithoutComments(source(fileName));
            for (String forbidden : FORBIDDEN_PRODUCTION_REFERENCES) {
                Assert.assertFalse(fileName + " must not contain " + forbidden, source.contains(forbidden));
            }
        }
    }

    @Test
    public void testHistoricalReferenceHelpersStayOutsideProductionChainScan() throws IOException {
        Assert.assertTrue(codeWithoutComments(source("BaSsuIbltFixedLayerEndpoint.java")).contains(
            "class BaSsuIbltFixedLayerEndpoint"
        ));
        Assert.assertTrue(codeWithoutComments(source("BaSsuIbltSecureProtocol.java")).contains(
            "runQueuePeelAlignedReference"
        ));
        Assert.assertFalse(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltFixedLayerEndpoint.java"));
        Assert.assertFalse(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltSecureProtocol.java"));
        Assert.assertFalse(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltBiUpsuSender.java"));
        Assert.assertFalse(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltBiUpsuReceiver.java"));
        Assert.assertTrue(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltSecureLayerBuilder.java"));
        Assert.assertTrue(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltUpBaUpotOfflineSchedule.java"));
        Assert.assertTrue(PRODUCTION_CHAIN_FILES.contains("BaSsuIbltQueuePeelProbeContext.java"));
    }

    @Test
    public void testProductionChainFileListContainsCurrentEndpointImplementationClasses() {
        for (String fileName : List.of(
            "BaSsuIbltQueuePeelEndpoint.java",
            "BaSsuIbltProductionQueuePeelAdapter.java",
            "BaSsuIbltProductionQueuePeelPartyLocalProbeInput.java",
            "BaSsuIbltSecureLayerBuilder.java",
            "BaSsuIbltQueuePeelProbeContext.java",
            "BaSsuIbltUpBaUpotOfflineSchedule.java",
            "BaSsuIbltRpcUpBaUpotSender.java",
            "BaSsuIbltRpcUpBaUpotReceiver.java",
            "BaSsuIbltUpBaUpotBucketProbeGadget.java",
            "BaSsuIbltProductionUnionProbeResultCodec.java"
        )) {
            Assert.assertTrue(fileName + " must stay in production-chain forbidden dependency scan",
                PRODUCTION_CHAIN_FILES.contains(fileName));
        }
    }

    @Test
    public void testProductionClaimSurfaceDoesNotUseSupersededReadinessWording() throws IOException {
        for (String fileName : PRODUCTION_CLAIM_SURFACE_FILES) {
            String source = source(fileName);
            for (String forbidden : FORBIDDEN_SUPERSEDED_CLAIM_WORDING) {
                Assert.assertFalse(fileName + " must not contain stale claim wording: " + forbidden,
                    source.contains(forbidden));
            }
        }
        String fairBenchmark = source("BaSsuIbltSecureFairBenchmark.java");
        Assert.assertTrue(fairBenchmark.contains("RPC/Core-COT candidate endpoint"));
        Assert.assertTrue(fairBenchmark.contains("not production-certified"));
        Assert.assertTrue(fairBenchmark.contains("measured-production wired"));
    }

    @Test
    public void testQueuePeelProductionEndpointCannotDelegateToCandidateEndpoint() throws IOException {
        String source = codeWithoutComments(source("BaSsuIbltQueuePeelEndpoint.java"));
        String productionEndpointShort = methodBody(
            source,
            "static BiUpsuPartyOutput runProductionEndpoint(\n"
                + "        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,\n"
                + "        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,\n"
                + "        boolean parallel) throws MpcAbortException",
            "\n    static BiUpsuPartyOutput runProductionEndpoint(\n"
                + "        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,\n"
                + "        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,\n"
                + "        boolean parallel, PhaseRecorder phaseRecorder) throws MpcAbortException"
        );
        Assert.assertTrue(productionEndpointShort.contains("return runProductionEndpoint("));
        Assert.assertTrue(productionEndpointShort.contains("parallel, null"));
        Assert.assertFalse(productionEndpointShort.contains("runCandidateEndpoint("));
        Assert.assertFalse(productionEndpointShort.contains("runEndpointCore("));

        String productionEndpoint = methodBody(
            source,
            "static BiUpsuPartyOutput runProductionEndpoint(\n"
                + "        Rpc rpc, Party otherParty, boolean localIsProtocolSender, Set<ByteBuffer> localElementSet,\n"
                + "        int senderCapacity, int receiverCapacity, int elementByteLength, BaSsuIbltBiUpsuConfig config, int taskId,\n"
                + "        boolean parallel, PhaseRecorder phaseRecorder) throws MpcAbortException",
            "\n    private static BiUpsuPartyOutput runEndpointCore"
        );
        Assert.assertTrue(productionEndpoint.contains("BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config);"));
        Assert.assertTrue(productionEndpoint.contains("return runEndpointCore("));
        Assert.assertFalse(productionEndpoint.contains("runCandidateEndpoint("));
        for (String forbidden : FORBIDDEN_PRODUCTION_REFERENCES) {
            Assert.assertFalse("production endpoint must not contain " + forbidden, productionEndpoint.contains(forbidden));
        }
    }

    @Test
    public void testQueuePeelEndpointCoreStaysOnRpcUpBaUpotPath() throws IOException {
        String source = codeWithoutComments(source("BaSsuIbltQueuePeelEndpoint.java"));
        String core = methodBody(
            source,
            "private static BiUpsuPartyOutput runEndpointCore",
            "\n    private static BaSsuIbltOprfTagOutput generateTags"
        );
        Assert.assertTrue(core.contains("requireExactProductionBackend(config.getUnionProbeBackendConfig())"));
        Assert.assertTrue(core.contains("offlineSchedule(params, elementByteLength, backendConfig)"));
        for (String forbidden : FORBIDDEN_PRODUCTION_REFERENCES) {
            Assert.assertFalse("endpoint core must not contain " + forbidden, core.contains(forbidden));
        }

        String anchor = methodBody(
            source,
            "private static BiUpsuPartyOutput runAnchorEndpoint",
            "\n    private static BiUpsuPartyOutput runShadowEndpoint"
        );
        String shadow = methodBody(
            source,
            "private static BiUpsuPartyOutput runShadowEndpoint",
            "\n    private static BiUpsuPartyOutput runLocalQueuePeel"
        );
        Assert.assertTrue(anchor.contains("new BaSsuIbltRpcUpBaUpotSender("));
        Assert.assertTrue(shadow.contains("new BaSsuIbltRpcUpBaUpotReceiver("));
        Assert.assertFalse(anchor.contains("BaSsuIbltProductionUnionProbeSender("));
        Assert.assertFalse(shadow.contains("BaSsuIbltProductionUnionProbeReceiver("));
    }

    @Test
    public void testProductionHardGateSettersAreNotPublicConfigurationApi() throws IOException {
        String source = source("BaSsuIbltProductionUnionProbeBackendConfig.java");
        String uncommented = codeWithoutComments(source);
        Assert.assertFalse(uncommented.contains("public Builder setProductionAuditPassed"));
        Assert.assertFalse(uncommented.contains("public Builder setNoReferenceFallbackCertificate"));
        Assert.assertFalse(uncommented.contains("public Builder setMeasuredEndpointWired"));
        Assert.assertTrue(uncommented.contains("Builder setProductionReadinessCertificate"));
    }

    @Test
    public void testOnlyRpcUnionProbeClassesImplementProductionUpBaUpotApi() throws IOException {
        String legacySender = codeWithoutComments(source("BaSsuIbltProductionUnionProbeSender.java"));
        String legacyReceiver = codeWithoutComments(source("BaSsuIbltProductionUnionProbeReceiver.java"));
        Assert.assertFalse(legacySender.contains("implements BaSsuIbltUnionProbeSender, BaSsuIbltUpBaUpotSender"));
        Assert.assertFalse(legacySender.contains("implements BaSsuIbltUpBaUpotSender"));
        Assert.assertFalse(legacyReceiver.contains("implements BaSsuIbltUnionProbeReceiver, BaSsuIbltUpBaUpotReceiver"));
        Assert.assertFalse(legacyReceiver.contains("implements BaSsuIbltUpBaUpotReceiver"));

        String rpcSender = codeWithoutComments(source("BaSsuIbltRpcUpBaUpotSender.java"));
        String rpcReceiver = codeWithoutComments(source("BaSsuIbltRpcUpBaUpotReceiver.java"));
        Assert.assertTrue(rpcSender.contains("implements BaSsuIbltUpBaUpotSender"));
        Assert.assertTrue(rpcReceiver.contains("implements BaSsuIbltUpBaUpotReceiver"));
    }

    @Test
    public void testSecureSenderPathUsesGateThenQueuePeelEndpointOnly() throws IOException {
        String source = source("BaSsuIbltBiUpsuSender.java");
        Assert.assertTrue(source.contains(
            "BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config);\n"
                + "            return runSecureSemiHonest(senderElementSet);"
        ));
        String method = methodBody(codeWithoutComments(source), "private BiUpsuPartyOutput runSecureSemiHonest");
        Assert.assertTrue(method.contains("BaSsuIbltQueuePeelEndpoint.runProductionEndpoint("));
        Assert.assertFalse(method.contains("runCandidateEndpoint("));
        for (String forbidden : FORBIDDEN_PRODUCTION_REFERENCES) {
            Assert.assertFalse("sender secure path must not contain " + forbidden, method.contains(forbidden));
        }
    }

    @Test
    public void testSecureReceiverPathUsesGateThenQueuePeelEndpointOnly() throws IOException {
        String source = source("BaSsuIbltBiUpsuReceiver.java");
        Assert.assertTrue(source.contains(
            "BaSsuIbltBiUpsuProductionGate.checkEndpointReady(config);\n"
                + "            return runSecureSemiHonest(receiverElementSet);"
        ));
        String method = methodBody(codeWithoutComments(source), "private BiUpsuPartyOutput runSecureSemiHonest");
        Assert.assertTrue(method.contains("BaSsuIbltQueuePeelEndpoint.runProductionEndpoint("));
        Assert.assertFalse(method.contains("runCandidateEndpoint("));
        for (String forbidden : FORBIDDEN_PRODUCTION_REFERENCES) {
            Assert.assertFalse("receiver secure path must not contain " + forbidden, method.contains(forbidden));
        }
    }

    private static String source(String fileName) throws IOException {
        Path modulePath = Paths.get("src/main/java").resolve(PACKAGE_PATH).resolve(fileName);
        if (Files.exists(modulePath)) {
            return Files.readString(modulePath);
        }
        Path rootPath = Paths.get("mpc4j-s2pc-upso/src/main/java").resolve(PACKAGE_PATH).resolve(fileName);
        if (Files.exists(rootPath)) {
            return Files.readString(rootPath);
        }
        throw new IOException("cannot locate source file: " + fileName);
    }

    private static String methodBody(String source, String signaturePrefix) {
        return methodBody(source, signaturePrefix, "\n    private int secureEndpointTaskId");
    }

    private static String methodBody(String source, String signaturePrefix, String endMarker) {
        int start = source.indexOf(signaturePrefix);
        Assert.assertTrue("method not found: " + signaturePrefix, start >= 0);
        int end = source.indexOf(endMarker, start);
        Assert.assertTrue("method end not found: " + signaturePrefix, end > start);
        return source.substring(start, end);
    }

    private static String codeWithoutComments(String source) {
        String noBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlockComments.replaceAll("(?m)//.*$", "");
    }
}
