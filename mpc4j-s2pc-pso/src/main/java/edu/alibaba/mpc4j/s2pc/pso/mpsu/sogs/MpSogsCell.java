package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Local MP-SOGS cell for the clear prototype.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
class MpSogsCell {
    /**
     * Non-negative count.
     */
    private int count;
    /**
     * Sum of element encodings. The clear prototype uses positive long values.
     */
    private long valueSum;
    /**
     * Sum of check values.
     */
    private long checkSum;

    void add(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("the clear prototype reserves non-positive values");
        }
        count++;
        valueSum += value;
        checkSum += MpSogsHashUtils.check(value);
    }

    void remove(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("the clear prototype reserves non-positive values");
        }
        if (count == 0) {
            throw new IllegalStateException("delete underflow");
        }
        count--;
        valueSum -= value;
        checkSum -= MpSogsHashUtils.check(value);
    }

    MpSogsCellState state() {
        if (count == 0) {
            return MpSogsCellState.EMPTY;
        }
        if (count != 1) {
            return MpSogsCellState.HEAVY;
        }
        return checkSum == MpSogsHashUtils.check(valueSum) ? MpSogsCellState.SINGLETON : MpSogsCellState.HEAVY;
    }

    long singletonValue() {
        if (state() != MpSogsCellState.SINGLETON) {
            throw new IllegalStateException("cell is not singleton");
        }
        return valueSum;
    }

    int count() {
        return count;
    }
}
