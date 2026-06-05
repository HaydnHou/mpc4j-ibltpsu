package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P37 production retry gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltSecureProtocolProductionRetryTest {

    @Test
    public void testNoRetryTranscriptIsProducedWhenBackendIsNotReady() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureProtocol.runQueuePeelAligned(
                null, null, Long.BYTES, null, null, null, null, null, null, null, backend
            ));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
    }

    @Test
    public void testNullBackendFailsBeforeRetryExecution() {
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureProtocol.runQueuePeelAligned(
                null, null, Long.BYTES, null, null, null, null, null, null, null, null
            ));
        Assert.assertTrue(abort.getMessage().contains("unionProbeBackendConfig"));
    }
}
