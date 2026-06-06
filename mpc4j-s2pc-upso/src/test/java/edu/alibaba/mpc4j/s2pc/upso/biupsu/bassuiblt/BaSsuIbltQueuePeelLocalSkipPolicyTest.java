package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * P59 public-version-only queue-peel skip policy tests.
 *
 * @author donghai hou
 * @date 2026/06/06
 */
public class BaSsuIbltQueuePeelLocalSkipPolicyTest {

    @Test
    public void testNeverProbedBucketStillProbesRemote() {
        Assert.assertEquals(
            BaSsuIbltQueuePeelLocalSkipPolicy.Decision.PROBE_REMOTE,
            BaSsuIbltQueuePeelLocalSkipPolicy.decide(
                7, 0, BaSsuIbltQueuePeelLocalSkipPolicy.NEVER_PROBED, Set.of()
            )
        );
    }

    @Test
    public void testDuplicateSameBatchBucketIsSkipped() {
        HashSet<Integer> scheduled = new HashSet<>();
        scheduled.add(3);
        Assert.assertEquals(
            BaSsuIbltQueuePeelLocalSkipPolicy.Decision.SKIP_DUPLICATE_PUBLIC,
            BaSsuIbltQueuePeelLocalSkipPolicy.decide(3, 2, 1, scheduled)
        );
    }

    @Test
    public void testUnchangedPublicReProbeIsSkipped() {
        Assert.assertEquals(
            BaSsuIbltQueuePeelLocalSkipPolicy.Decision.SKIP_UNCHANGED_PUBLIC,
            BaSsuIbltQueuePeelLocalSkipPolicy.decide(5, 4, 4, Set.of())
        );
    }

    @Test
    public void testPublicVersionIncreaseForcesRemoteProbe() {
        Assert.assertEquals(
            BaSsuIbltQueuePeelLocalSkipPolicy.Decision.PROBE_REMOTE,
            BaSsuIbltQueuePeelLocalSkipPolicy.decide(5, 5, 4, Set.of())
        );
    }

    @Test
    public void testPolicyDoesNotReferencePrivateLocalState() throws IOException {
        String source = Files.readString(sourcePath("BaSsuIbltQueuePeelLocalSkipPolicy.java"));
        Assert.assertFalse(source.contains("BaSsuIbltSecureCellView"));
        Assert.assertFalse(source.contains("BaSsuIbltUpBaUpotLocalInput"));
        Assert.assertFalse(source.contains("getAnchorCellView"));
        Assert.assertFalse(source.contains("getShadowCellView"));
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
