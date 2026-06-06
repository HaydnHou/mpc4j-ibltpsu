package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P43 wrong public bucket index tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotWrongBucketIndexTest {

    @Test
    public void testWrongBucketIndexRejected() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 2);
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(config, seed, schedule);
        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, seed, schedule);
        BaSsuIbltUpBaUpotPublicInput bucketZero = BaSsuIbltUpBaUpotApiTest.publicInput(0);
        BaSsuIbltUpBaUpotPublicInput bucketOne = BaSsuIbltUpBaUpotApiTest.publicInput(1);
        sender.init(1);
        receiver.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(bucketZero, BaSsuIbltUpBaUpotApiTest.singletonLocalInput(bucketZero, 1L));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            receiver.validateCapsuleAndFailClosed(bucketOne, BaSsuIbltUpBaUpotLocalInput.empty(bucketOne), capsule));
    }

    @Test
    public void testForgedBucketIndexWithOldEncodedAuthRejected() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 2);
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(config, seed, schedule);
        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, seed, schedule);
        BaSsuIbltUpBaUpotPublicInput bucketZero = BaSsuIbltUpBaUpotApiTest.publicInput(0);
        BaSsuIbltUpBaUpotPublicInput bucketOne = BaSsuIbltUpBaUpotApiTest.publicInput(1);
        sender.init(1);
        receiver.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(bucketZero, BaSsuIbltUpBaUpotApiTest.singletonLocalInput(bucketZero, 1L));
        BaSsuIbltProductionUnionProbeCapsule forgedBucketIndex =
            new BaSsuIbltProductionUnionProbeCapsule(1, capsule.getEncoded(), config.capsuleByteLength());
        Exception abort = Assert.assertThrows(Exception.class, () ->
            receiver.validateCapsuleAndFailClosed(bucketOne, BaSsuIbltUpBaUpotLocalInput.empty(bucketOne), forgedBucketIndex));
        Assert.assertTrue(abort.getMessage().contains("auth"));
    }
}
