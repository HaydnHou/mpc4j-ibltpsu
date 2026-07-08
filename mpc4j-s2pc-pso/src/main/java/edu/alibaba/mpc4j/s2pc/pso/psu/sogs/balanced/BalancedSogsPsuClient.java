package edu.alibaba.mpc4j.s2pc.pso.psu.sogs.balanced;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuProfile;

/**
 * Balanced SOGS-PSU client.
 *
 * <p>This profile is the current IBLT-style union-peel baseline for single-session balanced PSU.</p>
 *
 * @author donghai hou
 * @date 2026/06/12
 */
public class BalancedSogsPsuClient extends SogsPsuClient {
    public BalancedSogsPsuClient(Rpc clientRpc, Party serverParty, SogsPsuConfig config) {
        super(clientRpc, serverParty, config);
        if (config.getProfile() != SogsPsuProfile.BALANCED) {
            throw new IllegalArgumentException("profile must be BALANCED: " + config.getProfile());
        }
    }
}
