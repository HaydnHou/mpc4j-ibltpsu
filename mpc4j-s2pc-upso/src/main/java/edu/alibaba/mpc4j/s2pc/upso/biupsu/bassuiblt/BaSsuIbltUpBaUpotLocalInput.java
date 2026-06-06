package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;

/**
 * Private local input for one queue-peel UP-BA-UPOT bucket probe.
 *
 * <p>The class is package-private on purpose: it is protocol-internal material, not a public output surface. It may
 * hold local tag/check/auth bytes, but no public accessor exposes those bytes outside this package.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
class BaSsuIbltUpBaUpotLocalInput {
    /**
     * local authenticated bucket state.
     */
    enum State {
        /**
         * empty bucket.
         */
        EMPTY,
        /**
         * singleton bucket.
         */
        SINGLETON,
        /**
         * blocked, many, or invalid bucket.
         */
        BLOCKED
    }

    /**
     * local state.
     */
    private final State state;
    /**
     * bound profile id.
     */
    private final String profileId;
    /**
     * bound retry id.
     */
    private final int retryId;
    /**
     * bound bucket index.
     */
    private final int bucketIndex;
    /**
     * bound probe ordinal within the retry.
     */
    private final int probeOrdinal;
    /**
     * bound element byte length.
     */
    private final int elementByteLength;
    /**
     * bound tag byte length.
     */
    private final int tagByteLength;
    /**
     * bound check byte length.
     */
    private final int checkByteLength;
    /**
     * bound auth tag byte length.
     */
    private final int authTagByteLength;
    /**
     * local candidate or dummy.
     */
    private final byte[] element;
    /**
     * local tag material.
     */
    private final byte[] tag;
    /**
     * local check material.
     */
    private final byte[] check;
    /**
     * local auth material.
     */
    private final byte[] auth;

    private BaSsuIbltUpBaUpotLocalInput(BaSsuIbltUpBaUpotPublicInput publicInput, State state,
                                        byte[] element, byte[] tag, byte[] check, byte[] auth) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must be non-null");
        }
        if (element == null || tag == null || check == null || auth == null) {
            throw new IllegalArgumentException("local byte arrays must be non-null");
        }
        this.state = state;
        profileId = publicInput.getProfileId();
        retryId = publicInput.getRetryId();
        bucketIndex = publicInput.getBucketIndex();
        probeOrdinal = publicInput.getProbeOrdinal();
        elementByteLength = publicInput.getElementByteLength();
        tagByteLength = publicInput.getTagByteLength();
        checkByteLength = publicInput.getCheckByteLength();
        authTagByteLength = publicInput.getAuthTagByteLength();
        this.element = Arrays.copyOf(element, element.length);
        this.tag = Arrays.copyOf(tag, tag.length);
        this.check = Arrays.copyOf(check, check.length);
        this.auth = Arrays.copyOf(auth, auth.length);
    }

    static BaSsuIbltUpBaUpotLocalInput empty(BaSsuIbltUpBaUpotPublicInput publicInput) {
        return empty(publicInput, new byte[publicInput.getAuthTagByteLength()]);
    }

    private static BaSsuIbltUpBaUpotLocalInput empty(BaSsuIbltUpBaUpotPublicInput publicInput, byte[] auth) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (auth == null || auth.length != publicInput.getAuthTagByteLength()) {
            throw new IllegalArgumentException("auth length must match public input");
        }
        return new BaSsuIbltUpBaUpotLocalInput(
            publicInput,
            State.EMPTY,
            new byte[publicInput.getElementByteLength()],
            new byte[publicInput.getTagByteLength()],
            new byte[publicInput.getCheckByteLength()],
            auth
        );
    }

    static BaSsuIbltUpBaUpotLocalInput singleton(BaSsuIbltUpBaUpotPublicInput publicInput, byte[] element,
                                                 byte[] tag, byte[] check, byte[] auth) {
        validateLengths(publicInput, element, tag, check, auth);
        return new BaSsuIbltUpBaUpotLocalInput(publicInput, State.SINGLETON, element, tag, check, auth);
    }

    static BaSsuIbltUpBaUpotLocalInput blocked(BaSsuIbltUpBaUpotPublicInput publicInput, byte[] element,
                                               byte[] tag, byte[] check, byte[] auth) {
        validateLengths(publicInput, element, tag, check, auth);
        return new BaSsuIbltUpBaUpotLocalInput(publicInput, State.BLOCKED, element, tag, check, auth);
    }

    static BaSsuIbltUpBaUpotLocalInput fromCellView(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                    BaSsuIbltSecureCellView cellView, byte[] auth) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (cellView == null) {
            throw new IllegalArgumentException("cellView must be non-null");
        }
        if (auth == null || auth.length != publicInput.getAuthTagByteLength()) {
            throw new IllegalArgumentException("auth length must match public input");
        }
        if (cellView.getElementByteLength() != publicInput.getElementByteLength()
            || cellView.getTagByteLength() != publicInput.getTagByteLength()
            || cellView.getCheckByteLength() != publicInput.getCheckByteLength()) {
            throw new IllegalArgumentException("cellView shape must match public input");
        }
        if (isAuthenticatedEmpty(cellView)) {
            return empty(publicInput, auth);
        }
        byte[] element = cellView.getKeyXor();
        byte[] tag = cellView.getTagXor();
        byte[] check = cellView.getCheckXor();
        return cellView.isValidSingleton()
            ? singleton(publicInput, element, tag, check, auth)
            : blocked(publicInput, element, tag, check, auth);
    }

    State getState() {
        return state;
    }

    int cellCount() {
        return switch (state) {
            case EMPTY -> 0;
            case SINGLETON -> 1;
            case BLOCKED -> 2;
        };
    }

    byte[] elementCopy() {
        return Arrays.copyOf(element, element.length);
    }

    byte[] tagCopy() {
        return Arrays.copyOf(tag, tag.length);
    }

    byte[] checkCopy() {
        return Arrays.copyOf(check, check.length);
    }

    byte[] authCopy() {
        return Arrays.copyOf(auth, auth.length);
    }

    void validatePublicInput(BaSsuIbltUpBaUpotPublicInput publicInput) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (!profileId.equals(publicInput.getProfileId())
            || retryId != publicInput.getRetryId()
            || bucketIndex != publicInput.getBucketIndex()
            || probeOrdinal != publicInput.getProbeOrdinal()
            || elementByteLength != publicInput.getElementByteLength()
            || tagByteLength != publicInput.getTagByteLength()
            || checkByteLength != publicInput.getCheckByteLength()
            || authTagByteLength != publicInput.getAuthTagByteLength()) {
            throw new IllegalArgumentException("localInput must be bound to the same public probe slot");
        }
    }

    BaSsuIbltSecureCellView toCellView() {
        return BaSsuIbltSecureCellView.of(cellCount(), element, tag, check);
    }

    private static void validateLengths(BaSsuIbltUpBaUpotPublicInput publicInput, byte[] element, byte[] tag,
                                        byte[] check, byte[] auth) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (element == null || element.length != publicInput.getElementByteLength()) {
            throw new IllegalArgumentException("element length must match public input");
        }
        if (tag == null || tag.length != publicInput.getTagByteLength()) {
            throw new IllegalArgumentException("tag length must match public input");
        }
        if (check == null || check.length != publicInput.getCheckByteLength()) {
            throw new IllegalArgumentException("check length must match public input");
        }
        if (auth == null || auth.length != publicInput.getAuthTagByteLength()) {
            throw new IllegalArgumentException("auth length must match public input");
        }
    }

    private static boolean isAuthenticatedEmpty(BaSsuIbltSecureCellView cellView) {
        return cellView.getCount() == 0 && isZero(cellView.getKeyXorReference())
            && isZero(cellView.getTagXorReference()) && isZero(cellView.getCheckXorReference());
    }

    private static boolean isZero(byte[] input) {
        for (byte value : input) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
