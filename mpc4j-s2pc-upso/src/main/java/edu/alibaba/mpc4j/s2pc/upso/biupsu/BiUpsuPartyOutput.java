package edu.alibaba.mpc4j.s2pc.upso.biupsu;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bi-output UPSU party output.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
public class BiUpsuPartyOutput {
    /**
     * Unknown PSI-CA.
     */
    public static final int UNKNOWN_PSICA = -1;
    /**
     * union set.
     */
    private final Set<ByteBuffer> unionSet;
    /**
     * PSI-CA.
     */
    private final int psica;

    public BiUpsuPartyOutput(Set<ByteBuffer> unionSet, int psica) {
        this.unionSet = immutableCopy(unionSet);
        this.psica = psica;
    }

    public Set<ByteBuffer> getUnion() {
        return immutableCopy(unionSet);
    }

    public int getPsica() {
        return psica;
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
