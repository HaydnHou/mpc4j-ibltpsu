package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;

/**
 * BA-SSU-IBLT MP-OPRF tag receiver wrapper.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltOprfTagReceiver {
    /**
     * config.
     */
    private final BaSsuIbltOprfTagConfig config;
    /**
     * MP-OPRF receiver.
     */
    private final MpOprfReceiver mpOprfReceiver;

    public BaSsuIbltOprfTagReceiver(Rpc receiverRpc, Party senderParty, BaSsuIbltOprfTagConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        this.config = config;
        mpOprfReceiver = OprfFactory.createMpOprfReceiver(receiverRpc, senderParty, config.getMpOprfConfig());
    }

    public void setTaskId(int taskId) {
        mpOprfReceiver.setTaskId(taskId);
    }

    public void setParallel(boolean parallel) {
        mpOprfReceiver.setParallel(parallel);
    }

    public void init() throws MpcAbortException {
        mpOprfReceiver.init(config.getPublicCapacity());
    }

    BaSsuIbltOprfTagOutput generate(byte[][] fixedInputs) throws MpcAbortException {
        BaSsuIbltOprfTagSender.checkFixedInputs(fixedInputs, config.getPublicCapacity());
        MpOprfReceiverOutput receiverOutput = mpOprfReceiver.oprf(fixedInputs);
        byte[][] tags = new byte[fixedInputs.length][];
        byte[][] checks = new byte[fixedInputs.length][];
        for (int index = 0; index < fixedInputs.length; index++) {
            byte[] prf = receiverOutput.getPrf(index);
            tags[index] = BaSsuIbltOprfTagPipeline.tagFromPrf(prf, config.getTagByteLength());
            checks[index] = BaSsuIbltOprfTagPipeline.checkFromTag(tags[index], config.getCheckByteLength());
        }
        return new BaSsuIbltOprfTagOutput(config.getTagByteLength(), config.getCheckByteLength(), tags, checks);
    }

    public void destroy() {
        mpOprfReceiver.destroy();
    }
}
