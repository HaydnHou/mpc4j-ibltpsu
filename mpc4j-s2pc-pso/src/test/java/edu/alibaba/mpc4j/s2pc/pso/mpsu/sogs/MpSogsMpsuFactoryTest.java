package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MP-SOGS MPSU factory tests.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuFactoryTest {
    @Test
    public void testPtoDesc() {
        Assert.assertEquals("MP_SOGS_MPSU", MpSogsMpsuPtoDesc.getInstance().getPtoName());
        Assert.assertTrue(MpSogsMpsuPtoDesc.getInstance().getPtoId() > 0);
    }

    @Test
    public void testDefaultConfigDoesNotUseDummyForSemiHonest() {
        MpSogsMpsuConfig config = MpSogsMpsuFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, 3, 256);
        Assert.assertEquals(MpSogsMpsuFactory.MpSogsMpsuType.MP_SOGS, config.getPtoType());
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.ABB3, config.getSecurePeelType());
        Assert.assertEquals(4, config.getMaxHashSeedRetries());
    }

    @Test
    public void testDefaultFourPartyConfigUsesShamir() {
        MpSogsMpsuConfig config = MpSogsMpsuFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, 4, 256);
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.SHAMIR, config.getSecurePeelType());
    }

    @Test
    public void testRep4PackedConfigAcceptsFourParty() {
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(4, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PACKED)
            .build();
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.REP4_PACKED, config.getSecurePeelType());
    }

    @Test
    public void testRep4PrssPackedConfigAcceptsFourParty() {
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(4, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_PACKED)
            .build();
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_PACKED, config.getSecurePeelType());
    }

    @Test
    public void testRep4PrssOpenedFirstConfigAcceptsFourParty() {
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(4, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_OPENED_FIRST)
            .build();
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_OPENED_FIRST, config.getSecurePeelType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRep4PackedConfigRejectsFiveParty() {
        new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(5, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PACKED)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRep4PrssPackedConfigRejectsFiveParty() {
        new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(5, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_PACKED)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRep4PrssOpenedFirstConfigRejectsFiveParty() {
        new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(5, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP4_PRSS_OPENED_FIRST)
            .build();
    }

    @Test
    public void testRep5PackedConfigAcceptsFiveParty() {
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(5, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP5_PACKED)
            .build();
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.REP5_PACKED, config.getSecurePeelType());
    }

    @Test
    public void testRep5PrssOpenedFirstConfigAcceptsFiveParty() {
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(5, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP5_PRSS_OPENED_FIRST)
            .build();
        Assert.assertEquals(MpSogsMpsuConfig.SecurePeelType.REP5_PRSS_OPENED_FIRST, config.getSecurePeelType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRep5PackedConfigRejectsFourParty() {
        new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(4, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP5_PACKED)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRep5PrssOpenedFirstConfigRejectsFourParty() {
        new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(4, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.REP5_PRSS_OPENED_FIRST)
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAbb3ConfigRejectsFourPartyUntilGenericBackendExists() {
        new MpSogsMpsuConfig.Builder(new MpSogsMpsuParams.Builder(4, 256).build())
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.ABB3)
            .build();
    }

    @Test
    public void testLocalDummyRunner() {
        List<Set<Long>> inputs = generateInputs(3, 64, 0.5);
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(
            new MpSogsMpsuParams.Builder(3, union(inputs).size()).build()
        ).build();
        MpSogsMpsuRunner runner = MpSogsMpsuFactory.createLocalRunner(config);
        MpSogsTranscript transcript = runner.run(inputs);
        Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
        Assert.assertEquals(union(inputs), transcript.getUnionOutput());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLocalSemiHonestRunnerPointsToPartyLocalBackend() {
        List<Set<Long>> inputs = generateInputs(3, 32, 0.5);
        MpSogsMpsuConfig config = MpSogsMpsuFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, 3, union(inputs).size());
        MpSogsMpsuFactory.createLocalRunner(config).run(inputs);
    }

    private List<Set<Long>> generateInputs(int parties, int n, double commonOverlap) {
        int commonCount = (int) Math.round(n * commonOverlap);
        int uniqueCount = n - commonCount;
        List<Set<Long>> inputs = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < parties; partyIndex++) {
            Set<Long> input = new HashSet<>();
            for (long value = 1; value <= commonCount; value++) {
                input.add(value);
            }
            long start = commonCount + (long) partyIndex * uniqueCount + 1;
            for (long value = start; value < start + uniqueCount; value++) {
                input.add(value);
            }
            inputs.add(input);
        }
        return inputs;
    }

    private Set<Long> union(List<Set<Long>> inputs) {
        Set<Long> union = new HashSet<>();
        inputs.forEach(union::addAll);
        return union;
    }
}
