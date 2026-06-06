package edu.alibaba.mpc4j.s2pc.upso.main.upsu;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.lll24.Lll24DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.gmr21.Gmr21NetRosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.ms13.Ms13NetRosnConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23ByteEccDdhPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23EccDdhPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23PsOprfPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.pir.stdpir.index.vectorized.VectorizedStdIdxPirConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuMode;
import edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt.PisIbltUpsuParams;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.zlp24.Zlp24PeqtUpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.zlp24.Zlp24PkeUpsuConfig;

import java.util.Properties;

/**
 * UPSU config utils.
 *
 * @author Liqiang Peng
 * @date 2024/3/29
 */
public class UpsuConfigUtils {
    /**
     * private constructor.
     */
    private UpsuConfigUtils() {
        // empty
    }

    /**
     * create config.
     *
     * @param properties properties.
     * @return config.
     */
    public static UpsuConfig createConfig(Properties properties) {
        UpsuMainType upsuMainType = MainPtoConfigUtils.readEnum(UpsuMainType.class, properties, UpsuMain.PTO_NAME_KEY);
        switch (upsuMainType) {
            case TCL23_BYTE_ECC_DDH:
                return new Tcl23UpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23ByteEccDdhPmPeqtConfig.Builder().build())
                    .build();
            case TCL23_ECC_DDH:
                return new Tcl23UpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23EccDdhPmPeqtConfig.Builder().setCompressEncode(false).build())
                    .build();
            case TCL23_PS_OPRF_MS13:
                return new Tcl23UpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23PsOprfPmPeqtConfig.Builder()
                        .setOsnConfig(new Lll24DosnConfig.Builder(new Ms13NetRosnConfig.Builder(false).build()).build())
                        .build())
                    .build();
            case TCL23_PS_OPRF_GMR21:
                return new Tcl23UpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23PsOprfPmPeqtConfig.Builder()
                        .setOsnConfig(new Lll24DosnConfig.Builder(new Gmr21NetRosnConfig.Builder(false).build()).build())
                        .build())
                    .build();
            case ZLP24_PKE_VECTORIZED_PIR:
                return new Zlp24PkeUpsuConfig.Builder()
                    .setStdIdxPirConfig(new VectorizedStdIdxPirConfig.Builder().build())
                    .build();
            case ZLP24_PEQT_VECTORIZED_PIR_DDH:
                return new Zlp24PeqtUpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23ByteEccDdhPmPeqtConfig.Builder().build())
                    .setStdIdxPirConfig(new VectorizedStdIdxPirConfig.Builder().build())
                    .build();
            case ZLP24_PEQT_VECTORIZED_PIR_PS_OPRF:
                return new Zlp24PeqtUpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23PsOprfPmPeqtConfig.Builder()
                        .setOsnConfig(new Lll24DosnConfig.Builder(new Gmr21NetRosnConfig.Builder(false).build()).build())
                        .build())
                    .setStdIdxPirConfig(new VectorizedStdIdxPirConfig.Builder().build())
                    .build();
            case PIS_IBLT:
                return createPisIbltConfig(properties);
            default:
                throw new IllegalArgumentException("Invalid " + UpsuMainType.class.getSimpleName() + ": " + upsuMainType.name());
        }
    }

    private static PisIbltUpsuConfig createPisIbltConfig(Properties properties) {
        PisIbltUpsuConfig.Builder builder = new PisIbltUpsuConfig.Builder();
        String modeName = PropertiesUtils.readString(properties, "pis_iblt_mode", PisIbltUpsuMode.PISF_FAST.name());
        builder.setMode(PisIbltUpsuMode.valueOf(modeName));
        int maxElementByteLength = PropertiesUtils.readInt(properties, "pis_iblt_max_element_byte_length", 32);
        builder.setMaxElementByteLength(maxElementByteLength);
        double alpha = PropertiesUtils.readDouble(properties, "pis_iblt_alpha", 1.5);
        int dMax = PropertiesUtils.readInt(properties, "pis_iblt_d_max", 3);
        int repetitionNum = PropertiesUtils.readInt(properties, "pis_iblt_repetition_num", 3);
        int keyByteLength = PropertiesUtils.readInt(properties, "pis_iblt_key_byte_length", 16);
        int tagByteLength = PropertiesUtils.readInt(properties, "pis_iblt_tag_byte_length", 16);
        builder
            .setFastAlpha(alpha)
            .setDMax(dMax)
            .setRepetitionNum(repetitionNum)
            .setKeyByteLength(keyByteLength)
            .setTagByteLength(tagByteLength);
        int senderCapacity = PropertiesUtils.readInt(properties, "pis_iblt_sender_capacity", 0);
        int receiverCapacity = PropertiesUtils.readInt(properties, "pis_iblt_receiver_capacity", 0);
        if (senderCapacity > 0 || receiverCapacity > 0) {
            if (senderCapacity <= 0 || receiverCapacity <= 0) {
                throw new IllegalArgumentException(
                    "pis_iblt_sender_capacity and pis_iblt_receiver_capacity must be set together"
                );
            }
            PisIbltUpsuParams.Builder paramsBuilder = new PisIbltUpsuParams.Builder(senderCapacity, receiverCapacity);
            int tableLength = PropertiesUtils.readInt(properties, "pis_iblt_table_length", 0);
            if (tableLength > 0) {
                paramsBuilder.setTableLength(tableLength);
            }
            int peelRound = PropertiesUtils.readInt(properties, "pis_iblt_peel_round", 0);
            int securityParameter = PropertiesUtils.readInt(properties, "pis_iblt_security_parameter", 128);
            paramsBuilder
                .setDMax(dMax)
                .setRepetitionNum(repetitionNum)
                .setSecurityParameter(securityParameter)
                .setKeyByteLength(keyByteLength)
                .setTagByteLength(tagByteLength)
                .setConservative(true);
            if (peelRound > 0) {
                paramsBuilder.setPeelRound(peelRound);
            }
            builder.setParams(paramsBuilder.build());
        }
        return builder.build();
    }
}
