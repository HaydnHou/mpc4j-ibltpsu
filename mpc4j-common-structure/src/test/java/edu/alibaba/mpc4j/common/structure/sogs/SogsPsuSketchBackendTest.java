package edu.alibaba.mpc4j.common.structure.sogs;

import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tests for PSU sketch backends.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
public class SogsPsuSketchBackendTest {
    /**
     * Seed.
     */
    private static final long SEED = 20260609L;
    /**
     * Payload byte length.
     */
    private static final int PAYLOAD_BYTE_LENGTH = 16;
    /**
     * Conservative SOGS alpha for deterministic unit tests.
     */
    private static final double SOGS_ALPHA = 1.45;
    /**
     * SOGS degree.
     */
    private static final int SOGS_DEGREE = 3;

    @Test
    public void testPositiveUnionBackendEquivalence() {
        Map<Long, byte[]> unionMap = positiveUnionMap(1 << 12, 1 << 8, 1 << 7);
        SogsPsuSketchBackend h5 = createH5(unionMap.size());
        SogsPsuSketchBackend sogs = createSogs(unionMap.size());
        for (Map.Entry<Long, byte[]> entry : unionMap.entrySet()) {
            h5.add(entry.getKey(), entry.getValue());
            sogs.add(entry.getKey(), entry.getValue());
        }

        SogsPsuSketchPeelResult h5Result = h5.peel();
        SogsPsuSketchPeelResult sogsResult = sogs.peel();
        Assert.assertTrue(h5Result.success());
        Assert.assertTrue(sogsResult.success());
        assertSignedMapEquals(toPositiveSignedMap(unionMap), toSignedMap(h5Result));
        assertSignedMapEquals(toPositiveSignedMap(unionMap), toSignedMap(sogsResult));
        Assert.assertTrue(sogs.tableSize() < h5.tableSize());
        Assert.assertEquals(H5LongIblt.HASH_NUM, h5.hashNum());
        Assert.assertEquals(SOGS_DEGREE, sogs.hashNum());
    }

    @Test
    public void testSignedDifferenceBackendEquivalence() {
        int commonSize = 1 << 8;
        int leftOnlySize = 1 << 9;
        int rightOnlySize = 1 << 8;
        int threshold = leftOnlySize + rightOnlySize;
        SogsPsuSketchBackend h5 = createH5(threshold);
        SogsPsuSketchBackend sogs = createSogs(threshold);
        Map<Long, SignedPayload> expected = new LinkedHashMap<>();

        for (int i = 0; i < commonSize; i++) {
            long item = i;
            long label = label(item);
            byte[] payload = payload(item);
            h5.add(label, payload);
            h5.remove(label, payload);
            sogs.add(label, payload);
            sogs.remove(label, payload);
        }
        long leftBase = 1L << 32;
        for (int i = 0; i < leftOnlySize; i++) {
            long item = leftBase + i;
            long label = label(item);
            byte[] payload = payload(item);
            h5.add(label, payload);
            sogs.add(label, payload);
            expected.put(label, new SignedPayload(1, payload));
        }
        long rightBase = 1L << 40;
        for (int i = 0; i < rightOnlySize; i++) {
            long item = rightBase + i;
            long label = label(item);
            byte[] payload = payload(item);
            h5.remove(label, payload);
            sogs.remove(label, payload);
            expected.put(label, new SignedPayload(-1, payload));
        }

        SogsPsuSketchPeelResult h5Result = h5.peel();
        SogsPsuSketchPeelResult sogsResult = sogs.peel();
        Assert.assertTrue(h5Result.success());
        Assert.assertTrue(sogsResult.success());
        Assert.assertEquals(leftOnlySize, h5Result.positiveEntries().size());
        Assert.assertEquals(rightOnlySize, h5Result.negativeEntries().size());
        Assert.assertEquals(leftOnlySize, sogsResult.positiveEntries().size());
        Assert.assertEquals(rightOnlySize, sogsResult.negativeEntries().size());
        assertSignedMapEquals(expected, toSignedMap(h5Result));
        assertSignedMapEquals(expected, toSignedMap(sogsResult));
    }

