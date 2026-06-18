package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Token-keyed SOGS sketch for the unbalanced UPSU tail.
 *
 * <p>The graph key is a fresh one-time token. The payload is the sender element. This lets both parties reveal public
 * cover positions for every row while opening only an aggregate selected sketch.</p>
 *
 * @author donghai hou
 * @date 2026/06/17
 */
public class TokenKeyedSogsSketch {
    /**
     * Token byte length.
     */
    public static final int TOKEN_BYTE_LENGTH = 16;
    /**
     * Tag byte length.
     */
    public static final int TAG_BYTE_LENGTH = 16;
    /**
     * Cell number.
     */
    private final int cellNum;
    /**
     * Payload byte length.
     */
    private final int payloadByteLength;
    /**
     * SOGS degree.
     */
    private final int degree;
    /**
     * Cells.
     */
    private final Atom[] cells;

    public TokenKeyedSogsSketch(int cellNum, int payloadByteLength, int degree) {
        if (cellNum <= 0) {
            throw new IllegalArgumentException("cellNum must be positive");
        }
        if (payloadByteLength <= 0) {
            throw new IllegalArgumentException("payloadByteLength must be positive");
        }
        if (degree <= 0 || degree > cellNum) {
            throw new IllegalArgumentException("degree must be in range (0, cellNum]");
        }
        this.cellNum = cellNum;
        this.payloadByteLength = payloadByteLength;
        this.degree = degree;
        cells = new Atom[cellNum];
        for (int i = 0; i < cellNum; i++) {
            cells[i] = Atom.zero(payloadByteLength);
        }
    }

    /**
     * Returns the byte length of one atom.
     */
    public static int atomByteLength(int payloadByteLength) {
        if (payloadByteLength <= 0) {
            throw new IllegalArgumentException("payloadByteLength must be positive");
        }
        return TOKEN_BYTE_LENGTH + payloadByteLength + TAG_BYTE_LENGTH;
    }

    /**
     * Gets the cell number.
     */
    public int getCellNum() {
        return cellNum;
    }

    /**
     * Gets the payload byte length.
     */
    public int getPayloadByteLength() {
        return payloadByteLength;
    }

    /**
     * Gets the SOGS degree.
     */
    public int getDegree() {
        return degree;
    }

    /**
     * Creates an atom.
     */
    public Atom createAtom(byte[] token, byte[] payload) {
        checkToken(token);
        checkPayload(payload);
        return new Atom(
            Arrays.copyOf(token, TOKEN_BYTE_LENGTH),
            Arrays.copyOf(payload, payloadByteLength),
            hash(TAG_BYTE_LENGTH, "tag", token, payload)
        );
    }

    /**
     * Creates a serialized atom.
     */
    public byte[] createAtomBytes(byte[] token, byte[] payload) {
        return createAtom(token, payload).toBytes();
    }

    /**
     * Creates a serialized zero atom.
     */
    public static byte[] zeroAtomBytes(int payloadByteLength) {
        return Atom.zero(payloadByteLength).toBytes();
    }

    /**
     * Creates a serialized random atom mask.
     */
    public static byte[] randomAtomBytes(int payloadByteLength, SecureRandom secureRandom) {
        return Atom.random(payloadByteLength, secureRandom).toBytes();
    }

    /**
     * Adds an atom to all token positions.
     */
    public void add(byte[] token, byte[] payload) {
        Atom atom = createAtom(token, payload);
        for (int position : positions(token)) {
            cells[position].xori(atom);
        }
    }

    /**
     * XORs an atom into a cell.
     */
    public void xorCell(int position, Atom atom) {
        cells[position].xori(atom);
    }

    /**
     * XORs a serialized atom into all token positions.
     */
    public void xorAtomBytesToPositions(byte[] token, byte[] atomBytes) {
        checkToken(token);
        Atom atom = Atom.fromBytes(atomBytes, payloadByteLength);
        for (int position : positions(token)) {
            cells[position].xori(atom);
        }
    }

    /**
     * XORs a serialized cell payload into this sketch.
     */
    public void xorCellPayload(List<byte[]> cellPayload) {
        if (cellPayload.size() != cellNum) {
            throw new IllegalArgumentException("invalid cell payload size");
        }
        int atomByteLength = atomByteLength(payloadByteLength);
        for (int i = 0; i < cellNum; i++) {
            if (cellPayload.get(i).length != atomByteLength) {
                throw new IllegalArgumentException("invalid atom byte length");
            }
            cells[i].xori(Atom.fromBytes(cellPayload.get(i), payloadByteLength));
        }
    }

