package edu.alibaba.mpc4j.common.structure.iblt;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.prf.Prf;
import edu.alibaba.mpc4j.common.tool.crypto.prf.PrfFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prp.Prp;
import edu.alibaba.mpc4j.common.tool.crypto.prp.PrpFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * IBLT over long keys with five hash functions.
 *
 * <p>This follows the IBLT shape used by "Is PSI Really Faster Than PSU? Achieving Efficient PSU with Invertible Bloom
 * Filters": the table is split into five equal subtables, and each key is mapped once into every subtable. Each cell
 * stores the XOR of keys, the XOR of key checksums, the XOR of optional values, and a signed counter.</p>
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class H5LongIblt implements LongIblt {
    /**
     * number of hash functions.
     */
    public static final int HASH_NUM = 5;
    /**
     * default table length multiplier.
     */
    public static final double DEFAULT_MULTIPLIER = 3.5;
    /**
     * domain separator used in the second AES block for position hashing.
     */
    private static final long POSITION_HASH_DOMAIN_SEPARATOR = 0x9E35468B2FBBEC9CL;
    /**
     * threshold.
     */
    private final int threshold;
    /**
     * table length multiplier.
     */
    private final double multiplier;
    /**
     * subtable length.
     */
    private final int subTableLength;
    /**
     * table length.
     */
    private final int tableLength;
    /**
     * value byte length.
     */
    private final int valueByteLength;
    /**
     * position hash.
     */
    private final Prp positionHash;
    /**
     * hash key.
     */
    private final byte[] hashKey;
    /**
     * key checksum hash.
     */
    private final Prf keyCheckHash;
    /**
     * key XOR sums.
     */
    private final long[] keySums;
    /**
     * key checksum XOR sums.
     */
    private final long[] keyCheckSums;
    /**
     * value XOR sums.
     */
    private final byte[][] valueSums;
    /**
     * counters.
     */
    private final int[] counts;
    /**
     * net item count.
     */
    private int size;

    /**
     * Creates an empty IBLT.
     *
     * @param envType         environment.
     * @param threshold       expected number of items to peel.
     * @param valueByteLength value byte length.
     * @param key             hash key.
     * @return an empty IBLT.
     */
    public static H5LongIblt create(EnvType envType, int threshold, int valueByteLength, byte[] key) {
        return new H5LongIblt(envType, threshold, DEFAULT_MULTIPLIER, valueByteLength, key);
    }

    /**
     * Creates an empty IBLT.
     *
     * @param envType         environment.
     * @param threshold       expected number of items to peel.
     * @param multiplier      table length multiplier.
     * @param valueByteLength value byte length.
     * @param key             hash key.
     * @return an empty IBLT.
     */
    public static H5LongIblt create(EnvType envType, int threshold, double multiplier, int valueByteLength, byte[] key) {
        return new H5LongIblt(envType, threshold, multiplier, valueByteLength, key);
    }

    /**
     * Computes the subtable length.
     *
     * @param threshold  threshold.
     * @param multiplier table length multiplier.
     * @return subtable length.
     */
    public static int calcSubTableLength(int threshold, double multiplier) {
        MathPreconditions.checkPositive("threshold", threshold);
        Preconditions.checkArgument(Double.isFinite(multiplier));
        MathPreconditions.checkPositive("multiplier", multiplier);
        long subTableLength = (long) Math.ceil(multiplier * threshold / HASH_NUM);
        MathPreconditions.checkPositive("subTableLength", subTableLength);
        MathPreconditions.checkLessOrEqual("subTableLength", subTableLength, Integer.MAX_VALUE / HASH_NUM);
        return (int) subTableLength;
    }

    /**
     * Computes the table length.
     *
     * @param threshold  threshold.
     * @param multiplier table length multiplier.
     * @return table length.
     */
    public static int calcTableLength(int threshold, double multiplier) {
        return Math.multiplyExact(HASH_NUM, calcSubTableLength(threshold, multiplier));
    }

    private H5LongIblt(EnvType envType, int threshold, double multiplier, int valueByteLength, byte[] key) {
        MathPreconditions.checkPositive("threshold", threshold);
        this.threshold = threshold;
        MathPreconditions.checkPositive("multiplier", multiplier);
        this.multiplier = multiplier;
        MathPreconditions.checkNonNegative("valueByteLength", valueByteLength);
        this.valueByteLength = valueByteLength;
        MathPreconditions.checkEqual("key.length", "BLOCK_BYTE_LENGTH", key.length, CommonConstants.BLOCK_BYTE_LENGTH);
        subTableLength = calcSubTableLength(threshold, multiplier);
        tableLength = Math.multiplyExact(HASH_NUM, subTableLength);
        positionHash = PrpFactory.createInstance(envType);
        positionHash.setKey(key);
        hashKey = BytesUtils.clone(key);
        keyCheckHash = PrfFactory.createInstance(envType, Long.BYTES);
        keyCheckHash.setKey(key);
        keySums = new long[tableLength];
        keyCheckSums = new long[tableLength];
        valueSums = new byte[tableLength][valueByteLength];
        counts = new int[tableLength];
        size = 0;
    }

    private H5LongIblt(H5LongIblt that) {
        threshold = that.threshold;
        multiplier = that.multiplier;
        subTableLength = that.subTableLength;
        tableLength = that.tableLength;
        valueByteLength = that.valueByteLength;
        positionHash = PrpFactory.createInstance(that.positionHash.getPrpType());
        positionHash.setKey(that.hashKey);
        hashKey = BytesUtils.clone(that.hashKey);
        keyCheckHash = PrfFactory.createInstance(that.keyCheckHash.getPrfType(), Long.BYTES);
        keyCheckHash.setKey(that.keyCheckHash.getKey());
        keySums = LongUtils.clone(that.keySums);
        keyCheckSums = LongUtils.clone(that.keyCheckSums);
        valueSums = BytesUtils.clone(that.valueSums);
        counts = Arrays.copyOf(that.counts, that.counts.length);
        size = that.size;
    }

    /**
     * Creates a deep copy.
     *
     * @return a deep copy.
     */
    public H5LongIblt copy() {
        return new H5LongIblt(this);
    }

    @Override
    public LongIbltFactory.LongIbltType getType() {
        return LongIbltFactory.LongIbltType.H5_LONG;
    }

    /**
     * Adds a key without value.
     *
     * @param key key.
     */
    public void addKey(long key) {
        update(key, null, 1);
    }

    /**
     * Adds keys without values.
     *
     * @param keys keys.
     */
    public void addKeys(long[] keys) {
        Preconditions.checkNotNull(keys);
        for (long key : keys) {
            addKey(key);
        }
    }

    /**
     * Adds keys without values.
     *
     * @param keys keys.
     */
    public void addKeys(Collection<Long> keys) {
        Preconditions.checkNotNull(keys);
        for (long key : keys) {
            addKey(key);
        }
    }

    /**
     * Adds a key-value item.
     *
     * @param key   key.
     * @param value value.
     */
    public void add(long key, byte[] value) {
        checkValue(value);
        update(key, value, 1);
    }

    /**
     * Adds key-value items.
     *
     * @param keys   keys.
     * @param values values.
     */
    public void add(long[] keys, byte[][] values) {
        checkInputs(keys, values);
        for (int i = 0; i < keys.length; i++) {
            add(keys[i], values[i]);
        }
    }

    /**
     * Removes a key without value.
     *
     * @param key key.
     */
    public void removeKey(long key) {
        update(key, null, -1);
    }

    /**
     * Removes keys without values.
     *
     * @param keys keys.
     */
    public void removeKeys(long[] keys) {
        Preconditions.checkNotNull(keys);
        for (long key : keys) {
            removeKey(key);
        }
    }

    /**
     * Removes keys without values.
     *
     * @param keys keys.
     */
    public void removeKeys(Collection<Long> keys) {
        Preconditions.checkNotNull(keys);
        for (long key : keys) {
            removeKey(key);
        }
    }

    /**
     * Removes a key-value item.
     *
     * @param key   key.
     * @param value value.
     */
    public void remove(long key, byte[] value) {
        checkValue(value);
        update(key, value, -1);
    }

    /**
     * Removes key-value items.
     *
     * @param keys   keys.
     * @param values values.
     */
    public void remove(long[] keys, byte[][] values) {
        checkInputs(keys, values);
        for (int i = 0; i < keys.length; i++) {
            remove(keys[i], values[i]);
        }
    }

    /**
     * Subtracts another IBLT from this IBLT in place.
     *
     * @param other the other IBLT.
     */
    public void subtract(LongIblt other) {
        Preconditions.checkArgument(other instanceof H5LongIblt);
        H5LongIblt that = (H5LongIblt) other;
        checkCompatible(that);
        for (int i = 0; i < tableLength; i++) {
            keySums[i] ^= that.keySums[i];
            keyCheckSums[i] ^= that.keyCheckSums[i];
            BytesUtils.xori(valueSums[i], that.valueSums[i]);
            counts[i] -= that.counts[i];
        }
        size -= that.size;
    }

    /**
     * Peels the IBLT in place.
     *
     * @return peel result.
     */
    public LongIbltPeelResult peel() {
        ArrayDeque<Integer> queue = new ArrayDeque<>(tableLength);
        for (int i = 0; i < tableLength; i++) {
            if (isPure(i)) {
                queue.add(i);
            }
        }
        List<LongIbltEntry> entries = new ArrayList<>();
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            if (!isPure(index)) {
                continue;
            }
            int count = counts[index];
            int sign = count > 0 ? 1 : -1;
            long key = keySums[index];
            long keyCheck = keyCheck(key);
            byte[] value = BytesUtils.clone(valueSums[index]);
            entries.add(new LongIbltEntry(key, value, sign));

            int[] positions = positions(key);
            for (int position : positions) {
                keySums[position] ^= key;
                keyCheckSums[position] ^= keyCheck;
                BytesUtils.xori(valueSums[position], value);
                counts[position] -= sign;
                if (isPure(position)) {
                    queue.add(position);
                }
            }
            size -= sign;
        }
        return new LongIbltPeelResult(isEmpty(), entries);
    }

    /**
     * Tests if this IBLT is peelable without modifying the current IBLT.
     *
     * @return true if this IBLT is peelable.
     */
    public boolean isPeelable() {
        return copy().peel().success();
    }

    /**
     * Returns all hash positions of a key.
     *
     * @param key key.
     * @return positions.
     */
    public int[] positions(long key) {
        byte[] firstHashBytes = positionHashBlock(0L, key);
        byte[] secondHashBytes = positionHashBlock(POSITION_HASH_DOMAIN_SEPARATOR, key);
        ByteBuffer firstHashBuffer = ByteBuffer.wrap(firstHashBytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] positions = new int[HASH_NUM];
        for (int hashIndex = 0; hashIndex < HASH_NUM - 1; hashIndex++) {
            long hashOutput = Integer.toUnsignedLong(firstHashBuffer.getInt(hashIndex * Integer.BYTES));
            positions[hashIndex] = (int) (hashOutput % subTableLength) + hashIndex * subTableLength;
        }
        long hashOutput = Integer.toUnsignedLong(
            ByteBuffer.wrap(secondHashBytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        );
        positions[HASH_NUM - 1] = (int) (hashOutput % subTableLength) + (HASH_NUM - 1) * subTableLength;
        return positions;
    }

    /**
     * Returns deduplicated positions hit by the given keys, excluding positions marked by the bitmap.
     *
     * @param keys     keys.
     * @param excluded excluded bitmap.
     * @return deduplicated positions.
     */
    public int[] uniquePositions(long[] keys, boolean[] excluded) {
        Preconditions.checkNotNull(keys);
        if (excluded != null) {
            MathPreconditions.checkEqual("excluded.length", "tableLength", excluded.length, tableLength);
        }
        boolean[] seen = new boolean[tableLength];
        int[] uniquePositions = new int[Math.multiplyExact(keys.length, HASH_NUM)];
        int uniquePositionNum = 0;
        for (long key : keys) {
            int[] positions = positions(key);
            for (int position : positions) {
                if (!seen[position] && (excluded == null || !excluded[position])) {
                    seen[position] = true;
                    uniquePositions[uniquePositionNum] = position;
                    uniquePositionNum++;
                }
            }
        }
        return Arrays.copyOf(uniquePositions, uniquePositionNum);
    }

    /**
     * Returns deduplicated positions hit by the given keys.
     *
     * @param keys keys.
     * @return deduplicated positions.
     */
    public int[] uniquePositions(long[] keys) {
        return uniquePositions(keys, null);
    }

    /**
     * Clears this IBLT.
     */
    public void clear() {
        Arrays.fill(keySums, 0L);
        Arrays.fill(keyCheckSums, 0L);
        for (byte[] valueSum : valueSums) {
            Arrays.fill(valueSum, (byte) 0x00);
        }
        Arrays.fill(counts, 0);
        size = 0;
    }

    private void update(long key, byte[] value, int delta) {
        Preconditions.checkArgument(delta == 1 || delta == -1);
        if (value != null) {
            MathPreconditions.checkEqual("value.length", "valueByteLength", value.length, valueByteLength);
        }
        int[] positions = positions(key);
        long keyCheck = keyCheck(key);
        for (int position : positions) {
            keySums[position] ^= key;
            keyCheckSums[position] ^= keyCheck;
            if (value != null) {
                BytesUtils.xori(valueSums[position], value);
            }
            counts[position] += delta;
        }
        size += delta;
    }

    private void checkInputs(long[] keys, byte[][] values) {
        Preconditions.checkNotNull(keys);
        Preconditions.checkNotNull(values);
        MathPreconditions.checkEqual("keys.length", "values.length", keys.length, values.length);
        for (byte[] value : values) {
            checkValue(value);
        }
    }

    private void checkValue(byte[] value) {
        Preconditions.checkNotNull(value);
        MathPreconditions.checkEqual("value.length", "valueByteLength", value.length, valueByteLength);
    }

    private boolean isPure(int index) {
        return (counts[index] == 1 || counts[index] == -1) && keyCheckSums[index] == keyCheck(keySums[index]);
    }

    private byte[] positionHashBlock(long domainSeparator, long key) {
        byte[] input = positionHashInput(domainSeparator, key);
        byte[] output = positionHash.prp(input);
        BytesUtils.xori(output, input);
        return output;
    }

    private byte[] positionHashInput(long domainSeparator, long key) {
        return ByteBuffer.allocate(CommonConstants.BLOCK_BYTE_LENGTH)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(key)
            .putLong(domainSeparator)
            .array();
    }

    private long keyCheck(long key) {
        byte[] message = ByteBuffer.allocate(Byte.BYTES + Long.BYTES)
            .put((byte) 0x01)
            .putLong(key)
            .array();
        return ByteBuffer.wrap(keyCheckHash.getBytes(message)).getLong();
    }

    private boolean isEmpty() {
        for (int i = 0; i < tableLength; i++) {
            if (counts[i] != 0 || keySums[i] != 0L || keyCheckSums[i] != 0L || !isZero(valueSums[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean isZero(byte[] value) {
        for (byte v : value) {
            if (v != 0) {
                return false;
            }
        }
        return true;
    }

    private void checkCompatible(H5LongIblt other) {
        MathPreconditions.checkEqual("this.threshold", "other.threshold", threshold, other.threshold);
        MathPreconditions.checkEqual("this.subTableLength", "other.subTableLength", subTableLength, other.subTableLength);
        MathPreconditions.checkEqual("this.valueByteLength", "other.valueByteLength", valueByteLength, other.valueByteLength);
        Preconditions.checkArgument(positionHash.getPrpType().equals(other.positionHash.getPrpType()));
        Preconditions.checkArgument(Arrays.equals(hashKey, other.hashKey));
        Preconditions.checkArgument(keyCheckHash.getPrfType().equals(other.keyCheckHash.getPrfType()));
        Preconditions.checkArgument(Arrays.equals(keyCheckHash.getKey(), other.keyCheckHash.getKey()));
    }

    /**
     * Gets threshold.
     *
     * @return threshold.
     */
    public int threshold() {
        return threshold;
    }

    /**
     * Gets table length multiplier.
     *
     * @return table length multiplier.
     */
    public double multiplier() {
        return multiplier;
    }

    /**
     * Gets subtable length.
     *
     * @return subtable length.
     */
    public int subTableLength() {
        return subTableLength;
    }

    /**
     * Gets table length.
     *
     * @return table length.
     */
    public int tableLength() {
        return tableLength;
    }

    /**
     * Gets value byte length.
     *
     * @return value byte length.
     */
    public int valueByteLength() {
        return valueByteLength;
    }

    /**
     * Gets net item count.
     *
     * @return net item count.
     */
    public int size() {
        return size;
    }

    /**
     * Gets the hash key.
     *
     * @return hash key.
     */
    public byte[] hashKey() {
        return BytesUtils.clone(hashKey);
    }

    /**
     * Gets cloned key sums.
     *
     * @return cloned key sums.
     */
    public long[] keySums() {
        return LongUtils.clone(keySums);
    }

    /**
     * Gets cloned key checksum sums.
     *
     * @return cloned key checksum sums.
     */
    public long[] keyCheckSums() {
        return LongUtils.clone(keyCheckSums);
    }

    /**
     * Gets cloned value sums.
     *
     * @return cloned value sums.
     */
    public byte[][] valueSums() {
        return BytesUtils.clone(valueSums);
    }

    /**
     * Gets pure singleton indicators.
     *
     * @return pure singleton indicators.
     */
    public boolean[] pureSingletons() {
        boolean[] pureSingletons = new boolean[tableLength];
        for (int index = 0; index < tableLength; index++) {
            pureSingletons[index] = isPure(index);
        }
        return pureSingletons;
    }

    /**
     * Gets cloned counts.
     *
     * @return cloned counts.
     */
    public int[] counts() {
        return Arrays.copyOf(counts, counts.length);
    }
}
