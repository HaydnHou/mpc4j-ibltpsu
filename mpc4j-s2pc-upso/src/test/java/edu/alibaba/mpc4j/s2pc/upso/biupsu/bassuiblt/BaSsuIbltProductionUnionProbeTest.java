package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Production UP-BA-UPOT bucket-probe tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeTest {
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
     * seed.
     */
    private static final byte[] SEED = filled(32, (byte) 0x42);

    @Test
    public void testConfigProductionGate() {
        BaSsuIbltProductionUnionProbeBackendConfig config = config();
        Assert.assertTrue(config.isSpecializedBucketProbe());
        Assert.assertFalse(config.isQueuePeelProductionReady());
        Assert.assertEquals(
            BaSsuIbltProductionUnionProbeBackendConfig.NOT_PRODUCTION_READY_REASON,
            config.getProductionReadinessReason()
        );
        Assert.assertEquals(12, config.cotNum(4));
        Assert.assertEquals(config.capsuleByteLength(),
            1 + ELEMENT_BYTE_LENGTH + TAG_BYTE_LENGTH + CHECK_BYTE_LENGTH + config.getAuthTagByteLength());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltProductionUnionProbeBackendConfig.Builder().setCotNumPerProbe(0).build());
    }

    @Test
    public void testTruthTable() throws MpcAbortException {
        assertBottom(input(0, emptyCell(), emptyCell()));
        assertSingleton(input(1, singletonCell(element(1L)), emptyCell()), element(1L));
        assertSingleton(input(2, emptyCell(), singletonCell(element(2L))), element(2L));
        BaSsuIbltSecureCellView shared = singletonCell(element(3L));
        assertSingleton(input(3, shared, shared), element(3L));
        assertBottom(input(4, singletonCell(element(4L)), singletonCell(element(5L))));
        assertBottom(input(5, blockedCell(element(6L)), emptyCell()));
        assertBottom(input(6, emptyCell(), blockedCell(element(7L))));
    }

    @Test
    public void testCapsuleLengthIsCaseIndependent() {
        BaSsuIbltProductionUnionProbeBackendConfig config = config();
        BaSsuIbltProductionUnionProbeCodec codec = new BaSsuIbltProductionUnionProbeCodec(config, SEED);
        int expectedLength = config.capsuleByteLength();
        BaSsuIbltProductionUnionProbeCapsule empty = codec.encode(0, emptyCell());
        BaSsuIbltProductionUnionProbeCapsule singleton = codec.encode(0, singletonCell(element(11L)));
        BaSsuIbltProductionUnionProbeCapsule blocked = codec.encode(0, blockedCell(element(12L)));
        Assert.assertEquals(expectedLength, empty.getEncoded().length);
        Assert.assertEquals(expectedLength, singleton.getEncoded().length);
        Assert.assertEquals(expectedLength, blocked.getEncoded().length);
        Assert.assertFalse(containsSubArray(singleton.getEncoded(), element(11L)));
        Assert.assertFalse(singleton.getEncoded()[0] == 0x01);
    }

    @Test
    public void testDummyAndNonOutputBranchesAuthenticateButDoNotOutput() throws MpcAbortException {
        BaSsuIbltSecureBucketInput emptyInput = input(7, emptyCell(), emptyCell());
        BaSsuIbltProductionUnionProbeOutput emptyOutput = runProbe(emptyInput);
        Assert.assertFalse(emptyOutput.isSingleton());

        BaSsuIbltSecureBucketInput blockedInput = input(8, blockedCell(element(13L)), singletonCell(element(14L)));
        BaSsuIbltProductionUnionProbeOutput blockedOutput = runProbe(blockedInput);
        Assert.assertFalse(blockedOutput.isSingleton());
        Assert.assertThrows(IllegalStateException.class, blockedOutput::getElement);
    }

    @Test
    public void testMalformedCapsuleRejected() {
        BaSsuIbltProductionUnionProbeBackendConfig config = config();
        BaSsuIbltProductionUnionProbeCodec codec = new BaSsuIbltProductionUnionProbeCodec(config, SEED);
        BaSsuIbltProductionUnionProbeReferenceCodec referenceCodec =
            new BaSsuIbltProductionUnionProbeReferenceCodec(config, SEED);
        BaSsuIbltProductionUnionProbeCapsule senderCapsule = codec.encode(9, singletonCell(element(15L)));
        BaSsuIbltProductionUnionProbeCapsule receiverCapsule = codec.encode(9, emptyCell());
        byte[] tampered = senderCapsule.getEncoded();
        tampered[tampered.length - 1] ^= 0x01;
        BaSsuIbltProductionUnionProbeCapsule badCapsule =
            new BaSsuIbltProductionUnionProbeCapsule(9, tampered, config.capsuleByteLength());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> referenceCodec.open(9, badCapsule, receiverCapsule));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> referenceCodec.open(10, senderCapsule, receiverCapsule));
    }

    @Test
    public void testThreadWrappers() throws InterruptedException {
        BaSsuIbltProductionUnionProbeBackendConfig config = config();
        BaSsuIbltSecureBucketInput bucketInput = input(10, singletonCell(element(16L)), emptyCell());
        BaSsuIbltProductionUnionProbeSender sender =
            new BaSsuIbltProductionUnionProbeSender(config, SEED);
        BaSsuIbltProductionUnionProbeSenderThread senderThread =
            new BaSsuIbltProductionUnionProbeSenderThread(sender, 10, bucketInput, 1, ELEMENT_BYTE_LENGTH);
        senderThread.start();
        senderThread.join();
        if (senderThread.getException() != null) {
            throw new AssertionError(senderThread.getException());
        }
        BaSsuIbltProductionUnionProbeReceiver receiver =
            new BaSsuIbltProductionUnionProbeReceiver(config, SEED);
        BaSsuIbltProductionUnionProbeReceiverThread receiverThread =
            new BaSsuIbltProductionUnionProbeReceiverThread(
                receiver, 10, bucketInput, senderThread.getCapsule(), 1, ELEMENT_BYTE_LENGTH
            );
        receiverThread.start();
        receiverThread.join();
        Assert.assertNotNull(receiverThread.getException());
        Assert.assertTrue(receiverThread.getException().getMessage().contains("remote-state-hiding UP-BA-UPOT"));
        Assert.assertNull(receiverThread.getOutput());
    }

    private static void assertSingleton(BaSsuIbltSecureBucketInput input, byte[] expected) throws MpcAbortException {
        BaSsuIbltProductionUnionProbeOutput output = runProbe(input);
        Assert.assertTrue(output.isSingleton());
        Assert.assertArrayEquals(expected, output.getElement());
    }

    private static void assertBottom(BaSsuIbltSecureBucketInput input) throws MpcAbortException {
        BaSsuIbltProductionUnionProbeOutput output = runProbe(input);
        Assert.assertFalse(output.isSingleton());
    }

    private static BaSsuIbltProductionUnionProbeOutput runProbe(BaSsuIbltSecureBucketInput input)
        throws MpcAbortException {
        BaSsuIbltProductionUnionProbeBackendConfig config = config();
        BaSsuIbltProductionUnionProbeCodec codec = new BaSsuIbltProductionUnionProbeCodec(config, SEED);
        BaSsuIbltProductionUnionProbeReferenceCodec referenceCodec =
            new BaSsuIbltProductionUnionProbeReferenceCodec(config, SEED);
        BaSsuIbltProductionUnionProbeCapsule anchorCapsule = codec.encode(
            input.getBucketIndex(), BaSsuIbltProductionUnionProbeLocalLayer.ANCHOR.select(input)
        );
        BaSsuIbltProductionUnionProbeCapsule shadowCapsule = codec.encode(
            input.getBucketIndex(), BaSsuIbltProductionUnionProbeLocalLayer.SHADOW.select(input)
        );
        return referenceCodec.open(input.getBucketIndex(), anchorCapsule, shadowCapsule);
    }

    private static BaSsuIbltProductionUnionProbeBackendConfig config() {
        return new BaSsuIbltProductionUnionProbeBackendConfig.Builder()
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setTagByteLength(TAG_BYTE_LENGTH)
            .setCheckByteLength(CHECK_BYTE_LENGTH)
            .setCotNumPerProbe(3)
            .build();
    }

    private static BaSsuIbltSecureBucketInput input(int bucketIndex, BaSsuIbltSecureCellView anchor,
                                                    BaSsuIbltSecureCellView shadow) {
        return BaSsuIbltSecureBucketInput.of(bucketIndex, anchor, shadow);
    }

    private static BaSsuIbltSecureCellView emptyCell() {
        return BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH);
    }

    private static BaSsuIbltSecureCellView singletonCell(byte[] element) {
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(element, TAG_BYTE_LENGTH);
        return BaSsuIbltSecureCellView.of(1, element, tag, BaSsuIbltOprfTagPipeline.checkFromTag(tag, CHECK_BYTE_LENGTH));
    }

    private static BaSsuIbltSecureCellView blockedCell(byte[] element) {
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(element, TAG_BYTE_LENGTH);
        byte[] badCheck = BaSsuIbltOprfTagPipeline.checkFromTag(tag, CHECK_BYTE_LENGTH);
        badCheck[0] ^= 0x01;
        return BaSsuIbltSecureCellView.of(1, element, tag, badCheck);
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
}
