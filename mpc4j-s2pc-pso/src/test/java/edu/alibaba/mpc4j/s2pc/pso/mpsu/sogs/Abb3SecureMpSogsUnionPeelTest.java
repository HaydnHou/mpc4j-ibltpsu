package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractThreePartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs.abb3.Abb3MpSogsMpsuPartyRunner;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.TripletZ2cParty;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.replicate.Aby3Z2cConfig;
import edu.alibaba.mpc4j.s3pc.abb3.basic.core.z2.replicate.Aby3Z2cFactory;
import edu.alibaba.mpc4j.s3pc.abb3.context.TripletProvider;
import edu.alibaba.mpc4j.s3pc.abb3.context.TripletProviderConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * ABB3 secure backend tests for MP-SOGS union-peel.
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class Abb3SecureMpSogsUnionPeelTest extends AbstractThreePartyMemoryRpcPto {
    public Abb3SecureMpSogsUnionPeelTest() {
        super("Abb3SecureMpSogsUnionPeelTest");
    }

    @Test
    public void testThreePartySecureUnionPeel() throws InterruptedException {
        testThreePartySecureUnionPeel(MpSogsLabelEncoding.FULL_VALUE);
    }

    @Test
    public void testThreePartyExactQuotientUnionPeel() throws InterruptedException {
        testThreePartySecureUnionPeel(MpSogsLabelEncoding.EXACT_QUOTIENT);
    }

    private void testThreePartySecureUnionPeel(MpSogsLabelEncoding labelEncoding) throws InterruptedException {
        List<Set<Long>> partyInputs = List.of(
            Set.of(1L, 2L, 3L, 11L),
            Set.of(2L, 3L, 4L, 12L),
            Set.of(3L, 5L, 13L)
        );
        Set<Long> expectedUnion = ClearMpSogsMpsu.unionOf(partyInputs);
        MpSogsMpsuParams params = new MpSogsMpsuParams.Builder(3, expectedUnion.size())
            .setAlpha(3.0)
            .setHashNum(3)
            .setMaxPeelRounds(100)
            .build();
        MpSogsMpsuConfig config = new MpSogsMpsuConfig.Builder(params)
            .setSecurePeelType(MpSogsMpsuConfig.SecurePeelType.ABB3)
            .setLabelEncoding(labelEncoding)
            .build();
        Assert.assertTrue(ClearMpSogsMpsu.run(partyInputs, params).isSuccess());
        TripletZ2cParty[] parties = createParties();
        SecurePeelThread[] threads = IntStream.range(0, parties.length)
            .mapToObj(partyIndex -> new SecurePeelThread(parties[partyIndex], partyInputs.get(partyIndex), config,
                expectedUnion))
            .toArray(SecurePeelThread[]::new);
        Arrays.stream(threads).forEach(Thread::start);
        for (SecurePeelThread thread : threads) {
            thread.join();
        }
        for (SecurePeelThread thread : threads) {
            if (thread.getThrowable() != null) {
                throw new AssertionError("secure peel thread failed", thread.getThrowable());
            }
            MpSogsTranscript transcript = thread.getTranscript();
            Assert.assertTrue(transcript.getFailureReason(), transcript.isSuccess());
            Assert.assertEquals(expectedUnion, transcript.getUnionOutput());
        }
        Arrays.stream(parties).forEach(party -> new Thread(party::destroy).start());
    }

    private TripletZ2cParty[] createParties() {
        Rpc[] rpcAll = new Rpc[]{firstRpc, secondRpc, thirdRpc};
        Aby3Z2cConfig z2cConfig = new Aby3Z2cConfig.Builder(false).build();
        TripletProvider[] tripletProviders = IntStream.range(0, 3)
            .mapToObj(index -> new TripletProvider(rpcAll[index], new TripletProviderConfig.Builder(false).build()))
            .toArray(TripletProvider[]::new);
        TripletZ2cParty[] parties = IntStream.range(0, 3)
            .mapToObj(index -> Aby3Z2cFactory.createParty(rpcAll[index], z2cConfig, tripletProviders[index]))
            .toArray(TripletZ2cParty[]::new);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        Arrays.stream(parties).forEach(party -> {
            party.setTaskId(taskId);
            party.setParallel(false);
        });
        return parties;
    }

    /**
     * Party-local MP-SOGS driver used only by the integration test.
     */
    private static class SecurePeelThread extends Thread {
        private final TripletZ2cParty z2cParty;
        private final Set<Long> input;
        private final MpSogsMpsuConfig config;
        private final Set<Long> expectedUnion;
        private MpSogsTranscript transcript;
        private Throwable throwable;

        private SecurePeelThread(TripletZ2cParty z2cParty, Set<Long> input, MpSogsMpsuConfig config,
                                 Set<Long> expectedUnion) {
            this.z2cParty = z2cParty;
            this.input = input;
            this.config = config;
            this.expectedUnion = expectedUnion;
        }

        @Override
        public void run() {
            try {
                Abb3MpSogsMpsuPartyRunner runner = MpSogsMpsuFactory.createAbb3PartyRunner(z2cParty, config);
                transcript = runner.run(input, expectedUnion);
            } catch (Throwable t) {
                throwable = t;
            }
        }

        private MpSogsTranscript getTranscript() {
            return transcript;
        }

        private Throwable getThrowable() {
            return throwable;
        }
    }
}
