package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * BA-UPOT case-gate circuit tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotCaseGateCircuitTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 32;

    @Test
    public void testAllCasesMatchIdeal() {
        BaUpotBucketInput[] inputs = new BaUpotBucketInput[]{
            BaUpotBucketInput.empty(0, ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH),
            BaUpotBucketInput.of(
                1, 1, 0, element(1), new byte[ELEMENT_BYTE_LENGTH],
                check(element(1)), new byte[CHECK_BYTE_LENGTH]
            ),
            BaUpotBucketInput.of(
                2, 0, 1, new byte[ELEMENT_BYTE_LENGTH], element(2),
                new byte[CHECK_BYTE_LENGTH], check(element(2))
            ),
            BaUpotBucketInput.of(
                3, 1, 1, element(3), element(3), check(element(3)), check(element(3))
            ),
            BaUpotBucketInput.of(
                4, 1, 1, element(4), element(5), check(element(4)), check(element(5))
            ),
            BaUpotBucketInput.of(
                5, 1, 0, element(6), new byte[ELEMENT_BYTE_LENGTH],
                tamperedCheck(element(6)), new byte[CHECK_BYTE_LENGTH]
            ),
        };
        for (BaUpotBucketInput input : inputs) {
            BaUpotBucketOutput ideal = BaUpotIdeal.evaluate(input);
            BaUpotCaseGateCircuit.Evaluation evaluation = BaUpotCaseGateCircuit.evaluate(input);
            assertOutputEquals(ideal, evaluation.getOutput());
            Assert.assertEquals(8, evaluation.getGateStats().getAndGateCount());
            Assert.assertEquals(6, evaluation.getGateStats().getXorGateCount());
            Assert.assertEquals(1, evaluation.getGateStats().getNotGateCount());
        }
    }

    @Test
    public void testLivePeelWithCaseGateEvaluator() {
        Set<ByteBuffer> leftSet = new HashSet<>();
        Set<ByteBuffer> rightSet = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            leftSet.add(ByteBuffer.wrap(element(i + 1L)));
        }
        for (int i = 48; i < 80; i++) {
            rightSet.add(ByteBuffer.wrap(element(i + 1L)));
        }
        BaSsuIbltBiUpsuParams params = new BaSsuIbltBiUpsuParams.Builder(64, 32)
            .setDegree(3)
            .setAlphaAnchor(5.0)
            .setPublicPlaceSeed(20260605L)
            .build();
        BaSsuIbltPlainResult idealResult = BaSsuIbltPlainProtocol.runBiOutputWithTrace(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params
        );
        BaUpotCaseGateEvaluator evaluator = new BaUpotCaseGateEvaluator();
        BaSsuIbltPlainResult gateResult = BaSsuIbltPlainProtocol.runBiOutputWithBucketEvaluator(
            leftSet, rightSet, ELEMENT_BYTE_LENGTH, params, evaluator
        );
        Assert.assertTrue(gateResult.isSuccess());
        Assert.assertEquals(idealResult.getLeftUnion(), gateResult.getLeftUnion());
        Assert.assertEquals(gateResult.getScheduledBucketCount(), evaluator.getBucketCount());
        Assert.assertEquals(
            gateResult.getScheduledBucketCount() * 8,
            evaluator.getAggregateGateStats().getAndGateCount()
        );
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

    private static byte[] check(byte[] element) {
        return BaUpotIdeal.digest(element, CHECK_BYTE_LENGTH);
    }

    private static byte[] tamperedCheck(byte[] element) {
        byte[] check = check(element);
        check[0] ^= 1;
        return check;
    }
}
