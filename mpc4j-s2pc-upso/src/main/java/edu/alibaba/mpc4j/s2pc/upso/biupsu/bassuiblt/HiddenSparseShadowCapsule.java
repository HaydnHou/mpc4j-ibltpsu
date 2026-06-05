package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Fixed-shape hidden sparse shadow capsule metadata for simulator and tests.
 *
 * <p>The real protocol must encode all classes into the same external message shape. This class records only the
 * logical class used by local tests and must not be serialized as a clear online message.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class HiddenSparseShadowCapsule {
    /**
     * bucket count class.
     */
    public enum CountClass {
        /**
         * empty bucket.
         */
        EMPTY,
        /**
         * singleton bucket.
         */
        SINGLETON,
        /**
         * many bucket.
         */
        MANY,
        /**
         * dummy bucket.
         */
        DUMMY
    }

    /**
     * count class.
     */
    private final CountClass countClass;
    /**
     * singleton element, or 0 for non-singleton.
     */
    private final int singletonElement;

    private HiddenSparseShadowCapsule(CountClass countClass, int singletonElement) {
        this.countClass = countClass;
        this.singletonElement = singletonElement;
    }

    public static HiddenSparseShadowCapsule empty() {
        return new HiddenSparseShadowCapsule(CountClass.EMPTY, 0);
    }

    public static HiddenSparseShadowCapsule singleton(int element) {
        if (element <= 0) {
            throw new IllegalArgumentException("singleton element must be positive");
        }
        return new HiddenSparseShadowCapsule(CountClass.SINGLETON, element);
    }

    public static HiddenSparseShadowCapsule many() {
        return new HiddenSparseShadowCapsule(CountClass.MANY, 0);
    }

    public static HiddenSparseShadowCapsule dummy() {
        return new HiddenSparseShadowCapsule(CountClass.DUMMY, 0);
    }

    public CountClass getCountClass() {
        return countClass;
    }

    public int getSingletonElement() {
        return singletonElement;
    }
}
