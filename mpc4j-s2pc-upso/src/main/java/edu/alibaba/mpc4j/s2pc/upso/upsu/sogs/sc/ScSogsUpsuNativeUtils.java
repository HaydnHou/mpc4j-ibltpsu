package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.sc;

import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuNativeUtils;

import java.util.List;

/**
 * SC-SOGS UPSU native FHE utilities.
 *
 * <p>This facade keeps SC-SOGS independent from the MC-SOGS Java protocol layer. It delegates to the existing
 * binary-compatible TCL23 native entry points because the current native library exports those symbols.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ScSogsUpsuNativeUtils {

    private ScSogsUpsuNativeUtils() {
        // empty
    }

    public static byte[] genEncryptionParameters(int polyModulusDegree, long plainModulus, int[] coeffModulusBits) {
        return Tcl23UpsuNativeUtils.genEncryptionParameters(polyModulusDegree, plainModulus, coeffModulusBits);
    }

    public static List<byte[]> keyGen(byte[] encryptionParameters) {
        return Tcl23UpsuNativeUtils.keyGen(encryptionParameters);
    }

    public static List<byte[]> preprocessDatabase(byte[] encryptionParameters, long[][] coeffs, int psLowDegree) {
        return Tcl23UpsuNativeUtils.preprocessDatabase(encryptionParameters, coeffs, psLowDegree);
    }

    public static List<byte[]> computeEncryptedPowers(byte[] encryptionParams, byte[] relinKeys,
                                                      List<byte[]> encryptedQuery, int[][] parentPowers,
                                                      int[] sourcePowers, int psLowDegree) {
        return Tcl23UpsuNativeUtils.computeEncryptedPowers(
            encryptionParams, relinKeys, encryptedQuery, parentPowers, sourcePowers, psLowDegree
        );
    }

    public static byte[] optComputeMatches(byte[] encryptionParams, byte[] relinKeys, List<byte[]> plaintexts,
                                           List<byte[]> ciphertexts, int psLowDegree, long[] mask) {
        return Tcl23UpsuNativeUtils.optComputeMatches(
            encryptionParams, relinKeys, plaintexts, ciphertexts, psLowDegree, mask
        );
    }

    public static byte[] naiveComputeMatches(byte[] encryptionParams, List<byte[]> plaintexts,
                                             List<byte[]> ciphertexts, long[] mask) {
        return Tcl23UpsuNativeUtils.naiveComputeMatches(encryptionParams, plaintexts, ciphertexts, mask);
    }

    public static List<byte[]> generateQuery(byte[] encryptionParams, byte[] secretKey, long[][] plainQuery) {
        return Tcl23UpsuNativeUtils.generateQuery(encryptionParams, secretKey, plainQuery);
    }

    public static long[] decodeReply(byte[] encryptionParams, byte[] secretKey, byte[] encryptedResponse) {
        return Tcl23UpsuNativeUtils.decodeReply(encryptionParams, secretKey, encryptedResponse);
    }
}

