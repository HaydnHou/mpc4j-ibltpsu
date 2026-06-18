package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.mc;

import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuNativeUtils;

import java.util.List;

/**
 * MC-SOGS UPSU native FHE utilities.
 *
 * <p>This facade gives MC_SOGS its own protocol-local native utility surface. The current implementation delegates
 * to the existing binary-compatible FHE entry points; the protocol layer imports only this MC-SOGS package.</p>
 *
 * @author donghai hou
 * @date 2026/06/18
 */
public class McSogsUpsuNativeUtils {

    private McSogsUpsuNativeUtils() {
        // empty
    }

    /**
     * Generates encryption parameters.
     *
     * @param polyModulusDegree polynomial modulus degree.
     * @param plainModulus      plain modulus.
     * @param coeffModulusBits  coefficient modulus bits.
     * @return encryption parameters.
     */
    public static byte[] genEncryptionParameters(int polyModulusDegree, long plainModulus, int[] coeffModulusBits) {
        return Tcl23UpsuNativeUtils.genEncryptionParameters(polyModulusDegree, plainModulus, coeffModulusBits);
    }

    /**
     * Generates an FHE key pair.
     *
     * @param encryptionParameters encryption parameters.
     * @return key pair.
     */
    public static List<byte[]> keyGen(byte[] encryptionParameters) {
        return Tcl23UpsuNativeUtils.keyGen(encryptionParameters);
    }

    /**
     * Preprocesses the encoded database.
     *
     * @param encryptionParameters encryption parameters.
     * @param coeffs               plaintexts in coefficient form.
     * @param psLowDegree          Paterson-Stockmeyer low degree.
     * @return plaintexts in NTT form.
     */
    public static List<byte[]> preprocessDatabase(byte[] encryptionParameters, long[][] coeffs, int psLowDegree) {
        return Tcl23UpsuNativeUtils.preprocessDatabase(encryptionParameters, coeffs, psLowDegree);
    }

    /**
     * Computes all powers of the encrypted query.
     *
     * @param encryptionParams encryption parameters.
     * @param relinKeys        relinearization keys.
     * @param encryptedQuery   encrypted query.
     * @param parentPowers     parent powers.
     * @param sourcePowers     source powers.
     * @param psLowDegree      Paterson-Stockmeyer low degree.
     * @return encrypted query powers.
     */
    public static List<byte[]> computeEncryptedPowers(byte[] encryptionParams, byte[] relinKeys,
                                                      List<byte[]> encryptedQuery, int[][] parentPowers,
                                                      int[] sourcePowers, int psLowDegree) {
        return Tcl23UpsuNativeUtils.computeEncryptedPowers(
            encryptionParams, relinKeys, encryptedQuery, parentPowers, sourcePowers, psLowDegree
        );
    }

    /**
     * Evaluates the polynomial using the Paterson-Stockmeyer algorithm.
     *
     * @param encryptionParams encryption parameters.
     * @param relinKeys        relinearization keys.
     * @param plaintexts       plaintexts.
     * @param ciphertexts      ciphertexts.
     * @param psLowDegree      Paterson-Stockmeyer low degree.
     * @param mask             random mask.
     * @return encrypted matches.
     */
    public static byte[] optComputeMatches(byte[] encryptionParams, byte[] relinKeys, List<byte[]> plaintexts,
                                           List<byte[]> ciphertexts, int psLowDegree, long[] mask) {
        return Tcl23UpsuNativeUtils.optComputeMatches(
            encryptionParams, relinKeys, plaintexts, ciphertexts, psLowDegree, mask
        );
    }

    /**
     * Evaluates the polynomial using the naive algorithm.
     *
     * @param encryptionParams encryption parameters.
     * @param plaintexts       plaintexts.
     * @param ciphertexts      ciphertexts.
     * @param mask             random mask.
     * @return encrypted matches.
     */
    public static byte[] naiveComputeMatches(byte[] encryptionParams, List<byte[]> plaintexts,
                                             List<byte[]> ciphertexts, long[] mask) {
        return Tcl23UpsuNativeUtils.naiveComputeMatches(encryptionParams, plaintexts, ciphertexts, mask);
    }

    /**
     * Encrypts the query.
     *
     * @param encryptionParams encryption parameters.
     * @param secretKey        secret key.
     * @param plainQuery       plaintext query.
     * @return encrypted query.
     */
    public static List<byte[]> generateQuery(byte[] encryptionParams, byte[] secretKey, long[][] plainQuery) {
        return Tcl23UpsuNativeUtils.generateQuery(encryptionParams, secretKey, plainQuery);
    }

    /**
     * Decrypts the response.
     *
     * @param encryptionParams  encryption parameters.
     * @param secretKey         secret key.
     * @param encryptedResponse encrypted response.
     * @return plaintext in coefficient form.
     */
    public static long[] decodeReply(byte[] encryptionParams, byte[] secretKey, byte[] encryptedResponse) {
        return Tcl23UpsuNativeUtils.decodeReply(encryptionParams, secretKey, encryptedResponse);
    }
}
