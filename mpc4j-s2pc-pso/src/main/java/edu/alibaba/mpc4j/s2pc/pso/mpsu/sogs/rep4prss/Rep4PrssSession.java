package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.rep4prss;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed.PrssPhase;

/**
 * Attempt-local REP4 PRSS session. All actual-size backends share one seed setup and one domain allocator.
 *
 * @author donghai hou
 * @date 2026/07/18
 */
class Rep4PrssSession {
    private final Rpc rpc;
    private final long taskId;
    private final Rep4PrssSeedManager seedManager;
    private long nextBackendId;
    private long nextOperationId;
    private int networkRoundCount;

    Rep4PrssSession(Rpc rpc, long taskId) {
        this.rpc = rpc;
        this.taskId = taskId;
        seedManager = new Rep4PrssSeedManager(rpc, taskId);
        // Backend id 0 is reserved for the standalone public backend constructor.
        nextBackendId = 1L;
        nextOperationId = 0L;
        networkRoundCount = 0;
    }

    synchronized Rep4PrssPackedBooleanBackend createBackend(int batchSize, PrssPhase phase) {
        long backendId = nextBackendId;
        nextBackendId = Math.incrementExact(nextBackendId);
        return new Rep4PrssPackedBooleanBackend(rpc, batchSize, taskId, this, backendId, phase);
    }

    synchronized long nextOperationId() {
        long operationId = nextOperationId;
        nextOperationId = Math.incrementExact(nextOperationId);
        return operationId;
    }

    Rep4PrssSeedManager getSeedManager() {
        return seedManager;
    }

    synchronized int getNetworkRoundCount() {
        return networkRoundCount;
    }

    synchronized void resetNetworkRoundCount() {
        networkRoundCount = 0;
    }

    synchronized void incrementNetworkRoundCount() {
        networkRoundCount = Math.incrementExact(networkRoundCount);
    }
}
