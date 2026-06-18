package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuParams;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * MC-SOGS UPSU FHE relation-prefix parameters.
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuParams implements UpsuParams {
    /**
     * Cuckoo hash type.
     */
    private final CuckooHashBinType cuckooHashBinType;
    /**
     * Bin number.
     */
    private final int binNum;
    /**
     * Max partition size per bin.
     */
    private final int maxPartitionSizePerBin;
    /**
     * Item encoded slot size.
     */
    private final int itemEncodedSlotSize;
    /**
     * Paterson-Stockmeyer low degree.
     */
    private final int psLowDegree;
    /**
     * Query powers.
     */
    private final int[] queryPowers;
    /**
     * Plain modulus.
     */
    private final long plainModulus;
    /**
     * Polynomial modulus degree.
     */
    private final int polyModulusDegree;
    /**
     * Coefficient modulus bits.
     */
    private final int[] coeffModulusBits;
    /**
     * Max sender size.
     */
    private final int maxSenderSize;
    /**
     * Item per ciphertext.
     */
    private final int itemPerCiphertext;
    /**
     * Ciphertext number.
     */
    private final int ciphertextNum;
    /**
     * Encryption parameters.
     */
    private final byte[] encryptionParameters;
    /**
     * Plain modulus size.
     */
    private final int plainModulusSize;
    /**
     * Bit length per encoded item.
     */
    private final int l;

    private McSogsUpsuParams(CuckooHashBinType cuckooHashBinType, int binNum, int maxPartitionSizePerBin,
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
        encryptionParameters = McSogsUpsuNativeUtils.genEncryptionParameters(
            polyModulusDegree, plainModulus, coeffModulusBits
        );
        plainModulusSize = BigInteger.valueOf(plainModulus).bitLength();
        l = itemEncodedSlotSize * plainModulusSize;
    }

    /**
     * Creates valid MC-SOGS UPSU parameters.
     *
     * @param cuckooHashBinType      cuckoo hash type.
     * @param binNum                 bin number.
     * @param maxPartitionSizePerBin max partition size per bin.
     * @param itemEncodedSlotSize    item encoded slot size.
     * @param psLowDegree            Paterson-Stockmeyer low degree.
     * @param queryPowers            query powers.
     * @param plainModulus           plain modulus.
     * @param polyModulusDegree      polynomial modulus degree.
     * @param coeffModulusBits       coefficient modulus bits.
     * @param maxSenderSize          max sender size.
     * @return valid MC-SOGS UPSU parameters.
     */
    public static McSogsUpsuParams create(CuckooHashBinType cuckooHashBinType, int binNum,
                                              int maxPartitionSizePerBin, int itemEncodedSlotSize, int psLowDegree,
                                              int[] queryPowers, long plainModulus, int polyModulusDegree,
                                              int[] coeffModulusBits, int maxSenderSize) {
        McSogsUpsuParams params = new McSogsUpsuParams(
            cuckooHashBinType, binNum, maxPartitionSizePerBin, itemEncodedSlotSize, psLowDegree, queryPowers,
            plainModulus, polyModulusDegree, coeffModulusBits, maxSenderSize
        );
        if (McSogsUpsuParamsChecker.checkValid(params)) {
            return params;
        }
        throw new IllegalArgumentException("Invalid MC-SOGS UPSU parameters: " + params);
    }

    /**
     * Receiver size up to 16 million, sender size up to 1024.
     */
    public static final McSogsUpsuParams RECEIVER_16M_SENDER_MAX_1024 = McSogsUpsuParams.create(
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
        return "MC-SOGS parameters chosen:" + "\n" +
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
