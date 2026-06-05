package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;

/**
 * Non-production queue-peel union-probe backend markers.
 *
 * <p>These markers make rejection tests explicit: benchmark/plain/transducer and generic routes must not enable the
 * fast secure endpoint.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltNonProductionUnionProbeBackendConfig extends AbstractMultiPartyPtoConfig
    implements BaSsuIbltUnionProbeBackendConfig {
    /**
     * non-production route.
     */
    public enum Route {
        /**
         * Plain benchmark evaluator.
         */
        PLAIN_BENCHMARK,
        /**
         * Wire-masked transducer scaffold.
         */
        WIRE_MASKED_TRANSDUCER,
        /**
         * Generic MPC route.
         */
        GENERIC_MPC,
        /**
         * PEQT route.
         */
        PEQT,
        /**
         * OKVS route.
         */
        OKVS,
        /**
         * PIR route.
         */
        PIR
    }

    /**
     * route.
     */
    private final Route route;

    public BaSsuIbltNonProductionUnionProbeBackendConfig(Route route) {
        super(SecurityModel.SEMI_HONEST);
        if (route == null) {
            throw new IllegalArgumentException("route must be non-null");
        }
        this.route = route;
    }

    public Route getRoute() {
        return route;
    }

    @Override
    public String getUnionProbeBackendName() {
        return "non-production union-probe route: " + route.name();
    }

    @Override
    public boolean isSpecializedBucketProbe() {
        return false;
    }

    @Override
    public boolean isQueuePeelProductionReady() {
        return false;
    }
}
