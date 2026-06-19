package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.rpc.utils.PayloadType;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.folded.tcl23.Tcl23PsOprfFoldedLabelPmPeqtPtoDesc;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.label.tcl23.Tcl23PsOprfLabelPmPeqtPtoDesc;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.tcl23.Tcl23PsOprfFoldedSharePmPeqtPtoDesc;
import edu.alibaba.mpc4j.s2pc.pso.PsoUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiverOutput;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.ScSogsUpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Full-protocol route gate for the SC-SOGS share-cancel profile.
 *
 * <p>This test does not prove cryptographic simulation. It is a regression gate that prevents the
 * SC-SOGS path from silently falling back to label-output PM-PEQT or the old token release route.
 * The share-output PM-PEQT and share-cancel tail wrappers delegate network sends to sub-protocols, so this test checks
 * the concrete network-emitting SOGS tail and the absence of the old release route.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsFullProtocolRouteTest extends AbstractTwoPartyMemoryRpcPto {
    /**
     * Sender element size.
     */
    private static final int SENDER_ELEMENT_SIZE = 1 << 4;
    /**
     * Receiver element size.
     */
    private static final int RECEIVER_ELEMENT_SIZE = 1 << 8;
    /**
     * Element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = 8;

    public ShareCancelSogsFullProtocolRouteTest() {
        super(ShareCancelSogsFullProtocolRouteTest.class.getSimpleName());
    }

    @Test
    public void testShareCancelUsesShareCarrierAndTailOnly() throws InterruptedException {
        List<PacketRecord> packetRecords = runShareCancelAndGetPacketRecords();
        Set<Integer> ptoIds = packetRecords.stream()
            .map(packetRecord -> packetRecord.ptoId)
            .collect(Collectors.toSet());

        Assert.assertTrue(
            "SHARE_CANCEL_SOGS must use the token-keyed aggregate SOGS tail",
            ptoIds.contains(TokenKeyedSogsTailPtoDesc.getInstance().getPtoId())
        );
        Assert.assertTrue(
            "SHARE_CANCEL_SOGS default path must use the strict Byte-ECC-DDH share-output carrier",
            ptoIds.contains(Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.getInstance().getPtoId())
        );
        Assert.assertFalse(
            "SHARE_CANCEL_SOGS default path must not silently fall back to the PS-OPRF share carrier",
            ptoIds.contains(Tcl23PsOprfFoldedSharePmPeqtPtoDesc.getInstance().getPtoId())
        );
        Assert.assertFalse(
            "SHARE_CANCEL_SOGS must not use label-output PM-PEQT",
            ptoIds.contains(Tcl23PsOprfLabelPmPeqtPtoDesc.getInstance().getPtoId())
        );
        Assert.assertFalse(
            "SHARE_CANCEL_SOGS must not use folded label-output PM-PEQT",
            ptoIds.contains(Tcl23PsOprfFoldedLabelPmPeqtPtoDesc.getInstance().getPtoId())
        );
        Assert.assertFalse(
            "SHARE_CANCEL_SOGS must not use the old MCRG token release route",
            ptoIds.contains(McrgTokenSogsReleasePtoDesc.getInstance().getPtoId())
        );
    }

    @Test
    public void testShareCancelCriticalTranscriptUsesAggregateOnlySteps() throws InterruptedException {
        List<PacketRecord> packetRecords = runShareCancelAndGetPacketRecords();

        List<PacketRecord> tailPackets = tokenKeyedTailPackets(packetRecords);
        Assert.assertEquals("token-keyed SOGS tail must expose exactly three aggregate packets", 3, tailPackets.size());
        assertConsecutiveExtraInfo("tail packets", tailPackets);
        PacketRecord tokenPacket = onlyPacketWithStep(
            tailPackets, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal()
        );
        PacketRecord otMaskPacket = onlyPacketWithStep(
            tailPackets, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_OT_MASKS.ordinal()
        );
        PacketRecord cellSharePacket = onlyPacketWithStep(
            tailPackets, TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_CELL_SHARE.ordinal()
        );
        int rowNum = tokenPacket.payloadNum;
        Assert.assertTrue("tail row number must be fixed by the public carrier size", rowNum > SENDER_ELEMENT_SIZE);
        Assert.assertEquals(TokenKeyedSogsSketch.TOKEN_BYTE_LENGTH, tokenPacket.equalLength);
        Assert.assertEquals(rowNum * 2, otMaskPacket.payloadNum);
        Assert.assertEquals(512, cellSharePacket.payloadNum);
        Assert.assertEquals(otMaskPacket.equalLength, cellSharePacket.equalLength);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, tokenPacket.payloadType);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, otMaskPacket.payloadType);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, cellSharePacket.payloadType);

        List<PacketRecord> shareCarrierPackets = packetRecords.stream()
            .filter(packetRecord ->
                packetRecord.ptoId == Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.getInstance().getPtoId()
            )
            .collect(Collectors.toList());
        Assert.assertEquals(
            "folded share carrier must expose only receiver PRF and permuted sender PRF packets",
            2, shareCarrierPackets.size()
        );
        PacketRecord receiverPrfPacket = onlyPacketWithStep(shareCarrierPackets, 0);
        PacketRecord senderPrfPacket = onlyPacketWithStep(shareCarrierPackets, 1);
        Assert.assertEquals(
            "folded share carrier packets must share one aggregate protocol clock, not per-row/per-bin clocks",
            receiverPrfPacket.extraInfo, senderPrfPacket.extraInfo
        );
        Assert.assertEquals(receiverPrfPacket.payloadNum, senderPrfPacket.payloadNum);
        Assert.assertEquals(receiverPrfPacket.equalLength, senderPrfPacket.equalLength);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, receiverPrfPacket.payloadType);
        Assert.assertEquals(PayloadType.EQUAL_SIZE, senderPrfPacket.payloadType);
        Assert.assertEquals(
            "folded share carrier payloads must cover complete anonymous columns",
            0, receiverPrfPacket.payloadNum % rowNum
        );
        int alpha = receiverPrfPacket.payloadNum / rowNum;
        Assert.assertTrue("folded share carrier alpha must be a positive public matrix height", alpha > 0);
    }

    @Test
    public void testShareCancelFullProtocolUsesFreshOneTimeTailTokens() throws InterruptedException {
        List<PacketRecord> firstRunPackets = runShareCancelAndGetPacketRecords();
        List<PacketRecord> secondRunPackets = runShareCancelAndGetPacketRecords();

        PacketRecord firstTokenPacket = onlyPacketWithStep(
            tokenKeyedTailPackets(firstRunPackets), TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal()
        );
        PacketRecord secondTokenPacket = onlyPacketWithStep(
            tokenKeyedTailPackets(secondRunPackets), TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal()
        );
        Set<ByteBuffer> firstTokens = payloadSet(firstTokenPacket);
        Set<ByteBuffer> secondTokens = payloadSet(secondTokenPacket);

        Assert.assertEquals("full protocol tail tokens must be unique within one run",
            firstTokenPacket.payloadNum, firstTokens.size());
        Assert.assertEquals("full protocol tail tokens must be unique within one run",
            secondTokenPacket.payloadNum, secondTokens.size());
        Set<ByteBuffer> reusedTokens = new HashSet<>(firstTokens);
        reusedTokens.retainAll(secondTokens);
        Assert.assertTrue("full protocol tail tokens must be fresh one-time row keys across runs", reusedTokens.isEmpty());
    }

    @Test
    public void testShareCancelTailPayloadBytesChangeAcrossRepeatedRuns() throws InterruptedException {
        byte[] intersection = sentinelElement("SC-SOGS-FRESH-HIT", 0);
        byte[] senderOnly = sentinelElement("SC-SOGS-FRESH-SENDER-ONLY", 1);
        Set<ByteBuffer> senderElementSet = new LinkedHashSet<>();
        senderElementSet.add(ByteBuffer.wrap(intersection));
        senderElementSet.add(ByteBuffer.wrap(senderOnly));
        for (int index = 2; index < SENDER_ELEMENT_SIZE; index++) {
            senderElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-FRESH-SENDER", index)));
        }
        Set<ByteBuffer> receiverElementSet = new LinkedHashSet<>();
        receiverElementSet.add(ByteBuffer.wrap(Arrays.copyOf(intersection, intersection.length)));
        for (int index = 1; index < RECEIVER_ELEMENT_SIZE; index++) {
            receiverElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-FRESH-RECEIVER", index)));
        }

        List<PacketRecord> firstTailPackets = tokenKeyedTailPackets(
            runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet).packetRecords
        );
        List<PacketRecord> secondTailPackets = tokenKeyedTailPackets(
            runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet).packetRecords
        );
        assertSamePublicTailShape(firstTailPackets, secondTailPackets);
        for (TokenKeyedSogsTailPtoDesc.PtoStep step : TokenKeyedSogsTailPtoDesc.PtoStep.values()) {
            PacketRecord firstPacket = onlyPacketWithStep(firstTailPackets, step.ordinal());
            PacketRecord secondPacket = onlyPacketWithStep(secondTailPackets, step.ordinal());
            Assert.assertFalse(
                "full-protocol SOGS tail " + step + " payload bytes must not be reused across equal-input runs",
                payloadsEqual(firstPacket.payloads, secondPacket.payloads)
            );
        }
    }

    @Test
    public void testShareCancelCarrierPayloadBytesChangeAcrossRepeatedRuns() throws InterruptedException {
        byte[] intersection = sentinelElement("SC-SOGS-CARRIER-FRESH-HIT", 0);
        byte[] senderOnly = sentinelElement("SC-SOGS-CARRIER-FRESH-SENDER-ONLY", 1);
        Set<ByteBuffer> senderElementSet = new LinkedHashSet<>();
        senderElementSet.add(ByteBuffer.wrap(intersection));
        senderElementSet.add(ByteBuffer.wrap(senderOnly));
        for (int index = 2; index < SENDER_ELEMENT_SIZE; index++) {
            senderElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-CARRIER-FRESH-SENDER", index)));
        }
        Set<ByteBuffer> receiverElementSet = new LinkedHashSet<>();
        receiverElementSet.add(ByteBuffer.wrap(Arrays.copyOf(intersection, intersection.length)));
        for (int index = 1; index < RECEIVER_ELEMENT_SIZE; index++) {
            receiverElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-CARRIER-FRESH-RECEIVER", index)));
        }

        List<PacketRecord> firstCarrierPackets = foldedShareCarrierPackets(
            runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet).packetRecords
        );
        List<PacketRecord> secondCarrierPackets = foldedShareCarrierPackets(
            runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet).packetRecords
        );
        Assert.assertEquals(
            "folded share carrier must expose only two aggregate packets across runs",
            2, firstCarrierPackets.size()
        );
        Assert.assertEquals(firstCarrierPackets.size(), secondCarrierPackets.size());
        for (int stepId = 0; stepId < 2; stepId++) {
            PacketRecord firstPacket = onlyPacketWithStep(firstCarrierPackets, stepId);
            PacketRecord secondPacket = onlyPacketWithStep(secondCarrierPackets, stepId);
            assertSamePublicPacketShape(firstPacket, secondPacket);
            Assert.assertFalse(
                "folded-share carrier step " + stepId
                    + " payload bytes must not be reused across equal-input SC-SOGS runs",
                payloadsEqual(firstPacket.payloads, secondPacket.payloads)
            );
        }
    }

    @Test
    public void testShareCancelTranscriptDoesNotContainRawInputElements() throws InterruptedException {
        byte[] intersection = sentinelElement("SC-SOGS-RAW-HIT", 0);
        byte[] senderOnly = sentinelElement("SC-SOGS-RAW-SENDER-ONLY", 1);
        byte[] receiverOnly = sentinelElement("SC-SOGS-RAW-RECEIVER-ONLY", 1);
        Set<ByteBuffer> senderElementSet = new LinkedHashSet<>();
        senderElementSet.add(ByteBuffer.wrap(intersection));
        senderElementSet.add(ByteBuffer.wrap(senderOnly));
        for (int index = 2; index < SENDER_ELEMENT_SIZE; index++) {
            senderElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-SENDER", index)));
        }
        Set<ByteBuffer> receiverElementSet = new LinkedHashSet<>();
        receiverElementSet.add(ByteBuffer.wrap(Arrays.copyOf(intersection, intersection.length)));
        receiverElementSet.add(ByteBuffer.wrap(receiverOnly));
        for (int index = 2; index < RECEIVER_ELEMENT_SIZE; index++) {
            receiverElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-RECEIVER", index)));
        }

        ProtocolRun run = runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet);
        Assert.assertEquals(1, run.output.getPsica());
        assertTranscriptDoesNotContain(run.packetRecords, intersection, "raw intersection element");
        assertTranscriptDoesNotContain(run.packetRecords, senderOnly, "raw sender-only element");
        assertTranscriptDoesNotContain(run.packetRecords, receiverOnly, "raw receiver-only element");
    }

    @Test
    public void testShareCancelTranscriptDoesNotDirectlySendRawSogsAtoms() throws InterruptedException {
        byte[] intersection = sentinelElement("SC-SOGS-ATOM-HIT", 0);
        byte[] senderOnly = sentinelElement("SC-SOGS-ATOM-SENDER-ONLY", 1);
        byte[] receiverOnly = sentinelElement("SC-SOGS-ATOM-RECEIVER-ONLY", 1);
        Set<ByteBuffer> senderElementSet = new LinkedHashSet<>();
        senderElementSet.add(ByteBuffer.wrap(intersection));
        senderElementSet.add(ByteBuffer.wrap(senderOnly));
        for (int index = 2; index < SENDER_ELEMENT_SIZE; index++) {
            senderElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-ATOM-SENDER", index)));
        }
        Set<ByteBuffer> receiverElementSet = new LinkedHashSet<>();
        receiverElementSet.add(ByteBuffer.wrap(Arrays.copyOf(intersection, intersection.length)));
        receiverElementSet.add(ByteBuffer.wrap(receiverOnly));
        for (int index = 2; index < RECEIVER_ELEMENT_SIZE; index++) {
            receiverElementSet.add(ByteBuffer.wrap(sentinelElement("SC-SOGS-ATOM-RECEIVER", index)));
        }

        ProtocolRun run = runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet);
        PacketRecord tokenPacket = onlyPacketWithStep(
            tokenKeyedTailPackets(run.packetRecords), TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_TOKENS.ordinal()
        );
        PacketRecord otMaskPacket = onlyPacketWithStep(
            tokenKeyedTailPackets(run.packetRecords), TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_OT_MASKS.ordinal()
        );
        PacketRecord cellSharePacket = onlyPacketWithStep(
            tokenKeyedTailPackets(run.packetRecords), TokenKeyedSogsTailPtoDesc.PtoStep.SENDER_SEND_CELL_SHARE.ordinal()
        );
        TokenKeyedSogsSketch atomFactory = new TokenKeyedSogsSketch(1, ELEMENT_BYTE_LENGTH, 1);
        Set<ByteBuffer> rawAtoms = new HashSet<>(tokenPacket.payloadNum * 3);
        for (byte[] token : tokenPacket.payloads) {
            rawAtoms.add(ByteBuffer.wrap(atomFactory.createAtomBytes(token, intersection)));
            rawAtoms.add(ByteBuffer.wrap(atomFactory.createAtomBytes(token, senderOnly)));
            rawAtoms.add(ByteBuffer.wrap(atomFactory.createAtomBytes(token, receiverOnly)));
        }
        assertPayloadsAreNotRawAtoms(otMaskPacket, rawAtoms, "OT-mask packet");
        assertPayloadsAreNotRawAtoms(cellSharePacket, rawAtoms, "aggregate-cell-share packet");
    }

    private List<PacketRecord> runShareCancelAndGetPacketRecords() throws InterruptedException {
        List<Set<ByteBuffer>> sets = PsoUtils.generateBytesSets(
            SENDER_ELEMENT_SIZE, RECEIVER_ELEMENT_SIZE, ELEMENT_BYTE_LENGTH
        );
        Set<ByteBuffer> senderElementSet = sets.get(0);
        Set<ByteBuffer> receiverElementSet = sets.get(1);
        return runShareCancelAndGetPacketRecords(senderElementSet, receiverElementSet).packetRecords;
    }

    private ProtocolRun runShareCancelAndGetPacketRecords(Set<ByteBuffer> senderElementSet,
                                                          Set<ByteBuffer> receiverElementSet)
        throws InterruptedException {
        RecordingRpc recordingFirstRpc = new RecordingRpc(firstRpc);
        RecordingRpc recordingSecondRpc = new RecordingRpc(secondRpc);
        ScSogsUpsuConfig config = new ScSogsUpsuConfig.Builder()
            .setCellNum(512)
            .build();
        Assert.assertTrue(
            "SHARE_CANCEL_SOGS default config must bind the folded PM-PEQT row-share carrier adapter",
            config.getRowShareRelationCarrierConfig() instanceof FoldedPmPeqtRowShareCarrierConfig
        );
        FoldedPmPeqtRowShareCarrierConfig rowCarrierConfig =
            (FoldedPmPeqtRowShareCarrierConfig) config.getRowShareRelationCarrierConfig();
        Assert.assertTrue(
            "SHARE_CANCEL_SOGS default row-share carrier must bind the strict Byte-ECC-DDH share-output carrier",
            rowCarrierConfig.getFoldedSharePmPeqtConfig() instanceof Tcl23ByteEccDdhFoldedSharePmPeqtConfig
        );
        Assert.assertTrue(
            "SHARE_CANCEL_SOGS default config must keep compact PEQT tags enabled",
            ((Tcl23ByteEccDdhFoldedSharePmPeqtConfig) rowCarrierConfig.getFoldedSharePmPeqtConfig())
                .isCompactPeqtByteLength()
        );
        UpsuSender sender = UpsuFactory.createSender(recordingFirstRpc, recordingSecondRpc.ownParty(), config);
        UpsuReceiver receiver = UpsuFactory.createReceiver(recordingSecondRpc, recordingFirstRpc.ownParty(), config);
        int taskId = Math.abs(new SecureRandom().nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

        SenderThread senderThread = new SenderThread(sender, receiverElementSet.size(), senderElementSet);
        ReceiverThread receiverThread = new ReceiverThread(receiver, senderElementSet.size(), receiverElementSet);
        firstRpc.reset();
        secondRpc.reset();
        recordingFirstRpc.clear();
        recordingSecondRpc.clear();
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();

        Assert.assertNull(senderThread.throwable);
        Assert.assertNull(receiverThread.throwable);
        assertOutput(senderElementSet, receiverElementSet, receiverThread.output);
        List<PacketRecord> packetRecords = new ArrayList<>();
        packetRecords.addAll(recordingFirstRpc.snapshot());
        packetRecords.addAll(recordingSecondRpc.snapshot());

        Thread senderDestroyThread = new Thread(sender::destroy);
        Thread receiverDestroyThread = new Thread(receiver::destroy);
        senderDestroyThread.start();
        receiverDestroyThread.start();
        senderDestroyThread.join();
        receiverDestroyThread.join();
        return new ProtocolRun(packetRecords, receiverThread.output);
    }

    private static List<PacketRecord> tokenKeyedTailPackets(List<PacketRecord> packetRecords) {
        return packetRecords.stream()
            .filter(packetRecord -> packetRecord.ptoId == TokenKeyedSogsTailPtoDesc.getInstance().getPtoId())
            .collect(Collectors.toList());
    }

    private static List<PacketRecord> foldedShareCarrierPackets(List<PacketRecord> packetRecords) {
        return packetRecords.stream()
            .filter(packetRecord ->
                packetRecord.ptoId == Tcl23ByteEccDdhFoldedSharePmPeqtPtoDesc.getInstance().getPtoId()
            )
            .collect(Collectors.toList());
    }

    private static PacketRecord onlyPacketWithStep(List<PacketRecord> packetRecords, int stepId) {
        List<PacketRecord> matches = packetRecords.stream()
            .filter(packetRecord -> packetRecord.stepId == stepId)
            .collect(Collectors.toList());
        Assert.assertEquals("expected exactly one packet with step " + stepId, 1, matches.size());
        return matches.get(0);
    }

    private static Set<ByteBuffer> payloadSet(PacketRecord packetRecord) {
        return packetRecord.payloads.stream()
            .map(payload -> ByteBuffer.wrap(Arrays.copyOf(payload, payload.length)))
            .collect(Collectors.toSet());
    }

    private static void assertSamePublicTailShape(List<PacketRecord> firstTailPackets,
                                                  List<PacketRecord> secondTailPackets) {
        Assert.assertEquals(firstTailPackets.size(), secondTailPackets.size());
        for (TokenKeyedSogsTailPtoDesc.PtoStep step : TokenKeyedSogsTailPtoDesc.PtoStep.values()) {
            PacketRecord firstPacket = onlyPacketWithStep(firstTailPackets, step.ordinal());
            PacketRecord secondPacket = onlyPacketWithStep(secondTailPackets, step.ordinal());
            assertSamePublicPacketShape(firstPacket, secondPacket);
        }
    }

    private static void assertSamePublicPacketShape(PacketRecord firstPacket, PacketRecord secondPacket) {
        Assert.assertEquals(firstPacket.ptoId, secondPacket.ptoId);
        Assert.assertEquals(firstPacket.stepId, secondPacket.stepId);
        Assert.assertEquals(firstPacket.payloadType, secondPacket.payloadType);
        Assert.assertEquals(firstPacket.payloadNum, secondPacket.payloadNum);
        Assert.assertEquals(firstPacket.equalLength, secondPacket.equalLength);
    }

    private static boolean payloadsEqual(List<byte[]> left, List<byte[]> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (!Arrays.equals(left.get(index), right.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static void assertConsecutiveExtraInfo(String name, List<PacketRecord> packetRecords) {
        List<Long> extraInfos = packetRecords.stream()
            .map(packetRecord -> packetRecord.extraInfo)
            .sorted()
            .collect(Collectors.toList());
        for (int i = 1; i < extraInfos.size(); i++) {
            Assert.assertEquals(
                name + " must use fixed aggregate packet clocks, not per-row/per-bin clocks",
                extraInfos.get(i - 1) + 1,
                extraInfos.get(i).longValue()
            );
        }
    }

    private static void assertOutput(Set<ByteBuffer> senderElementSet, Set<ByteBuffer> receiverElementSet,
                                     UpsuReceiverOutput output) {
        Assert.assertNotNull(output);
        Set<ByteBuffer> expectUnion = new HashSet<>(receiverElementSet);
        expectUnion.addAll(senderElementSet);
        Set<ByteBuffer> expectIntersection = new HashSet<>(receiverElementSet);
        expectIntersection.retainAll(senderElementSet);
        Assert.assertEquals(expectUnion, output.getUnion());
        Assert.assertEquals(expectIntersection.size(), output.getPsica());
    }

    private static void assertTranscriptDoesNotContain(List<PacketRecord> packetRecords, byte[] forbidden,
                                                       String description) {
        for (PacketRecord packetRecord : packetRecords) {
            for (byte[] payload : packetRecord.payloads) {
                Assert.assertFalse(
                    "SC-SOGS transcript must not serialize " + description
                        + " in ptoId=" + packetRecord.ptoId + ", stepId=" + packetRecord.stepId,
                    containsSubarray(payload, forbidden)
                );
            }
        }
    }

    private static void assertPayloadsAreNotRawAtoms(PacketRecord packetRecord, Set<ByteBuffer> rawAtoms,
                                                     String description) {
        for (byte[] payload : packetRecord.payloads) {
            Assert.assertFalse(
                "SC-SOGS " + description + " must not directly serialize a raw token-keyed SOGS atom",
                rawAtoms.contains(ByteBuffer.wrap(Arrays.copyOf(payload, payload.length)))
            );
        }
    }

    private static boolean containsSubarray(byte[] data, byte[] needle) {
        if (needle.length == 0 || data.length < needle.length) {
            return false;
        }
        for (int offset = 0; offset <= data.length - needle.length; offset++) {
            boolean match = true;
            for (int i = 0; i < needle.length; i++) {
                if (data[offset + i] != needle[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static byte[] sentinelElement(String domain, int index) {
        byte[] seed = (domain + ":" + index + ":").getBytes(StandardCharsets.UTF_8);
        byte[] element = new byte[ELEMENT_BYTE_LENGTH];
        for (int i = 0; i < element.length; i++) {
            element[i] = (byte) (seed[i % seed.length] ^ (0x5A + 31 * i));
        }
        return element;
    }

    private static class ProtocolRun {
        private final List<PacketRecord> packetRecords;
        private final UpsuReceiverOutput output;

        private ProtocolRun(List<PacketRecord> packetRecords, UpsuReceiverOutput output) {
            this.packetRecords = packetRecords;
            this.output = output;
        }
    }

    private static class RecordingRpc implements Rpc {
        private final Rpc delegate;
        private final List<PacketRecord> packetRecords;

        private RecordingRpc(Rpc delegate) {
            this.delegate = delegate;
            packetRecords = Collections.synchronizedList(new ArrayList<>());
        }

        @Override
        public Party ownParty() {
            return delegate.ownParty();
        }

        @Override
        public Set<Party> getPartySet() {
            return delegate.getPartySet();
        }

        @Override
        public Party getParty(int partyId) {
            return delegate.getParty(partyId);
        }

        @Override
        public void connect() {
            delegate.connect();
        }

        @Override
        public void send(DataPacket dataPacket) {
            packetRecords.add(new PacketRecord(dataPacket));
            delegate.send(dataPacket);
        }

        @Override
        public DataPacket receive(DataPacketHeader header) {
            return delegate.receive(header);
        }

        @Override
        public DataPacket receiveAny(int ptoId) {
            return delegate.receiveAny(ptoId);
        }

        @Override
        public long getPayloadByteLength() {
            return delegate.getPayloadByteLength();
        }

        @Override
        public long getSendByteLength() {
            return delegate.getSendByteLength();
        }

        @Override
        public long getSendDataPacketNum() {
            return delegate.getSendDataPacketNum();
        }

        @Override
        public void synchronize() {
            delegate.synchronize();
        }

        @Override
        public void reset() {
            delegate.reset();
            clear();
        }

        @Override
        public void disconnect() {
            delegate.disconnect();
        }

        private void clear() {
            packetRecords.clear();
        }

        private List<PacketRecord> snapshot() {
            synchronized (packetRecords) {
                return List.copyOf(packetRecords);
            }
        }
    }

    private static class PacketRecord {
        private final int ptoId;
        private final int stepId;
        private final long extraInfo;
        private final PayloadType payloadType;
        private final int payloadNum;
        private final int equalLength;
        private final List<byte[]> payloads;

        private PacketRecord(DataPacket dataPacket) {
            DataPacketHeader header = dataPacket.getHeader();
            ptoId = header.getPtoId();
            stepId = header.getStepId();
            extraInfo = header.getExtraInfo();
            payloadType = dataPacket.getPayloadType();
            payloadNum = dataPacket.getPayload().size();
            equalLength = dataPacket.getEqualLength();
            payloads = dataPacket.getPayload().stream()
                .map(payload -> Arrays.copyOf(payload, payload.length))
                .collect(Collectors.toList());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PacketRecord that)) {
                return false;
            }
            return ptoId == that.ptoId
                && stepId == that.stepId
                && extraInfo == that.extraInfo
                && payloadType == that.payloadType
                && payloadNum == that.payloadNum
                && equalLength == that.equalLength;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ptoId, stepId, extraInfo, payloadType, payloadNum, equalLength);
        }
    }

    private static class SenderThread extends Thread {
        private final UpsuSender sender;
        private final int receiverElementSize;
        private final Set<ByteBuffer> senderElementSet;
        private Throwable throwable;

        private SenderThread(UpsuSender sender, int receiverElementSize, Set<ByteBuffer> senderElementSet) {
            this.sender = sender;
            this.receiverElementSize = receiverElementSize;
            this.senderElementSet = senderElementSet;
        }

        @Override
        public void run() {
            try {
                sender.init(senderElementSet.size(), receiverElementSize);
                sender.psu(senderElementSet, ELEMENT_BYTE_LENGTH);
            } catch (MpcAbortException | RuntimeException e) {
                throwable = e;
            }
        }
    }

    private static class ReceiverThread extends Thread {
        private final UpsuReceiver receiver;
        private final int senderElementSize;
        private final Set<ByteBuffer> receiverElementSet;
        private UpsuReceiverOutput output;
        private Throwable throwable;

        private ReceiverThread(UpsuReceiver receiver, int senderElementSize, Set<ByteBuffer> receiverElementSet) {
            this.receiver = receiver;
            this.senderElementSize = senderElementSize;
            this.receiverElementSet = receiverElementSet;
        }

        @Override
        public void run() {
            try {
                receiver.init(receiverElementSet, senderElementSize, ELEMENT_BYTE_LENGTH);
                output = receiver.psu(senderElementSize);
            } catch (MpcAbortException | RuntimeException e) {
                throwable = e;
            }
        }
    }
}
