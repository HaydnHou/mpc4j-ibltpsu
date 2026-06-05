package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;

/**
 * BA-SSU-IBLT MP-OPRF tag pipeline config.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public class BaSsuIbltOprfTagConfig {
    /**
     * MP-OPRF config.
     */
    private final MpOprfConfig mpOprfConfig;
    /**
     * receiver public fixed capacity.
     */
    private final int publicCapacity;
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * check byte length.
     */
    private final int checkByteLength;

    public BaSsuIbltOprfTagConfig(MpOprfConfig mpOprfConfig, int publicCapacity, int tagByteLength,
                                  int checkByteLength) {
        if (mpOprfConfig == null) {
            throw new IllegalArgumentException("mpOprfConfig must be non-null");
        }
        if (publicCapacity <= 1) {
            throw new IllegalArgumentException("publicCapacity must be greater than 1");
        }
        if (tagByteLength <= 0) {
            throw new IllegalArgumentException("tagByteLength must be positive");
        }
        if (checkByteLength <= 0) {
            throw new IllegalArgumentException("checkByteLength must be positive");
        }
        if (tagByteLength < checkByteLength) {
            throw new IllegalArgumentException("tagByteLength must be at least checkByteLength");
        }
        this.mpOprfConfig = mpOprfConfig;
        this.publicCapacity = publicCapacity;
        this.tagByteLength = tagByteLength;
        this.checkByteLength = checkByteLength;
    }

    public static BaSsuIbltOprfTagConfig fromParams(MpOprfConfig mpOprfConfig,
                                                    BaSsuIbltBiUpsuParams params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be non-null");
        }
        return new BaSsuIbltOprfTagConfig(
            mpOprfConfig,
            Math.max(2, params.getNShadow()),
            BaSsuIbltOprfTagPipeline.byteLength(params.getTagBits()),
            BaSsuIbltOprfTagPipeline.byteLength(params.getCheckBits())
        );
    }

    public MpOprfConfig getMpOprfConfig() {
        return mpOprfConfig;
    }

    public int getPublicCapacity() {
        return publicCapacity;
    }

    public int getReceiverPublicCapacity() {
        return publicCapacity;
    }

    public int getTagByteLength() {
        return tagByteLength;
    }

    public int getCheckByteLength() {
        return checkByteLength;
    }
}
