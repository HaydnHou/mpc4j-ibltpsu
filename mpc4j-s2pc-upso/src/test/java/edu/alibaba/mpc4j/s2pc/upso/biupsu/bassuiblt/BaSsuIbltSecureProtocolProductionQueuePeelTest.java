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
        Assert.assertTrue(abort.getMessage().contains(
            BaSsuIbltProductionUnionProbeBackendConfig.PRODUCTION_AUDIT_NOT_READY_REASON
        ));
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

    @Test
    public void testAdapterDerivesScheduleFromPublicQueueShape() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltProductionUnionProbeBackendConfig config = config(params);
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionQueuePeelAdapter.offlineSchedule(
            params, Long.BYTES, config
        );
        long perRetryCap = (long) params.getTableLength()
            + (long) params.getDegree() * (params.getNLarge() + params.getNShadow());
        Assert.assertEquals(params.getProfileId(), schedule.getProfileId());
        Assert.assertEquals(params.getRetryCount(), schedule.getRetryNum());
        Assert.assertEquals(perRetryCap, schedule.getMaxProbeNum());
        Assert.assertEquals(params.getTableLength(), schedule.getTableLength());
        Assert.assertEquals(Long.BYTES, schedule.getElementByteLength());
        Assert.assertEquals(BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()), schedule.getTagByteLength());
        Assert.assertEquals(BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits()), schedule.getCheckByteLength());
    }

    @Test
    public void testAdapterPublicInputIsScheduleBound() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltProductionUnionProbeBackendConfig config = config(params);
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionQueuePeelAdapter.offlineSchedule(
            params, Long.BYTES, config
        );
        BaSsuIbltQueuePeelProbeContext context = new BaSsuIbltQueuePeelProbeContext(1, 7, 3);
        BaSsuIbltUpBaUpotPublicInput publicInput = BaSsuIbltProductionQueuePeelAdapter.publicInput(
            schedule, context
        );
        Assert.assertEquals(params.getProfileId(), publicInput.getProfileId());
        Assert.assertEquals(1, publicInput.getRetryId());
        Assert.assertEquals(7, publicInput.getBucketIndex());
        Assert.assertEquals(3, publicInput.getProbeOrdinal());
        schedule.validate(publicInput);

        BaSsuIbltQueuePeelProbeContext outOfRetry = new BaSsuIbltQueuePeelProbeContext(
            params.getRetryCount(), 0, 0
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProductionQueuePeelAdapter.publicInput(schedule, outOfRetry));
        BaSsuIbltQueuePeelProbeContext outOfProbeCap = new BaSsuIbltQueuePeelProbeContext(
            0, 0, schedule.getMaxProbeNum()
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProductionQueuePeelAdapter.publicInput(schedule, outOfProbeCap));
        BaSsuIbltQueuePeelProbeContext outOfBucketRange = new BaSsuIbltQueuePeelProbeContext(
            0, schedule.getTableLength(), 0
        );
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltProductionQueuePeelAdapter.publicInput(schedule, outOfBucketRange));
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

    private static BaSsuIbltBiUpsuParams params() {
        return new BaSsuIbltBiUpsuParams.Builder(8, 8)
            .setRetryCount(2)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(182)
            .setTagBits(182)
            .setPublicPlaceSeed(20260605L)
            .build();
    }

    private static BaSsuIbltProductionUnionProbeBackendConfig config(BaSsuIbltBiUpsuParams params) {
        return new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
            .setElementByteLength(Long.BYTES)
            .setTagByteLength(BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()))
            .setCheckByteLength(BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits()))
            .build();
    }
}
