package edu.alibaba.mpc4j.s2pc.pso.psu.sogs.unbalanced;

import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuProfile;

/**
 * Reusable SOGS large-set index metadata.
 *
 * <p>This class is only a protocol-design anchor for the unbalanced reusable profile. It intentionally does not expose
 * a queryable membership index.</p>
 *
 * @author donghai hou
 * @date 2026/06/12
 */
public class ReusableSogsIndex {
    /**
     * Large static set capacity.
     */
    private final int largeSetCapacity;
    /**
     * Small per-session set capacity.
     */
    private final int smallSetCapacity;
    /**
     * SOGS graph multiplier.
     */
    private final double sogsAlpha;
    /**
     * SOGS graph degree.
     */
    private final int sogsDegree;

    public ReusableSogsIndex(int largeSetCapacity, int smallSetCapacity, SogsPsuConfig config) {
        if (largeSetCapacity <= 0) {
            throw new IllegalArgumentException("largeSetCapacity must be positive: " + largeSetCapacity);
        }
        if (smallSetCapacity <= 0) {
            throw new IllegalArgumentException("smallSetCapacity must be positive: " + smallSetCapacity);
        }
        if (config.getProfile() != SogsPsuProfile.UNBALANCED_REUSABLE) {
            throw new IllegalArgumentException("profile must be UNBALANCED_REUSABLE: " + config.getProfile());
        }
        this.largeSetCapacity = largeSetCapacity;
        this.smallSetCapacity = smallSetCapacity;
        sogsAlpha = config.getSogsAlpha();
        sogsDegree = config.getSogsDegree();
    }

    public int getLargeSetCapacity() {
        return largeSetCapacity;
    }

    public int getSmallSetCapacity() {
        return smallSetCapacity;
    }

    public double getSogsAlpha() {
        return sogsAlpha;
    }

    public int getSogsDegree() {
        return sogsDegree;
    }
}
