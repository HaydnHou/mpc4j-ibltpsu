package edu.alibaba.mpc4j.s2pc.pso.psu.sogs;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.structure.sogs.SogsGraphParams;
import edu.alibaba.mpc4j.common.structure.sogs.SogsPsuSketchBackend;
import edu.alibaba.mpc4j.common.structure.sogs.SogsPsuSketchBackendFactory;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prp.Prp;
import edu.alibaba.mpc4j.common.tool.crypto.prp.PrpFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilities for SOGS-PSU.
 *
 * @author donghai hou
 * @date 2026/06/09
 */
class SogsPsuUtils {
    /**
     * private constructor.
     */
    private SogsPsuUtils() {
        // empty
    }

    /**
     * Computes a public long key for an element.
     *
     * @param envType environment.
     * @param element element.
     * @return key.
     */
    static long elementKey(EnvType envType, byte[] element) {
        Hash hash = HashFactory.createInstance(envType, Long.BYTES);
        return ByteBuffer.wrap(hash.digestToBytes(element)).getLong();
    }

    /**
     * Reduces an OPRF output to one block.
     *
     * @param envType environment.
     * @param oprf    OPRF output.
     * @return block seed.
     */
    static byte[] seed(EnvType envType, byte[] oprf) {
        Hash hash = HashFactory.createInstance(envType, CommonConstants.BLOCK_BYTE_LENGTH);
        return hash.digestToBytes(oprf);
    }

    /**
     * Computes the seed check tag used by union peel.
     *
     * @param envType environment.
     * @param seed    seed.
     * @param round   round.
     * @param index   table index.
     * @return tag.
     */
    static byte[] seedTag(EnvType envType, byte[] seed, int round, int index) {
        MathPreconditions.checkEqual("seed.length", "BLOCK_BYTE_LENGTH", seed.length, CommonConstants.BLOCK_BYTE_LENGTH);
        Prp prp = PrpFactory.createInstance(envType);
        prp.setKey(seed);
        byte[] input = ByteBuffer.allocate(CommonConstants.BLOCK_BYTE_LENGTH)
            .putLong(round)
            .putLong(index)
            .array();
        return prp.prp(input);
    }

    /**
     * Creates a SOGS sketch backend.
     *
     * @param config          config.
     * @param threshold       threshold.
     * @param valueByteLength value byte length.
     * @param key             public sketch key.
     * @return sketch backend.
     */
    static SogsPsuSketchBackend createSketchBackend(
        SogsPsuConfig config, int threshold, int valueByteLength, byte[] key
    ) {
        return createMainSketchBackend(config, threshold, valueByteLength, key);
    }

    /**
     * Creates the main SOGS sketch backend.
     *
     * @param config          config.
     * @param threshold       threshold.
     * @param valueByteLength value byte length.
     * @param key             public sketch key.
     * @return main sketch backend.
     */
    static SogsPsuSketchBackend createMainSketchBackend(
        SogsPsuConfig config, int threshold, int valueByteLength, byte[] key
    ) {
        return SogsPsuSketchBackendFactory.createSogsBackend(
            threshold, config.getSogsAlpha(), config.getSogsDegree(), valueByteLength, mainSogsSeed(key)
        );
    }

    /**
     * Creates the fixed auxiliary SOGS sketch backend.
     *
     * @param config          config.
     * @param threshold       threshold.
     * @param valueByteLength value byte length.
     * @param key             public sketch key.
     * @return auxiliary sketch backend.
     */
    static SogsPsuSketchBackend createAuxiliarySketchBackend(
        SogsPsuConfig config, int threshold, int valueByteLength, byte[] key
    ) {
        SogsGraphParams params = SogsGraphParams.fromFixedVertexCount(
            threshold, config.getAuxiliaryVertexCount(), config.getAuxiliaryDegree(), auxiliarySogsSeed(key)
        );
        return SogsPsuSketchBackendFactory.createSogsBackend(params, valueByteLength);
    }

