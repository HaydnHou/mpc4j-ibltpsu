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

/**
 * BA-SSU-IBLT real MP-OPRF tag protocol tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltOprfTagProtocolTest {
    /**
     * public capacity.
     */
    private static final int PUBLIC_CAPACITY = 8;
    /**
     * tag byte length.
     */
    private static final int TAG_BYTE_LENGTH = 24;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 16;
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testRealMpOprfTagsMatchAcrossIndexes() throws InterruptedException, MpcAbortException {
        BaSsuIbltOprfTagConfig config = defaultTagConfig(PUBLIC_CAPACITY);
        byte[][] senderInputs = fixedInputs(1000L, PUBLIC_CAPACITY + 4);
        byte[][] receiverInputs = fixedInputs(2000L, PUBLIC_CAPACITY);
        byte[] commonElement = element(7L);
        senderInputs[10] = Arrays.copyOf(commonElement, commonElement.length);
        receiverInputs[5] = Arrays.copyOf(commonElement, commonElement.length);

        RpcManager rpcManager = new MemoryRpcManager(2);
        Rpc senderRpc = rpcManager.getRpc(0);
        Rpc receiverRpc = rpcManager.getRpc(1);
        senderRpc.connect();
        receiverRpc.connect();
        try {
            BaSsuIbltOprfTagSender sender = new BaSsuIbltOprfTagSender(senderRpc, receiverRpc.ownParty(), config);
            BaSsuIbltOprfTagReceiver receiver = new BaSsuIbltOprfTagReceiver(receiverRpc, senderRpc.ownParty(), config);
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
            BaSsuIbltOprfTagOutput senderOutput = senderThread.getOutput();
            BaSsuIbltOprfTagOutput receiverOutput = receiverThread.getOutput();
            Assert.assertEquals(PUBLIC_CAPACITY + 4, senderOutput.getBatchSize());
            Assert.assertEquals(PUBLIC_CAPACITY, receiverOutput.getBatchSize());
            Assert.assertArrayEquals(senderOutput.getTag(10), receiverOutput.getTag(5));
            Assert.assertArrayEquals(senderOutput.getCheck(10), receiverOutput.getCheck(5));
            Assert.assertFalse(Arrays.equals(senderOutput.getTag(10), senderOutput.getCheck(10)));
        } finally {
            senderRpc.disconnect();
            receiverRpc.disconnect();
        }
    }

    @Test
    public void testOutputDefensiveCopies() {
        byte[][] tags = new byte[][]{filled(TAG_BYTE_LENGTH, (byte) 0x11), filled(TAG_BYTE_LENGTH, (byte) 0x22)};
        byte[][] checks = new byte[][]{filled(CHECK_BYTE_LENGTH, (byte) 0x33), filled(CHECK_BYTE_LENGTH, (byte) 0x44)};
        BaSsuIbltOprfTagOutput output = new BaSsuIbltOprfTagOutput(
            TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH, tags, checks
        );
        tags[0][0] ^= 0x01;
        checks[0][0] ^= 0x01;
        Assert.assertEquals(0x11, output.getTag(0)[0]);
        Assert.assertEquals(0x33, output.getCheck(0)[0]);
        byte[] returnedTag = output.getTag(1);
        returnedTag[0] ^= 0x01;
        Assert.assertEquals(0x22, output.getTag(1)[0]);
    }

    @Test
    public void testConfigValidationAndCapacityFloor() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltOprfTagConfig(null, PUBLIC_CAPACITY, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltOprfTagConfig(
                OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST), 1, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH
            ));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltOprfTagConfig(
                OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST),
                PUBLIC_CAPACITY, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH
            ));

        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(64, 16)
            .setTagBits(TAG_BYTE_LENGTH * Byte.SIZE)
            .setCheckBits(CHECK_BYTE_LENGTH * Byte.SIZE)
            .build();
        Assert.assertEquals(16, BaSsuIbltOprfTagConfig.fromParams(
            OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST), params
        ).getPublicCapacity());
        BaSsuIbltBiUpsuParams oneSidedParams = new BaSsuIbltBiUpsuParams.Builder(64, 0)
            .setTagBits(TAG_BYTE_LENGTH * Byte.SIZE)
            .setCheckBits(CHECK_BYTE_LENGTH * Byte.SIZE)
            .build();
        Assert.assertEquals(2, BaSsuIbltOprfTagConfig.fromParams(
            OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST), oneSidedParams
        ).getPublicCapacity());
    }

    @Test
    public void testFixedInputsMustUsePublicCapacity() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagSender.checkFixedInputs(null));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagSender.checkFixedInputs(new byte[0][ELEMENT_BYTE_LENGTH]));
        byte[][] inputs = fixedInputs(3000L, PUBLIC_CAPACITY);
        inputs[0] = null;
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagSender.checkFixedInputs(inputs));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltOprfTagSender.checkFixedInputs(fixedInputs(4000L, PUBLIC_CAPACITY - 1),
                PUBLIC_CAPACITY));
        BaSsuIbltOprfTagSender.checkFixedInputs(fixedInputs(5000L, PUBLIC_CAPACITY + 4));
    }

    private static BaSsuIbltOprfTagConfig defaultTagConfig(int publicCapacity) {
        return new BaSsuIbltOprfTagConfig(
            OprfFactory.createMpOprfDefaultConfig(SecurityModel.SEMI_HONEST),
            publicCapacity,
            TAG_BYTE_LENGTH,
            CHECK_BYTE_LENGTH
        );
    }

    private static byte[][] fixedInputs(long offset, int size) {
        byte[][] inputs = new byte[size][];
        for (int index = 0; index < size; index++) {
            inputs[index] = element(offset + index);
        }
        return inputs;
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
