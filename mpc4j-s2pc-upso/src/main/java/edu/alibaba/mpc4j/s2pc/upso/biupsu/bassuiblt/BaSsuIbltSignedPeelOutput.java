package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Signed/source-split peel output summary.
 *
 * <p>This is a local correctness artifact: anchor-only elements are delivered to the shadow-side party, shadow-only
 * elements are delivered to the anchor-side party, and shared singletons are consumed only for peeling.</p>
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BaSsuIbltSignedPeelOutput {
    /**
     * anchor-only elements.
     */
    private final Set<ByteBuffer> anchorOnlyElements;
    /**
     * shadow-only elements.
     */
    private final Set<ByteBuffer> shadowOnlyElements;
    /**
     * accepted shared singleton count.
     */
    private final int sharedSingletonCount;

    BaSsuIbltSignedPeelOutput(Set<ByteBuffer> anchorOnlyElements, Set<ByteBuffer> shadowOnlyElements,
                              int sharedSingletonCount) {
        this.anchorOnlyElements = immutableCopy(anchorOnlyElements);
        this.shadowOnlyElements = immutableCopy(shadowOnlyElements);
        this.sharedSingletonCount = sharedSingletonCount;
    }

    static BaSsuIbltSignedPeelOutput empty() {
        return new BaSsuIbltSignedPeelOutput(Collections.emptySet(), Collections.emptySet(), 0);
    }

    public Set<ByteBuffer> getAnchorOnlyElements() {
        return immutableCopy(anchorOnlyElements);
    }

    public Set<ByteBuffer> getShadowOnlyElements() {
        return immutableCopy(shadowOnlyElements);
    }

    public int getAnchorOnlyCount() {
        return anchorOnlyElements.size();
    }

    public int getShadowOnlyCount() {
        return shadowOnlyElements.size();
    }

    public int getSharedSingletonCount() {
        return sharedSingletonCount;
    }

    public int getAcceptedSingletonCount() {
        return anchorOnlyElements.size() + shadowOnlyElements.size() + sharedSingletonCount;
    }

    private static Set<ByteBuffer> immutableCopy(Set<ByteBuffer> input) {
        Set<ByteBuffer> copy = new LinkedHashSet<>(input.size());
        for (ByteBuffer element : input) {
            copy.add(ByteBuffer.wrap(toBytes(element)));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static byte[] toBytes(ByteBuffer byteBuffer) {
        ByteBuffer duplicate = byteBuffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
