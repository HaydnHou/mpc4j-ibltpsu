package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * BA-SSU-IBLT union-probe contract tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUnionProbeContractTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * tag byte length.
     */
    private static final int TAG_BYTE_LENGTH = 23;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 23;

    @Test
    public void testOutputDoesNotExposeCaseOrSourceLabels() {
        for (Method method : BaSsuIbltUnionProbeOutput.class.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
        }
        for (Method method : BaSsuIbltUnionProbeCapsule.class.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("anchor"));
            Assert.assertFalse(name.contains("shadow"));
        }
    }

    @Test
    public void testLocalGadgetReturnsSourceAgnosticSingleton() {
        byte[] element = element(7L);
        BaSsuIbltSecureBucketInput input = BaSsuIbltSecureBucketInput.of(
            3, singletonCell(element), emptyCell()
        );
        BaSsuIbltUnionProbeOutput output = BaSsuIbltUnionProbeGadget.evaluateLocal(input);
        Assert.assertTrue(output.isSingleton());
        Assert.assertEquals(3, output.getBucketIndex());
        Assert.assertArrayEquals(element, output.getElement());
    }

    @Test
    public void testLocalGadgetReturnsBottomForBlockedBucket() {
        BaSsuIbltSecureBucketInput input = BaSsuIbltSecureBucketInput.of(
            5, singletonCell(element(8L)), singletonCell(element(9L))
        );
        BaSsuIbltUnionProbeOutput output = BaSsuIbltUnionProbeGadget.evaluateLocal(input);
        Assert.assertFalse(output.isSingleton());
        Assert.assertEquals(5, output.getBucketIndex());
        Assert.assertThrows(IllegalStateException.class, output::getElement);
    }

    @Test
    public void testLocalGadgetReturnsSingletonForSharedBucket() {
        byte[] element = element(10L);
        BaSsuIbltSecureBucketInput input = BaSsuIbltSecureBucketInput.of(
            6, singletonCell(element), singletonCell(element)
        );
        BaSsuIbltUnionProbeOutput output = BaSsuIbltUnionProbeGadget.evaluateLocal(input);
        Assert.assertTrue(output.isSingleton());
        Assert.assertArrayEquals(element, output.getElement());
    }

    @Test
    public void testCapsuleAndBatchDefensiveCopies() {
        byte[] encoded = new byte[]{1, 2, 3};
        Assert.assertThrows(IllegalArgumentException.class, () -> new BaSsuIbltUnionProbeCapsule(1, encoded, 4));
        BaSsuIbltUnionProbeCapsule capsule = new BaSsuIbltUnionProbeCapsule(1, encoded, encoded.length);
        encoded[0] = 9;
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, capsule.getEncoded());
        byte[] returned = capsule.getEncoded();
        returned[1] = 9;
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, capsule.getEncoded());
        Assert.assertThrows(IllegalArgumentException.class,
            () -> BaSsuIbltUnionProbeOutput.singleton(1, element(1L), ELEMENT_BYTE_LENGTH + 1));

        BaSsuIbltUnionProbeBatch batch = new BaSsuIbltUnionProbeBatch(List.of(
            BaSsuIbltUnionProbeOutput.bottom(1, ELEMENT_BYTE_LENGTH),
            BaSsuIbltUnionProbeOutput.singleton(2, element(11L), ELEMENT_BYTE_LENGTH)
        ));
        Assert.assertEquals(2, batch.size());
        Assert.assertThrows(UnsupportedOperationException.class,
            () -> batch.getOutputs().add(BaSsuIbltUnionProbeOutput.bottom(3, ELEMENT_BYTE_LENGTH)));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> new BaSsuIbltUnionProbeBatch(Arrays.asList(
                BaSsuIbltUnionProbeOutput.bottom(1, ELEMENT_BYTE_LENGTH), null
            )));
    }

    private static BaSsuIbltSecureCellView emptyCell() {
        return BaSsuIbltSecureCellView.empty(ELEMENT_BYTE_LENGTH, TAG_BYTE_LENGTH, CHECK_BYTE_LENGTH);
    }

    private static BaSsuIbltSecureCellView singletonCell(byte[] element) {
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(element, TAG_BYTE_LENGTH);
        byte[] check = BaSsuIbltOprfTagPipeline.checkFromTag(tag, CHECK_BYTE_LENGTH);
        return BaSsuIbltSecureCellView.of(1, element, tag, check);
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }
}
