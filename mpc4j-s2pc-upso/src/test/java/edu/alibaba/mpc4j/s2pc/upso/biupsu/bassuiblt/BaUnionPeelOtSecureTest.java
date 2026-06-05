package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * COT-backed BA-UnionPeel-OT secure tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaUnionPeelOtSecureTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * tag byte length.
     */
    private static final int TAG_BYTE_LENGTH = 24;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 16;
    /**
     * bucket count.
     */
    private static final int BUCKET_NUM = 5;

    @Test
    public void testSecureBackendDecodesFixedBuckets() throws InterruptedException, MpcAbortException {
        BaUnionPeelOtSecureConfig config = new BaUnionPeelOtSecureConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCotNumPerBucket(3)
            .setOnlineBatchSize(2)
            .build();
        Assert.assertFalse(config.isObliviousBranchSelection());
        List<BaSsuIbltSecureBucketInput> bucketInputs = bucketInputs();

        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaUnionPeelOtSender sender = new BaUnionPeelOtSender(senderRpc, receiverRpc.ownParty(), config);
            BaUnionPeelOtReceiver receiver = new BaUnionPeelOtReceiver(receiverRpc, senderRpc.ownParty(), config);
            int taskId = Math.abs(new SecureRandom().nextInt());
            sender.setTaskId(taskId);
            receiver.setTaskId(taskId);
            SenderThread senderThread = new SenderThread(sender, bucketInputs);
            ReceiverThread receiverThread = new ReceiverThread(receiver, bucketInputs.size());
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
            BaUnionPeelOtSenderOutput senderOutput = senderThread.getOutput();
            BaUnionPeelOtReceiverOutput receiverOutput = receiverThread.getOutput();
            Assert.assertEquals(BUCKET_NUM, senderOutput.getBucketNum());
            Assert.assertEquals(BUCKET_NUM * config.getCotNumPerBucket(), senderOutput.getCotNum());
            Assert.assertEquals(senderOutput.getCotNum(), receiverOutput.getCotNum());
            Assert.assertEquals(senderOutput.getChecksum(), receiverOutput.getChecksum());
            Assert.assertEquals(BUCKET_NUM, receiverOutput.getOutputs().size());
            for (int index = 0; index < BUCKET_NUM; index++) {
                assertSameOutput(BaUnionPeelOtSecureEvaluator.evaluate(bucketInputs.get(index)),
                    receiverOutput.getOutputs().get(index));
            }
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testSecureCapsuleHasNoClearCaseOrElement() {
        byte[] seed = filled(32, (byte) 0x5A);
        BaUnionPeelOtSecureOutputCapsuleCodec codec = new BaUnionPeelOtSecureOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, 16, seed
        );
        BaUpotBucketOutput output = BaUpotBucketOutput.singleton(
            0, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(123L)
        );
        byte[] capsule = codec.encode(0, output);
        Assert.assertEquals(1 + ELEMENT_BYTE_LENGTH + 16, capsule.length);
        Assert.assertFalse(containsSubArray(capsule, element(123L)));
        Assert.assertFalse(capsule[0] == 0x01);
        assertSameOutput(output, codec.decode(0, 0, capsule));
    }

    @Test
    public void testMalformedInputsRejected() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaUnionPeelOtSecureConfig.Builder().setCotNumPerBucket(0).build());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaUnionPeelOtSecureOutputCapsuleCodec(ELEMENT_BYTE_LENGTH, 0, filled(32, (byte) 1)));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaUnionPeelOtSender.checkBucketInputs(null, BUCKET_NUM));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaUnionPeelOtSender.checkBucketInputs(bucketInputs(), BUCKET_NUM + 1));
        List<BaSsuIbltSecureBucketInput> bucketInputs = new ArrayList<>(bucketInputs());
        bucketInputs.set(0, null);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaUnionPeelOtSender.checkBucketInputs(bucketInputs, BUCKET_NUM));
        BaUnionPeelOtSecureOutputCapsuleCodec codec = new BaUnionPeelOtSecureOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, 16, filled(32, (byte) 2)
        );
        byte[] capsule = codec.encode(0, BaUpotBucketOutput.empty(0));
        capsule[capsule.length - 1] ^= 0x01;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(0, 0, capsule));
    }

    private static List<BaSsuIbltSecureBucketInput> bucketInputs() {
        List<BaSsuIbltSecureBucketInput> inputs = new ArrayList<>();
        inputs.add(BaSsuIbltSecureBucketInput.empty(0, ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH));
        inputs.add(BaSsuIbltSecureBucketInput.of(1,
            singletonCell(element(1L)),
            BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH)));
        inputs.add(BaSsuIbltSecureBucketInput.of(2,
            BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH),
            singletonCell(element(2L))));
        BaSsuIbltSecureCellView shared = singletonCell(element(3L));
        inputs.add(BaSsuIbltSecureBucketInput.of(3, shared, shared));
        inputs.add(BaSsuIbltSecureBucketInput.of(4,
            BaSsuIbltSecureCellView.of(2, element(4L), tag(element(4L)), check(tag(element(4L)))),
            BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH)));
        return inputs;
    }

    private static BaSsuIbltSecureCellView singletonCell(byte[] element) {
        byte[] tag = tag(element);
        return BaSsuIbltSecureCellView.of(1, element, tag, check(tag));
    }

    private static byte[] tag(byte[] element) {
        return BaSsuIbltOprfTagPipeline.tagFromPrf(element, TAG_BYTE_LENGTH);
    }

    private static byte[] check(byte[] tag) {
        return BaSsuIbltOprfTagPipeline.checkFromTag(tag, CHECK_BYTE_LENGTH);
    }

    private static void assertSameOutput(BaUpotBucketOutput expected, BaUpotBucketOutput actual) {
        Assert.assertEquals(expected.getBucketIndex(), actual.getBucketIndex());
        Assert.assertEquals(expected.getCaseType(), actual.getCaseType());
        Assert.assertEquals(expected.isSingleton(), actual.isSingleton());
        if (expected.isSingleton()) {
            Assert.assertArrayEquals(expected.getElement(), actual.getElement());
        }
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }

    private static byte[] filled(int byteLength, byte value) {
        byte[] bytes = new byte[byteLength];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static boolean containsSubArray(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
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

    /**
     * sender thread.
     */
    private static class SenderThread extends Thread {
        /**
         * sender.
         */
        private final BaUnionPeelOtSender sender;
        /**
         * bucket inputs.
         */
        private final List<BaSsuIbltSecureBucketInput> bucketInputs;
        /**
         * output.
         */
        private BaUnionPeelOtSenderOutput output;
        /**
         * exception.
         */
        private Exception exception;

        SenderThread(BaUnionPeelOtSender sender, List<BaSsuIbltSecureBucketInput> bucketInputs) {
            this.sender = sender;
            this.bucketInputs = bucketInputs;
        }

        @Override
        public void run() {
            try {
                sender.init(bucketInputs.size());
                output = sender.execute(bucketInputs);
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        BaUnionPeelOtSenderOutput getOutput() {
            return output;
        }

        Exception getException() {
            return exception;
        }
    }

    /**
     * receiver thread.
     */
    private static class ReceiverThread extends Thread {
        /**
         * receiver.
         */
        private final BaUnionPeelOtReceiver receiver;
        /**
         * bucket count.
         */
        private final int bucketNum;
        /**
         * output.
         */
        private BaUnionPeelOtReceiverOutput output;
        /**
         * exception.
         */
        private Exception exception;

        ReceiverThread(BaUnionPeelOtReceiver receiver, int bucketNum) {
            this.receiver = receiver;
            this.bucketNum = bucketNum;
        }

        @Override
        public void run() {
            try {
                receiver.init(bucketNum);
                output = receiver.execute();
            } catch (MpcAbortException | RuntimeException e) {
                exception = e;
            }
        }

        BaUnionPeelOtReceiverOutput getOutput() {
            return output;
        }

        Exception getException() {
            return exception;
        }
    }
}
