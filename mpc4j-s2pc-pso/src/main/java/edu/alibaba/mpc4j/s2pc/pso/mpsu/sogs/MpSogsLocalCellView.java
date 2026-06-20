package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * One participant's private local view for a public SOGS cell.
 *
 * <p>In the clear prototype this object is materialized directly. In a secure backend the same fields are
 * represented as private wires / shares and must not be opened.</p>
 *
 * @author donghai hou
 * @date 2026/06/20
 */
public class MpSogsLocalCellView {
    /**
     * Local cell state.
     */
    private final MpSogsCellState state;
    /**
     * Singleton value. Meaningful only when state is SINGLETON.
     */
    private final long singletonValue;

    private MpSogsLocalCellView(MpSogsCellState state, long singletonValue) {
        this.state = state;
        this.singletonValue = singletonValue;
    }

    public static MpSogsLocalCellView empty() {
        return new MpSogsLocalCellView(MpSogsCellState.EMPTY, 0L);
    }

    public static MpSogsLocalCellView singleton(long value) {
        return new MpSogsLocalCellView(MpSogsCellState.SINGLETON, value);
    }

    public static MpSogsLocalCellView heavy() {
        return new MpSogsLocalCellView(MpSogsCellState.HEAVY, 0L);
    }

    public MpSogsCellState getState() {
        return state;
    }

    public boolean isEmpty() {
        return state == MpSogsCellState.EMPTY;
    }

    public boolean isSingleton() {
        return state == MpSogsCellState.SINGLETON;
    }

    public boolean isHeavy() {
        return state == MpSogsCellState.HEAVY;
    }

    public long getSingletonValue() {
        if (!isSingleton()) {
            throw new IllegalStateException("singleton value is only available for singleton cells");
        }
        return singletonValue;
    }
}
