package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.function.BooleanSupplier;

/**
 * Source-agnostic authenticated frontier delete helper.
 *
 * <p>The helper keeps the local membership decision adjacent to the authenticated source-layer delete. In the real
 * protocol this decision remains inside the endpoint that owns the source layer; this local scaffold uses the same
 * discipline without exporting a source label.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltSourceAgnosticFrontierDelete {
    /**
     * private constructor.
     */
    private BaSsuIbltSourceAgnosticFrontierDelete() {
        // empty
    }

    static boolean apply(BooleanSupplier localDeleteMarker, Runnable authenticatedDelete) {
        if (localDeleteMarker == null) {
            throw new IllegalArgumentException("localDeleteMarker must be non-null");
        }
        if (authenticatedDelete == null) {
            throw new IllegalArgumentException("authenticatedDelete must be non-null");
        }
        boolean locallyPresent = localDeleteMarker.getAsBoolean();
        if (locallyPresent) {
            authenticatedDelete.run();
        }
        return locallyPresent;
    }
}
