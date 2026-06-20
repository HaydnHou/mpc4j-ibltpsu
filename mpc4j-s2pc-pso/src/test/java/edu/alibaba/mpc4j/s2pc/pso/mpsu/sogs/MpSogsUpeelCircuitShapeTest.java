package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * MP-SOGS secure-uPeel circuit shape tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsUpeelCircuitShapeTest {
    @Test
    public void testShapeScalesWithPublicBatchOnly() {
        MpSogsUpeelCircuitShape oneCell = new MpSogsUpeelCircuitShape(3, 64, 1);
        MpSogsUpeelCircuitShape tenCells = new MpSogsUpeelCircuitShape(3, 64, 10);
        Assert.assertEquals(3, oneCell.getEqualityChecksPerCell());
        Assert.assertEquals(3, oneCell.getCandidateMuxesPerCell());
        Assert.assertEquals(66, oneCell.getPrivateInputVectorsPerParty());
        Assert.assertEquals(65, oneCell.getOpenedOutputVectors());
        Assert.assertEquals(6, oneCell.getEqualityAndDepth());
        Assert.assertEquals(3, oneCell.getCandidateMuxAndDepth());
        Assert.assertEquals(3L * 66L, oneCell.getTotalPrivateInputBits());
        Assert.assertEquals(65L, oneCell.getTotalOpenedOutputBits());
        Assert.assertTrue(oneCell.getAndGateProxyPerCell() > 0);
        Assert.assertEquals(oneCell.getAndGateProxyPerCell() * 10, tenCells.getTotalAndGateProxy());
        Assert.assertEquals(oneCell.getTotalPrivateInputBits() * 10, tenCells.getTotalPrivateInputBits());
        Assert.assertEquals(oneCell.getTotalOpenedOutputBits() * 10, tenCells.getTotalOpenedOutputBits());
    }

    @Test
    public void testBatchInputBuildsShape() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 128).build();
        BatchMpSogsPeelInput input = new BatchMpSogsPeelInput(0, Arrays.asList(0, 1, 2, 3));
        MpSogsUpeelCircuitShape shape = input.circuitShape(params);
        Assert.assertEquals(params.getPartyNum(), shape.getPartyNum());
        Assert.assertEquals(MpSogsMpsuParams.ELEMENT_BIT_LENGTH, shape.getElementBitLength());
        Assert.assertEquals(input.size(), shape.getBatchSize());
    }
}
