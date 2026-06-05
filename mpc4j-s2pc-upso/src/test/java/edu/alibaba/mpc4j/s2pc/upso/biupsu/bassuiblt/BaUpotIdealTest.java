package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BA-UPOT ideal bucket functionality tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaUpotIdealTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * check byte length.
     */
    private static final int CHECK_BYTE_LENGTH = 32;

    @Test
    public void testEmptyBucket() {
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(
            BaUpotBucketInput.empty(0, ELEMENT_BYTE_LENGTH, CHECK_BYTE_LENGTH)
        );
        Assert.assertEquals(BaUpotBucketOutput.CaseType.EMPTY, output.getCaseType());
        Assert.assertFalse(output.isSingleton());
    }

    @Test
    public void testAnchorOnlySingleton() {
        byte[] element = element(1);
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(BaUpotBucketInput.of(
            1, 1, 0, element, new byte[ELEMENT_BYTE_LENGTH], check(element), new byte[CHECK_BYTE_LENGTH]
        ));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, output.getCaseType());
        Assert.assertArrayEquals(element, output.getElement());
    }

    @Test
    public void testShadowOnlySingleton() {
        byte[] element = element(2);
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(BaUpotBucketInput.of(
            2, 0, 1, new byte[ELEMENT_BYTE_LENGTH], element, new byte[CHECK_BYTE_LENGTH], check(element)
        ));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, output.getCaseType());
        Assert.assertArrayEquals(element, output.getElement());
    }

    @Test
    public void testSharedSingleton() {
        byte[] element = element(3);
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(BaUpotBucketInput.of(
            3, 1, 1, element, element, check(element), check(element)
        ));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.SHARED_SINGLETON, output.getCaseType());
        Assert.assertArrayEquals(element, output.getElement());
    }

    @Test
    public void testCrossLayerBlocked() {
        byte[] anchor = element(4);
        byte[] shadow = element(5);
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(BaUpotBucketInput.of(
            4, 1, 1, anchor, shadow, check(anchor), check(shadow)
        ));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.BLOCKED, output.getCaseType());
        Assert.assertFalse(output.isSingleton());
    }

    @Test
    public void testTamperedCheckBlocked() {
        byte[] element = element(6);
        byte[] badCheck = check(element);
        badCheck[0] ^= 1;
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(BaUpotBucketInput.of(
            5, 1, 0, element, new byte[ELEMENT_BYTE_LENGTH], badCheck, new byte[CHECK_BYTE_LENGTH]
        ));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.BLOCKED, output.getCaseType());
    }

    @Test
    public void testZeroElementSingleton() {
        byte[] element = new byte[ELEMENT_BYTE_LENGTH];
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(BaUpotBucketInput.of(
            6, 1, 0, element, new byte[ELEMENT_BYTE_LENGTH], check(element), new byte[CHECK_BYTE_LENGTH]
        ));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, output.getCaseType());
        Assert.assertArrayEquals(element, output.getElement());
    }

    private static byte[] element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return byteBuffer.array();
    }

    private static byte[] check(byte[] element) {
        return Arrays.copyOf(BaUpotIdeal.digest(element, CHECK_BYTE_LENGTH), CHECK_BYTE_LENGTH);
    }
}
