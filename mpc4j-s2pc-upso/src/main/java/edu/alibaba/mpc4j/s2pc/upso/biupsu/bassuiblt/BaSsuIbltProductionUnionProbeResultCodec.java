package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Fixed-shape public result codec for one production UP-BA-UPOT bucket probe.
 *
 * <p>This package-private codec is not an authentication boundary for the public bucket index. Callers must pass a
 * bucket index that has already been validated by the public schedule and monotone material-ordinal checks.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltProductionUnionProbeResultCodec {
    /**
     * bottom result code.
     */
    private static final byte TYPE_BOTTOM = 0x00;
    /**
     * singleton result code.
     */
    private static final byte TYPE_SINGLETON = 0x01;
    /**
     * element byte length.
     */
    private final int elementByteLength;

    BaSsuIbltProductionUnionProbeResultCodec(int elementByteLength) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        this.elementByteLength = elementByteLength;
    }

    int byteLength() {
        return 1 + elementByteLength;
    }

    byte[] encode(BaSsuIbltProductionUnionProbeOutput output) {
        if (output == null) {
            throw new IllegalArgumentException("output must be non-null");
        }
        if (output.getElementByteLength() != elementByteLength) {
            throw new IllegalArgumentException("output elementByteLength must match codec");
        }
        byte[] encoded = new byte[byteLength()];
        encoded[0] = switch (output.getType()) {
            case BOTTOM -> TYPE_BOTTOM;
            case UNION_SINGLETON -> TYPE_SINGLETON;
        };
        if (output.isSingleton()) {
            System.arraycopy(output.getElement(), 0, encoded, 1, elementByteLength);
        }
        return encoded;
    }

    BaSsuIbltProductionUnionProbeOutput decode(int bucketIndex, byte[] encoded) {
        if (bucketIndex < 0) {
            throw new IllegalArgumentException("bucketIndex must be non-negative");
        }
        if (encoded == null || encoded.length != byteLength()) {
            throw new IllegalArgumentException("fixed result payload has invalid length");
        }
        byte[] payload = Arrays.copyOfRange(encoded, 1, encoded.length);
        return switch (encoded[0]) {
            case TYPE_BOTTOM -> {
                requireZeroPayload(payload);
                yield BaSsuIbltProductionUnionProbeOutput.bottom(bucketIndex, elementByteLength);
            }
            case TYPE_SINGLETON -> BaSsuIbltProductionUnionProbeOutput.singleton(bucketIndex, payload, elementByteLength);
            default -> throw new IllegalArgumentException("unknown fixed result type");
        };
    }

    private static void requireZeroPayload(byte[] payload) {
        for (byte value : payload) {
            if (value != 0) {
                throw new IllegalArgumentException("non-singleton fixed result must carry dummy-zero payload");
            }
        }
    }
}
