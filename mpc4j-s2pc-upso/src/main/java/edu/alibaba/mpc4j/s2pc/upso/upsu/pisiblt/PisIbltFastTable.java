package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flat source-split PISF-IBLT table for the fast UPSU mode.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class PisIbltFastTable {
    /**
     * payload item count.
     */
    static final int SENDER_PAYLOAD_ITEM_NUM = 4;
    /**
     * parameters.
     */
    private final PisIbltUpsuParams params;
    /**
     * element byte length.
     */
    private final int elementByteLength;
    /**
     * table length.
     */
    private final int tableLength;
    /**
     * key byte length.
     */
    private final int keyByteLength;
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * sender counts.
     */
    private final int[] senderCounts;
    /**
     * receiver counts.
     */
    private final int[] receiverCounts;
    /**
     * sender key XORs.
     */
    private final byte[] senderKeyXors;
    /**
     * sender tag XORs.
     */
    private final byte[] senderTagXors;
    /**
     * sender payload XORs.
     */
    private final byte[] senderPayloadXors;
    /**
     * receiver key XORs.
     */
    private final byte[] receiverKeyXors;
    /**
     * receiver tag XORs.
     */
    private final byte[] receiverTagXors;

    PisIbltFastTable(PisIbltUpsuParams params, int elementByteLength) {
        this.params = params;
        this.elementByteLength = elementByteLength;
        tableLength = params.getTableLength();
        keyByteLength = params.getKeyByteLength();
        tagByteLength = params.getTagByteLength();
        senderCounts = new int[tableLength];
        receiverCounts = new int[tableLength];
        senderKeyXors = new byte[Math.multiplyExact(tableLength, keyByteLength)];
        senderTagXors = new byte[Math.multiplyExact(tableLength, tagByteLength)];
        senderPayloadXors = new byte[Math.multiplyExact(tableLength, elementByteLength)];
        receiverKeyXors = new byte[Math.multiplyExact(tableLength, keyByteLength)];
        receiverTagXors = new byte[Math.multiplyExact(tableLength, tagByteLength)];
    }

    static PisIbltFastTable fromSenderPayload(PisIbltUpsuParams params, int elementByteLength, List<byte[]> payload) {
        MathPreconditions.checkEqual("payload.size", "SENDER_PAYLOAD_ITEM_NUM", payload.size(), SENDER_PAYLOAD_ITEM_NUM);
        PisIbltFastTable table = new PisIbltFastTable(params, elementByteLength);
        byte[] countBytes = payload.get(0);
        MathPreconditions.checkEqual(
            "countBytes.length", "Integer.BYTES * tableLength", countBytes.length,
            Integer.BYTES * table.tableLength
        );
        ByteBuffer countBuffer = ByteBuffer.wrap(countBytes);
        for (int i = 0; i < table.tableLength; i++) {
            table.senderCounts[i] = countBuffer.getInt();
        }
        copyChecked(payload.get(1), table.senderKeyXors, "senderKeyXors");
        copyChecked(payload.get(2), table.senderTagXors, "senderTagXors");
        copyChecked(payload.get(3), table.senderPayloadXors, "senderPayloadXors");
        return table;
    }

    List<byte[]> createSenderPayload() {
        List<byte[]> payload = new ArrayList<>(SENDER_PAYLOAD_ITEM_NUM);
        ByteBuffer countBuffer = ByteBuffer.allocate(Integer.BYTES * tableLength);
        for (int count : senderCounts) {
            countBuffer.putInt(count);
        }
        payload.add(countBuffer.array());
        payload.add(BytesUtils.clone(senderKeyXors));
        payload.add(BytesUtils.clone(senderTagXors));
        payload.add(BytesUtils.clone(senderPayloadXors));
        return payload;
    }

    void insertSender(byte[] key, byte[] tag, byte[] element, int[] positions) {
        checkItem(key, tag, element);
        for (int position : positions) {
            checkPosition(position);
            senderCounts[position]++;
            xori(senderKeyXors, position, keyByteLength, key);
            xori(senderTagXors, position, tagByteLength, tag);
            xori(senderPayloadXors, position, elementByteLength, element);
        }
    }

    void insertReceiver(byte[] key, byte[] tag, int[] positions) {
        checkKeyTag(key, tag);
        for (int position : positions) {
            checkPosition(position);
            receiverCounts[position]++;
            xori(receiverKeyXors, position, keyByteLength, key);
            xori(receiverTagXors, position, tagByteLength, tag);
        }
    }

    boolean deleteSender(byte[] key, byte[] tag, byte[] element, int[] positions) {
        checkItem(key, tag, element);
        if (!canDeleteSender(positions)) {
            return false;
        }
        for (int position : positions) {
            senderCounts[position]--;
            xori(senderKeyXors, position, keyByteLength, key);
            xori(senderTagXors, position, tagByteLength, tag);
            xori(senderPayloadXors, position, elementByteLength, element);
        }
        return true;
    }

    boolean deleteReceiver(byte[] key, byte[] tag, int[] positions) {
        checkKeyTag(key, tag);
        if (!canDeleteReceiver(positions)) {
            return false;
        }
        for (int position : positions) {
            receiverCounts[position]--;
            xori(receiverKeyXors, position, keyByteLength, key);
            xori(receiverTagXors, position, tagByteLength, tag);
        }
        return true;
    }

    boolean canDeleteSender(int[] positions) {
        for (int position : positions) {
            checkPosition(position);
            if (senderCounts[position] <= 0) {
                return false;
            }
        }
        return true;
    }

    boolean canDeleteReceiver(int[] positions) {
        for (int position : positions) {
            checkPosition(position);
            if (receiverCounts[position] <= 0) {
                return false;
            }
        }
        return true;
    }

    int getSenderCount(int position) {
        checkPosition(position);
        return senderCounts[position];
    }

    int getReceiverCount(int position) {
        checkPosition(position);
        return receiverCounts[position];
    }

    byte[] getSenderKey(int position) {
        return copy(senderKeyXors, position, keyByteLength);
    }

    byte[] getSenderTag(int position) {
        return copy(senderTagXors, position, tagByteLength);
    }

    byte[] getSenderPayload(int position) {
        return copy(senderPayloadXors, position, elementByteLength);
    }

    byte[] getReceiverKey(int position) {
        return copy(receiverKeyXors, position, keyByteLength);
    }

    byte[] getReceiverTag(int position) {
        return copy(receiverTagXors, position, tagByteLength);
    }

    int getTableLength() {
        return tableLength;
    }

    boolean allZero() {
        return intArrayAllZero(senderCounts)
            && intArrayAllZero(receiverCounts)
            && byteArrayAllZero(senderKeyXors)
            && byteArrayAllZero(senderTagXors)
            && byteArrayAllZero(senderPayloadXors)
            && byteArrayAllZero(receiverKeyXors)
            && byteArrayAllZero(receiverTagXors);
    }

    private void checkItem(byte[] key, byte[] tag, byte[] element) {
        checkKeyTag(key, tag);
        MathPreconditions.checkEqual("element.length", "elementByteLength", element.length, elementByteLength);
    }

    private void checkKeyTag(byte[] key, byte[] tag) {
        MathPreconditions.checkEqual("key.length", "keyByteLength", key.length, keyByteLength);
        MathPreconditions.checkEqual("tag.length", "tagByteLength", tag.length, tagByteLength);
    }

    private void checkPosition(int position) {
        MathPreconditions.checkNonNegativeInRange("position", position, tableLength);
    }

    private static void copyChecked(byte[] source, byte[] target, String name) {
        MathPreconditions.checkEqual(name + ".length", "expected", source.length, target.length);
        System.arraycopy(source, 0, target, 0, target.length);
    }

    private static byte[] copy(byte[] source, int position, int byteLength) {
        int offset = Math.multiplyExact(position, byteLength);
        return Arrays.copyOfRange(source, offset, offset + byteLength);
    }

    private static void xori(byte[] target, int position, int byteLength, byte[] value) {
        int offset = Math.multiplyExact(position, byteLength);
        for (int i = 0; i < byteLength; i++) {
            target[offset + i] ^= value[i];
        }
    }

    private static boolean intArrayAllZero(int[] array) {
        for (int value : array) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean byteArrayAllZero(byte[] array) {
        for (byte value : array) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
