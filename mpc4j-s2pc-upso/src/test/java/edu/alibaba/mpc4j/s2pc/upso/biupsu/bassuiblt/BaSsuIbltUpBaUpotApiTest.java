package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * P41 UP-BA-UPOT protocol-facing API tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotApiTest {

    @Test
    public void testOnlyRpcClassesImplementProductionApi() {
        Assert.assertTrue(BaSsuIbltUpBaUpotSender.class.isAssignableFrom(BaSsuIbltRpcUpBaUpotSender.class));
        Assert.assertTrue(BaSsuIbltUpBaUpotReceiver.class.isAssignableFrom(BaSsuIbltRpcUpBaUpotReceiver.class));
        Assert.assertFalse(BaSsuIbltUpBaUpotSender.class.isAssignableFrom(
            BaSsuIbltProductionUnionProbeSender.class
        ));
        Assert.assertFalse(BaSsuIbltUpBaUpotReceiver.class.isAssignableFrom(
            BaSsuIbltProductionUnionProbeReceiver.class
        ));
    }

    @Test
    public void testLegacyCapsuleBuilderBuildsFixedCapsuleButIsNotProductionApi() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 1)
            );
        BaSsuIbltUpBaUpotPublicInput publicInput = publicInput(0);
        BaSsuIbltUpBaUpotLocalInput localInput = singletonLocalInput(publicInput, 7L);
        sender.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule = sender.buildCapsule(publicInput, localInput);
        Assert.assertEquals(publicInput.getBucketIndex(), capsule.getBucketIndex());
        Assert.assertEquals(config.capsuleByteLength(), capsule.getEncoded().length);
    }

    @Test
    public void testReceiverApiFailsClosedUntilTrueEvaluatorExists() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 2);
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(config, seed, schedule);
        BaSsuIbltProductionUnionProbeReceiver receiver = new BaSsuIbltProductionUnionProbeReceiver(config, seed, schedule);
        BaSsuIbltUpBaUpotPublicInput publicInput = publicInput(1);
        sender.init(1);
        receiver.init(1);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(publicInput, singletonLocalInput(publicInput, 11L));
        Exception abort = Assert.assertThrows(Exception.class, () ->
            receiver.validateCapsuleAndFailClosed(publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput), capsule));
        Assert.assertTrue(abort.getMessage().contains("remote-state-hiding UP-BA-UPOT"));
        Assert.assertFalse(config.isQueuePeelProductionReady());
    }

    @Test
    public void testPublicInputRejectsBadShape() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("", 0, 0, 0, 8, 192, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", -1, 0, 0, 8, 192, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, -1, 0, 8, 192, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, 0, -1, 8, 192, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, 0, 0, 0, 192, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, 0, 0, 8, 0, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, 0, 0, 8, 185, 128, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, 0, 0, 8, 192, 127, 128));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUpBaUpotPublicInput("p", 0, 0, 0, 8, 192, 128, 129));
    }

    @Test
    public void testCapsuleBindsProfileAndRetryDomain() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotPublicInput base = publicInput("M10_N18_D3", 0, 2);
        BaSsuIbltUpBaUpotPublicInput differentProfile = publicInput("M12_N18_D3", 0, 2);
        BaSsuIbltUpBaUpotPublicInput differentRetry = publicInput("M10_N18_D3", 1, 2);
        byte[] baseCapsule = oneProbe(
            config, seed, BaSsuIbltUpBaUpotOfflineShapeTest.schedule("M10_N18_D3", 1, 1), base
        ).getEncoded();
        byte[] profileCapsule = oneProbe(
            config, seed, BaSsuIbltUpBaUpotOfflineShapeTest.schedule("M12_N18_D3", 1, 1), differentProfile
        ).getEncoded();
        byte[] retryCapsule = oneProbe(
            config, seed, BaSsuIbltUpBaUpotOfflineShapeTest.schedule("M10_N18_D3", 2, 1), differentRetry
        ).getEncoded();

        BaSsuIbltUpBaUpotPublicInput firstOrdinal = publicInput("M10_N18_D3", 0, 2, 0);
        BaSsuIbltUpBaUpotPublicInput differentProbeOrdinal = publicInput("M10_N18_D3", 0, 2, 1);
        BaSsuIbltProductionUnionProbeSender ordinalSender = new BaSsuIbltProductionUnionProbeSender(
            config, seed, BaSsuIbltUpBaUpotOfflineShapeTest.schedule("M10_N18_D3", 1, 2)
        );
        ordinalSender.init(2);
        ordinalSender.buildCapsule(firstOrdinal, singletonLocalInput(firstOrdinal, 7L));
        byte[] ordinalCapsule = ordinalSender.buildCapsule(
            differentProbeOrdinal, singletonLocalInput(differentProbeOrdinal, 7L)
        ).getEncoded();
        Assert.assertFalse(Arrays.equals(baseCapsule, profileCapsule));
        Assert.assertFalse(Arrays.equals(baseCapsule, retryCapsule));
        Assert.assertFalse(Arrays.equals(baseCapsule, ordinalCapsule));
    }

    @Test
    public void testCapsuleBindsFullOfflineScheduleDomain() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        byte[] seed = BaSsuIbltProductionUnionProbeTestUtils.seed();
        BaSsuIbltUpBaUpotOfflineSchedule table8 = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltUpBaUpotOfflineSchedule table9 = new BaSsuIbltUpBaUpotOfflineSchedule(
            table8.getProfileId(), table8.getRetryNum(), table8.getMaxProbeNum(), 9,
            table8.getElementByteLength(), table8.getTagByteLength(), table8.getCheckByteLength(),
            table8.getAuthTagByteLength()
        );
        BaSsuIbltUpBaUpotPublicInput publicInput = publicInput("M10_N18_D3", 0, 0, 0);
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(config, seed, table8);
        sender.init(table8);
        BaSsuIbltProductionUnionProbeCapsule capsule =
            sender.buildCapsule(publicInput, singletonLocalInput(publicInput, 17L));

        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, seed, table9);
        receiver.init(table9);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            receiver.validateCapsuleAndFailClosed(
                publicInput, BaSsuIbltUpBaUpotLocalInput.empty(publicInput), capsule
            ));
    }

    @Test
    public void testBlockedLocalInputIsNotSingletonLike() {
        BaSsuIbltUpBaUpotPublicInput publicInput = publicInput(0);
        BaSsuIbltSecureCellView cell = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(9L);
        BaSsuIbltUpBaUpotLocalInput blocked = BaSsuIbltUpBaUpotLocalInput.blocked(
            publicInput, cell.getKeyXor(), cell.getTagXor(), cell.getCheckXor(),
            new byte[publicInput.getAuthTagByteLength()]
        );
        BaSsuIbltSecureCellView blockedCell = blocked.toCellView();
        Assert.assertEquals(2, blockedCell.getCount());
        Assert.assertFalse(blockedCell.isValidSingleton());
    }

    @Test
    public void testBoundaryRejectsShapeAndProbeMisuse() throws Exception {
        BaSsuIbltProductionUnionProbeBackendConfig config = BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 1)
            );
        sender.init(1);
        BaSsuIbltUpBaUpotPublicInput publicInput = publicInput(0);
        sender.buildCapsule(publicInput, singletonLocalInput(publicInput, 1L));
        Assert.assertThrows(Exception.class, () -> sender.buildCapsule(publicInput, singletonLocalInput(publicInput, 1L)));

        BaSsuIbltUpBaUpotPublicInput lengthMismatch = new BaSsuIbltUpBaUpotPublicInput(
            "M10_N18_D3", 0, 0, 0, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH,
            23 * Byte.SIZE, BaSsuIbltProductionUnionProbeTestUtils.CHECK_BYTE_LENGTH * Byte.SIZE,
            config.getAuthTagByteLength() * Byte.SIZE
        );
        BaSsuIbltProductionUnionProbeSender secondSender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 1)
            );
        secondSender.init(1);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> secondSender.buildCapsule(lengthMismatch, BaSsuIbltUpBaUpotLocalInput.empty(lengthMismatch)));

        BaSsuIbltUpBaUpotPublicInput skippedOrdinal = publicInput("M10_N18_D3", 0, 0, 1);
        BaSsuIbltProductionUnionProbeSender thirdSender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 1)
            );
        thirdSender.init(2);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> thirdSender.buildCapsule(skippedOrdinal, BaSsuIbltUpBaUpotLocalInput.empty(skippedOrdinal)));

        BaSsuIbltUpBaUpotOfflineSchedule strictSchedule =
            BaSsuIbltUpBaUpotOfflineShapeTest.schedule("M10_N18_D3", 1, 1);
        BaSsuIbltProductionUnionProbeSender scheduledSender =
            new BaSsuIbltProductionUnionProbeSender(config, BaSsuIbltProductionUnionProbeTestUtils.seed(), strictSchedule);
        scheduledSender.init(1);
        BaSsuIbltUpBaUpotPublicInput outOfRetry = publicInput("M10_N18_D3", 1, 0, 0);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> scheduledSender.buildCapsule(outOfRetry, BaSsuIbltUpBaUpotLocalInput.empty(outOfRetry)));

        BaSsuIbltProductionUnionProbeReceiver scheduledReceiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, BaSsuIbltProductionUnionProbeTestUtils.seed(), strictSchedule);
        scheduledReceiver.init(1);
        BaSsuIbltUpBaUpotPublicInput wrongProfile = publicInput("M12_N18_D3", 0, 0, 0);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            scheduledReceiver.validateCapsuleAndFailClosed(wrongProfile, BaSsuIbltUpBaUpotLocalInput.empty(wrongProfile), null));

        BaSsuIbltUpBaUpotPublicInput currentSlot = publicInput("M10_N18_D3", 0, 0, 0);
        BaSsuIbltUpBaUpotPublicInput staleSlot = publicInput("M10_N18_D3", 0, 1, 0);
        BaSsuIbltProductionUnionProbeSender staleSender =
            new BaSsuIbltProductionUnionProbeSender(
                config, BaSsuIbltProductionUnionProbeTestUtils.seed(),
                BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 2)
            );
        staleSender.init(1);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            staleSender.buildCapsule(currentSlot, BaSsuIbltUpBaUpotLocalInput.empty(staleSlot)));
    }

    static BaSsuIbltUpBaUpotPublicInput publicInput(int bucketIndex) {
        return publicInput("M10_N18_D3", 0, bucketIndex);
    }

    static BaSsuIbltUpBaUpotPublicInput publicInput(String profileId, int retryId, int bucketIndex) {
        return publicInput(profileId, retryId, bucketIndex, 0);
    }

    static BaSsuIbltUpBaUpotPublicInput publicInput(String profileId, int retryId, int bucketIndex, int probeOrdinal) {
        return new BaSsuIbltUpBaUpotPublicInput(
            profileId, retryId, bucketIndex, probeOrdinal,
            BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH,
            BaSsuIbltProductionUnionProbeTestUtils.TAG_BYTE_LENGTH * Byte.SIZE,
            BaSsuIbltProductionUnionProbeTestUtils.CHECK_BYTE_LENGTH * Byte.SIZE,
            new BaSsuIbltProductionUnionProbeBackendConfig.Builder().build().getAuthTagByteLength() * Byte.SIZE
        );
    }

    static BaSsuIbltUpBaUpotLocalInput singletonLocalInput(BaSsuIbltUpBaUpotPublicInput publicInput, long value) {
        BaSsuIbltSecureCellView cell = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(value);
        return BaSsuIbltUpBaUpotLocalInput.singleton(
            publicInput, cell.getKeyXor(), cell.getTagXor(), cell.getCheckXor(),
            new byte[publicInput.getAuthTagByteLength()]
        );
    }

    private static BaSsuIbltProductionUnionProbeCapsule oneProbe(
        BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed, BaSsuIbltUpBaUpotPublicInput publicInput
    ) throws Exception {
        return oneProbe(
            config, seed, BaSsuIbltUpBaUpotOfflineShapeTest.schedule(
                publicInput.getProfileId(), publicInput.getRetryId() + 1, publicInput.getProbeOrdinal() + 1
            ), publicInput
        );
    }

    private static BaSsuIbltProductionUnionProbeCapsule oneProbe(
        BaSsuIbltProductionUnionProbeBackendConfig config, byte[] seed,
        BaSsuIbltUpBaUpotOfflineSchedule schedule, BaSsuIbltUpBaUpotPublicInput publicInput
    ) throws Exception {
        BaSsuIbltProductionUnionProbeSender sender = new BaSsuIbltProductionUnionProbeSender(config, seed, schedule);
        sender.init(schedule);
        int targetMaterialOrdinal = publicInput.getRetryId() * schedule.getMaxProbeNum()
            + publicInput.getProbeOrdinal();
        for (int materialOrdinal = 0; materialOrdinal < targetMaterialOrdinal; materialOrdinal++) {
            int retryId = materialOrdinal / schedule.getMaxProbeNum();
            int probeOrdinal = materialOrdinal % schedule.getMaxProbeNum();
            BaSsuIbltUpBaUpotPublicInput dummyInput = publicInput(
                publicInput.getProfileId(), retryId, publicInput.getBucketIndex(), probeOrdinal
            );
            sender.buildCapsule(dummyInput, BaSsuIbltUpBaUpotLocalInput.empty(dummyInput));
        }
        return sender.buildCapsule(publicInput, singletonLocalInput(publicInput, 7L));
    }
}
