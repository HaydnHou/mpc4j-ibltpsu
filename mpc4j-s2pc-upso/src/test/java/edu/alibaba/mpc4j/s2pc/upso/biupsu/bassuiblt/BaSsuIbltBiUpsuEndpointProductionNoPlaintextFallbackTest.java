package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * P53/P54 SECURE_SEMI_HONEST endpoint no-plaintext-fallback tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltBiUpsuEndpointProductionNoPlaintextFallbackTest {
    /**
     * package path.
     */
    private static final String PACKAGE_PATH = "edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt/";

    @Test
    public void testBuilderRejectsReferenceEndpointThenSecureMode() {
        BaSsuIbltBiUpsuConfig.Builder builder = new BaSsuIbltBiUpsuConfig.Builder()
            .setEnableFixedLayerReferenceEndpoint(true)
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST)
            .setScheduleShape(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED)
            .setOprfConfig(OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST))
            .setUnionProbeBackendConfig(new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build());
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class, builder::build);
        Assert.assertTrue(abort.getMessage().contains("fixed-layer reference endpoint"));
    }

    @Test
    public void testBuilderRejectsSecureModeThenReferenceEndpoint() {
        BaSsuIbltBiUpsuConfig.Builder builder = new BaSsuIbltBiUpsuConfig.Builder()
            .setProtocolMode(BaSsuIbltProtocolMode.SECURE_SEMI_HONEST);
        IllegalArgumentException abort = Assert.assertThrows(
            IllegalArgumentException.class, () -> builder.setEnableFixedLayerReferenceEndpoint(true)
        );
        Assert.assertTrue(abort.getMessage().contains("SECURE_SEMI_HONEST"));
    }

    @Test
    public void testSecureEndpointSourceCannotCallPlaintextFallback() throws IOException {
        assertSecureMethodNoPlaintextFallback("BaSsuIbltBiUpsuSender.java", "private BiUpsuPartyOutput runSecureSemiHonest");
        assertSecureMethodNoPlaintextFallback("BaSsuIbltBiUpsuReceiver.java", "private BiUpsuPartyOutput runSecureSemiHonest");
    }

    private static void assertSecureMethodNoPlaintextFallback(String fileName, String signaturePrefix)
        throws IOException {
        String source = codeWithoutComments(source(fileName));
        String method = methodBody(source, signaturePrefix);
        assertDirectProductionReturnShape(fileName, method);
        Assert.assertFalse(method.contains("BaSsuIbltFixedLayerEndpoint"));
        Assert.assertFalse(method.contains("runPlainReference"));
        Assert.assertFalse(method.contains("encodeOwnLayer("));
        Assert.assertFalse(method.contains("decodeAndPeel("));
        Assert.assertFalse(method.contains("sendOtherPartyPayload("));
        Assert.assertFalse(method.contains("receiveOtherPartyPayload("));
    }

    private static void assertDirectProductionReturnShape(String fileName, String method) {
        String normalized = normalize(method);
        if ("BaSsuIbltBiUpsuSender.java".equals(fileName)) {
            Assert.assertEquals(fileName + " secure method must stay as a direct production-endpoint return",
                normalize(senderSecureMethodShape()), normalized);
        } else if ("BaSsuIbltBiUpsuReceiver.java".equals(fileName)) {
            Assert.assertEquals(fileName + " secure method must stay as a direct production-endpoint return",
                normalize(receiverSecureMethodShape()), normalized);
        } else {
            Assert.fail("unexpected secure endpoint file: " + fileName);
        }
    }

    private static String senderSecureMethodShape() {
        return "private BiUpsuPartyOutput runSecureSemiHonest(Set<ByteBuffer> senderElementSet) "
            + "throws MpcAbortException {"
            + "if (senderElementSet == null) {"
            + "throw new IllegalArgumentException(\"senderElementSet must be non-null\");"
            + "}"
            + "MathPreconditions.checkPositiveInRangeClosed("
            + "\"senderElementSize\", senderElementSet.size(), maxSenderElementSize"
            + ");"
            + "Preconditions.checkArgument(receiverElementSize > 0);"
            + "extraInfo++;"
            + "return BaSsuIbltQueuePeelEndpoint.runProductionEndpoint("
            + "rpc, otherParty(), true, senderElementSet, maxSenderElementSize, receiverElementSize,"
            + "elementByteLength, config, secureEndpointTaskId(), parallel"
            + ");"
            + "}";
    }

    private static String receiverSecureMethodShape() {
        return "private BiUpsuPartyOutput runSecureSemiHonest(Set<ByteBuffer> receiverElementSet) "
            + "throws MpcAbortException {"
            + "if (receiverElementSet == null) {"
            + "throw new IllegalArgumentException(\"receiverElementSet must be non-null\");"
            + "}"
            + "MathPreconditions.checkPositiveInRangeClosed("
            + "\"receiverElementSize\", receiverElementSet.size(), receiverElementSize"
            + ");"
            + "extraInfo++;"
            + "return BaSsuIbltQueuePeelEndpoint.runProductionEndpoint("
            + "rpc, otherParty(), false, receiverElementSet, maxSenderElementSize, receiverElementSize,"
            + "elementByteLength, config, secureEndpointTaskId(), parallel"
            + ");"
            + "}";
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "");
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
        int start = source.indexOf(signaturePrefix);
        Assert.assertTrue("method not found: " + signaturePrefix, start >= 0);
        int end = source.indexOf("\n    private int secureEndpointTaskId", start);
        Assert.assertTrue("method end not found: " + signaturePrefix, end > start);
        return source.substring(start, end);
    }

    private static String codeWithoutComments(String source) {
        String noBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlockComments.replaceAll("(?m)//.*$", "");
    }
}
