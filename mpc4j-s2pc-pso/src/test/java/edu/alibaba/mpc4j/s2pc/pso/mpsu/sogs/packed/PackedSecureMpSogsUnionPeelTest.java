package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.packed;

import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsLocalCellView;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsPeelResult;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsUnionPeelFunction;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Packed MP-SOGS uPeel circuit tests.
 *
 * @author donghai hou
 * @date 2026/06/22
 */
public class PackedSecureMpSogsUnionPeelTest {
    @Test
    public void testTruthTableFourParty() {
        assertPackedResult(
            List.of(empty(), empty(), empty(), empty()),
            MpSogsPeelResult.bottom()
        );
        assertPackedResult(
            List.of(singleton(7), empty(), empty(), empty()),
            MpSogsPeelResult.element(7)
        );
        assertPackedResult(
            List.of(singleton(7), singleton(7), empty(), singleton(7)),
            MpSogsPeelResult.element(7)
        );
        assertPackedResult(
            List.of(singleton(7), singleton(9), empty(), empty()),
            MpSogsPeelResult.bottom()
        );
        assertPackedResult(
            List.of(singleton(7), empty(), heavy(), empty()),
            MpSogsPeelResult.bottom()
        );
    }

    @Test
    public void testTruthTableFiveParty() {
        assertPackedResult(
            List.of(empty(), empty(), empty(), empty(), empty()),
            MpSogsPeelResult.bottom()
        );
        assertPackedResult(
            List.of(singleton(11), empty(), singleton(11), empty(), singleton(11)),
            MpSogsPeelResult.element(11)
        );
        assertPackedResult(
            List.of(singleton(11), empty(), singleton(13), empty(), singleton(11)),
            MpSogsPeelResult.bottom()
        );
        assertPackedResult(
            List.of(empty(), empty(), empty(), heavy(), empty()),
            MpSogsPeelResult.bottom()
        );
    }

    @Test
    public void testRandomBatches() {
        Random random = new Random(42);
        for (int partyNum : new int[]{3, 4, 5}) {
            for (int batchSize : new int[]{1, 63, 64, 65, 130}) {
                List<List<MpSogsLocalCellView>> viewsByParty = randomViews(random, partyNum, batchSize);
                List<MpSogsPeelResult> expected = expected(viewsByParty, batchSize);
                Assert.assertEquals(expected, packedPeel(viewsByParty, batchSize));
            }
        }
    }

    private static void assertPackedResult(List<MpSogsLocalCellView> cellViews, MpSogsPeelResult expected) {
        int partyNum = cellViews.size();
        List<List<MpSogsLocalCellView>> viewsByParty = new ArrayList<>(partyNum);
        for (MpSogsLocalCellView view : cellViews) {
            viewsByParty.add(List.of(view));
        }
        Assert.assertEquals(List.of(expected), packedPeel(viewsByParty, 1));
    }

    private static List<MpSogsPeelResult> packedPeel(List<List<MpSogsLocalCellView>> viewsByParty, int batchSize) {
        ClearPackedBooleanBackend backend = new ClearPackedBooleanBackend(batchSize);
        PackedMpSogsCellBatch batch = PackedMpSogsCellBatch.fromClearViews(backend, viewsByParty);
        return new PackedSecureMpSogsUnionPeel(backend).peel(batch);
    }

    private static List<MpSogsPeelResult> expected(List<List<MpSogsLocalCellView>> viewsByParty, int batchSize) {
        List<MpSogsPeelResult> expected = new ArrayList<>(batchSize);
        for (int cellIndex = 0; cellIndex < batchSize; cellIndex++) {
            List<MpSogsLocalCellView> cellViews = new ArrayList<>(viewsByParty.size());
            for (List<MpSogsLocalCellView> partyViews : viewsByParty) {
                cellViews.add(partyViews.get(cellIndex));
            }
            expected.add(MpSogsUnionPeelFunction.evaluate(cellViews));
        }
        return expected;
    }

    private static List<List<MpSogsLocalCellView>> randomViews(Random random, int partyNum, int batchSize) {
        List<List<MpSogsLocalCellView>> viewsByParty = new ArrayList<>(partyNum);
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            List<MpSogsLocalCellView> views = new ArrayList<>(batchSize);
            for (int cellIndex = 0; cellIndex < batchSize; cellIndex++) {
                int state = random.nextInt(5);
                if (state == 0) {
                    views.add(heavy());
                } else if (state <= 2) {
                    views.add(singleton(random.nextLong()));
                } else {
                    views.add(empty());
                }
            }
            viewsByParty.add(views);
        }
        return viewsByParty;
    }

    private static MpSogsLocalCellView empty() {
        return MpSogsLocalCellView.empty();
    }

    private static MpSogsLocalCellView singleton(long value) {
        return MpSogsLocalCellView.singleton(value);
    }

    private static MpSogsLocalCellView heavy() {
        return MpSogsLocalCellView.heavy();
    }
}
