package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Dummy batch union-peel adapter tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class DummySecureMpSogsUnionPeelTest {
    @Test
    public void testDummyMatchesClearUpeel() {
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, 64).build();
        List<MpSogsSketch> sketches = Arrays.asList(
            MpSogsSketch.encode(Collections.singleton(7L), params),
            MpSogsSketch.encode(Collections.singleton(7L), params),
            MpSogsSketch.encode(Collections.emptySet(), params)
        );
        DummySecureMpSogsUnionPeel dummy = new DummySecureMpSogsUnionPeel(sketches);
        List<Integer> cells = Arrays.stream(MpSogsHashUtils.cells(7L, params)).boxed().toList();
        BatchMpSogsPeelOutput output = dummy.peelBatch(new BatchMpSogsPeelInput(0, cells));
        Assert.assertEquals(cells.size(), output.getResults().size());
        Assert.assertEquals(0L, output.getSendBytes());
        Assert.assertEquals(0L, output.getReceiveBytes());
        Assert.assertEquals(0, output.getRoundCount());
        output.getResults().forEach(result -> Assert.assertEquals(MpSogsPeelResult.element(7L), result));
    }
}
