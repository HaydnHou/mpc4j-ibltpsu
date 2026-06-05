package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Retry-local duplicate-safe union-list coalescer.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class DuplicateSafeUnionListCoalescer {
    /**
     * seen bitmap.
     */
    private final BitSet seen;
    /**
     * output elements.
     */
    private int[] elements;
    /**
     * output size.
     */
    private int size;

    public DuplicateSafeUnionListCoalescer(int maxElement) {
        seen = new BitSet(maxElement + 1);
        elements = new int[Math.max(1, Math.min(maxElement, 1024))];
        size = 0;
    }

    /**
     * Inserts an element if it has not appeared in this retry.
     *
     * @param element positive element id.
     * @return true if this is a new element.
     */
    public boolean insert(int element) {
        if (element <= 0) {
            throw new IllegalArgumentException("element must be positive");
        }
        if (seen.get(element)) {
            return false;
        }
        seen.set(element);
        ensureCapacity(size + 1);
        elements[size++] = element;
        return true;
    }

    /**
     * Returns the canonical sorted union.
     *
     * @return sorted union.
     */
    public int[] toCanonicalArray() {
        int[] result = Arrays.copyOf(elements, size);
        Arrays.sort(result);
        return result;
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(int minCapacity) {
        if (elements.length >= minCapacity) {
            return;
        }
        int newCapacity = Math.max(minCapacity, elements.length << 1);
        elements = Arrays.copyOf(elements, newCapacity);
    }
}
