package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public queue-probe batch transcript.
 *
 * <p>The batch records public bucket indices and source-agnostic probe outputs only.</p>
 *
 * @author donghai hou
 * @date 2026/06/05
 */
public class BaSsuIbltUnionProbeBatch {
    /**
     * outputs.
     */
    private final List<BaSsuIbltUnionProbeOutput> outputs;

    public BaSsuIbltUnionProbeBatch(List<BaSsuIbltUnionProbeOutput> outputs) {
        if (outputs == null) {
            throw new IllegalArgumentException("outputs must be non-null");
        }
        for (BaSsuIbltUnionProbeOutput output : outputs) {
            if (output == null) {
                throw new IllegalArgumentException("outputs must not contain null");
            }
        }
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
    }

    public int size() {
        return outputs.size();
    }

    public List<BaSsuIbltUnionProbeOutput> getOutputs() {
        return outputs;
    }
}
