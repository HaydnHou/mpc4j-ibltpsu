package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P43 auth failure tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotAuthFailureTest {

    @Test
    public void testTamperedCapsuleFailsClosedBeforeOutput() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 1);
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(config, seed, schedule);
        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, seed, schedule);
        BaSsuIbltUpBaUpotPublicInput publicInput = BaSsuIbltUpBaUpotApiTest.publicInput(0);
        sender.init(1);
        receiver.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(publicInput, BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 1L));
        byte[] tampered = capsule.getEncoded();
        tampered[tampered.length - 1] ^= 0x01;
        BaSsuIbltProductionUnionProbeCapsule tamperedCapsule =
            new BaSsuIbltProductionUnionProbeCapsule(capsule.getBucketIndex(), tampered, config.capsuleByteLength());
        Exception abort = Assert.assertThrows(Exception.class, () -> receiver.validateCapsuleAndFailClosed(
            publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput), tamperedCapsule
        ));
        Assert.assertTrue(abort.getMessage().contains("auth"));
    }
}
