package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P37 production queue-peel leakage gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltSecureProtocolProductionQueuePeelLeakageTest {

    @Test
    public void testProductionAdapterIsNotPublicProtocolSurface() {
        int modifiers = BaSsuIbltProductionQueuePeelAdapter.class.getModifiers();
        Assert.assertFalse(Modifier.isPublic(modifiers));
        Assert.assertFalse(Modifier.isProtected(modifiers));
        Assert.assertFalse(Modifier.isPrivate(modifiers));
    }

    @Test
    public void testProductionAdapterApiHasNoReferenceOrDebugSurface() {
        for (Method method : BaSsuIbltProductionQueuePeelAdapter.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("reference"));
            Assert.assertFalse(name.contains("debug"));
            Assert.assertFalse(name.contains("trace"));
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("membership"));
        }
    }

    @Test
    public void testPartyLocalProbeInputIsNotPublicProtocolSurface() {
        int modifiers = BaSsuIbltProductionQueuePeelPartyLocalProbeInput.class.getModifiers();
        Assert.assertFalse(Modifier.isPublic(modifiers));
        for (Method method : BaSsuIbltProductionQueuePeelPartyLocalProbeInput.class.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("check"));
            Assert.assertFalse(name.contains("auth"));
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("membership"));
            Assert.assertFalse(name.contains("choice"));
        }
    }

    @Test
    public void testLocalLayerHasNoCombinedBucketSelector() {
        for (Method method : BaSsuIbltProductionUnionProbeLocalLayer.class.getDeclaredMethods()) {
            Assert.assertNotEquals(BaSsuIbltSecureBucketInput.class, method.getReturnType());
            for (Class<?> parameterType : method.getParameterTypes()) {
                Assert.assertNotEquals(BaSsuIbltSecureBucketInput.class, parameterType);
            }
        }
    }

    @Test
    public void testP50ProductionBridgeSourcesDoNotUseCombinedBucketOrCellViewOpeners() throws Exception {
        assertSourceDoesNotContain(
            "BaSsuIbltProductionQueuePeelAdapter.java", "BaSsuIbltSecureBucketInput", ".toCellView("
        );
        assertSourceDoesNotContain(
            "BaSsuIbltProductionQueuePeelPartyLocalProbeInput.java",
            "BaSsuIbltSecureBucketInput", ".toCellView(", "anchorLocalInput", "shadowLocalInput"
        );
        assertSourceDoesNotContain(
            "BaSsuIbltProductionUnionProbeLocalLayer.java", "BaSsuIbltSecureBucketInput", "select("
        );
    }

    @Test
    public void testTrustedBackendConfigCannotBeSubclassedIntoFakeReadyBackend() {
        Assert.assertTrue(Modifier.isFinal(BaSsuIbltProductionUnionProbeBackendConfig.class.getModifiers()));
    }

    private static void assertSourceDoesNotContain(String fileName, String... forbidden) throws Exception {
        String source = Files.readString(sourcePath(fileName));
        for (String token : forbidden) {
            Assert.assertFalse("source must not contain " + token, source.contains(token));
        }
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