    /**
     * Serializes all cells.
     */
    public List<byte[]> toCellPayload() {
        List<byte[]> cellPayload = new ArrayList<>(cellNum);
        for (Atom cell : cells) {
            cellPayload.add(cell.toBytes());
        }
        return cellPayload;
    }

    /**
     * Builds a sketch from serialized cells.
     */
    public static TokenKeyedSogsSketch fromCellPayload(List<byte[]> cellPayload, int payloadByteLength, int degree) {
        TokenKeyedSogsSketch sketch = new TokenKeyedSogsSketch(cellPayload.size(), payloadByteLength, degree);
        sketch.xorCellPayload(cellPayload);
        return sketch;
    }

    /**
     * Returns token positions.
     */
    public int[] positions(byte[] token) {
        checkToken(token);
        int[] positions = new int[degree];
        int count = 0;
        int salt = 0;
        while (count < degree) {
            byte[] saltBytes = ByteBuffer.allocate(Integer.BYTES).putInt(salt).array();
            int position = positiveInt(hash(8, "pos", token, saltBytes)) % cellNum;
            boolean exists = false;
            for (int i = 0; i < count; i++) {
                exists |= positions[i] == position;
            }
            if (!exists) {
                positions[count] = position;
                count++;
            }
            salt++;
        }
        return positions;
    }

    /**
     * Peels the current sketch.
     */
    public PeelResult peel() {
        Set<ByteBuffer> recovered = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>(cellNum);
        boolean[] queued = new boolean[cellNum];
        for (int i = 0; i < cellNum; i++) {
            queue.add(i);
            queued[i] = true;
        }
        while (!queue.isEmpty()) {
            int position = queue.removeFirst();
            queued[position] = false;
            Atom atom = cells[position];
            if (atom.isZero()) {
                continue;
            }
            if (!atom.isValid()) {
                continue;
            }
            if (!contains(positions(atom.token), position)) {
                continue;
            }
            Atom peeledAtom = atom.copy();
            recovered.add(ByteBuffer.wrap(Arrays.copyOf(peeledAtom.payload, payloadByteLength)));
            for (int p : positions(peeledAtom.token)) {
                cells[p].xori(peeledAtom);
                if (!queued[p]) {
                    queue.add(p);
                    queued[p] = true;
                }
            }
        }
        int residual = 0;
        for (Atom cell : cells) {
            if (!cell.isZero()) {
                residual++;
            }
        }
        return new PeelResult(recovered, residual == 0, residual);
    }

    /**
     * Builds an aggregate selected sketch from secret-shared row selectors.
     *
     * <p>This simulates the intended row-share correction. One masked atom share is generated per row and then locally
     * copied to every public token edge. This is intentionally not per-edge correction.</p>
     */
    public static TokenKeyedSogsSketch aggregate(
        boolean[] releaseBits, boolean[] validBits, byte[][] tokens, byte[][] payloads, int cellNum, int degree,
        SecureRandom secureRandom
    ) {
        if (releaseBits.length != validBits.length || releaseBits.length != tokens.length
            || releaseBits.length != payloads.length) {
            throw new IllegalArgumentException("all arrays must have the same length");
        }
        if (releaseBits.length == 0) {
            throw new IllegalArgumentException("empty rows");
        }
        int payloadByteLength = payloads[0].length;
        TokenKeyedSogsSketch helper = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        TokenKeyedSogsSketch serverAggregate = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        TokenKeyedSogsSketch clientAggregate = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        for (int i = 0; i < releaseBits.length; i++) {
            boolean serverShare = secureRandom.nextBoolean();
            boolean clientShare = serverShare ^ releaseBits[i];
            Atom atom = validBits[i] ? helper.createAtom(tokens[i], payloads[i]) : Atom.zero(payloadByteLength);
            Atom pad = Atom.random(payloadByteLength, secureRandom);
            Atom serverPiece = serverShare ? pad.xor(atom) : pad.copy();
            Atom clientPiece = clientShare ? pad.xor(atom) : pad.copy();
            for (int position : helper.positions(tokens[i])) {
                serverAggregate.xorCell(position, serverPiece);
                clientAggregate.xorCell(position, clientPiece);
            }
        }
        TokenKeyedSogsSketch result = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
        for (int position = 0; position < cellNum; position++) {
            result.cells[position] = serverAggregate.cells[position].xor(clientAggregate.cells[position]);
        }
        return result;
    }

