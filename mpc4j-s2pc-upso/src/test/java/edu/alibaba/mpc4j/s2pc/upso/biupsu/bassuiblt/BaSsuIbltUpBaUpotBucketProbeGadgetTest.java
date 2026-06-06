package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * P52 specialized bucket-probe gadget tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotBucketProbeGadgetTest {
    /**
     * random state.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /**
     * fixed per-probe COT count.
     */
    private static final int COT_NUM_PER_PROBE = 3;

    @Test
    public void testCotSelectedRowsImplementTruthTable() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltUpBaUpotPublicInput publicInput = schedule.publicInput(0, 0, 0);
        BaSsuIbltUpBaUpotLocalInput empty = BaSsuIbltUpBaUpotLocalInput.empty(publicInput);
        BaSsuIbltUpBaUpotLocalInput senderSingleton = BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L);
        BaSsuIbltUpBaUpotLocalInput sameSingleton = BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L);
        BaSsuIbltUpBaUpotLocalInput otherSingleton = BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 11L);
        BaSsuIbltUpBaUpotLocalInput blocked = BaSsuIbltUpBaUpotFixedOnlineShapeTest.blockedLocalInput(
            publicInput, 12L
        );

        Assert.assertTrue(runProbe(schedule, publicInput, empty, empty).isBottom());
        BaSsuIbltProductionUnionProbeOutput senderOnly = runProbe(schedule, publicInput, senderSingleton, empty);
        Assert.assertTrue(senderOnly.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L), senderOnly.getElement());

        BaSsuIbltProductionUnionProbeOutput receiverOnly = runProbe(schedule, publicInput, empty, otherSingleton);
        Assert.assertTrue(receiverOnly.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(11L), receiverOnly.getElement());

        BaSsuIbltProductionUnionProbeOutput sameOutput = runProbe(
            schedule, publicInput, senderSingleton, sameSingleton
        );
        Assert.assertTrue(sameOutput.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L), sameOutput.getElement());
        Assert.assertTrue(runProbe(schedule, publicInput, senderSingleton, otherSingleton).isBottom());
        Assert.assertTrue(runProbe(schedule, publicInput, blocked, empty).isBottom());
        Assert.assertTrue(runProbe(schedule, publicInput, empty, blocked).isBottom());
        Assert.assertTrue(runProbe(schedule, publicInput, senderSingleton, blocked).isBottom());
        Assert.assertTrue(runProbe(schedule, publicInput, blocked, otherSingleton).isBottom());
        Assert.assertTrue(runProbe(schedule, publicInput, blocked, blocked).isBottom());
    }

    @Test
    public void testZeroElementSingletonSurvivesMaskedRows() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltUpBaUpotPublicInput publicInput = schedule.publicInput(0, 0, 0);
        byte[] zeroElement = new byte[publicInput.getElementByteLength()];
        BaSsuIbltSecureCellView zeroCell = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(zeroElement);
        BaSsuIbltUpBaUpotLocalInput zeroSingleton = BaSsuIbltUpBaUpotLocalInput.singleton(
            publicInput, zeroCell.getKeyXor(), zeroCell.getTagXor(), zeroCell.getCheckXor(),
            new byte[publicInput.getAuthTagByteLength()]
        );
        BaSsuIbltProductionUnionProbeOutput output = runProbe(
            schedule, publicInput, zeroSingleton, BaSsuIbltUpBaUpotLocalInput.empty(publicInput)
        );
        Assert.assertTrue(output.isSingleton());
        Assert.assertArrayEquals(zeroElement, output.getElement());
    }

    @Test
    public void testSharedSingletonGateConsumesAuthMaterial() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltUpBaUpotPublicInput publicInput = schedule.publicInput(0, 0, 0);
        BaSsuIbltSecureCellView cell = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(10L);
        byte[] auth = auth(publicInput, (byte) 0x33);
        BaSsuIbltUpBaUpotLocalInput senderSingleton = singletonWithAuth(publicInput, cell, auth);
        BaSsuIbltUpBaUpotLocalInput sameAuthSingleton = singletonWithAuth(publicInput, cell, auth);
        BaSsuIbltProductionUnionProbeOutput sameOutput = runProbe(
            schedule, publicInput, senderSingleton, sameAuthSingleton
        );
        Assert.assertTrue(sameOutput.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L), sameOutput.getElement());

        BaSsuIbltUpBaUpotLocalInput differentAuthSingleton = singletonWithAuth(
            publicInput, cell, auth(publicInput, (byte) 0x55)
        );
        Assert.assertTrue(runProbe(schedule, publicInput, senderSingleton, differentAuthSingleton).isBottom());
    }

    @Test
    public void testWrongCotChoiceBindingRejects() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltUpBaUpotPublicInput publicInput = schedule.publicInput(0, 0, 0);
        BaSsuIbltUpBaUpotLocalInput senderSingleton =
            BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L);
        BaSsuIbltUpBaUpotLocalInput receiverSingleton =
            BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L);
        CotSenderOutput cotSenderOutput = CotSenderOutput.createRandom(
            COT_NUM_PER_PROBE, BlockUtils.randomBlock(SECURE_RANDOM), SECURE_RANDOM
        );
        BaSsuIbltUpBaUpotMaskedProbeRows rows = BaSsuIbltUpBaUpotBucketProbeGadget.encodeRows(
            schedule, publicInput, senderSingleton, cotSenderOutput, 0, COT_NUM_PER_PROBE
        );
        BaSsuIbltUpBaUpotLocalInput[] localInputs = new BaSsuIbltUpBaUpotLocalInput[]{
            BaSsuIbltUpBaUpotLocalInput.empty(publicInput),
            receiverSingleton,
            BaSsuIbltUpBaUpotFixedOnlineShapeTest.blockedLocalInput(publicInput, 12L),
        };
        for (BaSsuIbltUpBaUpotLocalInput actualInput : localInputs) {
            boolean[] actualChoices = BaSsuIbltUpBaUpotBucketProbeGadget.receiverChoices(
                actualInput, COT_NUM_PER_PROBE
            );
            for (BaSsuIbltUpBaUpotLocalInput wrongInput : localInputs) {
                boolean[] wrongChoices = BaSsuIbltUpBaUpotBucketProbeGadget.receiverChoices(
                    wrongInput, COT_NUM_PER_PROBE
                );
                if (Arrays.equals(actualChoices, wrongChoices)) {
                    continue;
                }
                CotReceiverOutput wrongReceiverOutput = receiverOutputForChoices(cotSenderOutput, wrongChoices);
                Assert.assertThrows(IllegalArgumentException.class, () ->
                    BaSsuIbltUpBaUpotBucketProbeGadget.decodeSelectedRow(
                        schedule, publicInput, actualInput, rows, wrongReceiverOutput, 0, COT_NUM_PER_PROBE
                    ));
            }
        }
    }

    @Test
    public void testNonZeroCotOffsetConsumesTheBoundMaterialWindow() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(2, 8);
        BaSsuIbltUpBaUpotPublicInput publicInput = schedule.publicInput(0, 0, 1);
        BaSsuIbltUpBaUpotLocalInput senderSingleton =
            BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L);
        BaSsuIbltUpBaUpotLocalInput receiverEmpty = BaSsuIbltUpBaUpotLocalInput.empty(publicInput);
        int cotOffset = COT_NUM_PER_PROBE;
        CotSenderOutput cotSenderOutput = CotSenderOutput.createRandom(
            2 * COT_NUM_PER_PROBE, BlockUtils.randomBlock(SECURE_RANDOM), SECURE_RANDOM
        );
        BaSsuIbltUpBaUpotMaskedProbeRows rows = BaSsuIbltUpBaUpotBucketProbeGadget.encodeRows(
            schedule, publicInput, senderSingleton, cotSenderOutput, cotOffset, COT_NUM_PER_PROBE
        );
        boolean[] choices = new boolean[2 * COT_NUM_PER_PROBE];
        boolean[] selectedChoices = BaSsuIbltUpBaUpotBucketProbeGadget.receiverChoices(
            receiverEmpty, COT_NUM_PER_PROBE
        );
        System.arraycopy(selectedChoices, 0, choices, cotOffset, selectedChoices.length);
        CotReceiverOutput cotReceiverOutput = receiverOutputForChoices(cotSenderOutput, choices);
        BaSsuIbltProductionUnionProbeOutput output = BaSsuIbltUpBaUpotBucketProbeGadget.decodeSelectedRow(
            schedule, publicInput, receiverEmpty, rows, cotReceiverOutput, cotOffset, COT_NUM_PER_PROBE
        );
        Assert.assertTrue(output.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L), output.getElement());
    }

    @Test
    public void testPayloadShapeAndSerializationAreFixed() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule = BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        BaSsuIbltUpBaUpotPublicInput publicInput = schedule.publicInput(0, 0, 0);
        BaSsuIbltUpBaUpotLocalInput senderSingleton =
            BaSsuIbltUpBaUpotApiTest.singletonLocalInput(publicInput, 10L);
        CotSenderOutput cotSenderOutput = CotSenderOutput.createRandom(
            COT_NUM_PER_PROBE, BlockUtils.randomBlock(SECURE_RANDOM), SECURE_RANDOM
        );
        BaSsuIbltUpBaUpotMaskedProbeRows rows = BaSsuIbltUpBaUpotBucketProbeGadget.encodeRows(
            schedule, publicInput, senderSingleton, cotSenderOutput, 0, COT_NUM_PER_PROBE
        );
        int expectedRowByteLength = BaSsuIbltUpBaUpotBucketProbeGadget.rowPayloadByteLength(schedule);
        Assert.assertEquals(3, rows.rowNum());
        Assert.assertEquals(expectedRowByteLength, rows.rowByteLength());
        Assert.assertEquals(BaSsuIbltUpBaUpotBucketProbeGadget.maskedRowsByteLength(schedule), rows.totalByteLength());

        List<byte[]> payload = rows.toPayload();
        Assert.assertEquals(BaSsuIbltUpBaUpotMaskedProbeRows.ROW_NUM, payload.size());
        for (byte[] row : payload) {
            Assert.assertEquals(expectedRowByteLength, row.length);
            Assert.assertFalse(BaSsuIbltProductionUnionProbeTestUtils.containsSubArray(
                row, BaSsuIbltProductionUnionProbeTestUtils.element(10L)
            ));
        }
        BaSsuIbltUpBaUpotMaskedProbeRows decodedRows =
            BaSsuIbltUpBaUpotMaskedProbeRows.fromPayload(payload, expectedRowByteLength);
        for (BaSsuIbltUpBaUpotFunctionality.LocalSymbol symbol : BaSsuIbltUpBaUpotFunctionality.LocalSymbol.values()) {
            Assert.assertArrayEquals(rows.row(symbol).getBytes(), decodedRows.row(symbol).getBytes());
        }

        byte[] malformed = Arrays.copyOf(payload.get(0), payload.get(0).length - 1);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltUpBaUpotMaskedProbeRows.fromPayload(List.of(malformed, payload.get(1), payload.get(2)),
                expectedRowByteLength));
    }

    private static BaSsuIbltProductionUnionProbeOutput runProbe(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                               BaSsuIbltUpBaUpotPublicInput publicInput,
                                                               BaSsuIbltUpBaUpotLocalInput senderLocalInput,
                                                               BaSsuIbltUpBaUpotLocalInput receiverLocalInput) {
        CotSenderOutput cotSenderOutput = CotSenderOutput.createRandom(
            COT_NUM_PER_PROBE, BlockUtils.randomBlock(SECURE_RANDOM), SECURE_RANDOM
        );
        BaSsuIbltUpBaUpotMaskedProbeRows rows = BaSsuIbltUpBaUpotBucketProbeGadget.encodeRows(
            schedule, publicInput, senderLocalInput, cotSenderOutput, 0, COT_NUM_PER_PROBE
        );
        boolean[] choices = BaSsuIbltUpBaUpotBucketProbeGadget.receiverChoices(receiverLocalInput, COT_NUM_PER_PROBE);
        CotReceiverOutput cotReceiverOutput = receiverOutputForChoices(cotSenderOutput, choices);
        return BaSsuIbltUpBaUpotBucketProbeGadget.decodeSelectedRow(
            schedule, publicInput, receiverLocalInput, rows, cotReceiverOutput, 0, COT_NUM_PER_PROBE
        );
    }

    private static CotReceiverOutput receiverOutputForChoices(CotSenderOutput cotSenderOutput, boolean[] choices) {
        byte[][] rbArray = new byte[choices.length][];
        for (int i = 0; i < choices.length; i++) {
            byte[] rb = choices[i] ? cotSenderOutput.getR1(i) : cotSenderOutput.getR0(i);
            rbArray[i] = Arrays.copyOf(rb, rb.length);
        }
        return CotReceiverOutput.create(choices, rbArray);
    }

    private static BaSsuIbltUpBaUpotLocalInput singletonWithAuth(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                                 BaSsuIbltSecureCellView cell, byte[] auth) {
        return BaSsuIbltUpBaUpotLocalInput.singleton(
            publicInput, cell.getKeyXor(), cell.getTagXor(), cell.getCheckXor(), auth
        );
    }

    private static byte[] auth(BaSsuIbltUpBaUpotPublicInput publicInput, byte value) {
        byte[] auth = new byte[publicInput.getAuthTagByteLength()];
        Arrays.fill(auth, value);
        return auth;
    }
}
