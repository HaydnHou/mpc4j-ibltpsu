package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * P49 source-split local UP-BA-UPOT input boundary tests.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUpBaUpotLocalInputBoundaryTest {
    /**
     * public input.
     */
    private static final BaSsuIbltUpBaUpotPublicInput PUBLIC_INPUT =
        BaSsuIbltProductionUnionProbeTestUtils.schedule(8, 16).publicInput(0, 3, 0);

    @Test
    public void testEmptyCellMapsToEmptyLocalInput() {
        BaSsuIbltUpBaUpotLocalInput localInput = BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(), auth()
        );
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.EMPTY, localInput.getState());
        BaSsuIbltSecureCellView view = localInput.toCellView();
        Assert.assertEquals(0, view.getCount());
        Assert.assertArrayEquals(new byte[PUBLIC_INPUT.getElementByteLength()], view.getKeyXor());
        Assert.assertArrayEquals(new byte[PUBLIC_INPUT.getTagByteLength()], view.getTagXor());
        Assert.assertArrayEquals(new byte[PUBLIC_INPUT.getCheckByteLength()], view.getCheckXor());
    }

    @Test
    public void testValidSingletonCellMapsToSingletonLocalInput() {
        BaSsuIbltSecureCellView singletonCell = BaSsuIbltProductionUnionProbeTestUtils.singletonCell(7L);
        BaSsuIbltUpBaUpotLocalInput localInput = BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, singletonCell, auth()
        );
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.SINGLETON, localInput.getState());
        BaSsuIbltSecureCellView view = localInput.toCellView();
        Assert.assertEquals(1, view.getCount());
        Assert.assertArrayEquals(singletonCell.getKeyXor(), view.getKeyXor());
        Assert.assertArrayEquals(singletonCell.getTagXor(), view.getTagXor());
        Assert.assertArrayEquals(singletonCell.getCheckXor(), view.getCheckXor());
    }

    @Test
    public void testInvalidSingletonCellMapsToBlockedLocalInput() {
        BaSsuIbltSecureCellView invalidSingleton = BaSsuIbltProductionUnionProbeTestUtils.blockedCell(9L);
        BaSsuIbltUpBaUpotLocalInput localInput = BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, invalidSingleton, auth()
        );
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.BLOCKED, localInput.getState());
        Assert.assertEquals(2, localInput.toCellView().getCount());
    }

    @Test
    public void testManyCellMapsToBlockedLocalInput() {
        byte[] element = BaSsuIbltProductionUnionProbeTestUtils.element(11L);
        byte[] tag = BaSsuIbltOprfTagPipeline.tagFromPrf(
            element, BaSsuIbltProductionUnionProbeTestUtils.TAG_BYTE_LENGTH
        );
        BaSsuIbltSecureCellView manyCell = BaSsuIbltSecureCellView.of(
            2, element, tag, BaSsuIbltOprfTagPipeline.checkFromTag(tag, PUBLIC_INPUT.getCheckByteLength())
        );
        BaSsuIbltUpBaUpotLocalInput localInput = BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, manyCell, auth()
        );
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.BLOCKED, localInput.getState());
        Assert.assertEquals(2, localInput.toCellView().getCount());
    }

    @Test
    public void testMalformedEmptyCellMapsToBlockedLocalInput() {
        byte[] nonZeroElement = new byte[PUBLIC_INPUT.getElementByteLength()];
        nonZeroElement[0] = 0x01;
        BaSsuIbltSecureCellView malformedEmpty = BaSsuIbltSecureCellView.of(
            0, nonZeroElement, new byte[PUBLIC_INPUT.getTagByteLength()], new byte[PUBLIC_INPUT.getCheckByteLength()]
        );
        BaSsuIbltUpBaUpotLocalInput localInput = BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, malformedEmpty, auth()
        );
        Assert.assertEquals(BaSsuIbltUpBaUpotLocalInput.State.BLOCKED, localInput.getState());
    }

    @Test
    public void testShapeAndAuthLengthMustMatchPublicInput() {
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, BaSsuIbltProductionUnionProbeTestUtils.emptyCell(),
            new byte[PUBLIC_INPUT.getAuthTagByteLength() - 1]
        ));

        BaSsuIbltSecureCellView wrongShapeCell = BaSsuIbltSecureCellView.empty(
            PUBLIC_INPUT.getElementByteLength() + 1,
            PUBLIC_INPUT.getTagByteLength(),
            PUBLIC_INPUT.getCheckByteLength()
        );
        Assert.assertThrows(IllegalArgumentException.class, () -> BaSsuIbltUpBaUpotLocalInput.fromCellView(
            PUBLIC_INPUT, wrongShapeCell, auth()
        ));
    }

    @Test
    public void testNoPublicRawLocalInputAccessors() {
        for (Method method : BaSsuIbltUpBaUpotLocalInput.class.getMethods()) {
            String name = method.getName().toLowerCase();
            Assert.assertFalse(name.contains("tag"));
            Assert.assertFalse(name.contains("check"));
            Assert.assertFalse(name.contains("auth"));
            Assert.assertFalse(name.contains("case"));
            Assert.assertFalse(name.contains("source"));
            Assert.assertFalse(name.contains("membership"));
            Assert.assertFalse(name.contains("choice"));
        }
    }

    private static byte[] auth() {
        byte[] auth = new byte[PUBLIC_INPUT.getAuthTagByteLength()];
        Arrays.fill(auth, (byte) 0x5A);
        return auth;
    }
}
