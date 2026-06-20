package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import java.util.List;
import java.util.Set;

/**
 * Local MP-SOGS MPSU runner for development and tests.
 *
 * <p>The {@link MpSogsMpsuConfig.SecurePeelType#DUMMY_CLEAR} mode is not secure. It exists to keep the
 * protocol driver, queue rule, public transcript accounting, and SOGS sketch mutations testable while the
 * semi-honest secure uPeel backend is implemented.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class LocalMpSogsMpsuRunner implements MpSogsMpsuRunner {
    /**
     * Config.
     */
    private final MpSogsMpsuConfig config;

    LocalMpSogsMpsuRunner(MpSogsMpsuConfig config) {
        this.config = config;
    }

    @Override
    public MpSogsMpsuConfig getConfig() {
        return config;
    }

    @Override
    public MpSogsTranscript run(List<Set<Long>> partyInputs) {
        switch (config.getSecurePeelType()) {
            case DUMMY_CLEAR:
                return BatchMpSogsMpsu.runDummyClear(partyInputs, config.getParams());
            case ABB3:
                throw new UnsupportedOperationException(
                    "ABB3 is party-local; use MpSogsMpsuFactory.createAbb3PartyRunner"
                );
            case GENERIC_MPC:
            case OPT_BATCH:
            default:
                throw new UnsupportedOperationException(
                    "secure peel backend is not implemented yet: " + config.getSecurePeelType()
                );
        }
    }
}
