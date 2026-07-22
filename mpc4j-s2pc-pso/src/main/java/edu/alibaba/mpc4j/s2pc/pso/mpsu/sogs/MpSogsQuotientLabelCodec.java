package edu.alibaba.mpc4j.s2pc.pso.mpsu.sogs;

/**
 * Exact quotient-label codec for one public row-disjoint SOGS cell.
 *
 * <p>The public row-cell remainder and the secret quotient form a lossless decomposition of the 64-bit row-hash
 * word. No fingerprint truncation or statistical collision is introduced.</p>
 *
 * @author donghai hou
 * @date 2026/07/18
 */
public final class MpSogsQuotientLabelCodec {
    private MpSogsQuotientLabelCodec() {
        // empty
    }

    public static int bitLength(MpSogsMpsuParams params, MpSogsTier tier) {
        int rowCellNum = params.getRowCellNum(tier);
        long maxQuotient = Long.divideUnsigned(-1L, rowCellNum);
        return Long.SIZE - Long.numberOfLeadingZeros(maxQuotient);
    }

    public static long encode(long value, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
        CellCoordinate coordinate = coordinate(params, tier, cellIndex);
        long rowHash = MpSogsHashUtils.rowHash(value, params, tier, coordinate.hashIndex);
        int actualRemainder = (int) Long.remainderUnsigned(rowHash, coordinate.rowCellNum);
        if (actualRemainder != coordinate.remainder) {
            throw new IllegalArgumentException("value does not map to " + tier + " cell " + cellIndex);
        }
        return Long.divideUnsigned(rowHash, coordinate.rowCellNum);
    }

    public static long decode(long quotient, MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
        CellCoordinate coordinate = coordinate(params, tier, cellIndex);
        int labelBitLength = bitLength(params, tier);
        if (labelBitLength < Long.SIZE && (quotient >>> labelBitLength) != 0L) {
            throw new IllegalArgumentException("quotient exceeds " + labelBitLength + " bits");
        }
        long rowHash = quotient * coordinate.rowCellNum + coordinate.remainder;
        if (Long.divideUnsigned(rowHash, coordinate.rowCellNum) != quotient
            || Long.remainderUnsigned(rowHash, coordinate.rowCellNum) != coordinate.remainder) {
            throw new IllegalArgumentException("quotient is outside the exact row-hash range");
        }
        long permutedInput = MpSogsHashUtils.inverseSplitMix64(rowHash);
        return permutedInput ^ params.getHashSeed(tier) ^ MpSogsHashUtils.rowSeed(coordinate.hashIndex);
    }

    private static CellCoordinate coordinate(MpSogsMpsuParams params, MpSogsTier tier, int cellIndex) {
        int cellNum = params.getCellNum(tier);
        if (cellIndex < 0 || cellIndex >= cellNum) {
            throw new IllegalArgumentException("invalid " + tier + " cell index: " + cellIndex);
        }
        int rowCellNum = params.getRowCellNum(tier);
        return new CellCoordinate(cellIndex / rowCellNum, cellIndex % rowCellNum, rowCellNum);
    }

    private record CellCoordinate(int hashIndex, int remainder, int rowCellNum) {
        // empty
    }
}
