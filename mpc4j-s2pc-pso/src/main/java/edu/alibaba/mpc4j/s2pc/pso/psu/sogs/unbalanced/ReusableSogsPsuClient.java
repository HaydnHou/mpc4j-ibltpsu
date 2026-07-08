package edu.alibaba.mpc4j.s2pc.pso.psu.sogs.unbalanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuProfile;
import edu.alibaba.mpc4j.s2pc.pso.psu.sogs.SogsPsuPtoDesc;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Reusable unbalanced SOGS-PSU client skeleton.
 *
 * <p>The protocol is intentionally not implemented yet. This class reserves the package/API location for the static
 * large-set repeated-session design.</p>
 *
 * @author donghai hou
 * @date 2026/06/12
 */
public class ReusableSogsPsuClient extends AbstractPsuClient {
    public ReusableSogsPsuClient(Rpc clientRpc, Party serverParty, SogsPsuConfig config) {
        super(SogsPsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        if (config.getProfile() != SogsPsuProfile.UNBALANCED_REUSABLE) {
            throw new IllegalArgumentException("profile must be UNBALANCED_REUSABLE: " + config.getProfile());
        }
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        throw notImplemented();
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        throw notImplemented();
    }

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException(
            "UNBALANCED_REUSABLE SOGS-PSU is a protocol-design skeleton and is not implemented yet"
        );
    }
}