    /**
     * Derives a SOGS position seed from the public sketch key.
     *
     * @param key public sketch key.
     * @return seed.
     */
    static long sogsSeed(byte[] key) {
        MathPreconditions.checkEqual("key.length", "BLOCK_BYTE_LENGTH", key.length, CommonConstants.BLOCK_BYTE_LENGTH);
        ByteBuffer buffer = ByteBuffer.wrap(key);
        return buffer.getLong() ^ Long.rotateLeft(buffer.getLong(), 17);
    }

    /**
     * Derives the main SOGS position seed.
     *
     * @param key public sketch key.
     * @return main seed.
     */
    static long mainSogsSeed(byte[] key) {
        return sogsSeed(key);
    }

    /**
     * Derives the auxiliary SOGS position seed with domain separation.
     *
     * @param key public sketch key.
     * @return auxiliary seed.
     */
    static long auxiliarySogsSeed(byte[] key) {
        long seed = sogsSeed(key) ^ 0x4D41494E5F415558L;
        return Long.rotateLeft(seed, 29) ^ 0x4155585F54494552L;
    }

    /**
     * Converts a local table index to a global two-layer index.
     *
     * @param phase         peel phase.
     * @param localIndex    local index.
     * @param mainTableSize main table size.
     * @return global index.
     */
    static int toGlobalIndex(PeelPhase phase, int localIndex, int mainTableSize) {
        return phase == PeelPhase.MAIN ? localIndex : Math.addExact(mainTableSize, localIndex);
    }

    /**
     * Converts a global index to a local table index.
     *
     * @param globalIndex   global index.
     * @param mainTableSize main table size.
     * @return local index.
     */
    static int toLocalIndex(int globalIndex, int mainTableSize) {
        return globalIndex < mainTableSize ? globalIndex : globalIndex - mainTableSize;
    }

    /**
     * Gets the phase encoded by a global index.
     *
     * @param globalIndex   global index.
     * @param mainTableSize main table size.
     * @return phase.
     */
    static PeelPhase phaseOfGlobalIndex(int globalIndex, int mainTableSize) {
        return globalIndex < mainTableSize ? PeelPhase.MAIN : PeelPhase.AUXILIARY;
    }

    /**
     * Encodes a peeled table index and element.
     *
     * @param index   table index.
     * @param element element.
     * @return encoded item.
     */
    static byte[] encodePeeledElement(int index, byte[] element) {
        return ByteBuffer.allocate(Integer.BYTES + element.length)
            .putInt(index)
            .put(element)
            .array();
    }

    /**
     * Decodes the peeled table index.
     *
     * @param encoded encoded item.
     * @return table index.
     */
    static int decodePeeledIndex(byte[] encoded) {
        return ByteBuffer.wrap(encoded).getInt();
    }

    /**
     * Decodes the peeled element.
     *
     * @param encoded           encoded item.
     * @param elementByteLength element byte length.
     * @return element.
     */
    static byte[] decodePeeledElement(byte[] encoded, int elementByteLength) {
        MathPreconditions.checkEqual(
            "encoded.length", "Integer.BYTES + elementByteLength",
            encoded.length, Integer.BYTES + elementByteLength
        );
        return Arrays.copyOfRange(encoded, Integer.BYTES, encoded.length);
    }

    /**
     * Pads a message to the given length.
     *
     * @param message message.
     * @param length  length.
     * @return padded message.
     */
    static byte[] pad(byte[] message, int length) {
        Preconditions.checkArgument(message.length <= length);
        byte[] padded = new byte[length];
        System.arraycopy(message, 0, padded, 0, message.length);
        return padded;
    }

    /**
     * Encodes an optional element as a fixed flag-and-element message.
     *
     * @param element           element, or null for absent.
     * @param elementByteLength element byte length.
     * @return encoded optional element.
     */
    static byte[] encodeOptionalElement(byte[] element, int elementByteLength) {
        byte[] message = new byte[Byte.BYTES + elementByteLength];
        if (element != null) {
            MathPreconditions.checkEqual("element.length", "elementByteLength", element.length, elementByteLength);
            message[0] = 1;
            System.arraycopy(element, 0, message, Byte.BYTES, elementByteLength);
        }
        return message;
    }

