package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Queue-based conservative peeler for fast PISF-IBLT UPSU.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class PisIbltFastPeeler {
    /**
     * environment.
     */
    private final EnvType envType;
    /**
     * parameters.
     */
    private final PisIbltUpsuParams params;
    /**
     * public retry salt.
     */
    private final byte[] salt;
    /**
     * table.
     */
    private final PisIbltFastTable table;
    /**
     * receiver items indexed by retry key.
     */
    private final Map<ByteBuffer, ReceiverItem> receiverItemMap;
    /**
     * tentative sender-only delta.
     */
    private final Set<ByteBuffer> deltaSet;
    /**
     * tentative intersection count.
     */
    private int intersectionCount;
    /**
     * failure flag.
     */
    private boolean failure;

    private PisIbltFastPeeler(EnvType envType, PisIbltUpsuParams params, byte[] salt, PisIbltFastTable table,
                              Map<ByteBuffer, ReceiverItem> receiverItemMap) {
        this.envType = envType;
        this.params = params;
        this.salt = BytesUtils.clone(salt);
        this.table = table;
        this.receiverItemMap = receiverItemMap;
        deltaSet = new HashSet<>();
        intersectionCount = 0;
        failure = false;
    }

    static Result peel(EnvType envType, PisIbltUpsuParams params, byte[] salt, PisIbltFastTable table,
                       Map<ByteBuffer, ReceiverItem> receiverItemMap) {
        return new PisIbltFastPeeler(envType, params, salt, table, receiverItemMap).peel();
    }

    private Result peel() {
        ArrayDeque<Integer> queue = new ArrayDeque<>(table.getTableLength());
        boolean[] queued = new boolean[table.getTableLength()];
        for (int position = 0; position < table.getTableLength(); position++) {
            enqueue(queue, queued, position);
        }
        while (!queue.isEmpty() && !failure) {
            int position = queue.removeFirst();
            queued[position] = false;
            int[] touched = tryPeel(position);
            if (touched != null) {
                for (int neighbor : touched) {
                    enqueue(queue, queued, neighbor);
                }
            }
        }
        boolean success = !failure && table.allZero();
        return new Result(success, deltaSet, intersectionCount);
    }

    private int[] tryPeel(int position) {
        if (table.getSenderCount(position) == 1 && table.getReceiverCount(position) == 1) {
            int[] touched = tryIntersection(position);
            if (touched != null) {
                return touched;
            }
        }
        if (table.getSenderCount(position) == 1 && table.getReceiverCount(position) == 0) {
            return trySenderOnly(position);
        }
        if (table.getSenderCount(position) == 0 && table.getReceiverCount(position) == 1) {
            return tryReceiverOnly(position);
        }
        return null;
    }

    private int[] trySenderOnly(int position) {
        byte[] key = table.getSenderKey(position);
        byte[] tag = table.getSenderTag(position);
        byte[] payload = table.getSenderPayload(position);
        if (!validKeyTagPosition(key, tag, position)) {
            return null;
        }
        int[] positions = PisIbltHashUtils.fastPositions(envType, salt, key, params);
        if (!table.deleteSender(key, tag, payload, positions)) {
            failure = true;
            return null;
        }
        deltaSet.add(ByteBuffer.wrap(BytesUtils.clone(payload)));
        return positions;
    }

    private int[] tryReceiverOnly(int position) {
        byte[] key = table.getReceiverKey(position);
        byte[] tag = table.getReceiverTag(position);
        if (!validKeyTagPosition(key, tag, position)) {
            return null;
        }
        ReceiverItem item = receiverItemMap.get(ByteBuffer.wrap(key));
        if (item == null || item.removed || !Arrays.equals(item.tag, tag)
            || !PisIbltHashUtils.containsPosition(item.positions, position)) {
            return null;
        }
        if (!table.deleteReceiver(item.key, item.tag, item.positions)) {
            failure = true;
            return null;
        }
        item.removed = true;
        return item.positions;
    }

    private int[] tryIntersection(int position) {
        byte[] senderKey = table.getSenderKey(position);
        byte[] senderTag = table.getSenderTag(position);
        byte[] receiverKey = table.getReceiverKey(position);
        byte[] receiverTag = table.getReceiverTag(position);
        if (!Arrays.equals(senderKey, receiverKey) || !Arrays.equals(senderTag, receiverTag)
            || !validKeyTagPosition(senderKey, senderTag, position)) {
            return null;
        }
        ReceiverItem item = receiverItemMap.get(ByteBuffer.wrap(senderKey));
        if (item == null || item.removed || !Arrays.equals(item.tag, senderTag)
            || !PisIbltHashUtils.containsPosition(item.positions, position)) {
            return null;
        }
        byte[] senderPayload = table.getSenderPayload(position);
        if (!Arrays.equals(senderPayload, item.element)) {
            return null;
        }
        if (!table.canDeleteSender(item.positions) || !table.canDeleteReceiver(item.positions)) {
            failure = true;
            return null;
        }
        if (!table.deleteSender(item.key, item.tag, item.element, item.positions)
            || !table.deleteReceiver(item.key, item.tag, item.positions)) {
            failure = true;
            return null;
        }
        item.removed = true;
        intersectionCount++;
        return item.positions;
    }

    private boolean validKeyTagPosition(byte[] key, byte[] tag, int position) {
        byte[] expectTag = PisIbltHashUtils.fastTag(envType, salt, key, params.getTagByteLength());
        if (!Arrays.equals(expectTag, tag)) {
            return false;
        }
        int[] positions = PisIbltHashUtils.fastPositions(envType, salt, key, params);
        return PisIbltHashUtils.containsPosition(positions, position);
    }

    private void enqueue(ArrayDeque<Integer> queue, boolean[] queued, int position) {
        if (!queued[position]) {
            queue.addLast(position);
            queued[position] = true;
        }
    }

    static Map<ByteBuffer, ReceiverItem> createReceiverItemMap(EnvType envType, PisIbltUpsuParams params, byte[] salt,
                                                               byte[][] elements, byte[][] tokens,
                                                               PisIbltFastTable table) {
        Map<ByteBuffer, ReceiverItem> receiverItemMap = new HashMap<>(elements.length);
        for (int index = 0; index < elements.length; index++) {
            byte[] element = BytesUtils.clone(elements[index]);
            byte[] key = PisIbltHashUtils.fastKey(envType, salt, tokens[index], params.getKeyByteLength());
            byte[] tag = PisIbltHashUtils.fastTag(envType, salt, key, params.getTagByteLength());
            int[] positions = PisIbltHashUtils.fastPositions(envType, salt, key, params);
            ReceiverItem item = new ReceiverItem(element, key, tag, positions);
            ReceiverItem oldItem = receiverItemMap.put(ByteBuffer.wrap(BytesUtils.clone(key)), item);
            if (oldItem != null) {
                throw new IllegalStateException("receiver key collision in PISF-IBLT fast retry");
            }
            table.insertReceiver(key, tag, positions);
        }
        return receiverItemMap;
    }

    /**
     * Receiver item.
     */
    static class ReceiverItem {
        /**
         * element.
         */
        private final byte[] element;
        /**
         * retry key.
         */
        private final byte[] key;
        /**
         * retry tag.
         */
        private final byte[] tag;
        /**
         * retry positions.
         */
        private final int[] positions;
        /**
         * removed flag.
         */
        private boolean removed;

        ReceiverItem(byte[] element, byte[] key, byte[] tag, int[] positions) {
            this.element = element;
            this.key = key;
            this.tag = tag;
            this.positions = positions;
            removed = false;
        }
    }

    /**
     * Peel result.
     */
    static class Result {
        /**
         * success.
         */
        private final boolean success;
        /**
         * sender-only delta.
         */
        private final Set<ByteBuffer> deltaSet;
        /**
         * intersection count.
         */
        private final int intersectionCount;

        Result(boolean success, Set<ByteBuffer> deltaSet, int intersectionCount) {
            this.success = success;
            this.deltaSet = new HashSet<>(deltaSet);
            this.intersectionCount = intersectionCount;
        }

        boolean isSuccess() {
            return success;
        }

        Set<ByteBuffer> getDeltaSet() {
            Set<ByteBuffer> copy = new HashSet<>(deltaSet.size());
            for (ByteBuffer item : deltaSet) {
                copy.add(ByteBuffer.wrap(BytesUtils.clone(item.array())));
            }
            return copy;
        }

        int getIntersectionCount() {
            return intersectionCount;
        }
    }
}