    /**
     * Samples tokens until the full real-row graph peels.
     */
    public static TokenSample samplePeelableTokens(
        byte[][] payloads, boolean[] validBits, int cellNum, int degree, SecureRandom secureRandom, int maxRetries
    ) {
        if (payloads.length != validBits.length) {
            throw new IllegalArgumentException("payloads and validBits must have the same length");
        }
        int payloadByteLength = payloads[0].length;
        for (int retry = 0; retry <= maxRetries; retry++) {
            byte[][] tokens = new byte[payloads.length][TOKEN_BYTE_LENGTH];
            for (byte[] token : tokens) {
                secureRandom.nextBytes(token);
            }
            TokenKeyedSogsSketch sketch = new TokenKeyedSogsSketch(cellNum, payloadByteLength, degree);
            for (int i = 0; i < payloads.length; i++) {
                if (validBits[i]) {
                    sketch.add(tokens[i], payloads[i]);
                }
            }
            PeelResult result = sketch.peel();
            if (result.success) {
                return new TokenSample(tokens, retry);
            }
        }
        throw new IllegalStateException("could not sample a peelable token graph");
    }

    /**
     * Estimates bytes for the optimized token-keyed SOGS tail.
     */
    public static TailByteEstimate estimateTailBytes(int rowNum, int alpha, int cellNum, int payloadByteLength) {
        int atomByteLength = atomByteLength(payloadByteLength);
        int tokenBytes = rowNum * TOKEN_BYTE_LENGTH;
        int cellBytes = cellNum * atomByteLength;
        int rowCorrectionBytes = 2 * rowNum * atomByteLength;
        int labelBits = 40 + ceilLog2(Math.max(2, alpha * rowNum));
        int q = (labelBits + 3) / 4;
        int hiddenPeqtBytes = q * TOKEN_BYTE_LENGTH * ((alpha * rowNum + 7) / 8);
        return new TailByteEstimate(tokenBytes, cellBytes, rowCorrectionBytes, hiddenPeqtBytes);
    }

    /**
     * Expands a 128-bit OT key into an atom-length one-time pad.
     */
    static byte[] expandOtPad(byte[] seed, int rowIndex, int outputByteLength) {
        return hash(
            outputByteLength, "ot-pad", seed, ByteBuffer.allocate(Integer.BYTES).putInt(rowIndex).array()
        );
    }

    /**
     * XORs two byte arrays.
     */
    public static byte[] xor(byte[] left, byte[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        byte[] result = Arrays.copyOf(left, left.length);
        xori(result, right);
        return result;
    }

    /**
     * XORs right into left.
     */
    public static void xori(byte[] left, byte[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        for (int i = 0; i < left.length; i++) {
            left[i] ^= right[i];
        }
    }

    private static void checkToken(byte[] token) {
        if (token == null || token.length != TOKEN_BYTE_LENGTH) {
            throw new IllegalArgumentException("token must be 16 bytes");
        }
    }

    private void checkPayload(byte[] payload) {
        if (payload == null || payload.length != payloadByteLength) {
            throw new IllegalArgumentException("invalid payload length");
        }
    }

    private static boolean contains(int[] positions, int value) {
        for (int position : positions) {
            if (position == value) {
                return true;
            }
        }
        return false;
    }

    private static int positiveInt(byte[] bytes) {
        int value = ByteBuffer.wrap(bytes).getInt();
        return value & 0x7fffffff;
    }

    private static int ceilLog2(int value) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    private static byte[] hash(int outputByteLength, String domain, byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(domain.getBytes(StandardCharsets.UTF_8));
            for (byte[] part : parts) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(part.length).array());
                digest.update(part);
            }
            byte[] full = digest.digest();
            if (outputByteLength <= full.length) {
                return Arrays.copyOf(full, outputByteLength);
            }
            List<byte[]> blocks = new ArrayList<>();
            blocks.add(full);
            int counter = 1;
            int current = full.length;
            while (current < outputByteLength) {
                digest.reset();
                digest.update(full);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(counter).array());
                byte[] next = digest.digest();
                blocks.add(next);
                current += next.length;
                counter++;
            }
            byte[] output = new byte[outputByteLength];
            int offset = 0;
            for (byte[] block : blocks) {
                int copyLength = Math.min(block.length, output.length - offset);
                System.arraycopy(block, 0, output, offset, copyLength);
                offset += copyLength;
                if (offset == output.length) {
                    break;
                }
            }
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * SOGS atom.
     */
    public static class Atom {
        /**
         * Token.
         */
        private final byte[] token;
        /**
         * Payload.
         */
        private final byte[] payload;
        /**
         * Tag.
         */
        private final byte[] tag;

