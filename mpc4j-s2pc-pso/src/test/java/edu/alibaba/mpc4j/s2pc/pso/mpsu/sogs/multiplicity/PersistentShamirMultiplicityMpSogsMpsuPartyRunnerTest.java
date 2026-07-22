package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.multiplicity;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsMpsuParams;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsRoundStats;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.MpSogsTranscript;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * End-to-end and differential tests for persistent SSM-MPSU.
 *
 * @author donghai hou
 * @date 2026/07/22
 */
public class PersistentShamirMultiplicityMpSogsMpsuPartyRunnerTest {
    @Test
    public void testThreePartyAllOutput() throws InterruptedException {
        assertAllOutput(3, 12, 6);
    }

    @Test
    public void testFourPartyAllOutput() throws InterruptedException {
        assertAllOutput(4, 12, 6);
    }

    @Test
    public void testFivePartyT2AllOutput() throws InterruptedException {
        assertAllOutput(5, 16, 8);
    }

    @Test
    public void testThreePartyQuotientAllOutput() throws InterruptedException {
        assertAllOutput(3, 12, 6,
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT);
    }

    @Test
    public void testFivePartyT2QuotientAllOutput() throws InterruptedException {
        assertAllOutput(5, 16, 8,
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT);
    }

    @Test
    public void testThreePartyPrssAllOutput() throws InterruptedException {
        assertAllOutput(3, 12, 6,
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS);
    }

    @Test
    public void testFourPartyPrssAllOutput() throws InterruptedException {
        assertAllOutput(4, 12, 6,
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS);
    }

    @Test
    public void testFivePartyT2PrssAllOutput() throws InterruptedException {
        assertAllOutput(5, 16, 8,
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS);
    }

    @Test
    public void testDoubleShareThreeToFivePartyAllOutput() throws InterruptedException {
        for (int partyNum = 3; partyNum <= 5; partyNum++) {
            assertAllOutput(
                partyNum, 12, 6,
                MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
            );
        }
    }

    @Test
    public void testRttAwareThreeToFivePartyAllOutput() throws InterruptedException {
        for (SsmOpeningMode openingMode : new SsmOpeningMode[]{
            SsmOpeningMode.BALANCED_TWO_PHASE, SsmOpeningMode.ALL_TO_ALL_ONE_PHASE, SsmOpeningMode.AUTO
        }) {
            for (int partyNum = 3; partyNum <= 5; partyNum++) {
                assertAllOutput(partyNum, 12, 6, openingMode);
            }
        }
    }

    @Test
    public void testPackedRttAwareThreeToEightPartyAllOutput() throws InterruptedException {
        for (SsmOpeningMode openingMode : new SsmOpeningMode[]{
            SsmOpeningMode.BALANCED_TWO_PHASE, SsmOpeningMode.ALL_TO_ALL_ONE_PHASE, SsmOpeningMode.AUTO
        }) {
            for (int partyNum = 3; partyNum <= 8; partyNum++) {
                assertAllOutput(partyNum, 12, 6, openingMode, true);
            }
        }
    }

