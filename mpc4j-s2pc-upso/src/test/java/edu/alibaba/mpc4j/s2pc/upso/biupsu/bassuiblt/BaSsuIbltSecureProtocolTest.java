package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BA-SSU-IBLT fixed-schedule secure protocol core tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltSecureProtocolTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * public capacity.
     */
    private static final int PUBLIC_CAPACITY = 8;

    @Test
    public void testFixedScheduleWithRealMpOprfTags() throws InterruptedException, MpcAbortException {
        Set<ByteBuffer> leftSet = setOf(1L, 2L, 3L, 4L, 5L);
        Set<ByteBuffer> rightSet = setOf(4L, 5L, 6L, 7L);
        FixedInput leftInput = fixedInput(leftSet, 10_000L);
        FixedInput rightInput = fixedInput(rightSet, 20_000L);
        OprfPairOutput oprfPairOutput = realOprfTags(leftInput.fixedInputs, rightInput.fixedInputs);
        BaSsuIbltBiUpsuParams params = params();

        BaSsuIbltSecureProtocolResult result = BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params,
            leftInput.fixedInputs, leftInput.activeFlags, oprfPairOutput.senderOutput,
            rightInput.fixedInputs, rightInput.activeFlags, oprfPairOutput.receiverOutput
        );

        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
        Assert.assertEquals(BaSsuIbltSecureProtocol.fixedRoundCount(params), result.getFixedRoundCount());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.CURRENT_FIXED_LOOP_M14A, result.getShape());
        Assert.assertEquals(result.getScheduledBucketCount(), result.getActualProbeCount());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.currentFixedLoopM14a(params).getScheduledBucketCount(),
            result.getScheduledBucketCount());
    }

    @Test
    public void testQueuePeelAlignedWithLocalTags() {
        Set<ByteBuffer> leftSet = setOf(1L, 2L, 3L, 4L, 5L);
        Set<ByteBuffer> rightSet = setOf(4L, 5L, 6L, 7L);
        BaSsuIbltSecureProtocolResult result = runQueueWithLocalTags(leftSet, rightSet, params());
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.Shape.QUEUE_PEEL_ALIGNED, result.getShape());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.queuePeelMaxProbes(params()), result.getScheduledBucketCount());
        Assert.assertTrue(result.getActualProbeCount() <= result.getScheduledBucketCount());
        List<BaSsuIbltQueuePeelRetryStatus> retryStatuses = result.getRetryStatuses();
        Assert.assertEquals(params().getRetryCount(), retryStatuses.size());
        BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(retryStatuses);
        Assert.assertTrue(retryStatuses.get(0).isExecuted());
        Assert.assertTrue(retryStatuses.get(0).isSuccess());
        Assert.assertEquals(result.getActualProbeCount(), retryStatuses.get(0).getProbeCount());
    }

    @Test
    public void testQueuePeelNoIntersectionAndZeroElementWithLocalTags() {
        Set<ByteBuffer> leftSet = setOf(0L, 1L, 2L);
        Set<ByteBuffer> rightSet = setOf(9L, 10L, 11L);
        BaSsuIbltSecureProtocolResult result = runQueueWithLocalTags(leftSet, rightSet, params());
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
    }

    @Test
    public void testQueuePeelFullIntersectionWithLocalTags() {
        Set<ByteBuffer> leftSet = setOf(21L, 22L, 23L, 24L);
        Set<ByteBuffer> rightSet = setOf(21L, 22L, 23L, 24L);
        BaSsuIbltSecureProtocolResult result = runQueueWithLocalTags(leftSet, rightSet, params());
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
        Assert.assertTrue(result.getActualProbeCount() <= result.getScheduledBucketCount());
    }

    @Test
    public void testNoIntersectionAndZeroElementWithLocalTags() {
        Set<ByteBuffer> leftSet = setOf(0L, 1L, 2L);
        Set<ByteBuffer> rightSet = setOf(9L, 10L, 11L);
        BaSsuIbltSecureProtocolResult result = runWithLocalTags(leftSet, rightSet);
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
    }

    @Test
    public void testFullIntersectionWithLocalTags() {
        Set<ByteBuffer> leftSet = setOf(21L, 22L, 23L, 24L);
        Set<ByteBuffer> rightSet = setOf(21L, 22L, 23L, 24L);
        BaSsuIbltSecureProtocolResult result = runWithLocalTags(leftSet, rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
    }

    @Test
    public void testAllRetriesExecuteWithHiddenSelection() {
        Set<ByteBuffer> leftSet = setOf(31L, 32L, 33L, 34L);
        Set<ByteBuffer> rightSet = setOf(33L, 34L, 35L);
        FixedInput leftInput = fixedInput(leftSet, 50_000L);
        FixedInput rightInput = fixedInput(rightSet, 60_000L);
        BaSsuIbltBiUpsuParams retryParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setRetryCount(2)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltSecureProtocolResult result = BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, retryParams,
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs)
        );
        Set<ByteBuffer> expectedUnion = new HashSet<>(leftSet);
        expectedUnion.addAll(rightSet);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(expectedUnion, result.getLeftUnion());
        Assert.assertEquals(expectedUnion, result.getRightUnion());
        Assert.assertEquals(
            BaSsuIbltProtocolSchedule.currentFixedLoopM14a(retryParams).getScheduledBucketCount(),
            result.getScheduledBucketCount()
        );
    }

    @Test
    public void testFailureDoesNotReturnPartialProgress() {
        Set<ByteBuffer> leftSet = setOf(41L, 42L);
        Set<ByteBuffer> rightSet = setOf(43L, 44L);
        FixedInput leftInput = fixedInput(leftSet, 70_000L);
        FixedInput rightInput = fixedInput(rightSet, 80_000L);
        BaSsuIbltBiUpsuParams failingParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setRetryCount(2)
            .setDegree(3)
            .setTableLength(1)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltSecureProtocolResult result = BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, failingParams,
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs)
        );
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(0, result.getPeeledElementCount());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
        Assert.assertEquals(
            BaSsuIbltProtocolSchedule.currentFixedLoopM14a(failingParams).getScheduledBucketCount(),
            result.getScheduledBucketCount()
        );
    }

    @Test
    public void testQueuePeelFailureDoesNotReturnPartialProgress() {
        Set<ByteBuffer> leftSet = setOf(41L, 42L);
        Set<ByteBuffer> rightSet = setOf(43L, 44L);
        BaSsuIbltBiUpsuParams failingParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setRetryCount(2)
            .setDegree(3)
            .setTableLength(1)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltSecureProtocolResult result = runQueueWithLocalTags(leftSet, rightSet, failingParams);
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(0, result.getPeeledElementCount());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
        Assert.assertEquals(
            BaSsuIbltProtocolSchedule.queuePeelMaxProbes(failingParams),
            result.getScheduledBucketCount()
        );
        Assert.assertTrue(result.getActualProbeCount() <= result.getScheduledBucketCount());
        Assert.assertEquals(failingParams.getRetryCount(), result.getRetryStatuses().size());
        BaSsuIbltQueuePeelRetryStatus.validatePrefixTranscript(result.getRetryStatuses());
        for (BaSsuIbltQueuePeelRetryStatus retryStatus : result.getRetryStatuses()) {
            Assert.assertTrue(retryStatus.isExecuted());
            Assert.assertFalse(retryStatus.isSuccess());
        }
    }

    @Test
    public void testQueuePeelPartialFailureDoesNotReturnPartialProgress() {
        Set<ByteBuffer> leftSet = setOf(44L, 131L, 167L);
        Set<ByteBuffer> rightSet = setOf();
        BaSsuIbltBiUpsuParams partialFailParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, 0)
            .setRetryCount(1)
            .setDegree(3)
            .setTableLength(4)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltSecureProtocolResult result = runQueueWithLocalTags(leftSet, rightSet, partialFailParams);
        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(0, result.getPeeledElementCount());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(rightSet, result.getRightUnion());
        Assert.assertTrue(result.getActualProbeCount() > partialFailParams.getTableLength());
        Assert.assertTrue(result.getActualProbeCount() < result.getScheduledBucketCount());
    }

    @Test
    public void testQueuePeelCanSaturateProbeCap() {
        Set<ByteBuffer> leftSet = setOf(18L, 51L, 97L);
        Set<ByteBuffer> rightSet = setOf();
        BaSsuIbltBiUpsuParams capParams = new BaSsuIbltBiUpsuParams.Builder(3, 0)
            .setRetryCount(1)
            .setDegree(3)
            .setTableLength(8)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltSecureProtocolResult result = runQueueWithLocalTags(leftSet, rightSet, capParams);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(BaSsuIbltProtocolSchedule.queuePeelMaxProbes(capParams), result.getActualProbeCount());
        Assert.assertEquals(result.getScheduledBucketCount(), result.getActualProbeCount());
        Assert.assertEquals(leftSet, result.getLeftUnion());
        Assert.assertEquals(leftSet, result.getRightUnion());
    }

    @Test
    public void testMalformedInputsRejected() {
        Set<ByteBuffer> leftSet = setOf(1L, 2L);
        Set<ByteBuffer> rightSet = setOf(3L);
        FixedInput leftInput = fixedInput(leftSet, 10_000L);
        FixedInput rightInput = fixedInput(rightSet, 20_000L);
        BaSsuIbltOprfTagOutput leftTags = localTags(leftInput.fixedInputs);
        BaSsuIbltOprfTagOutput rightTags = localTags(rightInput.fixedInputs);
        boolean[] badActive = Arrays.copyOf(leftInput.activeFlags, leftInput.activeFlags.length);
        badActive[PUBLIC_CAPACITY - 1] = true;
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params(),
            leftInput.fixedInputs, badActive, leftTags,
            rightInput.fixedInputs, rightInput.activeFlags, rightTags
        ));
        byte[][] duplicateFixedInputs = Arrays.copyOf(leftInput.fixedInputs, leftInput.fixedInputs.length);
        boolean[] duplicateActive = Arrays.copyOf(leftInput.activeFlags, leftInput.activeFlags.length);
        duplicateFixedInputs[PUBLIC_CAPACITY - 1] = duplicateFixedInputs[0];
        duplicateActive[PUBLIC_CAPACITY - 1] = true;
        BaSsuIbltOprfTagOutput duplicateTags = localTags(duplicateFixedInputs);
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params(),
            duplicateFixedInputs, duplicateActive, duplicateTags,
            rightInput.fixedInputs, rightInput.activeFlags, rightTags
        ));
    }

    @Test
    public void testCurrentFixedLoopCheckBudgetRejected() {
        BaSsuIbltBiUpsuParams underBudgetParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(170)
            .setPublicPlaceSeed(20260604L)
            .build();
        Assert.assertTrue(BaSsuIbltProtocolSchedule.currentFixedLoopM14aMinCheckBits(underBudgetParams) > 170);
        Set<ByteBuffer> leftSet = setOf(1L, 2L);
        Set<ByteBuffer> rightSet = setOf(3L, 4L);
        FixedInput leftInput = fixedInput(leftSet, 90_000L);
        FixedInput rightInput = fixedInput(rightSet, 100_000L);
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, underBudgetParams,
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs)
        ));
    }

    @Test
    public void testQueuePeelCheckBudgetRejected() {
        BaSsuIbltBiUpsuParams underBudgetParams = new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(128)
            .setPublicPlaceSeed(20260604L)
            .build();
        Assert.assertTrue(BaSsuIbltProtocolSchedule.queuePeelMinCheckBits(underBudgetParams) > 128);
        Set<ByteBuffer> leftSet = setOf(1L, 2L);
        Set<ByteBuffer> rightSet = setOf(3L, 4L);
        FixedInput leftInput = fixedInput(leftSet, 110_000L);
        FixedInput rightInput = fixedInput(rightSet, 120_000L);
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltSecureProtocol.runQueuePeelAlignedReference(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, underBudgetParams,
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs)
        ));
    }

    private static BaSsuIbltSecureProtocolResult runWithLocalTags(Set<ByteBuffer> leftSet,
                                                                  Set<ByteBuffer> rightSet) {
        FixedInput leftInput = fixedInput(leftSet, 30_000L);
        FixedInput rightInput = fixedInput(rightSet, 40_000L);
        return BaSsuIbltSecureProtocol.runFixedSchedule(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params(),
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs)
        );
    }

    private static BaSsuIbltSecureProtocolResult runQueueWithLocalTags(Set<ByteBuffer> leftSet,
                                                                       Set<ByteBuffer> rightSet,
                                                                       BaSsuIbltBiUpsuParams params) {
        FixedInput leftInput = fixedInput(leftSet, 130_000L);
        FixedInput rightInput = fixedInput(rightSet, 140_000L);
        return BaSsuIbltSecureProtocol.runQueuePeelAlignedReference(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params,
            leftInput.fixedInputs, leftInput.activeFlags, localTags(leftInput.fixedInputs),
            rightInput.fixedInputs, rightInput.activeFlags, localTags(rightInput.fixedInputs)
        );
    }

    private static OprfPairOutput realOprfTags(byte[][] senderInputs, byte[][] receiverInputs)
        throws InterruptedException, MpcAbortException {
        BaSsuIbltOprfTagConfig tagConfig = new BaSsuIbltOprfTagConfig(
            OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST),
            PUBLIC_CAPACITY,
            BaSsuIbltOprfTagPipeline.byteLength(params().getTagBits()),
            BaSsuIbltOprfTagPipeline.byteLength(params().getCheckBits())
        );
        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltOprfTagSender sender = new BaSsuIbltOprfTagSender(senderRpc, receiverRpc.ownParty(), tagConfig);
            BaSsuIbltOprfTagReceiver receiver = new BaSsuIbltOprfTagReceiver(
                receiverRpc, senderRpc.ownParty(), tagConfig
            );
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            TagSenderThread senderThread = new TagSenderThread(sender, senderInputs);
            TagReceiverThread receiverThread = new TagReceiverThread(receiver, receiverInputs);
            senderThread.start();
            receiverThread.start();
            senderThread.join();
            receiverThread.join();
            if (senderThread.getException() != null) {
                throw new AssertionError(senderThread.getException());
            }
            if (receiverThread.getException() != null) {
                throw new AssertionError(receiverThread.getException());
            }
            return new OprfPairOutput(senderThread.getOutput(), receiverThread.getOutput());
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    private static BaSsuIbltOprfTagOutput localTags(byte[][] inputs) {
        byte[][] tags = new byte[inputs.length][];
        byte[][] checks = new byte[inputs.length][];
        for (int index = 0; index < inputs.length; index++) {
            tags[index] = BaSsuIbltOprfTagPipeline.tagFromPrf(inputs[index],
                BaSsuIbltOprfTagPipeline.byteLength(params().getTagBits()));
            checks[index] = BaSsuIbltOprfTagPipeline.checkFromTag(tags[index],
                BaSsuIbltOprfTagPipeline.byteLength(params().getCheckBits()));
        }
        return new BaSsuIbltOprfTagOutput(tags[0].length, checks[0].length, tags, checks);
    }

    private static FixedInput fixedInput(Set<ByteBuffer> set, long dummyOffset) {
        byte[][] fixedInputs = new byte[PUBLIC_CAPACITY][];
        boolean[] activeFlags = new boolean[PUBLIC_CAPACITY];
        int index = 0;
        for (ByteBuffer element : set) {
            ByteBuffer duplicate = element.asReadOnlyBuffer();
            duplicate.rewind();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            fixedInputs[index] = bytes;
            activeFlags[index] = true;
            index++;
        }
        while (index < PUBLIC_CAPACITY) {
            fixedInputs[index] = element(dummyOffset + index);
            index++;
        }
        return new FixedInput(fixedInputs, activeFlags);
    }

    private static BaSsuIbltBiUpsuParams params() {
        return new BaSsuIbltBiUpsuParams.Builder(PUBLIC_CAPACITY, PUBLIC_CAPACITY)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setCheckBits(182)
            .setPublicPlaceSeed(20260604L)
            .build();
    }

    private static Set<ByteBuffer> setOf(long... values) {
        Set<ByteBuffer> set = new HashSet<>();
        for (long value : values) {
            set.add(ByteBuffer.wrap(element(value)));
        }
        return set;
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }

    /**
     * fixed input.
     */
    private static class FixedInput {
        /**
         * fixed inputs.
         */
        private final byte[][] fixedInputs;
        /**
         * active flags.
         */
        private final boolean[] activeFlags;

        FixedInput(byte[][] fixedInputs, boolean[] activeFlags) {
            this.fixedInputs = fixedInputs;
            this.activeFlags = activeFlags;
        }
    }

    /**
     * OPRF pair output.
     */
    private static class OprfPairOutput {
        /**
         * sender output.
         */
        private final BaSsuIbltOprfTagOutput senderOutput;
        /**
         * receiver output.
         */
        private final BaSsuIbltOprfTagOutput receiverOutput;

        OprfPairOutput(BaSsuIbltOprfTagOutput senderOutput, BaSsuIbltOprfTagOutput receiverOutput) {
            this.senderOutput = senderOutput;
            this.receiverOutput = receiverOutput;
        }
    }

    /**
     * tag sender thread.
     */
    private static class TagSenderThread extends Thread {
        /**
         * sender.
         */
        private final BaSsuIbltOprfTagSender sender;
        /**
         * inputs.
         */
        private final byte[][] inputs;
        /**
         * output.
         */
        private BaSsuIbltOprfTagOutput output;
        /**
         * exception.
         */
        private Exception exception;

        TagSenderThread(BaSsuIbltOprfTagSender sender, byte[][] inputs) {
            this.sender = sender;
            this.inputs = inputs;
        }

        @Override
        public void run() {
            try {
                sender.init();
                output = sender.generate(inputs);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        BaSsuIbltOprfTagOutput getOutput() {
            return output;
        }

        Exception getException() {
            return exception;
        }
    }

    /**
     * tag receiver thread.
     */
    private static class TagReceiverThread extends Thread {
        /**
         * receiver.
         */
        private final BaSsuIbltOprfTagReceiver receiver;
        /**
         * inputs.
         */
        private final byte[][] inputs;
        /**
         * output.
         */
        private BaSsuIbltOprfTagOutput output;
        /**
         * exception.
         */
        private Exception exception;

        TagReceiverThread(BaSsuIbltOprfTagReceiver receiver, byte[][] inputs) {
            this.receiver = receiver;
            this.inputs = inputs;
        }

        @Override
        public void run() {
            try {
                receiver.init();
                output = receiver.generate(inputs);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        BaSsuIbltOprfTagOutput getOutput() {
            return output;
        }

        Exception getException() {
            return exception;
        }
    }
}
