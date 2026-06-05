package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain BA-UPOT output capsule codec tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotPlainOutputCapsuleCodecTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 23;
    /**
     * tag byte length.
     */
    private static final int TAG_BYTE_LENGTH = 23;

    @Test
    public void testRoundTripAllCases() {
        BaUpotPlainOutputCapsuleCodec codec = new BaUpotPlainOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH
        );
        List<BaUpotBucketOutput> outputs = List.of(
            BaUpotBucketOutput.empty(0),
            BaUpotBucketOutput.singleton(1, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(1)),
            BaUpotBucketOutput.singleton(2, BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, element(2)),
            BaUpotBucketOutput.singleton(3, BaUpotBucketOutput.CaseType.SHARED_SINGLETON, element(3)),
            BaUpotBucketOutput.blocked(4)
        );
        for (BaUpotBucketOutput output : outputs) {
            byte[] capsule = codec.encode(output);
            Assert.assertEquals(1 + ELEMENT_BYTE_LENGTH + CHECK_BYTE_LENGTH + TAG_BYTE_LENGTH, capsule.length);
            BaUpotBucketOutput decoded = codec.decode(output.getBucketIndex(), capsule);
            assertOutputEquals(output, decoded);
        }
        List<byte[]> capsules = codec.encodeBatch(outputs);
        List<BaUpotBucketOutput> decoded = codec.decodeBatch(List.of(0, 1, 2, 3, 4), capsules);
        for (int index = 0; index < outputs.size(); index++) {
            assertOutputEquals(outputs.get(index), decoded.get(index));
        }
    }

    @Test
    public void testTamperRejected() {
        BaUpotPlainOutputCapsuleCodec codec = new BaUpotPlainOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH
        );
        BaUpotBucketOutput output = BaUpotBucketOutput.singleton(
            7, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(7)
        );
        byte[] capsule = codec.encode(output);
        capsule[1] ^= 1;
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(7, capsule));
    }

    @Test
    public void testWrongBucketRejected() {
        BaUpotPlainOutputCapsuleCodec codec = new BaUpotPlainOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH
        );
        BaUpotBucketOutput output = BaUpotBucketOutput.singleton(
            9, BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, element(9)
        );
        byte[] capsule = codec.encode(output);
        Assert.assertThrows(IllegalArgumentException.class, () -> codec.decode(10, capsule));
    }

    @Test
    public void testTraceRoundTrip() {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            leftSet.add(ByteBuffer.wrap(element(i)));
        }
        for (int i = 16; i < 40; i++) {
            rightSet.add(ByteBuffer.wrap(element(i)));
        }
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(32, 24)
            .setDegree(3)
            .setAlphaAnchor(6.0)
            .setPublicPlaceSeed(20260603L)
            .build();
        BaSsuIbltPlainResult result = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(result.hasBucketTrace());
        BaUpotPlainOutputCapsuleCodec codec = new BaUpotPlainOutputCapsuleCodec(
            ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH, TAG_BYTE_LENGTH
        );
        List<byte[]> capsules = codec.encodeTraceOutputs(result.getBucketTrace());
        Assert.assertEquals(result.getBucketTrace().size(), capsules.size());
        List<BaUpotBucketOutput> decodedOutputs = codec.decodeTraceOutputs(result.getBucketTrace(), capsules);
        for (int index = 0; index < decodedOutputs.size(); index++) {
            assertOutputEquals(result.getBucketTrace().get(index).getOutput(), decodedOutputs.get(index));
        }
    }

    private static void assertOutputEquals(BaUpotBucketOutput expected, BaUpotBucketOutput actual) {
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
}