    /**
     * Decodes an optional element.
     *
     * @param message           message.
     * @param elementByteLength element byte length.
     * @return element, or null for absent.
     */
    static byte[] decodeOptionalElement(byte[] message, int elementByteLength) {
        MathPreconditions.checkGreaterOrEqual("message.length", message.length, Byte.BYTES + elementByteLength);
        if (message[0] == 0) {
            return null;
        }
        return Arrays.copyOfRange(message, Byte.BYTES, Byte.BYTES + elementByteLength);
    }

    /**
     * Gets all positions not excluded.
     *
     * @param excluded excluded bitmap.
     * @return positions.
     */
    static int[] allUnpeeledPositions(boolean[] excluded) {
        int[] positions = new int[excluded.length];
        int positionNum = 0;
        for (int i = 0; i < excluded.length; i++) {
            if (!excluded[i]) {
                positions[positionNum] = i;
                positionNum++;
            }
        }
        return Arrays.copyOf(positions, positionNum);
    }

    /**
     * Decodes peeled elements to SOGS keys.
     *
     * @param envType           environment.
     * @param peeledPayload     peeled payload.
     * @param elementByteLength element byte length.
     * @return SOGS keys.
     */
    static long[] peeledElementKeys(EnvType envType, List<byte[]> peeledPayload, int elementByteLength) {
        long[] keys = new long[peeledPayload.size()];
        for (int i = 0; i < peeledPayload.size(); i++) {
            byte[] element = decodePeeledElement(peeledPayload.get(i), elementByteLength);
            keys[i] = elementKey(envType, element);
        }
        return keys;
    }

    /**
     * Generates arbitrary-OT ciphertext payloads from COT sender output.
     *
     * @param envType      environment.
     * @param senderOutput COT sender output.
     * @param message0     0-messages.
     * @param message1     1-messages.
     * @param byteLength   message byte length.
     * @return ciphertext payload.
     */
    static List<byte[]> generateCotPayload(EnvType envType, CotSenderOutput senderOutput,
                                           byte[][] message0, byte[][] message1, int byteLength) {
        int num = senderOutput.getNum();
        MathPreconditions.checkEqual("message0.length", "num", message0.length, num);
        MathPreconditions.checkEqual("message1.length", "num", message1.length, num);
        Prg prg = PrgFactory.createInstance(envType, byteLength);
        List<byte[]> payload = new ArrayList<>(2 * num);
        for (int i = 0; i < num; i++) {
            byte[] ciphertext0 = prg.extendToBytes(senderOutput.getR0(i));
            BytesUtils.xori(ciphertext0, pad(message0[i], byteLength));
            payload.add(ciphertext0);
            byte[] ciphertext1 = prg.extendToBytes(senderOutput.getR1(i));
            BytesUtils.xori(ciphertext1, pad(message1[i], byteLength));
            payload.add(ciphertext1);
        }
        return payload;
    }

    /**
     * Handles arbitrary-OT ciphertext payloads with COT receiver output.
     *
     * @param envType        environment.
     * @param receiverOutput COT receiver output.
     * @param payload        ciphertext payload.
     * @param byteLength     message byte length.
     * @return selected messages.
     */
    static byte[][] handleCotPayload(EnvType envType, CotReceiverOutput receiverOutput,
                                     List<byte[]> payload, int byteLength) {
        int num = receiverOutput.getNum();
        MathPreconditions.checkEqual("payload.size", "2 * num", payload.size(), 2 * num);
        Prg prg = PrgFactory.createInstance(envType, byteLength);
        byte[][] messages = new byte[num][];
        for (int i = 0; i < num; i++) {
            int payloadIndex = 2 * i + (receiverOutput.getChoice(i) ? 1 : 0);
            byte[] message = BytesUtils.clone(payload.get(payloadIndex));
            byte[] pad = prg.extendToBytes(receiverOutput.getRb(i));
            BytesUtils.xori(message, pad);
            messages[i] = message;
        }
        return messages;
    }
}
