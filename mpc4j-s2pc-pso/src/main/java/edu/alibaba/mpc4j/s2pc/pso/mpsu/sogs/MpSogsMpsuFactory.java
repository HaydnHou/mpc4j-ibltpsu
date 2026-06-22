package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3.Abb3MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4.Rep4MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss.Rep4PrssMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep5prss.Rep5PrssMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.shamir.ShamirMpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;
import edu.alibaba.mpc4j.common.rpc.Rpc;

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
        if (config.getParams().getPartyNum() != MpSogsMpsuConfig.ABB3_PARTY_NUM) {
            throw new IllegalArgumentException("ABB3 MP-SOGS backend requires exactly "
                + MpSogsMpsuConfig.ABB3_PARTY_NUM + " parties: " + config.getParams().getPartyNum());
        }
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new Abb3MpSogsMpsuPartyRunner(z2cParty, config);
            default:
                throw new IllegalArgumentException("Invalid " + MpSogsMpsuType.class.getSimpleName() + ": "
                    + config.getPtoType());
        }
    }

    /**
     * Creates a Shamir party-local runner.
     *
     * @param rpc RPC.
     * @param config config.
     * @param taskId task id.
     * @return Shamir party-local runner.
     */
    public static ShamirMpSogsMpsuPartyRunner createShamirPartyRunner(Rpc rpc, MpSogsMpsuConfig config,
                                                                      long taskId) {
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new ShamirMpSogsMpsuPartyRunner(rpc, config, taskId);
            default:
                throw new IllegalArgumentException("Invalid " + MpSogsMpsuType.class.getSimpleName() + ": "
                    + config.getPtoType());
        }
    }

    /**
     * Creates a 4-party packed replicated Z2 runner.
     *
     * @param rpc RPC.
     * @param config config.
     * @param taskId task id.
     * @return REP4 packed party-local runner.
     */
    public static Rep4MpSogsMpsuPartyRunner createRep4PartyRunner(Rpc rpc, MpSogsMpsuConfig config, long taskId) {
        if (config.getParams().getPartyNum() != 4) {
            throw new IllegalArgumentException("REP4 packed MP-SOGS backend requires exactly 4 parties: "
                + config.getParams().getPartyNum());
        }
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new Rep4MpSogsMpsuPartyRunner(rpc, config, taskId);
            default:
                throw new IllegalArgumentException("Invalid " + MpSogsMpsuType.class.getSimpleName() + ": "
                    + config.getPtoType());
        }
    }

    /**
     * Creates a 4-party PRSS packed replicated Z2 runner.
     *
     * @param rpc RPC.
     * @param config config.
     * @param taskId task id.
     * @return REP4 PRSS packed party-local runner.
     */
    public static Rep4PrssMpSogsMpsuPartyRunner createRep4PrssPartyRunner(Rpc rpc, MpSogsMpsuConfig config,
                                                                          long taskId) {
        if (config.getParams().getPartyNum() != 4) {
            throw new IllegalArgumentException("REP4 PRSS packed MP-SOGS backend requires exactly 4 parties: "
                + config.getParams().getPartyNum());
        }
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new Rep4PrssMpSogsMpsuPartyRunner(rpc, config, taskId);
            default:
                throw new IllegalArgumentException("Invalid " + MpSogsMpsuType.class.getSimpleName() + ": "
                    + config.getPtoType());
        }
    }

    /**
     * Creates a 5-party packed replicated Z2 runner.
     *
     * @param rpc RPC.
     * @param config config.
     * @param taskId task id.
     * @return REP5 packed party-local runner.
     */
    public static Object createRep5PartyRunner(Rpc rpc, MpSogsMpsuConfig config, long taskId) {
        if (config.getParams().getPartyNum() != 5) {
            throw new IllegalArgumentException("REP5 packed MP-SOGS backend requires exactly 5 parties: "
                + config.getParams().getPartyNum());
        }
        throw new UnsupportedOperationException("REP5 packed MP-SOGS backend is not implemented yet");
    }

    /**
     * Creates a 5-party T1 PRSS packed replicated Z2 runner.
     *
     * @param rpc RPC.
     * @param config config.
     * @param taskId task id.
     * @return REP5 PRSS packed party-local runner.
     */
    public static Rep5PrssMpSogsMpsuPartyRunner createRep5PrssPartyRunner(Rpc rpc, MpSogsMpsuConfig config,
                                                                          long taskId) {
        if (config.getParams().getPartyNum() != 5) {
            throw new IllegalArgumentException("REP5 PRSS packed MP-SOGS backend requires exactly 5 parties: "
                + config.getParams().getPartyNum());
        }
        switch (config.getPtoType()) {
            case MP_SOGS:
                return new Rep5PrssMpSogsMpsuPartyRunner(rpc, config, taskId);
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
                MpSogsMpsuConfig.SecurePeelType securePeelType = partyNum == MpSogsMpsuConfig.ABB3_PARTY_NUM
                    ? MpSogsMpsuConfig.SecurePeelType.ABB3
                    : MpSogsMpsuConfig.SecurePeelType.SHAMIR;
                return new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(partyNum, tauMax).build())
                        .setSecurePeelType(securePeelType)
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
