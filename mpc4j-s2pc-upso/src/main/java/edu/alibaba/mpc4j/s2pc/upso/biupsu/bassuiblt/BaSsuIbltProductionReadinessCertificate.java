package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Package-private production readiness certificate for BA-SSU-IBLT queue-peel endpoint claims.
 *
 * <p>This class intentionally has no public constructor or public factory. It prevents external configuration code from
 * flipping production hard gates with ordinary builder booleans. A future final-audit implementation may mint this
 * certificate from inside the package after all production checks and measured endpoint wiring pass.</p>
 *
 * @author donghai hou
 * @date 2026/06/06
 */
final class BaSsuIbltProductionReadinessCertificate {
    /**
     * audit certificate.
     */
    private final boolean productionAuditPassed;
    /**
     * no-reference-fallback certificate.
     */
    private final boolean noReferenceFallbackCertificate;
    /**
     * measured endpoint wiring certificate.
     */
    private final boolean measuredEndpointWired;

    private BaSsuIbltProductionReadinessCertificate(
        boolean productionAuditPassed, boolean noReferenceFallbackCertificate, boolean measuredEndpointWired) {
        this.productionAuditPassed = productionAuditPassed;
        this.noReferenceFallbackCertificate = noReferenceFallbackCertificate;
        this.measuredEndpointWired = measuredEndpointWired;
    }

    static BaSsuIbltProductionReadinessCertificate none() {
        return new BaSsuIbltProductionReadinessCertificate(false, false, false);
    }

    static BaSsuIbltProductionReadinessCertificate finalAuditPassed() {
        return new BaSsuIbltProductionReadinessCertificate(true, true, true);
    }

    boolean hasProductionAuditPassed() {
        return productionAuditPassed;
    }

    boolean hasNoReferenceFallbackCertificate() {
        return noReferenceFallbackCertificate;
    }

    boolean hasMeasuredEndpointWired() {
        return measuredEndpointWired;
    }
}
