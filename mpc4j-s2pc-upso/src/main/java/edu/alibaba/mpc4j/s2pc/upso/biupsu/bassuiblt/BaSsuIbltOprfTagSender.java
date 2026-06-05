package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;

/**
 * BA-SSU-IBLT MP-OPRF tag sender wrapper.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltOprfTagSender {
    /**
     * config.
     */
    private final BaSsuIbltOprfTagConfig config;
    /**
     * MP-OPRF sender.
     */
    private final MpOprfSender mpOprfSender;

    public BaSsuIbltOprfTagSender(Rpc senderRpc, Party receiverParty, BaSsuIbltOprfTagConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must be non-null");
        }
        this.config = config;
        mpOprfSender = OprfFactory.createMpOprfSender(senderRpc, receiverParty, config.getMpOprfConfig());
    }

    public void setTaskId(int taskId) {
        mpOprfSender.setTaskId(taskId);
    }

    public void setParallel(boolean parallel) {
        mpOprfSender.setParallel(parallel);
    }

    public void init() throws MpcAbortException {
        mpOprfSender.init(config.getPublicCapacity());
    }

    BaSsuIbltOprfTagOutput generate(byte[][] fixedInputs) throws MpcAbortException {
        checkFixedInputs(fixedInputs);
        MpOprfSenderOutput senderOutput = mpOprfSender.oprf(config.getPublicCapacity());
        byte[][] tags = new byte[fixedInputs.length][];
        byte[][] checks = new byte[fixedInputs.length][];
        for (int index = 0; index < fixedInputs.length; index++) {
            byte[] prf = senderOutput.getPrf(fixedInputs[index]);
            tags[index] = BaSsuIbltOprfTagPipeline.tagFromPrf(prf, config.getTagByteLength());
            checks[index] = BaSsuIbltOprfTagPipeline.checkFromTag(tags[index], config.getCheckByteLength());
        }
        return new BaSsuIbltOprfTagOutput(config.getTagByteLength(), config.getCheckByteLength(), tags, checks);
    }

    public void destroy() {
        mpOprfSender.destroy();
    }

    static void checkFixedInputs(byte[][] fixedInputs, int publicCapacity) {
        checkFixedInputs(fixedInputs);
        if (fixedInputs.length != publicCapacity) {
            throw new IllegalArgumentException("fixedInputs length must equal publicCapacity");
        }
    }

    static void checkFixedInputs(byte[][] fixedInputs) {
        if (fixedInputs == null) {
            throw new IllegalArgumentException("fixedInputs must be non-null");
        }
        if (fixedInputs.length == 0) {
            throw new IllegalArgumentException("fixedInputs must be non-empty");
        }
        for (byte[] input : fixedInputs) {
            if (input == null) {
                throw new IllegalArgumentException("fixedInputs must not contain null entries");
            }
        }
    }
}
