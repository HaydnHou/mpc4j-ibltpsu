package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

/**
 * Local queue-probe gadget scaffold.
 *
 * <p>This class is not the production network backend. It is a contract-aligned local adapter that strips the internal
 * BA-UPOT case type down to the public bottom/singleton output used by queue peel.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUnionProbeGadget {
    /**
     * private constructor.
     */
    private BaSsuIbltUnionProbeGadget() {
        // empty
    }

    public static BaSsuIbltUnionProbeOutput evaluateLocal(BaSsuIbltSecureBucketInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must be non-null");
        }
        BaUpotBucketOutput output = BaUnionPeelOtSecureEvaluator.evaluate(input);
        return output.isSingleton()
            ? BaSsuIbltUnionProbeOutput.singleton(
                input.getBucketIndex(), output.getElementReference(), input.getAnchor().getElementByteLength()
            )
            : BaSsuIbltUnionProbeOutput.bottom(input.getBucketIndex(), input.getAnchor().getElementByteLength());
    }
}
