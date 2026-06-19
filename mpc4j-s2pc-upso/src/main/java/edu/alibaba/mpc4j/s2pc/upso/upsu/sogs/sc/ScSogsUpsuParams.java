package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuParams;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * SC-SOGS UPSU FHE relation-prefix parameters.
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ScSogsUpsuParams implements UpsuParams {
    private final CuckooHashBinType cuckooHashBinType;
    private final int binNum;
    private final int maxPartitionSizePerBin;
    private final int itemEncodedSlotSize;
    private final int psLowDegree;
    private final int[] queryPowers;
    private final long plainModulus;
    private final int polyModulusDegree;
    private final int[] coeffModulusBits;
    private final int maxSenderSize;
    private final int itemPerCiphertext;
    private final int ciphertextNum;
    private final byte[] encryptionParameters;
    private final int plainModulusSize;
    private final int l;

    private ScSogsUpsuParams(CuckooHashBinType cuckooHashBinType, int binNum, int maxPartitionSizePerBin,
                             int itemEncodedSlotSize, int psLowDegree, int[] queryPowers,
                             long plainModulus, int polyModulusDegree, int[] coeffModulusBits,
                             int maxSenderSize) {
        this.cuckooHashBinType = cuckooHashBinType;
        this.binNum = binNum;
        this.maxPartitionSizePerBin = maxPartitionSizePerBin;
        this.itemEncodedSlotSize = itemEncodedSlotSize;
        this.psLowDegree = psLowDegree;
        this.queryPowers = queryPowers;
        this.plainModulus = plainModulus;
        this.polyModulusDegree = polyModulusDegree;
        this.coeffModulusBits = coeffModulusBits;
        this.maxSenderSize = maxSenderSize;
        itemPerCiphertext = polyModulusDegree / itemEncodedSlotSize;
        ciphertextNum = binNum / itemPerCiphertext;
        encryptionParameters = ScSogsUpsuNativeUtils.genEncryptionParameters(
            polyModulusDegree, plainModulus, coeffModulusBits
        );
        plainModulusSize = BigInteger.valueOf(plainModulus).bitLength();
        l = itemEncodedSlotSize * plainModulusSize;
    }

    public static ScSogsUpsuParams create(CuckooHashBinType cuckooHashBinType, int binNum,
                                          int maxPartitionSizePerBin, int itemEncodedSlotSize, int psLowDegree,
                                          int[] queryPowers, long plainModulus, int polyModulusDegree,
                                          int[] coeffModulusBits, int maxSenderSize) {
        ScSogsUpsuParams params = new ScSogsUpsuParams(
            cuckooHashBinType, binNum, maxPartitionSizePerBin, itemEncodedSlotSize, psLowDegree, queryPowers,
            plainModulus, polyModulusDegree, coeffModulusBits, maxSenderSize
        );
        if (ScSogsUpsuParamsChecker.checkValid(params)) {
            return params;
        }
        throw new IllegalArgumentException("Invalid SC-SOGS UPSU parameters: " + params);
    }

    /**
     * Receiver size up to 16 million, sender size up to 1024.
     */
    public static final ScSogsUpsuParams RECEIVER_16M_SENDER_MAX_1024 = ScSogsUpsuParams.create(
        CuckooHashBinType.NAIVE_3_HASH, 1638, 1304,
        5,
        44, new int[]{1, 3, 11, 18, 45, 225},
        4079617, 8192, new int[]{56, 56, 56, 50},
        1024
    );

    public CuckooHashBinType getCuckooHashBinType() {
        return cuckooHashBinType;
    }

    public int getCuckooHashNum() {
        return CuckooHashBinFactory.getHashNum(cuckooHashBinType);
    }

    public int getBinNum() {
        return binNum;
    }

    public int getMaxPartitionSizePerBin() {
        return maxPartitionSizePerBin;
    }

    public int getItemEncodedSlotSize() {
        return itemEncodedSlotSize;
    }

    public int getPsLowDegree() {
        return psLowDegree;
    }

    public int[] getQueryPowers() {
        return queryPowers;
    }

    public long getPlainModulus() {
        return plainModulus;
    }

    public int getPolyModulusDegree() {
        return polyModulusDegree;
    }

    public int getCiphertextNum() {
        return ciphertextNum;
    }

    public int getItemPerCiphertext() {
        return itemPerCiphertext;
    }

    public int getL() {
        return l;
    }

    public int getPlainModulusSize() {
        return plainModulusSize;
    }

    public byte[] getEncryptionParameters() {
        return encryptionParameters;
    }

    @Override
    public int maxSenderElementSize() {
        return maxSenderSize;
    }

    @Override
    public String toString() {
        return "SC-SOGS parameters chosen:" + "\n" +
            "  - hash_bin_params: {" + "\n" +
            "     - cuckoo_hash_bin_type : " + cuckooHashBinType + "\n" +
            "     - bin_num : " + binNum + "\n" +
            "     - max_items_per_bin : " + maxPartitionSizePerBin + "\n" +
            "  }" + "\n" +
            "  - item_params: {" + "\n" +
            "     - felts_per_item : " + itemEncodedSlotSize + "\n" +
            "  }" + "\n" +
            "  - query_params: {" + "\n" +
            "     - ps_low_degree : " + psLowDegree + "\n" +
            "     - query_powers : " + Arrays.toString(queryPowers) + "\n" +
            "  }" + "\n" +
            "  - seal_params: {" + "\n" +
            "     - plain_modulus : " + plainModulus + "\n" +
            "     - poly_modulus_degree : " + polyModulusDegree + "\n" +
            "     - coeff_modulus_bits : " + Arrays.toString(coeffModulusBits) + "\n" +
            "  }" + "\n";
    }
}

