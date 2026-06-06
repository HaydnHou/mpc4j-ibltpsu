package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P51 RPC shell leakage-surface tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotRpcNoLocalDecodeTest {

    @Test
    public void testRpcShellDoesNotUseReferenceCodecOrRemoteOpeners() throws IOException {
        assertNoLocalDecode("BaSsuIbltRpcUpBaUpotSender.java");
        assertNoLocalDecode("BaSsuIbltRpcUpBaUpotReceiver.java");
    }

    @Test
    public void testBucketProbeGadgetDoesNotRebuildReferenceCellView() throws IOException {
        String source = Files.readString(sourcePath("BaSsuIbltUpBaUpotBucketProbeGadget.java"));
        Assert.assertFalse(source.contains(".toCellView("));
        Assert.assertFalse(source.contains("BaSsuIbltSecureCellView"));
    }

    @Test
    public void testRpcProbeTransportHasNoUnusedPlacementSeedParameter() throws IOException {
        String sender = Files.readString(sourcePath("BaSsuIbltRpcUpBaUpotSender.java"));
        String receiver = Files.readString(sourcePath("BaSsuIbltRpcUpBaUpotReceiver.java"));
        String endpoint = Files.readString(sourcePath("BaSsuIbltQueuePeelEndpoint.java"));
        for (String source : new String[]{sender, receiver}) {
            Assert.assertFalse(source.contains("byte[] seed"));
            Assert.assertFalse(source.contains("publicPlaceSeed"));
            Assert.assertFalse(source.contains("getPublicPlaceSeed"));
        }
        Assert.assertFalse(endpoint.contains("params.getPublicPlaceSeed()"));
    }

    @Test
    public void testResultCodecRemainsPackagePrivateNonAuthBoundary() throws ReflectiveOperationException {
        int classModifiers = BaSsuIbltProductionUnionProbeResultCodec.class.getModifiers();
        Assert.assertFalse(Modifier.isPublic(classModifiers));
        Method decode = BaSsuIbltProductionUnionProbeResultCodec.class.getDeclaredMethod(
            "decode", int.class, byte[].class
        );
        Assert.assertFalse(Modifier.isPublic(decode.getModifiers()));
    }

    private static void assertNoLocalDecode(String fileName) throws IOException {
        String source = Files.readString(sourcePath(fileName));
        Assert.assertFalse(source.contains("BaSsuIbltProductionUnionProbeReferenceCodec"));
        Assert.assertFalse(source.contains(".open("));
        Assert.assertFalse(source.contains("anchorLocalInput"));
        Assert.assertFalse(source.contains("shadowLocalInput"));
        Assert.assertFalse(source.contains("getAnchor()"));
        Assert.assertFalse(source.contains("getShadow()"));
        Assert.assertFalse(source.contains("BaSsuIbltSecureBucketInput"));
    }

    private static Path sourcePath(String fileName) {
        Path modulePath = Path.of(
            "src/main/java/edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt", fileName
        );
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        return Path.of(
            "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt", fileName
        );
    }
}