    @Test
    public void testZeroPayloadBackendEquivalence() {
        int size = 1 << 8;
        SogsPsuSketchBackend h5 = createH5(size);
        SogsPsuSketchBackend sogs = createSogs(size);
        Map<Long, byte[]> expected = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            long label = label(i);
            byte[] payload = new byte[PAYLOAD_BYTE_LENGTH];
            h5.add(label, payload);
            sogs.add(label, payload);
            expected.put(label, payload);
        }
        SogsPsuSketchPeelResult h5Result = h5.peel();
        SogsPsuSketchPeelResult sogsResult = sogs.peel();
        Assert.assertTrue(h5Result.success());
        Assert.assertTrue(sogsResult.success());
        assertSignedMapEquals(toPositiveSignedMap(expected), toSignedMap(h5Result));
        assertSignedMapEquals(toPositiveSignedMap(expected), toSignedMap(sogsResult));
    }

    @Test
    public void testSameLabelDifferentPayloadCannotPeel() {
        SogsPsuSketchBackend h5 = createH5(2);
        SogsPsuSketchBackend sogs = createSogs(2);
        long label = label(1);
        byte[] leftPayload = payload(1);
        byte[] rightPayload = payload(2);
        h5.add(label, leftPayload);
        h5.remove(label, rightPayload);
        sogs.add(label, leftPayload);
        sogs.remove(label, rightPayload);
        Assert.assertFalse(h5.peel().success());
        Assert.assertFalse(sogs.peel().success());
    }

    @Test
    public void testPositionsAndPayloadLength() {
        int threshold = 1 << 8;
        SogsPsuSketchBackend h5 = createH5(threshold);
        SogsPsuSketchBackend sogs = createSogs(threshold);
        long label = label(123);
        Assert.assertEquals(H5LongIblt.HASH_NUM, h5.positions(label).length);
        Assert.assertEquals(SOGS_DEGREE, sogs.positions(label).length);
        Assert.assertEquals(PAYLOAD_BYTE_LENGTH, h5.payloadByteLength());
        Assert.assertEquals(PAYLOAD_BYTE_LENGTH, sogs.payloadByteLength());
        Assert.assertThrows(IllegalArgumentException.class, () -> h5.add(label, new byte[PAYLOAD_BYTE_LENGTH - 1]));
        Assert.assertThrows(IllegalArgumentException.class, () -> sogs.add(label, new byte[PAYLOAD_BYTE_LENGTH - 1]));
    }

    private static SogsPsuSketchBackend createH5(int threshold) {
        return SogsPsuSketchBackendFactory.createH5Backend(
            EnvType.STANDARD, threshold, H5LongIblt.DEFAULT_MULTIPLIER, PAYLOAD_BYTE_LENGTH, hashKey(SEED)
        );
    }

    private static SogsPsuSketchBackend createSogs(int expectedItemSize) {
        return SogsPsuSketchBackendFactory.createSogsBackend(
            expectedItemSize, SOGS_ALPHA, SOGS_DEGREE, PAYLOAD_BYTE_LENGTH, SEED
        );
    }

    private static Map<Long, byte[]> positiveUnionMap(int largeSize, int smallSize, int overlapSize) {
        Map<Long, byte[]> unionMap = new LinkedHashMap<>(largeSize + smallSize - overlapSize);
        for (int i = 0; i < largeSize; i++) {
            unionMap.put(label(i), payload(i));
        }
        long smallOnlyBase = 1L << 40;
        for (int i = 0; i < smallSize - overlapSize; i++) {
            long item = smallOnlyBase + i;
            unionMap.put(label(item), payload(item));
        }
        return unionMap;
    }

    private static Map<Long, SignedPayload> toPositiveSignedMap(Map<Long, byte[]> payloadMap) {
        Map<Long, SignedPayload> signedMap = new LinkedHashMap<>(payloadMap.size());
        for (Map.Entry<Long, byte[]> entry : payloadMap.entrySet()) {
            signedMap.put(entry.getKey(), new SignedPayload(1, entry.getValue()));
        }
        return signedMap;
    }

    private static Map<Long, SignedPayload> toSignedMap(SogsPsuSketchPeelResult result) {
        Map<Long, SignedPayload> signedMap = new LinkedHashMap<>(result.entries().size());
        for (SogsPsuSketchEntry entry : result.entries()) {
            signedMap.put(entry.label(), new SignedPayload(entry.sign(), entry.payload()));
        }
        return signedMap;
    }

    private static void assertSignedMapEquals(Map<Long, SignedPayload> expected, Map<Long, SignedPayload> actual) {
        Assert.assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<Long, SignedPayload> entry : expected.entrySet()) {
            SignedPayload expectedPayload = entry.getValue();
            SignedPayload actualPayload = actual.get(entry.getKey());
            Assert.assertEquals(expectedPayload.sign(), actualPayload.sign());
            Assert.assertTrue(Arrays.equals(expectedPayload.payload(), actualPayload.payload()));
        }
    }

    private static long label(long item) {
        return SogsHashUtils.label(item, SEED);
    }

    private static byte[] payload(long item) {
        byte[] payload = new byte[PAYLOAD_BYTE_LENGTH];
        long state = SogsHashUtils.mix64(item ^ SEED ^ 0xC6BC279692B5C323L);
        for (int i = 0; i < payload.length; i++) {
            state = SogsHashUtils.mix64(state + i);
            payload[i] = (byte) state;
        }
        return payload;
    }

    private static byte[] hashKey(long seed) {
        byte[] key = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        ByteBuffer.wrap(key)
            .putLong(SogsHashUtils.mix64(seed ^ 0x243F6A8885A308D3L))
            .putLong(SogsHashUtils.mix64(seed ^ 0x13198A2E03707344L));
        return key;
    }

    private record SignedPayload(int sign, byte[] payload) {
        private SignedPayload {
            payload = payload.clone();
        }
    }
}
