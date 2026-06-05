package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P37 production queue-peel execution gate tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltSecureProtocolProductionQueuePeelExecutionTest {

    @Test
    public void testProductionAdapterRejectsCurrentOpaquePlaceholderBeforeInputUse() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProductionQueuePeelAdapter.run(
                null, null, Long.BYTES, null, null, null, null, null, null, null, backend
            ));
        Assert.assertTrue(abort.getMessage().contains("production-ready backend"));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
        Assert.assertTrue(abort.getMessage().contains("opaque fail-closed placeholder"));
    }

    @Test
    public void testSecureCoreUsesProductionAdapterGate() {
        BaSsuIbltProductionUnionProbeBackendConfig backend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureProtocol.runQueuePeelAligned(
                null, null, Long.BYTES, null, null, null, null, null, null, null, backend
            ));
        Assert.assertTrue(abort.getMessage().contains("production-ready backend"));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
    }
}
