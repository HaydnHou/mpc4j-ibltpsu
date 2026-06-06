package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * P50 party-local production queue-peel input bridge tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionQueuePeelLocalInputBridgeTest {
    /**
     * schedule.
     */
    private static final BaSsuIbltUpBaUpotOfflineSchedule SCHEDULE =
        BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 8, 16);
    /**
     * context.
     */
    private static final BaSsuIbltQueuePeelProbeContext CONTEXT = new BaSsuIbltQueuePeelProbeContext(1, 7, 3);

    @Test
    public void testEmptyLocalCellMapsToPartyLocalInput() {
        RecordingAuthProvider authProvider = new RecordingAuthProvider();
        BaSsuIbltProductionQueuePeelPartyLocalProbeInput probeInput =
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, CONTEXT, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(),
                BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR, authProvider
            );
        Assert.assertSame(CONTEXT, probeInput.getContext());
        Assert.assertEquals(BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR, probeInput.getOwnLayer());
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.EMPTY, probeInput.getOwnLocalInput().getState());
        assertPublicInput(probeInput.getPublicInput());
        authProvider.assertCalledWith(probeInput.getPublicInput(), BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR);
    }

    @Test
    public void testEmptyLocalCellPreservesProviderAuth() throws Exception {
        RecordingAuthProvider authProvider = new RecordingAuthProvider();
        BaSsuIbltProductionQueuePeelPartyLocalProbeInput probeInput =
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, CONTEXT, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(),
                BaSsuIbltProductionUnionProbeLocalLayer.SHADOW, authProvider
            );
        Assert.assertArrayEquals(authProvider.lastAuth, reflectAuth(probeInput.getOwnLocalInput()));
    }

    @Test
    public void testSingletonLocalCellMapsToPartyLocalInput() {
        RecordingAuthProvider authProvider = new RecordingAuthProvider();
        BaSsuIbltProductionQueuePeelPartyLocalProbeInput probeInput =
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, CONTEXT, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(11L),
                BaSsuIbltProductionUnionProbeLocalLayer.SHADOW, authProvider
            );
        Assert.assertEquals(BaSsuIbltProductionUnionProbeLocalLayer.SHADOW, probeInput.getOwnLayer());
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.SINGLETON, probeInput.getOwnLocalInput().getState());
        authProvider.assertCalledWith(probeInput.getPublicInput(), BaSsuIbltProductionUnionProbeLocalLayer.SHADOW);
    }

    @Test
    public void testBlockedLocalCellMapsToPartyLocalInput() {
        BaSsuIbltProductionQueuePeelPartyLocalProbeInput probeInput =
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, CONTEXT, BaSsuIbltProductionUnionProbeTestUtils.blockedCell(13L),
                BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR, new RecordingAuthProvider()
            );
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.BLOCKED, probeInput.getOwnLocalInput().getState());
    }

    @Test
    public void testWrongShapeAndAuthAreRejected() {
        BaSsuIbltSecureCellView wrongShapeCell = BaSsuIbltSecureCellView.empty(
            SCHEDULE.getElementByteLength() + 1, SCHEDULE.getTagByteLength(), SCHEDULE.getCheckByteLength()
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, CONTEXT, wrongShapeCell, BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR,
                new RecordingAuthProvider()
            ));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, CONTEXT, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(),
                BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR,
                (publicInput, ownLayer, ownCellView) -> new byte[publicInput.getAuthTagByteLength() - 1]
            ));
    }

    @Test
    public void testOutOfScheduleContextIsRejected() {
        BaSsuIbltQueuePeelProbeContext outOfRetry = new BaSsuIbltQueuePeelProbeContext(
            SCHEDULE.getRetryNum(), 0, 0
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, outOfRetry, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(),
                BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR, new RecordingAuthProvider()
            ));
        BaSsuIbltQueuePeelProbeContext outOfProbe = new BaSsuIbltQueuePeelProbeContext(
            0, 0, SCHEDULE.getMaxProbeNum()
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            BaSsuIbltProductionQueuePeelAdapter.partyLocalProbeInput(
                SCHEDULE, outOfProbe, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(),
                BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR, new RecordingAuthProvider()
            ));
    }

    private static void assertPublicInput(BaSsuIbltUpBaUpotPublicInput publicInput) {
        Assert.assertEquals(SCHEDULE.getProfileId(), publicInput.getProfileId());
        Assert.assertEquals(CONTEXT.getRetryIndex(), publicInput.getRetryId());
        Assert.assertEquals(CONTEXT.getBucketIndex(), publicInput.getBucketIndex());
        Assert.assertEquals(CONTEXT.getProbeOrdinal(), publicInput.getProbeOrdinal());
        SCHEDULE.validate(publicInput);
    }

    private static byte[] reflectAuth(BaSsuIbltUpBaUpotLocalInput localInput) throws Exception {
        Field authField = BaSsuIbltUpBaUpotLocalInput.class.getDeclaredField("auth");
        authField.setAccessible(true);
        return (byte[]) authField.get(localInput);
    }

    /**
     * recording provider.
     */
    private static class RecordingAuthProvider implements BaSsuIbltUpBaUpotAuthMaterialProvider {
        /**
         * called public input.
         */
        private BaSsuIbltUpBaUpotPublicInput calledPublicInput;
        /**
         * called layer.
         */
        private BaSsuIbltProductionUnionProbeLocalLayer calledLayer;
        /**
         * called cell view.
         */
        private BaSsuIbltSecureCellView calledCellView;
        /**
         * last auth.
         */
        private byte[] lastAuth;

        @Override
        public byte[] authMaterial(BaSsuIbltUpBaUpotPublicInput publicInput,
                                   BaSsuIbltProductionUnionProbeLocalLayer ownLayer,
                                   BaSsuIbltSecureCellView ownCellView) {
            calledPublicInput = publicInput;
            calledLayer = ownLayer;
            calledCellView = ownCellView;
            byte[] auth = new byte[publicInput.getAuthTagByteLength()];
            Arrays.fill(auth, ownLayer == BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR ? (byte) 0x11 : (byte) 0x22);
            auth[0] ^= (byte) publicInput.getRetryId();
            auth[1] ^= (byte) publicInput.getBucketIndex();
            auth[2] ^= (byte) publicInput.getProbeOrdinal();
            lastAuth = Arrays.copyOf(auth, auth.length);
            return auth;
        }

        void assertCalledWith(BaSsuIbltUpBaUpotPublicInput publicInput,
                              BaSsuIbltProductionUnionProbeLocalLayer ownLayer) {
            Assert.assertSame(publicInput, calledPublicInput);
            Assert.assertEquals(ownLayer, calledLayer);
            Assert.assertNotNull(calledCellView);
        }
    }
}
