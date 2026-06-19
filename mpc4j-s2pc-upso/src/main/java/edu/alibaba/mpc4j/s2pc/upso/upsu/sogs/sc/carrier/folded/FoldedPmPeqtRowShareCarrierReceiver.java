package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtFactory;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.share.folded.FoldedSharePmPeqtReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierPtoDesc;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierReceiver;

/**
 * Folded PM-PEQT adapter for the row-share relation carrier receiver.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class FoldedPmPeqtRowShareCarrierReceiver extends AbstractTwoPartyPto
    implements RowShareRelationCarrierReceiver {
    /**
     * Folded share-output PM-PEQT receiver.
     */
    private final FoldedSharePmPeqtReceiver foldedSharePmPeqtReceiver;

    public FoldedPmPeqtRowShareCarrierReceiver(Rpc receiverRpc, Party senderParty,
                                               FoldedPmPeqtRowShareCarrierConfig config) {
        super(RowShareRelationCarrierPtoDesc.getInstance(), receiverRpc, senderParty, config);
        foldedSharePmPeqtReceiver = FoldedSharePmPeqtFactory.createReceiver(
            receiverRpc, senderParty, config.getFoldedSharePmPeqtConfig()
        );
        addSubPto(foldedSharePmPeqtReceiver);
    }

    @Override
    public void init(int maxRow, int maxColumn) throws MpcAbortException {
        foldedSharePmPeqtReceiver.init(maxRow, maxColumn);
        initState();
    }

    @Override
    public SquareZ2Vector rowShareRelation(byte[][][] inputMatrix, int byteLength, int row, int column)
        throws MpcAbortException {
        checkInitialized();
        return foldedSharePmPeqtReceiver.foldedSharePmPeqt(inputMatrix, byteLength, row, column);
    }
}
