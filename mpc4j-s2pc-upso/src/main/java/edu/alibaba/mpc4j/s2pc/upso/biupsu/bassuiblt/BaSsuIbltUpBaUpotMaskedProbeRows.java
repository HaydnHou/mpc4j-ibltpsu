package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fixed three-row container for a specialized UP-BA-UPOT bucket probe.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltUpBaUpotMaskedProbeRows {
    /**
     * row count.
     */
    static final int ROW_NUM = 3;
    /**
     * rows indexed by local symbol ordinal.
     */
    private final BaSsuIbltUpBaUpotProbeRow[] rows;
    /**
     * row byte length.
     */
    private final int rowByteLength;

    private BaSsuIbltUpBaUpotMaskedProbeRows(BaSsuIbltUpBaUpotProbeRow[] rows) {
        if (rows == null || rows.length != ROW_NUM) {
            throw new IllegalArgumentException("exactly three rows are required");
        }
        this.rows = Arrays.copyOf(rows, rows.length);
        for (BaSsuIbltUpBaUpotProbeRow row : this.rows) {
            if (row == null) {
                throw new IllegalArgumentException("rows must be non-null");
            }
        }
        rowByteLength = this.rows[0].byteLength();
        for (BaSsuIbltUpBaUpotProbeRow row : this.rows) {
            if (row.byteLength() != rowByteLength) {
                throw new IllegalArgumentException("all rows must have the same public byte length");
            }
        }
    }

    static BaSsuIbltUpBaUpotMaskedProbeRows of(BaSsuIbltUpBaUpotProbeRow emptyRow,
                                               BaSsuIbltUpBaUpotProbeRow singletonRow,
                                               BaSsuIbltUpBaUpotProbeRow blockedRow) {
        return new BaSsuIbltUpBaUpotMaskedProbeRows(new BaSsuIbltUpBaUpotProbeRow[]{
            emptyRow, singletonRow, blockedRow
        });
    }

    static BaSsuIbltUpBaUpotMaskedProbeRows fromPayload(List<byte[]> payload, int rowByteLength) {
        if (payload == null || payload.size() != ROW_NUM) {
            throw new IllegalArgumentException("payload must contain exactly three rows");
        }
        if (rowByteLength <= 0) {
            throw new IllegalArgumentException("rowByteLength must be positive");
        }
        BaSsuIbltUpBaUpotProbeRow[] decodedRows = payload.stream()
            .peek(row -> {
                if (row == null || row.length != rowByteLength) {
                    throw new IllegalArgumentException("payload row length must match the public shape");
                }
            })
            .map(BaSsuIbltUpBaUpotProbeRow::of)
            .toArray(BaSsuIbltUpBaUpotProbeRow[]::new);
        return new BaSsuIbltUpBaUpotMaskedProbeRows(decodedRows);
    }

    static List<byte[]> toBatchPayload(List<BaSsuIbltUpBaUpotMaskedProbeRows> batchRows) {
        if (batchRows == null || batchRows.isEmpty()) {
            throw new IllegalArgumentException("batchRows must be non-empty");
        }
        List<byte[]> payload = new ArrayList<>(Math.multiplyExact(batchRows.size(), ROW_NUM));
        int rowByteLength = -1;
        for (BaSsuIbltUpBaUpotMaskedProbeRows rows : batchRows) {
            if (rows == null) {
                throw new IllegalArgumentException("batchRows must not contain null");
            }
            if (rowByteLength < 0) {
                rowByteLength = rows.rowByteLength();
            } else if (rows.rowByteLength() != rowByteLength) {
                throw new IllegalArgumentException("all batch rows must have the same public shape");
            }
            payload.addAll(rows.toPayload());
        }
        return payload;
    }

    static List<BaSsuIbltUpBaUpotMaskedProbeRows> fromBatchPayload(List<byte[]> payload, int batchSize,
                                                                   int rowByteLength) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (payload == null || payload.size() != Math.multiplyExact(batchSize, ROW_NUM)) {
            throw new IllegalArgumentException("batch payload row count must match the public batch shape");
        }
        List<BaSsuIbltUpBaUpotMaskedProbeRows> batchRows = new ArrayList<>(batchSize);
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            int fromIndex = Math.multiplyExact(batchIndex, ROW_NUM);
            batchRows.add(fromPayload(payload.subList(fromIndex, fromIndex + ROW_NUM), rowByteLength));
        }
        return batchRows;
    }

    int rowNum() {
        return ROW_NUM;
    }

    int rowByteLength() {
        return rowByteLength;
    }

    int totalByteLength() {
        return Math.multiplyExact(ROW_NUM, rowByteLength);
    }

    BaSsuIbltUpBaUpotProbeRow row(BaSsuIbltUpBaUpotFunctionality.LocalSymbol symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("symbol must be non-null");
        }
        return rows[symbol.ordinal()];
    }

    List<byte[]> toPayload() {
        return Arrays.stream(rows)
            .map(BaSsuIbltUpBaUpotProbeRow::getBytes)
            .collect(Collectors.toList());
    }
}
