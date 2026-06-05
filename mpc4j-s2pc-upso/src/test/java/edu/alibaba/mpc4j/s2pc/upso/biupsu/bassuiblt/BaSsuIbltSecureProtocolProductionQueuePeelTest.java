package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import org.junit.Assert;
import org.junit.Test;

/**
 * Production queue-peel secure-core adapter tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltSecureProtocolProductionQueuePeelTest {

    @Test
    public void testSecureCoreRejectsFailClosedCandidateBackend() {
        BaSsuIbltProductionUnionProbeBackendConfig candidateBackend =
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build();
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureProtocol.runQueuePeelAligned(
                null, null, Long.BYTES, null, null, null, null, null, null, null, candidateBackend
            ));
        Assert.assertTrue(abort.getMessage().contains("production-ready backend"));
        Assert.assertTrue(abort.getMessage().contains("fail-closed"));
    }

    @Test
    public void testSecureCoreRejectsUntrustedFakeBackend() {
        IllegalArgumentException abort = Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltSecureProtocol.runQueuePeelAligned(
                null, null, Long.BYTES, null, null, null, null, null, null, null,
                new FakeProductionUnionProbeBackendConfig()
            ));
        Assert.assertTrue(abort.getMessage().contains("trusted production union-probe backend type"));
    }

    /**
     * fake backend that should not be trusted by the secure core.
     */
    private static class FakeProductionUnionProbeBackendConfig extends AbstractMultiPartyPtoConfig
        implements BaSsuIbltUnionProbeBackendConfig {

        FakeProductionUnionProbeBackendConfig() {
            super(SecurityModel.SEMI_HONEST);
        }

        @Override
        public String getUnionProbeBackendName() {
            return "fake production union-probe backend";
        }

        @Override
        public boolean isSpecializedBucketProbe() {
            return true;
        }

        @Override
        public boolean isQueuePeelProductionReady() {
            return true;
        }
    }
}
