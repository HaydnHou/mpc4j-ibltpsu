package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * BA-SSU-IBLT anchor table tests.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltAnchorTableTest {
    /**
     * element byte length.
     */
    private static final int ELEMENT_BYTE_LENGTH = Long.BYTES;

    @Test
    public void testAnchorSingletonBuckets() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        ByteBuffer element = element(1L);
        table.insertAnchor(element);
        for (int position : table.positions(element)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            Assert.assertEquals(BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, output.getCaseType());
            Assert.assertArrayEquals(toBytes(element), output.getElement());
        }
    }

    @Test
    public void testSharedSingletonBuckets() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        ByteBuffer element = element(2L);
        table.insertAnchor(element);
        table.insertShadow(element);
        for (int position : table.positions(element)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            Assert.assertEquals(BaUpotBucketOutput.CaseType.SHARED_SINGLETON, output.getCaseType());
            Assert.assertArrayEquals(toBytes(element), output.getElement());
        }
    }

    @Test
    public void testCrossLayerBlockingBucket() {
        BaSsuIbltBiUpsuParams params = params();
        Collision collision = findCollision(params);
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        table.insertAnchor(element(collision.anchorValue));
        table.insertShadow(element(collision.shadowValue));
        BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(collision.bucketIndex));
        Assert.assertEquals(BaUpotBucketOutput.CaseType.BLOCKED, output.getCaseType());
    }

    @Test
    public void testDeleteRestoresEmptyBuckets() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        ByteBuffer element = element(3L);
        table.insertAnchor(element);
        table.insertShadow(element);
        table.deleteAnchor(element);
        table.deleteShadow(element);
        for (int position : table.positions(element)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            Assert.assertEquals(BaUpotBucketOutput.CaseType.EMPTY, output.getCaseType());
        }
    }

    @Test
    public void testByteArrayOverloadClonesInput() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        byte[] element = toBytes(element(4L));
        byte[] original = element.clone();
        table.insertAnchor(element);
        element[0] ^= (byte) 0xA5;
        for (int position : table.positions(original)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            Assert.assertEquals(BaUpotBucketOutput.CaseType.ANCHOR_SINGLETON, output.getCaseType());
            Assert.assertArrayEquals(original, output.getElement());
        }
        table.deleteAnchor(original);
        for (int position : table.positions(original)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            Assert.assertEquals(BaUpotBucketOutput.CaseType.EMPTY, output.getCaseType());
        }
    }

    @Test
    public void testRejectedDeleteDoesNotMutateCell() {
        BaSsuIbltBiUpsuParams params = params();
        BaSsuIbltAnchorTable table = new BaSsuIbltAnchorTable(params, 0, ELEMENT_BYTE_LENGTH);
        ByteBuffer element = element(5L);
        table.insertShadow(element);
        try {
            table.deleteAnchor(element);
            Assert.fail("deleting an absent anchor should fail");
        } catch (IllegalStateException ignored) {
            // expected
        }
        for (int position : table.positions(element)) {
            BaUpotBucketOutput output = BaUpotIdeal.evaluate(table.getBucketInput(position));
            Assert.assertEquals(BaUpotBucketOutput.CaseType.SHADOW_SINGLETON, output.getCaseType());
            Assert.assertArrayEquals(toBytes(element), output.getElement());
        }
    }

    private static BaSsuIbltBiUpsuParams params() {
        return new BaSsuIbltBiUpsuParams.Builder(128, 16)
            .setAlphaAnchor(4.0)
            .setPublicPlaceSeed(20260603L)
            .build();
    }

    private static Collision findCollision(BaSsuIbltBiUpsuParams params) {
        for (long anchorValue = 1; anchorValue < 1024; anchorValue++) {
            int[] anchorPositions = BaSsuIbltPlacement.positions(params, 0, toBytes(element(anchorValue)));
            for (long shadowValue = 1024; shadowValue < 4096; shadowValue++) {
                int[] shadowPositions = BaSsuIbltPlacement.positions(params, 0, toBytes(element(shadowValue)));
                for (int anchorPosition : anchorPositions) {
                    for (int shadowPosition : shadowPositions) {
                        if (anchorPosition == shadowPosition) {
                            return new Collision(anchorValue, shadowValue, anchorPosition);
                        }
                    }
                }
            }
        }
        throw new AssertionError("could not find a bucket collision");
    }

    private static ByteBuffer element(long value) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
        byteBuffer.putLong(value);
        return ByteBuffer.wrap(byteBuffer.array());
    }

    private static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    /**
     * collision.
     */
    private static class Collision {
        /**
         * anchor value.
         */
        private final long anchorValue;
        /**
         * shadow value.
         */
        private final long shadowValue;
        /**
         * bucket index.
         */
        private final int bucketIndex;

        Collision(long anchorValue, long shadowValue, int bucketIndex) {
            this.anchorValue = anchorValue;
            this.shadowValue = shadowValue;
            this.bucketIndex = bucketIndex;
        }
    }
}
