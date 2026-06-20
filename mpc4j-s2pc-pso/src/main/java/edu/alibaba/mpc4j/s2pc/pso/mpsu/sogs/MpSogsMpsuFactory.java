package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3.Abb3MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;

/**
 * MP-SOGS MPSU factory.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuFactory implements PtoFactory {
    /**
     * private constructor.
     */
    private MpSogsMpsuFactory() {
        // empty
    }

    /**
     * MP-SOGS MPSU type.
     */
    public enum MpSogsMpsuType {
        /**
         * All-output MP-SOGS MPSU.
         */
        MP_SOGS,
    }

    /**
     * Creates a local development runner.
     *
     * @param config config.
     * @return local runner.
     */
    public static MpSogsMpsuRunner createLocalRunner(MpSogsMpsuConfig config) {
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new LocalMpSogsMpsuRunner(config);
            default:
                throw new IllegalArgumentException("Invalid " + MpSogsMpsuType.class.getSimpleName() + ": "
                    + config.getPtoType());
        }
    }

    /**
     * Creates an ABB3 party-local runner.
     *
     * @param z2cParty ABB3 Z2 circuit party.
     * @param config config.
     * @return ABB3 party-local runner.
     */
    public static Abb3MpSogsMpsuPartyRunner createAbb3PartyRunner(TripletZ2cParty z2cParty,
                                                                  MpSogsMpsuConfig config) {
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new Abb3MpSogsMpsuPartyRunner(z2cParty, config);
            default:
                throw new IllegalArgumentException("Invalid " + MpSogsMpsuType.class.getSimpleName() + ": "
                    + config.getPtoType());
        }
    }

    /**
     * Creates default config.
     *
     * @param securityModel security model.
     * @param partyNum number of parties.
     * @param tauMax public union-size upper bound.
     * @return default config.
     */
    public static MpSogsMpsuConfig createDefaultConfig(SecurityModel securityModel, int partyNum, int tauMax) {
        switch (securityModel) {
            case SEMI_HONEST:
                return new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(partyNum, tauMax).build())
                    .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.ABB3)
                    .build();
            case IDEAL:
            case TRUSTED_DEALER:
                return new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(partyNum, tauMax).build())
                    .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.DUMMY_CLEAR)
                    .build();
            case MALICIOUS:
            default:
                throw new IllegalArgumentException("Invalid " + SecurityModel.class.getSimpleName() + ": "
                    + securityModel.name());
        }
    }
}
