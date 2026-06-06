package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * P50 party-local queue-peel input leakage surface tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltProductionQueuePeelLocalInputLeakageTest {
    /**
     * package path.
     */
    private static final String PACKAGE_PATH = "edu/alibaba/mpc4j/s2pc/upso/biupsu/bassuiblt/";
    /**
     * expected bridge field names.
     */
    private static final Set<String> EXPECTED_BRIDGE_FIELDS = Set.of(
        "context", "publicInput", "ownLocalInput", "ownLayer"
    );
    /**
     * forbidden bridge method-name fragments.
     */
    private static final Set<String> FORBIDDEN_BRIDGE_METHOD_FRAGMENTS = Set.of(
        "tag", "check", "auth", "membership", "choice", "case", "bucketinput", "cellview"
    );

    @Test
    public void testPartyLocalProbeInputShapeStaysMinimal() {
        Class<BaSsuIbltProductionQueuePeelPartyLocalProbeInput> clazz =
            BaSsuIbltProductionQueuePeelPartyLocalProbeInput.class;
        Assert.assertFalse(Modifier.isPublic(clazz.getModifiers()));
        Assert.assertTrue(Modifier.isFinal(clazz.getModifiers()));

        Set<String> fieldNames = Arrays.stream(clazz.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());
        Assert.assertEquals(EXPECTED_BRIDGE_FIELDS, fieldNames);
        for (Field field : clazz.getDeclaredFields()) {
            Assert.assertTrue(Modifier.isPrivate(field.getModifiers()));
            Assert.assertTrue(Modifier.isFinal(field.getModifiers()));
            Assert.assertFalse(field.getType().isArray());
            Assert.assertNotEquals(BaSsuIbltSecureBucketInput.class, field.getType());
            Assert.assertNotEquals(BaSsuIbltSecureCellView.class, field.getType());
        }
    }

    @Test
    public void testPartyLocalProbeInputDoesNotExposeRawMaterialAccessors() {
        for (Method method : BaSsuIbltProductionQueuePeelPartyLocalProbeInput.class.getDeclaredMethods()) {
            String lowerName = method.getName().toLowerCase();
            for (String forbidden : FORBIDDEN_BRIDGE_METHOD_FRAGMENTS) {
                Assert.assertFalse(method.getName() + " must not expose " + forbidden,
                    lowerName.contains(forbidden));
            }
            Assert.assertFalse(Modifier.isPublic(method.getModifiers()));
        }
    }

    @Test
    public void testProductionBridgeSourceDoesNotUseCombinedBucketOrRawMaterialCopies() throws IOException {
        String bridgeSource = codeWithoutComments(source("BaSsuIbltProductionQueuePeelPartyLocalProbeInput.java"));
        Assert.assertFalse(bridgeSource.contains("BaSsuIbltSecureBucketInput"));
        Assert.assertFalse(bridgeSource.contains("byte[]"));
        Assert.assertFalse(bridgeSource.contains("tagCopy"));
        Assert.assertFalse(bridgeSource.contains("checkCopy"));
        Assert.assertFalse(bridgeSource.contains("authCopy"));
        Assert.assertFalse(bridgeSource.contains("elementCopy"));

        String adapterSource = codeWithoutComments(source("BaSsuIbltProductionQueuePeelAdapter.java"));
        Assert.assertFalse(adapterSource.contains("BaSsuIbltSecureBucketInput"));
        Assert.assertFalse(adapterSource.contains("toCellView("));
        Assert.assertFalse(adapterSource.contains("elementCopy("));
        Assert.assertFalse(adapterSource.contains("tagCopy("));
        Assert.assertFalse(adapterSource.contains("checkCopy("));
        Assert.assertFalse(adapterSource.contains("authCopy("));
        Assert.assertFalse(adapterSource.contains("getKeyXor("));
        Assert.assertFalse(adapterSource.contains("getTagXor("));
        Assert.assertFalse(adapterSource.contains("getCheckXor("));
        Assert.assertFalse(adapterSource.contains("getKeyXorReference("));
        Assert.assertFalse(adapterSource.contains("getTagXorReference("));
        Assert.assertFalse(adapterSource.contains("getCheckXorReference("));
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

    private static String codeWithoutComments(String source) {
        String noBlockComments = source.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlockComments.replaceAll("(?m)//.*$", "");
    }
}
