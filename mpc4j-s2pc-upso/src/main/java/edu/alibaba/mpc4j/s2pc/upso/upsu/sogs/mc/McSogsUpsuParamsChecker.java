package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;

import java.util.Arrays;

/**
 * MC-SOGS UPSU parameter checker.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuParamsChecker {

    private McSogsUpsuParamsChecker() {
        // empty
    }

    /**
     * Checks parameter validity.
     *
     * @param params parameters.
     * @return whether the parameters are valid.
     */
    public static boolean checkValid(McSogsUpsuParams params) {
        assert params.getCuckooHashBinType().equals(CuckooHashBinFactory.CuckooHashBinType.NAIVE_3_HASH)
            || params.getCuckooHashBinType().equals(CuckooHashBinFactory.CuckooHashBinType.NO_STASH_ONE_HASH)
            : CuckooHashBinFactory.CuckooHashBinType.class.getSimpleName() + " only supports "
            + CuckooHashBinFactory.CuckooHashBinType.NO_STASH_ONE_HASH + " or "
            + CuckooHashBinFactory.CuckooHashBinType.NAIVE_3_HASH;
        assert params.getBinNum() > 0 : "bin num should be greater than 0";
        assert params.getItemEncodedSlotSize() >= 2 && params.getItemEncodedSlotSize() <= 32
            : "the size of slots for encoded item should be in range [2, 32]";
        assert params.getPsLowDegree() <= params.getMaxPartitionSizePerBin() && params.getPsLowDegree() >= 0
            : "psLowDegree should be in range [0, maxPartitionSizePerBin]";
        checkQueryPowers(params.getQueryPowers(), params.getPsLowDegree());
        assert (params.getPolyModulusDegree() & (params.getPolyModulusDegree() - 1)) == 0
            : "polyModulusDegree is not a power of two";
        assert params.getPlainModulus() % (2L * params.getPolyModulusDegree()) == 1
            : "plainModulus should be a specific prime number to support batching";
        int encodedBitLength = params.getItemEncodedSlotSize()
            * (int) Math.floor(Math.log(params.getPlainModulus()) / Math.log(2));
        assert encodedBitLength >= 80 && encodedBitLength <= 256
            : "encoded bits should be in range [80, 256]";
        assert params.getBinNum() % (params.getPolyModulusDegree() / params.getItemEncodedSlotSize()) == 0
            : "binNum should be a multiple of polyModulusDegree / itemEncodedSlotSize";
        int maxItemSize = CuckooHashBinFactory.getMaxItemSize(params.getCuckooHashBinType(), params.getBinNum());
        assert params.maxSenderElementSize() > 0 && params.maxSenderElementSize() <= maxItemSize
            : "maxSenderElementSize must be in range (0, " + maxItemSize + "]: "
            + params.maxSenderElementSize();
        return true;
    }

    private static void checkQueryPowers(int[] sourcePowers, int psLowDegree) {
        int[] sortSourcePowers = Arrays.stream(sourcePowers)
            .peek(sourcePower -> {
                assert sourcePower > 0 : "query power must be greater than 0: " + sourcePower;
            })
            .distinct()
            .sorted()
            .toArray();
        assert sortSourcePowers.length == sourcePowers.length : "query powers must be distinct";
        assert sortSourcePowers[0] == 1 : "query powers must contain 1";
        for (int sourcePower : sourcePowers) {
            assert sourcePower <= psLowDegree || sourcePower % (psLowDegree + 1) == 0
                : "query powers should be divided by ps_low_degree + 1 or smaller than ps_low_degree: "
                + sourcePower;
        }
    }
}
