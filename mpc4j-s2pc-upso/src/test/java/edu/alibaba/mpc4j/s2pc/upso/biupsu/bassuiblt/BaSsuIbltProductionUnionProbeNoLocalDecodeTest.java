package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * P36 no-local-decode regression tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeNoLocalDecodeTest {

    @Test
    public void testMainCodecHasNoRemoteOpenOrDecodeMethod() {
        for (Method method : BaSsuIbltProductionUnionProbeCodec.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("decode"));
            Assert.assertFalse(name.equals("open"));
            Assert.assertFalse(name.contains("parseplaintext"));
        }
        for (Class<?> declaredClass : BaSsuIbltProductionUnionProbeCodec.class.getDeclaredClasses()) {
            String name = declaredClass.getSimpleName().toLowerCase();
            Assert.assertFalse(name.contains("decoded"));
            Assert.assertFalse(name.contains("plaintext"));
        }
    }

    @Test
    public void testProductionReceiverFailsClosedInsteadOfOpeningRemoteCapsule() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 1);
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(config, seed, schedule);
        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, seed, schedule);
        BaSsuIbltUpBaUpotPublicInput publicInput = BaSsuIbltUpBaUpotApiTest.publicInput(0);
        sender.init(1);
        receiver.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(publicInput, BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 1L));
        Exception abort = Assert.assertThrows(Exception.class, () ->
            receiver.validateCapsuleAndFailClosed(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput), capsule));
        Assert.assertTrue(abort.getMessage().contains("remote-state-hiding UP-BA-UPOT"));
    }
}
