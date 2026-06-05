package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Protocol-facing BA-UnionPeel-OT delivery mode.
 *
 * <p>These modes are fixed-bucket payload bridges. They bind the BA-SSU-IBLT peel transcript to a two-party transport
 * shape, but they do not yet implement the final case-hiding BA-UPOT selection backend.</p>
 *
 * @author donghai hou
 * @date 2026/06/04
 */
public enum BaUnionPeelOtMode {
    /**
     * Plain fixed output capsules. Reveals bucket cases in the transport payload.
     */
    PLAIN_PAYLOAD,
    /**
     * Bucket-bound wire-masked capsules. The receiver-side bridge still holds the mask seed and can decode cases.
     */
    WIRE_MASKED_PAYLOAD
}
