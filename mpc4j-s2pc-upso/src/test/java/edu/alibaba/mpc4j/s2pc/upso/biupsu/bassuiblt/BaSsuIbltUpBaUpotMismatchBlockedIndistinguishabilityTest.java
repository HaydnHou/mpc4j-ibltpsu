package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

/**
 * P43 mismatch/blocked public-surface tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotMismatchBlockedIndistinguishabilityTest {

    @Test
    public void testMismatchAndBlockedBothMapToBottomSymbol() {
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol singleton =
            BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON;
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol blocked =
            BaSsuIbltUpBaUpotFunctionality.LocalSymbol.BLOCKED;
        BaSsuIbltUpBaUpotFunctionality.ResultSymbol bottom =
            BaSsuIbltUpBaUpotFunctionality.ResultSymbol.BOTTOM;
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(singleton, singleton, false));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(blocked, singleton, true));
        Assert.assertEquals(bottom, BaSsuIbltUpBaUpotFunctionality.truthTable(singleton, blocked, true));
    }

    @Test
    public void testBottomOutputDoesNotExposeWhyItIsBottom() {
        BaSsuIbltProductionUnionProbeOutput mismatchBottom =
            BaSsuIbltProductionUnionProbeOutput.bottom(0, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH);
        BaSsuIbltProductionUnionProbeOutput blockedBottom =
            BaSsuIbltProductionUnionProbeOutput.bottom(0, BaSsuIbltProductionUnionProbeTestUtils.ELEMENT_BYTE_LENGTH);
        Assert.assertFalse(mismatchBottom.isSingleton());
        Assert.assertFalse(blockedBottom.isSingleton());
        Assert.assertEquals(mismatchBottom.getElementByteLength(), blockedBottom.getElementByteLength());
        Assert.assertThrows(IllegalStateException.class, mismatchBottom::getElement);
        Assert.assertThrows(IllegalStateException.class, blockedBottom::getElement);
    }
}
