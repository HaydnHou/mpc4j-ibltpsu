package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Protocol-facing BA-UnionPeel-OT bridge tests.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaUnionPeelOtTwoPartyBridgeTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testPlainPayloadBridgeManualOutputs() throws InterruptedException {
        List<BaUpotBucketOutput> outputs = manualOutputs();
        BaUnionPeelOtConfig config = baseConfig(BaUnionPeelOtMode.PLAIN_PAYLOAD);
        BaUnionPeelOtResult result = BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithOutputs(config, outputs);
        Assert.assertEquals(BaUnionPeelOtMode.PLAIN_PAYLOAD, result.getConfig().getMode());
        Assert.assertEquals(outputs.size(), result.getBucketNum());
        Assert.assertTrue(result.isChecksumEqual());
        Assert.assertTrue(result.getOnlineSendBytes() > 0);
        Assert.assertEquals(outputs.size(), result.getDecodedOutputs().size());
        assertOutputsEqual(outputs, result.getDecodedOutputs());
    }

    @Test
    public void testWireMaskedPayloadBridgeManualOutputs() throws InterruptedException {
        List<BaUpotBucketOutput> outputs = manualOutputs();
        BaUnionPeelOtConfig config = baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD);
        BaUnionPeelOtResult result = BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithOutputs(config, outputs);
        Assert.assertEquals(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD, result.getConfig().getMode());
        Assert.assertEquals(outputs.size(), result.getBucketNum());
        Assert.assertTrue(result.isChecksumEqual());
        Assert.assertTrue(result.getOnlineSendBytes() > 0);
        Assert.assertEquals(outputs.size(), result.getDecodedOutputs().size());
        assertOutputsEqual(outputs, result.getDecodedOutputs());
    }

    @Test
    public void testBridgeRejectsEmptyOutputs() throws InterruptedException {
        BaUnionPeelOtConfig config = baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithOutputs(config, List.of());
            Assert.fail("empty bridge outputs should be rejected");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    @Test
    public void testBridgeRejectsNullOutput() throws InterruptedException {
        BaUnionPeelOtConfig config = baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD);
        List<BaUpotBucketOutput> outputs = new ArrayList<>();
        outputs.add(BaUpotBucketOutput.empty(0));
        outputs.add(null);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithOutputs(config, outputs);
            Assert.fail("null bridge outputs should be rejected");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    @Test
    public void testFixedTranscriptBridgeUsesCanonicalSchedule() throws InterruptedException {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            leftSet.add(ByteBuffer.wrap(element(i)));
        }
        for (int i = 24; i < 40; i++) {
            rightSet.add(ByteBuffer.wrap(element(i)));
        }
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(32, 16)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setRetryCount(2)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltTranscriptResult transcriptResult = BaSsuIbltTranscriptResult.runBiOutput(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        BaUnionPeelOtResult result = BaUnionPeelOtTwoPartyBridge.runMemoryBridge(
            baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), transcriptResult
        );
        Assert.assertEquals(transcriptResult.getTotalBucketTranscriptCount(), result.getBucketNum());
        Assert.assertTrue(result.isChecksumEqual());
        int tableLength = transcriptResult.getSchedule().getTableLength();
        for (int index = 0; index < result.getDecodedOutputs().size(); index++) {
            Assert.assertEquals(index % tableLength, result.getDecodedOutputs().get(index).getBucketIndex());
        }
    }

    @Test
    public void testStreamingBridgeDiscardsDecodedOutputs() throws InterruptedException {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            leftSet.add(ByteBuffer.wrap(element(i)));
        }
        for (int i = 48; i < 80; i++) {
            rightSet.add(ByteBuffer.wrap(element(i)));
        }
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(64, 32)
            .setDegree(3)
            .setAlphaAnchor(4.0)
            .setRetryCount(1)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaUnionPeelOtResult result = BaUnionPeelOtTwoPartyBridge.runMemoryStreamingBridge(
            baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        Assert.assertEquals(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD, result.getConfig().getMode());
        Assert.assertEquals(params.getTableLength(), result.getBucketNum());
        Assert.assertTrue(result.isChecksumEqual());
        Assert.assertFalse(result.isDecodedOutputsRetained());
        Assert.assertEquals(params.getTableLength(), result.getDecodedOutputCount());
        Assert.assertTrue(result.getDecodedOutputs().isEmpty());
    }

    @Test
    public void testStreamingBridgeCanRetainDecodedOutputs() throws InterruptedException {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            leftSet.add(ByteBuffer.wrap(element(i)));
        }
        for (int i = 24; i < 40; i++) {
            rightSet.add(ByteBuffer.wrap(element(i)));
        }
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(32, 16)
            .setDegree(3)
            .setAlphaAnchor(4.0)
            .setRetryCount(1)
            .setPublicPlaceSeed(20260605L)
            .build();
        BaUnionPeelOtResult result = BaUnionPeelOtTwoPartyBridge.runMemoryStreamingBridge(
            baseConfig(BaUnionPeelOtMode.PLAIN_PAYLOAD), leftSet, rightSet, ELEMENT_BYTE_LENGTH, params, true
        );
        Assert.assertEquals(params.getTableLength(), result.getBucketNum());
        Assert.assertTrue(result.isChecksumEqual());
        Assert.assertTrue(result.isDecodedOutputsRetained());
        Assert.assertEquals(params.getTableLength(), result.getDecodedOutputCount());
        Assert.assertEquals(params.getTableLength(), result.getDecodedOutputs().size());
        for (int index = 0; index < result.getDecodedOutputs().size(); index++) {
            Assert.assertEquals(index, result.getDecodedOutputs().get(index).getBucketIndex());
        }
    }

    @Test
    public void testStreamingBridgeRejectsShortOutputSource() {
        List<BaUpotBucketOutput> outputs = manualOutputs().subList(0, 3);
        List<Integer> bucketIndices = List.of(0, 1, 2, 3);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithSources(
                baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), 4, outputs, bucketIndices, false
            );
            Assert.fail("short streaming output source should be rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    public void testStreamingBridgeRejectsLongOutputSource() {
        List<BaUpotBucketOutput> outputs = manualOutputs().subList(0, 5);
        List<Integer> bucketIndices = List.of(0, 1, 2, 3);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithSources(
                baseConfig(BaUnionPeelOtMode.PLAIN_PAYLOAD), 4, outputs, bucketIndices, false
            );
            Assert.fail("long streaming output source should be rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    public void testStreamingBridgeRejectsShortBucketIndexSource() {
        List<BaUpotBucketOutput> outputs = manualOutputs().subList(0, 4);
        List<Integer> bucketIndices = List.of(0, 1, 2);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithSources(
                baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), 4, outputs, bucketIndices, false
            );
            Assert.fail("short bucket index source should be rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    public void testStreamingBridgeRejectsLongBucketIndexSource() {
        List<BaUpotBucketOutput> outputs = manualOutputs().subList(0, 4);
        List<Integer> bucketIndices = List.of(0, 1, 2, 3, 4);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithSources(
                baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), 4, outputs, bucketIndices, false
            );
            Assert.fail("long bucket index source should be rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    public void testStreamingBridgeRejectsNullStreamingOutputWithoutDeadlock() {
        List<BaUpotBucketOutput> outputs = new ArrayList<>();
        outputs.add(BaUpotBucketOutput.empty(0));
        outputs.add(null);
        outputs.add(BaUpotBucketOutput.empty(2));
        outputs.add(BaUpotBucketOutput.empty(3));
        List<Integer> bucketIndices = List.of(0, 1, 2, 3);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithSources(
                baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), 4, outputs, bucketIndices, false
            );
            Assert.fail("null streaming output should be rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    public void testStreamingBridgeRejectsInvalidStreamingElementLengthWithoutDeadlock() {
        List<BaUpotBucketOutput> outputs = List.of(
            BaUpotBucketOutput.singleton(0, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, new byte[]{0x01}),
            BaUpotBucketOutput.empty(1),
            BaUpotBucketOutput.empty(2),
            BaUpotBucketOutput.empty(3)
        );
        List<Integer> bucketIndices = List.of(0, 1, 2, 3);
        try {
            BaUnionPeelOtTwoPartyBridge.runMemoryBridgeWithSources(
                baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), 4, outputs, bucketIndices, false
            );
            Assert.fail("invalid streaming element length should be rejected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (IllegalStateException ignored) {
            // expected
        }
    }

    @Test
    public void testStreamingBridgeRetryTwoRetainsRetryLocalBucketIndices() throws InterruptedException {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            leftSet.add(ByteBuffer.wrap(element(i)));
        }
        for (int i = 24; i < 40; i++) {
            rightSet.add(ByteBuffer.wrap(element(i)));
        }
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(32, 16)
            .setDegree(3)
            .setAlphaAnchor(4.0)
            .setRetryCount(2)
            .setPublicPlaceSeed(20260605L)
            .build();
        BaUnionPeelOtResult result = BaUnionPeelOtTwoPartyBridge.runMemoryStreamingBridge(
            baseConfig(BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD), leftSet, rightSet, ELEMENT_BYTE_LENGTH, params, true
        );
        Assert.assertEquals(params.getTableLength() * 2, result.getBucketNum());
        Assert.assertTrue(result.isChecksumEqual());
        Assert.assertEquals(params.getTableLength() * 2, result.getDecodedOutputs().size());
        for (int index = 0; index < params.getTableLength(); index++) {
            Assert.assertEquals(index, result.getDecodedOutputs().get(index).getBucketIndex());
            Assert.assertEquals(index, result.getDecodedOutputs().get(index + params.getTableLength()).getBucketIndex());
        }
    }

    @Test
    public void testConfigRejectsNullMaskSeed() {
        try {
            new BaUnionPeelOtConfig.Builder().setMaskSeed(null);
            Assert.fail("null mask seed should be rejected");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    @Test
    public void testConfigRejectsNullBenchmarkConfig() {
        try {
            BaUnionPeelOtConfig.fromBenchmarkConfig(null, BaUnionPeelOtMode.WIRE_MASKED_PAYLOAD);
            Assert.fail("null benchmark config should be rejected");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
    }

    private static BaUnionPeelOtConfig baseConfig(BaUnionPeelOtMode mode) {
        return new BaUnionPeelOtConfig.Builder()
            .setMode(mode)
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(182)
            .setTagBits(182)
            .setCotNumPerBucket(2)
            .setOnlineBatchSize(11)
            .build();
    }

    private static List<BaUpotBucketOutput> manualOutputs() {
        List<BaUpotBucketOutput> outputs = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int bucketIndex = i % 16;
            switch (i % 5) {
                case 0:
                    outputs.add(BaUpotBucketOutput.empty(bucketIndex));
                    break;
                case 1:
                    outputs.add(BaUpotBucketOutput.singleton(
                        bucketIndex, BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, element(i)
                    ));
                    break;
                case 2:
                    outputs.add(BaUpotBucketOutput.singleton(
                        bucketIndex, BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, element(i)
                    ));
                    break;
                case 3:
                    outputs.add(BaUpotBucketOutput.singleton(
                        bucketIndex, BaUpotBucketOutput.CaseType.SHARED_SINGLETON, element(i)
                    ));
                    break;
                default:
                    outputs.add(BaUpotBucketOutput.blocked(bucketIndex));
                    break;
            }
        }
        return outputs;
    }

    private static void assertOutputsEqual(List<BaUpotBucketOutput> expected, List<BaUpotBucketOutput> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            Assert.assertEquals(expected.get(index).getBucketIndex(), actual.get(index).getBucketIndex());
            Assert.assertEquals(expected.get(index).getCaseType(), actual.get(index).getCaseType());
            Assert.assertEquals(expected.get(index).isSingleton(), actual.get(index).isSingleton());
            if (expected.get(index).isSingleton()) {
                Assert.assertArrayEquals(expected.get(index).getElement(), actual.get(index).getElement());
            }
        }
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }
}