        private Atom(byte[] token, byte[] payload, byte[] tag) {
            this.token = token;
            this.payload = payload;
            this.tag = tag;
        }

        private static Atom zero(int payloadByteLength) {
            return new Atom(new byte[TOKEN_BYTE_LENGTH], new byte[payloadByteLength], new byte[TAG_BYTE_LENGTH]);
        }

        private static Atom random(int payloadByteLength, SecureRandom secureRandom) {
            Atom atom = zero(payloadByteLength);
            secureRandom.nextBytes(atom.token);
            secureRandom.nextBytes(atom.payload);
            secureRandom.nextBytes(atom.tag);
            return atom;
        }

        private Atom copy() {
            return new Atom(
                Arrays.copyOf(token, token.length), Arrays.copyOf(payload, payload.length), Arrays.copyOf(tag, tag.length)
            );
        }

        private byte[] toBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(TOKEN_BYTE_LENGTH + payload.length + TAG_BYTE_LENGTH);
            buffer.put(token);
            buffer.put(payload);
            buffer.put(tag);
            return buffer.array();
        }

        private static Atom fromBytes(byte[] bytes, int payloadByteLength) {
            if (bytes.length != atomByteLength(payloadByteLength)) {
                throw new IllegalArgumentException("invalid atom byte length");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] token = new byte[TOKEN_BYTE_LENGTH];
            byte[] payload = new byte[payloadByteLength];
            byte[] tag = new byte[TAG_BYTE_LENGTH];
            buffer.get(token);
            buffer.get(payload);
            buffer.get(tag);
            return new Atom(token, payload, tag);
        }

        private Atom xor(Atom that) {
            Atom copy = copy();
            copy.xori(that);
            return copy;
        }

        private void xori(Atom that) {
            xorInPlace(token, that.token);
            xorInPlace(payload, that.payload);
            xorInPlace(tag, that.tag);
        }

        private boolean isZero() {
            return allZero(token) && allZero(payload) && allZero(tag);
        }

        private boolean isValid() {
            return Arrays.equals(hash(TAG_BYTE_LENGTH, "tag", token, payload), tag);
        }

        private static void xorInPlace(byte[] x, byte[] y) {
            for (int i = 0; i < x.length; i++) {
                x[i] ^= y[i];
            }
        }

        private static boolean allZero(byte[] x) {
            int acc = 0;
            for (byte b : x) {
                acc |= b;
            }
            return acc == 0;
        }
    }

    /**
     * Peel result.
     */
    public static class PeelResult {
        /**
         * Recovered payloads.
         */
        public final Set<ByteBuffer> recovered;
        /**
         * Success.
         */
        public final boolean success;
        /**
         * Residual cell count.
         */
        public final int residualCells;

        private PeelResult(Set<ByteBuffer> recovered, boolean success, int residualCells) {
            this.recovered = recovered;
            this.success = success;
            this.residualCells = residualCells;
        }
    }

    /**
     * Token sample.
     */
    public static class TokenSample {
        /**
         * Tokens.
         */
        public final byte[][] tokens;
        /**
         * Retry count.
         */
        public final int retries;

        private TokenSample(byte[][] tokens, int retries) {
            this.tokens = tokens;
            this.retries = retries;
        }
    }

    /**
     * Tail byte estimate.
     */
    public static class TailByteEstimate {
        public final int tokenBytes;
        public final int cellBytes;
        public final int rowCorrectionBytes;
        public final int hiddenPeqtBytes;
        public final int totalBytes;

        private TailByteEstimate(int tokenBytes, int cellBytes, int rowCorrectionBytes, int hiddenPeqtBytes) {
            this.tokenBytes = tokenBytes;
            this.cellBytes = cellBytes;
            this.rowCorrectionBytes = rowCorrectionBytes;
            this.hiddenPeqtBytes = hiddenPeqtBytes;
            totalBytes = tokenBytes + cellBytes + rowCorrectionBytes + hiddenPeqtBytes;
        }
    }
}
