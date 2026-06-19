package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.RowShareRelationCarrierFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc.carrier.folded.FoldedPmPeqtRowShareCarrierConfig;
import org.junit.Assert;
import org.junit.Test;

/**
 * Factory test for standalone SC-SOGS UPSU.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ScSogsUpsuFactoryTest extends AbstractTwoPartyMemoryRpcPto {
    public ScSogsUpsuFactoryTest() {
        super(ScSogsUpsuFactoryTest.class.getSimpleName());
    }

    @Test
    public void testStandaloneScSogsFactoryRoute() {
        ScSogsUpsuConfig config = new ScSogsUpsuConfig.Builder().build();
        Assert.assertEquals(UpsuFactory.UpsuType.SC_SOGS, config.getPtoType());
        Assert.assertEquals(
            RowShareRelationCarrierFactory.RowShareRelationCarrierType.FOLDED_PMPEQT,
            config.getRowShareRelationCarrierConfig().getPtoType()
        );
        Assert.assertEquals(
            RowShareRelationCarrierConfig.InputType.DIGEST,
            config.getRowShareRelationCarrierConfig().getInputType()
        );
        Assert.assertEquals(
            RowShareRelationCarrierConfig.NetworkShape.ALPHA_BY_BIN,
            config.getRowShareRelationCarrierConfig().getNetworkShape()
        );
        Assert.assertTrue(config.getRowShareRelationCarrierConfig() instanceof FoldedPmPeqtRowShareCarrierConfig);

        UpsuSender sender = UpsuFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        UpsuReceiver receiver = UpsuFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);

        Assert.assertTrue(sender instanceof ScSogsUpsuSender);
        Assert.assertTrue(receiver instanceof ScSogsUpsuReceiver);
        Assert.assertEquals("SC_SOGS_UPSU", sender.getPtoDesc().getPtoName());
        Assert.assertEquals("SC_SOGS_UPSU", receiver.getPtoDesc().getPtoName());

        sender.destroy();
        receiver.destroy();
    }
}
