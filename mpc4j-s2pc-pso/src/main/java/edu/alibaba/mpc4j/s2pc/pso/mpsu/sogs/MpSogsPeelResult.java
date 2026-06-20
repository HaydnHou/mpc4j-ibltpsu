package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.Objects;

/**
 * Public MP-SOGS peel result. It intentionally carries no bottom reason.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsPeelResult {
    /**
     * Shared bottom result.
     */
    private static final MpSogsPeelResult BOTTOM = new MpSogsPeelResult(true, 0L);
    /**
     * Is bottom.
     */
    private final boolean bottom;
    /**
     * Opened value, meaningful only when {@code bottom == false}.
     */
    private final long value;

    private MpSogsPeelResult(boolean bottom, long value) {
        this.bottom = bottom;
        this.value = value;
    }

    public static MpSogsPeelResult bottom() {
        return BOTTOM;
    }

    public static MpSogsPeelResult element(long value) {
        return new MpSogsPeelResult(false, value);
    }

    public boolean isBottom() {
        return bottom;
    }

    public long getValue() {
        if (bottom) {
            throw new IllegalStateException("bottom has no value");
        }
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MpSogsPeelResult that)) {
            return false;
        }
        return bottom == that.bottom && value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bottom, value);
    }

    @Override
    public String toString() {
        return bottom ? "bottom" : Long.toUnsignedString(value);
    }
}
