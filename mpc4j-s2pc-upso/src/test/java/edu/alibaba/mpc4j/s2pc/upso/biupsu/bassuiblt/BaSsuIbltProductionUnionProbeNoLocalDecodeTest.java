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
        BaSsuIbltSecureBucketInput input = BaSsuIbltProductionUnionProbeTestUtils.input(
            0,
            BaSsuIbltProductionUnionProbeTestUtils.singletonCell(1L),
            BaSsuIbltProductionUnionProbeTestUtils.emptyCell()
        );
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(config, seed);
        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, seed);
        sender.init(1, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH);
        receiver.init(1, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH);
        BaSsuIbltProductionUnionProbeCapsule capsule = sender.probeProduction(0, input);
        Exception abort = Assert.assertThrows(Exception.class, () -> receiver.probeProduction(0, input, capsule));
        Assert.assertTrue(abort.getMessage().contains("remote-state-hiding UP-BA-UPOT"));
    }
}