    @Test
    public void testEphemeralDifferentialAndCommunication() throws InterruptedException {
        int partyNum = 5;
        List<Set<Long>> inputs = generateInputs(partyNum, 64, 32);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(384)
            .setMaxPeelRounds(256)
            .build();
        RunResult ephemeral = run(
            inputs, expectedUnion, config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY), 9_000_000L
        );
        RunResult persistent = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT), 9_100_000L
        );
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            MpSogsTranscript left = ephemeral.transcripts.get(partyIndex);
            MpSogsTranscript right = persistent.transcripts.get(partyIndex);
            Assert.assertEquals(left.getUnionOutput(), right.getUnionOutput());
            Assert.assertEquals(left.getRoundNum(), right.getRoundNum());
            Assert.assertEquals(left.getUpeelCalls(), right.getUpeelCalls());
        }
        Assert.assertTrue(
            "persistent communication must be smaller: ephemeral=" + ephemeral.maxSendBytes
                + ", persistent=" + persistent.maxSendBytes,
            persistent.maxSendBytes < ephemeral.maxSendBytes
        );
    }

    @Test
    public void testLazyAuxiliaryAfterMainStall() throws InterruptedException {
        assertLazyAuxiliaryAfterMainStall(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT, 9_200_000L
        );
    }

    @Test
    public void testQuotientLazyAuxiliaryAfterMainStall() throws InterruptedException {
        assertLazyAuxiliaryAfterMainStall(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT, 9_210_000L
        );
    }

    @Test
    public void testPrssLazyAuxiliaryAfterMainStall() throws InterruptedException {
        assertLazyAuxiliaryAfterMainStall(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS, 9_215_000L
        );
    }

    @Test
    public void testDoubleShareLazyAuxiliaryAfterMainStall() throws InterruptedException {
        assertLazyAuxiliaryAfterMainStall(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE,
            9_217_000L
        );
    }

    private static void assertLazyAuxiliaryAfterMainStall(MpSogsMpsuConfig.SecurePeelType securePeelType,
                                                           long taskId) throws InterruptedException {
        int partyNum = 3;
        List<Set<Long>> inputs = generateInputs(partyNum, 48, 8);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.01)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(768)
            .setMaxPeelRounds(256)
            .build();
        RunResult result = run(
            inputs, expectedUnion,
            config(params, securePeelType), taskId
        );
        boolean observedStallBeforeLaterOutput = false;
        List<MpSogsRoundStats> stats = result.transcripts.get(0).getRoundStats();
        for (int index = 0; index + 1 < stats.size(); index++) {
            if (stats.get(index).getNewDistinctSize() == 0
                && stats.subList(index + 1, stats.size()).stream().anyMatch(round -> round.getNewDistinctSize() > 0)) {
                observedStallBeforeLaterOutput = true;
                break;
            }
        }
        Assert.assertTrue("test parameters did not exercise lazy Auxiliary transition", observedStallBeforeLaterOutput);
    }

    @Test
    public void testQuotientDifferentialAndCommunication() throws InterruptedException {
        int partyNum = 5;
        List<Set<Long>> inputs = generateInputs(partyNum, 64, 32);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(384)
            .setMaxPeelRounds(256)
            .build();
        RunResult full = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT), 9_250_000L
        );
        RunResult quotient = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT), 9_260_000L
        );
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            MpSogsTranscript left = full.transcripts.get(partyIndex);
            MpSogsTranscript right = quotient.transcripts.get(partyIndex);
            Assert.assertEquals(left.getUnionOutput(), right.getUnionOutput());
            Assert.assertEquals(left.getRoundNum(), right.getRoundNum());
            Assert.assertEquals(left.getUpeelCalls(), right.getUpeelCalls());
            Assert.assertEquals(left.getHashSeedAttempts(), right.getHashSeedAttempts());
        }
        Assert.assertTrue(
            "quotient communication must be smaller: full=" + full.maxSendBytes
                + ", quotient=" + quotient.maxSendBytes,
            quotient.maxSendBytes < full.maxSendBytes
        );
    }

    @Test
    public void testPrssDifferentialAndCommunication() throws InterruptedException {
        int partyNum = 5;
        List<Set<Long>> inputs = generateInputs(partyNum, 64, 32);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(384)
            .setMaxPeelRounds(256)
            .build();
        RunResult network = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT),
            9_270_000L
        );
        RunResult prss = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS),
            9_280_000L
        );
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            MpSogsTranscript left = network.transcripts.get(partyIndex);
            MpSogsTranscript right = prss.transcripts.get(partyIndex);
            Assert.assertEquals(left.getUnionOutput(), right.getUnionOutput());
            Assert.assertEquals(left.getRoundNum(), right.getRoundNum());
            Assert.assertEquals(left.getUpeelCalls(), right.getUpeelCalls());
            Assert.assertEquals(left.getHashSeedAttempts(), right.getHashSeedAttempts());
        }
        Assert.assertTrue(
            "PRSS communication must include setup and still be smaller: network=" + network.maxSendBytes
                + ", prss=" + prss.maxSendBytes,
            prss.maxSendBytes < network.maxSendBytes
        );
    }

    @Test
    public void testDoubleShareDifferentialAndCommunication() throws InterruptedException {
        int partyNum = 5;
        List<Set<Long>> inputs = generateInputs(partyNum, 64, 32);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(384)
            .setMaxPeelRounds(256)
            .build();
        RunResult prss = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS),
            9_285_000L
        );
        RunResult doubleShare = run(
            inputs, expectedUnion,
            config(
                params,
                MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
            ),
            9_290_000L
        );
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            MpSogsTranscript left = prss.transcripts.get(partyIndex);
            MpSogsTranscript right = doubleShare.transcripts.get(partyIndex);
            Assert.assertEquals(left.getUnionOutput(), right.getUnionOutput());
            Assert.assertEquals(left.getRoundNum(), right.getRoundNum());
            Assert.assertEquals(left.getUpeelCalls(), right.getUpeelCalls());
            Assert.assertEquals(left.getHashSeedAttempts(), right.getHashSeedAttempts());
        }
        Assert.assertTrue(
            "double-share communication must include setup and still be smaller: PRSS=" + prss.maxSendBytes
                + ", double-share=" + doubleShare.maxSendBytes,
            doubleShare.maxSendBytes < prss.maxSendBytes
        );
    }

    @Test
    public void testRttAwareDifferentialCommunicationAndRounds() throws InterruptedException {
        int partyNum = 5;
        List<Set<Long>> inputs = generateInputs(partyNum, 64, 32);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(384)
            .setMaxPeelRounds(256)
            .build();
        RunResult stage3 = run(
            inputs, expectedUnion,
            config(params, MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS),
            9_291_000L
        );
        RunResult stage4 = run(
            inputs, expectedUnion,
            config(
                params,
                MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
            ),
            9_292_000L
        );
        RunResult balanced = run(
            inputs, expectedUnion, config(params, SsmOpeningMode.BALANCED_TWO_PHASE), 9_293_000L
        );
        RunResult lowRound = run(
            inputs, expectedUnion, config(params, SsmOpeningMode.ALL_TO_ALL_ONE_PHASE), 9_294_000L
        );
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            MpSogsTranscript reference = stage4.transcripts.get(partyIndex);
            for (MpSogsTranscript candidate : List.of(
                balanced.transcripts.get(partyIndex), lowRound.transcripts.get(partyIndex)
            )) {
                Assert.assertEquals(reference.getUnionOutput(), candidate.getUnionOutput());
                Assert.assertEquals(reference.getRoundNum(), candidate.getRoundNum());
                Assert.assertEquals(reference.getUpeelCalls(), candidate.getUpeelCalls());
                Assert.assertEquals(reference.getHashSeedAttempts(), candidate.getHashSeedAttempts());
            }
        }
        Assert.assertTrue("terminal fusion must reduce balanced communication",
            balanced.maxSendBytes < stage4.maxSendBytes);
        Assert.assertTrue("low-round fusion must remain below Stage 3 communication",
            lowRound.maxSendBytes < stage3.maxSendBytes);
        Assert.assertTrue("one-phase terminal fusion must reduce logical network rounds",
            totalNetworkRounds(lowRound.transcripts.get(0)) < totalNetworkRounds(stage3.transcripts.get(0)));
    }

    @Test
    public void testPackedWireDifferentialAndCommunication() throws InterruptedException {
        int partyNum = 5;
        List<Set<Long>> inputs = generateInputs(partyNum, 64, 32);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.4)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(384)
            .setMaxPeelRounds(256)
            .build();
        RunResult unpacked = run(
            inputs, expectedUnion, config(params, SsmOpeningMode.BALANCED_TWO_PHASE, false), 9_291_000L
        );
        RunResult packed = run(
            inputs, expectedUnion, config(params, SsmOpeningMode.BALANCED_TWO_PHASE, true), 9_292_000L
        );
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            MpSogsTranscript left = unpacked.transcripts.get(partyIndex);
            MpSogsTranscript right = packed.transcripts.get(partyIndex);
            Assert.assertEquals(left.getUnionOutput(), right.getUnionOutput());
            Assert.assertEquals(left.getRoundNum(), right.getRoundNum());
            Assert.assertEquals(left.getUpeelCalls(), right.getUpeelCalls());
            Assert.assertEquals(left.getHashSeedAttempts(), right.getHashSeedAttempts());
            Assert.assertEquals(totalNetworkRounds(left), totalNetworkRounds(right));
        }
        Assert.assertTrue("packed wire must reduce communication", packed.maxSendBytes < unpacked.maxSendBytes);
    }

    @Test
    public void testRetryReinitializesPersistentState() throws InterruptedException {
        assertRetryReinitializesPersistentState(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT, 9_300_000L
        );
    }

    @Test
    public void testQuotientRetryReinitializesPersistentState() throws InterruptedException {
        assertRetryReinitializesPersistentState(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT, 9_310_000L
        );
    }

    @Test
    public void testPrssRetryReinitializesPersistentState() throws InterruptedException {
        assertRetryReinitializesPersistentState(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS, 9_315_000L
        );
    }

    @Test
    public void testDoubleShareRetryReinitializesPersistentState() throws InterruptedException {
        assertRetryReinitializesPersistentState(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE,
            9_317_000L
        );
    }

    @Test
    public void testRttAwareRetryReinitializesPersistentState() throws InterruptedException {
        assertRetryReinitializesPersistentState(
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE,
            9_319_000L
        );
    }

    private static void assertRetryReinitializesPersistentState(MpSogsMpsuConfig.SecurePeelType securePeelType,
                                                                 long taskId) throws InterruptedException {
        int partyNum = 3;
        List<Set<Long>> inputs = generateInputs(partyNum, 48, 8);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(1.01)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(768)
            .setMaxPeelRounds(1)
            .build();
        MpSogsMpsuConfig retryConfig = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(securePeelType)
            .setMaxHashSeedRetries(2)
            .setMaxBatchCells(256)
            .build();
        RunResult result = run(inputs, expectedUnion, retryConfig, taskId, false);
        for (MpSogsTranscript transcript : result.transcripts) {
            Assert.assertFalse(transcript.isSuccess());
            Assert.assertEquals(2, transcript.getHashSeedAttempts());
            Assert.assertTrue(transcript.getFailureReason().contains("after 2 public hash-seed attempt(s)"));
        }
    }

    private static void assertAllOutput(int partyNum, int setSize, int commonSize) throws InterruptedException {
        assertAllOutput(
            partyNum, setSize, commonSize,
            MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT
        );
    }

    private static void assertAllOutput(int partyNum, int setSize, int commonSize,
                                        MpSogsMpsuConfig.SecurePeelType securePeelType)
        throws InterruptedException {
        List<Set<Long>> inputs = generateInputs(partyNum, setSize, commonSize);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(4.0)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(192)
            .setMaxPeelRounds(256)
            .build();
        RunResult result = run(
            inputs, expectedUnion,
            config(params, securePeelType),
            8_100_000L + partyNum
        );
        for (MpSogsTranscript transcript : result.transcripts) {
            Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
            Assert.assertEquals(expectedUnion, transcript.getUnionOutput());
            Assert.assertTrue(
                "the test should exercise duplicate Cell openings",
                transcript.getRoundStats().stream().mapToInt(MpSogsRoundStats::getDuplicateOpenings).sum() > 0
            );
        }
    }

    private static void assertAllOutput(int partyNum, int setSize, int commonSize,
                                        SsmOpeningMode openingMode) throws InterruptedException {
        assertAllOutput(partyNum, setSize, commonSize, openingMode, false);
    }

    private static void assertAllOutput(int partyNum, int setSize, int commonSize,
                                        SsmOpeningMode openingMode, boolean packedWire) throws InterruptedException {
        List<Set<Long>> inputs = generateInputs(partyNum, setSize, commonSize);
        Set<Long> expectedUnion = union(inputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(partyNum, expectedUnion.size())
            .setAlpha(4.0)
            .setHashNum(3)
            .setTwoTier(true)
            .setAuxiliaryCellNum(192)
            .setMaxPeelRounds(256)
            .build();
        RunResult result = run(
            inputs, expectedUnion, config(params, openingMode, packedWire),
            8_200_000L + 10_000L * (packedWire ? 1L : 0L) + 10L * openingMode.ordinal() + partyNum
        );
        for (MpSogsTranscript transcript : result.transcripts) {
            Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
            Assert.assertEquals(expectedUnion, transcript.getUnionOutput());
        }
    }

    private static MpSogsMpsuConfig config(MpSogsMpsuParams params,
                                            MpSogsMpsuConfig.SecurePeelType securePeelType) {
        return new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(securePeelType)
            .setMaxHashSeedRetries(4)
            .setMaxBatchCells(256)
            .build();
    }

    private static MpSogsMpsuConfig config(MpSogsMpsuParams params, SsmOpeningMode openingMode) {
        return config(params, openingMode, false);
    }

    private static MpSogsMpsuConfig config(MpSogsMpsuParams params, SsmOpeningMode openingMode,
                                            boolean packedWire) {
        MpSogsMpsuConfig.Builder builder = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(
                packedWire
                    ? MpSogsMpsuConfig.SecurePeelType
                    .SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED
                    : MpSogsMpsuConfig.SecurePeelType
                    .SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE
            )
            .setSsmOpeningMode(openingMode)
            .setMaxHashSeedRetries(4)
            .setMaxBatchCells(256);
        if (openingMode == SsmOpeningMode.AUTO) {
            builder.setNetworkRttMillis(50.0).setNetworkBandwidthMbps(400.0);
        }
        return builder.build();
    }

    private static int totalNetworkRounds(MpSogsTranscript transcript) {
        return transcript.getRoundStats().stream().mapToInt(MpSogsRoundStats::getNetworkRoundCount).sum();
    }

    private static RunResult run(List<Set<Long>> inputs, Set<Long> expectedUnion, MpSogsMpsuConfig config,
                                 long taskId) throws InterruptedException {
        return run(inputs, expectedUnion, config, taskId, true);
    }

    private static RunResult run(List<Set<Long>> inputs, Set<Long> expectedUnion, MpSogsMpsuConfig config,
                                 long taskId, boolean expectedSuccess) throws InterruptedException {
        int partyNum = inputs.size();
        MemoryRpcManager rpcManager = new MemoryRpcManager(partyNum);
        Rpc[] rpcs = IntStream.range(0, partyNum).mapToObj(rpcManager::getRpc).toArray(Rpc[]::new);
        Arrays.stream(rpcs).forEach(Rpc::connect);
        PartyThread[] threads = IntStream.range(0, partyNum)
            .mapToObj(partyIndex -> new PartyThread(
                rpcs[partyIndex], inputs.get(partyIndex), expectedUnion, config, taskId
            ))
            .toArray(PartyThread[]::new);
        try {
            Arrays.stream(rpcs).forEach(Rpc::reset);
            Arrays.stream(threads).forEach(Thread::start);
            for (PartyThread thread : threads) {
                thread.join(60_000L);
            }
            List<MpSogsTranscript> transcripts = new ArrayList<>(partyNum);
            long maxSendBytes = 0L;
            for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
                PartyThread thread = threads[partyIndex];
                if (thread.isAlive()) {
                    thread.interrupt();
                    Assert.fail("persistent multiplicity MP-SOGS thread timed out: " + thread.getName());
                }
                if (thread.throwable != null) {
                    throw new AssertionError(
                        "persistent multiplicity MP-SOGS party failed: " + thread.getName(), thread.throwable
                    );
                }
                Assert.assertEquals(thread.transcript.getFailureReason(), expectedSuccess, thread.transcript.isSuccess());
                if (expectedSuccess) {
                    Assert.assertEquals(expectedUnion, thread.transcript.getUnionOutput());
                }
                transcripts.add(thread.transcript);
                maxSendBytes = Math.max(maxSendBytes, rpcs[partyIndex].getSendByteLength());
            }
            return new RunResult(transcripts, maxSendBytes);
        } finally {
            for (PartyThread thread : threads) {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            }
            Arrays.stream(rpcs).forEach(Rpc::disconnect);
        }
    }

    private static List<Set<Long>> generateInputs(int partyNum, int setSize, int commonSize) {
        List<Set<Long>> inputs = new ArrayList<>();
        for (int partyIndex = 0; partyIndex < partyNum; partyIndex++) {
            Set<Long> input = new HashSet<>();
            for (long value = 1; value <= commonSize; value++) {
                input.add(value);
            }
            int uniqueSize = setSize - commonSize;
            long start = 1_000L + (long) partyIndex * uniqueSize;
            for (long offset = 0; offset < uniqueSize; offset++) {
                input.add(start + offset);
            }
            if (partyIndex == 0) {
                input.add(Long.MIN_VALUE);
                input.add(-1L);
            }
            inputs.add(input);
        }
        return inputs;
    }

    private static Set<Long> union(List<Set<Long>> inputs) {
        Set<Long> union = new HashSet<>();
        inputs.forEach(union::addAll);
        return union;
    }

    private static final class RunResult {
        private final List<MpSogsTranscript> transcripts;
        private final long maxSendBytes;

        private RunResult(List<MpSogsTranscript> transcripts, long maxSendBytes) {
            this.transcripts = transcripts;
            this.maxSendBytes = maxSendBytes;
        }
    }

    private static final class PartyThread extends Thread {
        private final Rpc rpc;
        private final Set<Long> localInput;
        private final Set<Long> expectedUnion;
        private final MpSogsMpsuConfig config;
        private final long taskId;
        private MpSogsTranscript transcript;
        private Throwable throwable;

        private PartyThread(Rpc rpc, Set<Long> localInput, Set<Long> expectedUnion,
                            MpSogsMpsuConfig config, long taskId) {
            this.rpc = rpc;
            this.localInput = localInput;
            this.expectedUnion = expectedUnion;
            this.config = config;
            this.taskId = taskId;
            setName("persistent-shamir-multiplicity-party-" + rpc.ownParty().getPartyId());
        }

        @Override
        public void run() {
            try {
                if (config.getSecurePeelType()
                    == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT
                    || config.getSecurePeelType()
                    == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT
                    || config.getSecurePeelType()
                    == MpSogsMpsuConfig.SecurePeelType.SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS
                    || config.getSecurePeelType()
                    == MpSogsMpsuConfig.SecurePeelType
                    .SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_DOUBLE_SHARE
                    || config.getSecurePeelType()
                    == MpSogsMpsuConfig.SecurePeelType
                    .SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE
                    || config.getSecurePeelType()
                    == MpSogsMpsuConfig.SecurePeelType
                    .SHAMIR_MULTIPLICITY_PERSISTENT_QUOTIENT_PRSS_RTT_AWARE_PACKED) {
                    transcript = new PersistentShamirMultiplicityMpSogsMpsuPartyRunner(rpc, config, taskId)
                        .run(localInput, expectedUnion);
                } else {
                    transcript = new ShamirMultiplicityMpSogsMpsuPartyRunner(rpc, config, taskId)
                        .run(localInput, expectedUnion);
                }
            } catch (Throwable t) {
                throwable = t;
            }
        }
    }
}
