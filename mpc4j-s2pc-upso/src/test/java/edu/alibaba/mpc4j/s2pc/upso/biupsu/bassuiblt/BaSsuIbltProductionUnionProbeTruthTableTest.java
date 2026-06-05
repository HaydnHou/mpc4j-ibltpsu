package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P36 source-agnostic truth-table tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltProductionUnionProbeTruthTableTest {

    @Test
    public void testFunctionalityTruthTableSymbols() {
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol empty =
            BaSsuIbltUpBaUpotFunctionality.LocalSymbol.EMPTY;
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol singleton =
            BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON;
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol blocked =
            BaSsuIbltUpBaUpotFunctionality.LocalSymbol.BLOCKED;
        BaSsuIbltUpBaUpotFunctionality.ResultSymbol bottom =
            BaSsuIbltUpBaUpotFunctionality.ResultSymbol.BOTTOM;
        BaSsuIbltUpBaUpotFunctionality.ResultSymbol output =
            BaSsuIbltUpBaUpotFunctionality.ResultSymbol.SINGLETON;

        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(empty, empty, false));
        Assert.assertEquals(output, BaSsuIbltUpBaUpotFunctionality.truthTable(singleton, empty, false));
        Assert.assertEquals(output, BaSsuIbltUpBaUpotFunctionality.truthTable(empty, singleton, false));
        Assert.assertEquals(output, BaSsuIbltUpBaUpotFunctionality.truthTable(singleton, singleton, true));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(singleton, singleton, false));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(blocked, empty, false));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(empty, blocked, false));
    }

    @Test
    public void testReferenceDecoderMatchesTruthTableOnlyInTestSources() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeReferenceCodec referenceCodec =
            new BaSsuIbltProductionUnionProbeReferenceCodec(config, BaSsuIbltProductionUnionProbeTestUtils.seed());
        BaSsuIbltProductionUnionProbeOutput output = referenceCodec.open(
            2,
            referenceCodec.encode(2, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(21L)),
            referenceCodec.encode(2, BaSsuIbltProductionUnionProbeTestUtils.emptyCell())
        );
        Assert.assertTrue(output.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(21L), output.getElement());
    }
}
