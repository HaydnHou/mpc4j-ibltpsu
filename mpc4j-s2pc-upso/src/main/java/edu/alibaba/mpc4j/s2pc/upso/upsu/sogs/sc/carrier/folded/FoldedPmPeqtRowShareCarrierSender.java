package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierPtoDesc;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierSender;

/**
 * Folded PM-PEQT adapter for the row-share relation carrier sender.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class FoldedPmPeqtRowShareCarrierSender extends AbstractTwoPartyPto
    implements RowShareRelationCarrierSender {
    /**
     * Folded share-output PM-PEQT sender.
     */
    private final FoldedSharePmPeqtSender foldedSharePmPeqtSender;

    public FoldedPmPeqtRowShareCarrierSender(Rpc senderRpc, Party receiverParty,
                                             FoldedPmPeqtRowShareCarrierConfig config) {
        super(RowShareRelationCarrierPtoDesc.getInstance(), senderRpc, receiverParty, config);
        foldedSharePmPeqtSender = FoldedSharePmPeqtFactory.createSender(
            senderRpc, receiverParty, config.getFoldedSharePmPeqtConfig()
        );
        addSubPto(foldedSharePmPeqtSender);
    }

    @Override
    public void init(int maxRow, int maxColumn) throws MpcAbortException {
        foldedSharePmPeqtSender.init(maxRow, maxColumn);
        initState();
    }

    @Override
    public SquareZ2Vector rowShareRelation(byte[][][] inputMatrix, int[] rowPermutationMap,
                                           int[] columnPermutationMap, int byteLength)
        throws MpcAbortException {
        checkInitialized();
        return foldedSharePmPeqtSender.foldedSharePmPeqt(
            inputMatrix, rowPermutationMap, columnPermutationMap, byteLength
        );
    }
}
