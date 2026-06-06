package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P52 specialized bucket-probe truth-table tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotRpcTruthTableTest {

    @Test
    public void testAllSourceAgnosticSymbolCases() {
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
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(blocked, singleton, true));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(blocked, blocked, false));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(empty, blocked, false));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(singleton, blocked, true));
    }

    @Test
    public void testReferenceTruthTableUsesSourceAgnosticSingletonForSameSingleton() {
        BaSsuIbltProductionUnionProbeBackendConfig config =
            BaSsuIbltProductionUnionProbeTestUtils.config();
        BaSsuIbltProductionUnionProbeReferenceCodec codec =
            new BaSsuIbltProductionUnionProbeReferenceCodec(config, BaSsuIbltProductionUnionProbeTestUtils.seed());
        BaSsuIbltSecureCellView same = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(10L);
        BaSsuIbltProductionUnionProbeOutput sameOutput = codec.open(
            0, codec.encode(0, same), codec.encode(0, same)
        );
        Assert.assertTrue(sameOutput.isSingleton());
        Assert.assertArrayEquals(BaSsuIbltProductionUnionProbeTestUtils.element(10L), sameOutput.getElement());

        BaSsuIbltProductionUnionProbeOutput differentOutput = codec.open(
            1,
            codec.encode(1, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(11L)),
            codec.encode(1, BaSsuIbltProductionUnionProbeTestUtils.singletonCell(12L))
        );
        Assert.assertTrue(differentOutput.isBottom());
        Assert.assertThrows(IllegalStateException.class, differentOutput::getElement);
    }

    @Test
    public void testFixedThreeRowGadgetShape() {
        BaSsuIbltUpBaUpotOfflineSchedule schedule =
            BaSsuIbltProductionUnionProbeTestUtils.schedule(1, 8);
        int rowByteLength = BaSsuIbltUpBaUpotBucketProbeGadget.rowPayloadByteLength(schedule);
        BaSsuIbltUpBaUpotMaskedProbeRows rows = BaSsuIbltUpBaUpotMaskedProbeRows.of(
            BaSsuIbltUpBaUpotProbeRow.of(new byte[rowByteLength]),
            BaSsuIbltUpBaUpotProbeRow.of(new byte[rowByteLength]),
            BaSsuIbltUpBaUpotProbeRow.of(new byte[rowByteLength])
        );
        Assert.assertEquals(3, rows.rowNum());
        Assert.assertEquals(rowByteLength, rows.rowByteLength());
        Assert.assertEquals(
            BaSsuIbltUpBaUpotBucketProbeGadget.maskedRowsByteLength(schedule), rows.totalByteLength()
        );
        Assert.assertEquals(rowByteLength,
            rows.row(BaSsuIbltUpBaUpotFunctionality.LocalSymbol.EMPTY).getBytes().length);
        Assert.assertEquals(rowByteLength,
            rows.row(BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON).getBytes().length);
        Assert.assertEquals(rowByteLength,
            rows.row(BaSsuIbltUpBaUpotFunctionality.LocalSymbol.BLOCKED).getBytes().length);
    }

    @Test
    public void testGadgetConversionKeepsSourceAgnosticUnionSurface() {
        BaSsuIbltUpBaUpotPublicInput publicInput =
            BaSsuIbltUpBaUpotApiTest.publicInput("M10_N18_D3", 0, 0, 0);
        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(21L);
        BaSsuIbltProductionUnionProbeOutput singleton = BaSsuIbltUpBaUpotBucketProbeGadget.toOutput(
            publicInput, BaSsuIbltUpBaUpotFunctionality.ResultSymbol.SINGLETON, element
        );
        Assert.assertArrayEquals(element, singleton.getElement());
    }
}
