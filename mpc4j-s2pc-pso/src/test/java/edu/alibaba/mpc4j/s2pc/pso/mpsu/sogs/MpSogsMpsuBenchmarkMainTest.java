package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the local MP-SOGS benchmark entry.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsMpsuBenchmarkMainTest {
    @Test
    public void testSmallBenchmarkRun() throws InterruptedException {
        MpSogsMpsuBenchmarkMain.BenchmarkConfig config = MpSogsMpsuBenchmarkMain.BenchmarkConfig.fromArgs(new String[]{
            "--n=8",
            "--overlap=0.5",
            "--alpha=3.0",
            "--k=3",
            "--parallel=false",
            "--maxPeelRounds=100"
        });
        MpSogsMpsuBenchmarkMain.BenchmarkResult result = MpSogsMpsuBenchmarkMain.run(config, 0);
        Assert.assertTrue(result.allSuccess());
        Assert.assertEquals(3, result.getPartyResults().size());
        for (MpSogsMpsuBenchmarkMain.PartyBenchmarkResult partyResult : result.getPartyResults()) {
            Assert.assertTrue(partyResult.getTranscript().isSuccess());
            Assert.assertEquals(result.getPartyResults().get(0).getTranscript().getUnionOutput(),
                partyResult.getTranscript().getUnionOutput());
            Assert.assertTrue(partyResult.getSendBytes() > 0);
        }
    }

    @Test
    public void testPublicHashSeedRetry() throws InterruptedException {
        MpSogsMpsuBenchmarkMain.BenchmarkConfig config = MpSogsMpsuBenchmarkMain.BenchmarkConfig.fromArgs(new String[]{
            "--n=16",
            "--overlap=0.5",
            "--alpha=3.0",
            "--k=3",
            "--parallel=false",
            "--maxPeelRounds=100",
            "--retries=4"
        });
        MpSogsMpsuBenchmarkMain.BenchmarkResult result = MpSogsMpsuBenchmarkMain.run(config, 1);
        Assert.assertTrue(result.allSuccess());
        for (MpSogsMpsuBenchmarkMain.PartyBenchmarkResult partyResult : result.getPartyResults()) {
            Assert.assertTrue(partyResult.getTranscript().isSuccess());
            Assert.assertTrue(partyResult.getTranscript().getHashSeedAttempts() > 1);
            Assert.assertEquals(result.getPartyResults().get(0).getTranscript().getUnionOutput(),
                partyResult.getTranscript().getUnionOutput());
        }
    }
}
