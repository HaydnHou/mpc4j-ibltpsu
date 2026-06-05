package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wire-masked payload-bound BA-UPOT two-party benchmark tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotWireMaskedPayloadTwoPartyBenchmarkTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testManualOutputs() throws InterruptedException {
        List<BaUpotBucketOutput> outputs = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int bucketIndex = 5 * i + 2;
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
        BaUpotTwoPartyBenchmark.BenchmarkConfig config = new BaUpotTwoPartyBenchmark.BenchmarkConfig()
            .setBucketNum(outputs.size())
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(182)
            .setTagBits(182)
            .setOnlineBatchSize(19);
        BaUpotWireMaskedPayloadTwoPartyBenchmark.Result result =
            BaUpotWireMaskedPayloadTwoPartyBenchmark.runMemoryBenchmarkWithOutputs(
                config, outputs, BaUpotWireMaskedPayloadTwoPartyBenchmark.DEFAULT_MASK_SEED
            );
        Assert.assertTrue(result.isChecksumEqual());
        assertOutputsEqual(outputs, result.getDecodedOutputs());
        BaUpotWireMaskedOutputCapsuleCodec codec = BaUpotWireMaskedOutputCapsuleCodec.fromConfig(
            config.createBaUpotConfig(), BaUpotWireMaskedPayloadTwoPartyBenchmark.DEFAULT_MASK_SEED
        );
        long expectedPayloadBytes = (long) outputs.size() * codec.capsuleByteLength();
        Assert.assertTrue(result.getOnlineSendBytes() >= expectedPayloadBytes);
        Assert.assertTrue(result.getOnlineSendBytes() < expectedPayloadBytes + 2048);
    }

    @Test
    public void testTraceOutputs() throws InterruptedException {
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
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(20260604L)
            .build();
        BaSsuIbltPlainResult plainResult = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        Assert.assertTrue(plainResult.isSuccess());
        BaUpotTwoPartyBenchmark.BenchmarkConfig config = new BaUpotTwoPartyBenchmark.BenchmarkConfig()
            .setBucketNum(plainResult.getBucketTrace().size())
            .setElementByteLength(ELEMENT_BYTE_LENGTH)
            .setCheckBits(plainResult.getRecommendedCheckBits())
            .setTagBits(plainResult.getRecommendedCheckBits())
            .setOnlineBatchSize(41);
        BaUpotWireMaskedPayloadTwoPartyBenchmark.Result result =
            BaUpotWireMaskedPayloadTwoPartyBenchmark.runMemoryBenchmark(config, plainResult.getBucketTrace());
        Assert.assertTrue(result.isChecksumEqual());
        List<BaUpotBucketOutput> expectedOutputs = new ArrayList<>(plainResult.getBucketTrace().size());
        for (BaSsuIbltBucketTrace trace : plainResult.getBucketTrace()) {
            expectedOutputs.add(trace.getOutput());
        }
        assertOutputsEqual(expectedOutputs, result.getDecodedOutputs());
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
