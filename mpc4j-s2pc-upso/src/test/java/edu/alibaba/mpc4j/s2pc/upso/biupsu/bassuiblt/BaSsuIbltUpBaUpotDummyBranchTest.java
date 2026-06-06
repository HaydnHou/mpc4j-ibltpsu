package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P43 dummy/non-output branch tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotDummyBranchTest {

    @Test
    public void testDummyEmptyBranchStillAuthenticatesAsFixedCapsule() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 1);
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(
            config, seed, schedule
        );
        BaSsuIbltProductionUnionProbeCodec codec = new BaSsuIbltProductionUnionProbeCodec(config, seed);
        BaSsuIbltUpBaUpotPublicInput publicInput = BaSsuIbltUpBaUpotApiTest.publicInput(0);
        sender.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput));
        Assert.assertEquals(config.capsuleByteLength(), capsule.getEncoded().length);
        codec.validate(schedule, publicInput, capsule);
    }
}
