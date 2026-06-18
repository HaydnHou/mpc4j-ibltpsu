package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File loader for ePSU / pnMCRG pad-carrier outputs.
 *
 * <p>The native PAD path writes little-endian metadata followed by raw 16-byte pnECRG pads. The loader keeps only the
 * anonymous pad-equality carrier surface required by {@link McrgTokenSogsReleaseSender} and
 * {@link McrgTokenSogsReleaseReceiver}.</p>
 *
 * <p>In a real two-party deployment, the sender pad-carrier file is sender-local private state and the receiver
 * pad-carrier file is receiver-local private state. Loading both files in one JVM is only for local tests and benchmark
 * glue; the sender file must never be transmitted to the receiver.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class EpsuPnmcrgPadCarrierFileLoader {
    /**
     * Native files are written on the current little-endian ePSU target.
     */
    private static final ByteOrder FILE_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    /**
     * Bytes per unsigned 64-bit metadata value.
     */
    private static final int UINT64_BYTE_LENGTH = Long.BYTES;

    private EpsuPnmcrgPadCarrierFileLoader() {
        // empty
    }

    /**
     * Loads a sender pad-carrier file.
     */
    public static SenderFile loadSender(Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(FILE_BYTE_ORDER);
        int rowNum = checkedInt(readUnsignedLong(buffer, "row_num"), "row_num");
        long alphaMaxCacheCount = readUnsignedLong(buffer, "alpha_max_cache_count");
        int padByteLength = checkedInt(readUnsignedLong(buffer, "pad_byte_length"), "pad_byte_length");
        int payloadByteLength = checkedInt(readUnsignedLong(buffer, "payload_byte_length"), "payload_byte_length");
        checkPositive(rowNum, "row_num");
        checkPositive(padByteLength, "pad_byte_length");
        checkPositive(payloadByteLength, "payload_byte_length");

        byte[][] uPads = readRows(buffer, rowNum, padByteLength, "uPads");
        boolean[] realRowBits = readRealRows(buffer, rowNum);
        byte[][] payloads = readRows(buffer, rowNum, payloadByteLength, "payloads");
        checkNoTrailingBytes(buffer);
        return new SenderFile(
            rowNum, alphaMaxCacheCount, padByteLength, payloadByteLength,
            new McrgTokenSogsPadCarrierSenderOutput(uPads, realRowBits, payloads)
        );
    }

    /**
     * Loads a receiver pad-carrier file.
     */
    public static ReceiverFile loadReceiver(Path path) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(FILE_BYTE_ORDER);
        int rowNum = checkedInt(readUnsignedLong(buffer, "row_num"), "row_num");
        long alphaMaxCacheCount = readUnsignedLong(buffer, "alpha_max_cache_count");
        int padByteLength = checkedInt(readUnsignedLong(buffer, "pad_byte_length"), "pad_byte_length");
        checkPositive(rowNum, "row_num");
        checkPositive(padByteLength, "pad_byte_length");

        byte[][] vPads = readRows(buffer, rowNum, padByteLength, "vPads");
        checkNoTrailingBytes(buffer);
        return new ReceiverFile(
            rowNum, alphaMaxCacheCount, padByteLength,
            new McrgTokenSogsPadCarrierReceiverOutput(vPads)
        );
    }

    /**
     * Checks sender and receiver carrier metadata compatibility.
     */
    public static void checkCompatible(SenderFile senderFile, ReceiverFile receiverFile) {
        if (senderFile.getRowNum() != receiverFile.getRowNum()) {
            throw new IllegalArgumentException("row_num mismatch");
        }
        if (senderFile.getAlphaMaxCacheCount() != receiverFile.getAlphaMaxCacheCount()) {
            throw new IllegalArgumentException("alpha_max_cache_count mismatch");
        }
        if (senderFile.getPadByteLength() != receiverFile.getPadByteLength()) {
            throw new IllegalArgumentException("pad_byte_length mismatch");
        }
    }

    private static long readUnsignedLong(ByteBuffer buffer, String name) {
        if (buffer.remaining() < UINT64_BYTE_LENGTH) {
            throw new IllegalArgumentException("missing " + name);
        }
        long value = buffer.getLong();
        if (value < 0) {
            throw new IllegalArgumentException(name + " exceeds signed Java long range");
        }
        return value;
    }

    private static int checkedInt(long value, String name) {
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds Java int range");
        }
        return (int) value;
    }

    private static void checkPositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static byte[][] readRows(ByteBuffer buffer, int rowNum, int byteLength, String name) {
        long required = (long) rowNum * byteLength;
        if (buffer.remaining() < required) {
            throw new IllegalArgumentException("truncated " + name);
        }
        byte[][] rows = new byte[rowNum][byteLength];
        for (int row = 0; row < rowNum; row++) {
            buffer.get(rows[row]);
        }
        return rows;
    }

    private static boolean[] readRealRows(ByteBuffer buffer, int rowNum) {
        if (buffer.remaining() < rowNum) {
            throw new IllegalArgumentException("truncated realRowBits");
        }
        boolean[] realRowBits = new boolean[rowNum];
        for (int row = 0; row < rowNum; row++) {
            byte value = buffer.get();
            if (value != 0 && value != 1) {
                throw new IllegalArgumentException("realRowBits must be encoded as 0/1");
            }
            realRowBits[row] = value == 1;
        }
        return realRowBits;
    }

    private static void checkNoTrailingBytes(ByteBuffer buffer) {
        if (buffer.hasRemaining()) {
            throw new IllegalArgumentException("trailing bytes in pad carrier file");
        }
    }

    /**
     * Loaded sender file.
     */
    public static class SenderFile {
        /**
         * Row count.
         */
        private final int rowNum;
        /**
         * Native alpha max cache count.
         */
        private final long alphaMaxCacheCount;
        /**
         * Pad byte length.
         */
        private final int padByteLength;
        /**
         * Payload byte length.
         */
        private final int payloadByteLength;
        /**
         * Sender output.
         */
        private final McrgTokenSogsPadCarrierSenderOutput output;

        private SenderFile(
            int rowNum, long alphaMaxCacheCount, int padByteLength, int payloadByteLength,
            McrgTokenSogsPadCarrierSenderOutput output
        ) {
            this.rowNum = rowNum;
            this.alphaMaxCacheCount = alphaMaxCacheCount;
            this.padByteLength = padByteLength;
            this.payloadByteLength = payloadByteLength;
            this.output = output;
        }

        public int getRowNum() {
            return rowNum;
        }

        public long getAlphaMaxCacheCount() {
            return alphaMaxCacheCount;
        }

        public int getPadByteLength() {
            return padByteLength;
        }

        public int getPayloadByteLength() {
            return payloadByteLength;
        }

        public McrgTokenSogsPadCarrierSenderOutput getOutput() {
            return output;
        }
    }

    /**
     * Loaded receiver file.
     */
    public static class ReceiverFile {
        /**
         * Row count.
         */
        private final int rowNum;
        /**
         * Native alpha max cache count.
         */
        private final long alphaMaxCacheCount;
        /**
         * Pad byte length.
         */
        private final int padByteLength;
        /**
         * Receiver output.
         */
        private final McrgTokenSogsPadCarrierReceiverOutput output;

        private ReceiverFile(
            int rowNum, long alphaMaxCacheCount, int padByteLength,
            McrgTokenSogsPadCarrierReceiverOutput output
        ) {
            this.rowNum = rowNum;
            this.alphaMaxCacheCount = alphaMaxCacheCount;
            this.padByteLength = padByteLength;
            this.output = output;
        }

        public int getRowNum() {
            return rowNum;
        }

        public long getAlphaMaxCacheCount() {
            return alphaMaxCacheCount;
        }

        public int getPadByteLength() {
            return padByteLength;
        }

        public McrgTokenSogsPadCarrierReceiverOutput getOutput() {
            return output;
        }
    }
}
