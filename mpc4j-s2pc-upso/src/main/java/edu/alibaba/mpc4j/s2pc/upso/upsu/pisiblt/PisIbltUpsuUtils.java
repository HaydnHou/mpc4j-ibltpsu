package edu.alibaba.mpc4j.s2pc.upso.upsu.pisiblt;

import edu.alibaba.mpc4j.common.circuit.z2.Z2IntegerCircuit;
import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.circuit.z2.PlainZ2Vector;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.structure.database.ZlDatabase;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVectorFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * PISF-IBLT enhanced UPSU utilities.
 *
 * @author donghai hou
 * @date 2026/06/03
 */
class PisIbltUpsuUtils {
    private PisIbltUpsuUtils() {
        // empty
    }

    static void checkElementByteLength(int elementByteLength, int maxElementByteLength) {
        MathPreconditions.checkPositive("elementByteLength", elementByteLength);
        MathPreconditions.checkPositive("maxElementByteLength", maxElementByteLength);
        MathPreconditions.checkLessOrEqual("elementByteLength", elementByteLength, maxElementByteLength);
    }

    static int z2cInitBitNum(int senderCapacity, int receiverElementSize, int maxElementBitLength) {
        int recordNum = Math.addExact(senderCapacity, receiverElementSize);
        long batchHint = (long) recordNum * maxElementBitLength * 4L;
        return (int) Math.max(1, Math.min(1 << 16, batchHint));
    }

    static byte[] botElement(int elementByteLength) {
        byte[] bot = new byte[elementByteLength];
        Arrays.fill(bot, (byte) 0xFF);
        return bot;
    }

    static SenderSlots createSenderSlots(List<ByteBuffer> senderElementList, int senderCapacity, int elementByteLength,
                                         SecureRandom secureRandom) {
        byte[] bot = botElement(elementByteLength);
        List<SenderSlot> slotList = new ArrayList<>(senderCapacity);
        for (ByteBuffer element : senderElementList) {
            slotList.add(new SenderSlot(BytesUtils.clone(element.array()), true));
        }
        while (slotList.size() < senderCapacity) {
            slotList.add(new SenderSlot(BytesUtils.clone(bot), false));
        }
        Collections.shuffle(slotList, secureRandom);
        byte[][] elements = new byte[senderCapacity][];
        boolean[] real = new boolean[senderCapacity];
        for (int i = 0; i < senderCapacity; i++) {
            SenderSlot slot = slotList.get(i);
            elements[i] = slot.element;
            real[i] = slot.real;
        }
        return new SenderSlots(elements, real);
    }

    static ReceiverSlots createReceiverSlots(List<ByteBuffer> receiverElementList, int receiverCapacity,
                                             int elementByteLength, SecureRandom secureRandom) {
        MathPreconditions.checkPositiveInRangeClosed(
            "receiver element size", receiverElementList.size(), receiverCapacity
        );
        byte[] bot = botElement(elementByteLength);
        List<ReceiverSlot> slotList = new ArrayList<>(receiverCapacity);
        for (ByteBuffer element : receiverElementList) {
            slotList.add(new ReceiverSlot(ByteBuffer.wrap(BytesUtils.clone(element.array())), true));
        }
        while (slotList.size() < receiverCapacity) {
            slotList.add(new ReceiverSlot(ByteBuffer.wrap(BytesUtils.clone(bot)), false));
        }
        Collections.shuffle(slotList, secureRandom);
        List<ByteBuffer> elements = new ArrayList<>(receiverCapacity);
        boolean[] real = new boolean[receiverCapacity];
        for (int i = 0; i < receiverCapacity; i++) {
            ReceiverSlot slot = slotList.get(i);
            elements.add(slot.element);
            real[i] = slot.real;
        }
        return new ReceiverSlots(elements, real);
    }

    static SortFallbackOutput senderSortGroupFallback(Z2cParty z2cParty, EnvType envType, boolean parallel,
                                                      SenderSlots senderSlots, int receiverCapacity,
                                                      int elementBitLength) throws MpcAbortException {
        int senderCapacity = senderSlots.elements.length;
        int recordNum = senderCapacity + receiverCapacity;
        SquareZ2Vector[] senderKeyShares = z2cParty.shareOwn(
            payloadBitVectors(envType, parallel, senderKeyRecords(senderSlots.elements, receiverCapacity), elementBitLength)
        );
        SquareZ2Vector[] receiverKeyShares = z2cParty.shareOther(bitNums(elementBitLength, recordNum));
        SquareZ2Vector[] keyShares = z2cParty.xor(senderKeyShares, receiverKeyShares);
        SquareZ2Vector senderRealShares = z2cParty.shareOwn(senderRealBitVector(senderSlots.real, receiverCapacity));
        SquareZ2Vector receiverRealShares = z2cParty.shareOther(recordNum);
        SquareZ2Vector realShares = z2cParty.xor(senderRealShares, receiverRealShares);
        SquareZ2Vector[] payloadShares = z2cParty.shareOwn(
            payloadBitVectors(envType, parallel, senderPayloadRecords(senderSlots.elements, receiverCapacity), elementBitLength)
        );
        return sortGroupFallback(z2cParty, keyShares, realShares, payloadShares, senderCapacity, receiverCapacity);
    }

    static SortFallbackOutput receiverSortGroupFallback(Z2cParty z2cParty, EnvType envType, boolean parallel,
                                                        List<ByteBuffer> receiverSlots, boolean[] receiverReal,
                                                        int senderCapacity, int elementBitLength)
        throws MpcAbortException {
        int receiverCapacity = receiverSlots.size();
        int recordNum = senderCapacity + receiverCapacity;
        SquareZ2Vector[] senderKeyShares = z2cParty.shareOther(bitNums(elementBitLength, recordNum));
        SquareZ2Vector[] receiverKeyShares = z2cParty.shareOwn(
            payloadBitVectors(envType, parallel, receiverKeyRecords(senderCapacity, receiverSlots, elementBitLength),
                elementBitLength)
        );
        SquareZ2Vector[] keyShares = z2cParty.xor(senderKeyShares, receiverKeyShares);
        SquareZ2Vector senderRealShares = z2cParty.shareOther(recordNum);
        SquareZ2Vector receiverRealShares = z2cParty.shareOwn(receiverRealBitVector(senderCapacity, receiverReal));
        SquareZ2Vector realShares = z2cParty.xor(senderRealShares, receiverRealShares);
        SquareZ2Vector[] payloadShares = z2cParty.shareOther(bitNums(elementBitLength, recordNum));
        return sortGroupFallback(z2cParty, keyShares, realShares, payloadShares, senderCapacity, receiverCapacity);
    }

    private static SortFallbackOutput sortGroupFallback(Z2cParty z2cParty, SquareZ2Vector[] keyShares,
                                                        SquareZ2Vector realShares, SquareZ2Vector[] payloadShares,
                                                        int senderCapacity, int receiverCapacity)
        throws MpcAbortException {
        int recordNum = senderCapacity + receiverCapacity;
        int elementBitLength = keyShares.length;
        int ownerBitLength = Math.max(1, LongUtils.ceilLog2(recordNum));
        Z2IntegerCircuit circuit = new Z2IntegerCircuit(z2cParty);
        MpcZ2Vector[][] keyArrays = new MpcZ2Vector[][]{keyShares};
        MpcZ2Vector[][] payloadArrays = new MpcZ2Vector[2 + ownerBitLength + elementBitLength][];
        payloadArrays[0] = new MpcZ2Vector[]{
            z2cParty.setPublicValues(new BitVector[]{senderSourceBitVector(senderCapacity, receiverCapacity)})[0]
        };
        payloadArrays[1] = new MpcZ2Vector[]{realShares};
        SquareZ2Vector[] ownerShares = ownerBitVectors(z2cParty, recordNum, ownerBitLength);
        for (int i = 0; i < ownerBitLength; i++) {
            payloadArrays[2 + i] = new MpcZ2Vector[]{ownerShares[i]};
        }
        for (int i = 0; i < elementBitLength; i++) {
            payloadArrays[2 + ownerBitLength + i] = new MpcZ2Vector[]{payloadShares[i]};
        }
        circuit.psort(keyArrays, payloadArrays, PlainZ2Vector.createOnes(1), false, true);

        SquareZ2Vector[] sortedKeyShares = Arrays.stream(keyArrays[0])
            .map(vector -> (SquareZ2Vector) vector)
            .toArray(SquareZ2Vector[]::new);
        SquareZ2Vector sortedSenderSource = (SquareZ2Vector) payloadArrays[0][0];
        SquareZ2Vector sortedReal = (SquareZ2Vector) payloadArrays[1][0];
        SquareZ2Vector[] sortedOwnerShares = new SquareZ2Vector[ownerBitLength];
        for (int i = 0; i < ownerBitLength; i++) {
            sortedOwnerShares[i] = (SquareZ2Vector) payloadArrays[2 + i][0];
        }
        SquareZ2Vector[] sortedPayloadShares = new SquareZ2Vector[elementBitLength];
        for (int i = 0; i < elementBitLength; i++) {
            sortedPayloadShares[i] = (SquareZ2Vector) payloadArrays[2 + ownerBitLength + i][0];
        }

        SquareZ2Vector[][] keyRows = splitColumnsToRows(z2cParty, sortedKeyShares, recordNum);
        SquareZ2Vector[] senderRows = splitColumnToRows(z2cParty, sortedSenderSource, recordNum);
        SquareZ2Vector[] realRows = splitColumnToRows(z2cParty, sortedReal, recordNum);
        SquareZ2Vector[][] payloadRows = splitColumnsToRows(z2cParty, sortedPayloadShares, recordNum);
        SquareZ2Vector[] samePrev = samePreviousRows(z2cParty, circuit, keyRows);
        SquareZ2Vector[] sameNext = new SquareZ2Vector[recordNum];
        for (int i = 0; i < recordNum - 1; i++) {
            sameNext[i] = samePrev[i + 1];
        }
        sameNext[recordNum - 1] = SquareZ2Vector.createZeros(1);

        SquareZ2Vector[] receiverRealRows = new SquareZ2Vector[recordNum];
        for (int i = 0; i < recordNum; i++) {
            receiverRealRows[i] = z2cParty.and(z2cParty.not(senderRows[i]), realRows[i]);
        }
        SquareZ2Vector[] forwardReceiver = new SquareZ2Vector[recordNum];
        forwardReceiver[0] = receiverRealRows[0];
        for (int i = 1; i < recordNum; i++) {
            forwardReceiver[i] = z2cParty.or(
                receiverRealRows[i], z2cParty.and(samePrev[i], forwardReceiver[i - 1])
            );
        }
        SquareZ2Vector[] backwardReceiver = new SquareZ2Vector[recordNum];
        backwardReceiver[recordNum - 1] = receiverRealRows[recordNum - 1];
        for (int i = recordNum - 2; i >= 0; i--) {
            backwardReceiver[i] = z2cParty.or(
                receiverRealRows[i], z2cParty.and(sameNext[i], backwardReceiver[i + 1])
            );
        }

        SquareZ2Vector[] emitRows = new SquareZ2Vector[recordNum];
        SquareZ2Vector[][] selectedPayloadRows = new SquareZ2Vector[recordNum][elementBitLength];
        for (int i = 0; i < recordNum; i++) {
            SquareZ2Vector hasReceiver = z2cParty.or(forwardReceiver[i], backwardReceiver[i]);
            emitRows[i] = z2cParty.and(z2cParty.and(senderRows[i], realRows[i]), z2cParty.not(hasReceiver));
            for (int j = 0; j < elementBitLength; j++) {
                selectedPayloadRows[i][j] = z2cParty.and(emitRows[i], payloadRows[i][j]);
            }
        }

        SquareZ2Vector emitColumn = mergeRows(z2cParty, emitRows);
        SquareZ2Vector[] selectedPayloadColumns = mergePayloadRows(z2cParty, selectedPayloadRows, elementBitLength);
        MpcZ2Vector[][] ownerKeyArrays = new MpcZ2Vector[][]{sortedOwnerShares};
        MpcZ2Vector[][] outputPayloadArrays = new MpcZ2Vector[1 + elementBitLength][];
        outputPayloadArrays[0] = new MpcZ2Vector[]{emitColumn};
        for (int i = 0; i < elementBitLength; i++) {
            outputPayloadArrays[1 + i] = new MpcZ2Vector[]{selectedPayloadColumns[i]};
        }
        circuit.psort(ownerKeyArrays, outputPayloadArrays, PlainZ2Vector.createOnes(1), false, true);
        SquareZ2Vector emit = takePrefix(z2cParty, (SquareZ2Vector) outputPayloadArrays[0][0], recordNum,
            senderCapacity);
        SquareZ2Vector[] payload = new SquareZ2Vector[elementBitLength];
        for (int i = 0; i < elementBitLength; i++) {
            payload[i] = takePrefix(z2cParty, (SquareZ2Vector) outputPayloadArrays[1 + i][0], recordNum,
                senderCapacity);
        }
        return new SortFallbackOutput(emit, payload);
    }

    private static SquareZ2Vector[] samePreviousRows(Z2cParty z2cParty, Z2IntegerCircuit circuit,
                                                     SquareZ2Vector[][] keyRows) throws MpcAbortException {
        int recordNum = keyRows.length;
        SquareZ2Vector[] samePrev = new SquareZ2Vector[recordNum];
        samePrev[0] = SquareZ2Vector.createZeros(1);
        for (int i = 1; i < recordNum; i++) {
            samePrev[i] = (SquareZ2Vector) circuit.eq(keyRows[i - 1], keyRows[i]);
        }
        return samePrev;
    }

    private static SquareZ2Vector[][] splitColumnsToRows(Z2cParty z2cParty, SquareZ2Vector[] columns, int rowNum) {
        SquareZ2Vector[][] rows = new SquareZ2Vector[rowNum][columns.length];
        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
            SquareZ2Vector[] columnRows = splitColumnToRows(z2cParty, columns[columnIndex], rowNum);
            for (int rowIndex = 0; rowIndex < rowNum; rowIndex++) {
                rows[rowIndex][columnIndex] = columnRows[rowIndex];
            }
        }
        return rows;
    }

    private static SquareZ2Vector[] splitColumnToRows(Z2cParty z2cParty, SquareZ2Vector column, int rowNum) {
        int[] bitNums = bitNums(rowNum, 1);
        return Arrays.stream(z2cParty.split(column.copy(), bitNums))
            .map(vector -> (SquareZ2Vector) vector)
            .toArray(SquareZ2Vector[]::new);
    }

    private static SquareZ2Vector[] mergePayloadRows(Z2cParty z2cParty, SquareZ2Vector[][] rows, int bitLength) {
        SquareZ2Vector[] columns = new SquareZ2Vector[bitLength];
        for (int columnIndex = 0; columnIndex < bitLength; columnIndex++) {
            SquareZ2Vector[] columnRows = new SquareZ2Vector[rows.length];
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                columnRows[rowIndex] = rows[rowIndex][columnIndex];
            }
            columns[columnIndex] = mergeRows(z2cParty, columnRows);
        }
        return columns;
    }

    private static SquareZ2Vector mergeRows(Z2cParty z2cParty, SquareZ2Vector[] rows) {
        return (SquareZ2Vector) z2cParty.merge(rows);
    }

    private static SquareZ2Vector takePrefix(Z2cParty z2cParty, SquareZ2Vector vector, int rowNum, int prefixNum) {
        SquareZ2Vector[] rows = splitColumnToRows(z2cParty, vector, rowNum);
        return mergeRows(z2cParty, Arrays.copyOf(rows, prefixNum));
    }

    private static BitVector senderRealBitVector(boolean[] senderReal, int receiverCapacity) {
        BitVector bitVector = BitVectorFactory.createZeros(senderReal.length + receiverCapacity);
        for (int i = 0; i < senderReal.length; i++) {
            bitVector.set(i, senderReal[i]);
        }
        return bitVector;
    }

    private static BitVector receiverRealBitVector(int senderCapacity, boolean[] receiverReal) {
        BitVector bitVector = BitVectorFactory.createZeros(senderCapacity + receiverReal.length);
        for (int i = 0; i < receiverReal.length; i++) {
            bitVector.set(senderCapacity + i, receiverReal[i]);
        }
        return bitVector;
    }

    private static BitVector senderSourceBitVector(int senderCapacity, int receiverCapacity) {
        BitVector bitVector = BitVectorFactory.createZeros(senderCapacity + receiverCapacity);
        for (int i = 0; i < senderCapacity; i++) {
            bitVector.set(i, true);
        }
        return bitVector;
    }

    private static SquareZ2Vector[] ownerBitVectors(Z2cParty z2cParty, int recordNum, int ownerBitLength) {
        byte[][] ownerRecords = new byte[recordNum][];
        int ownerByteLength = CommonUtils.getByteLength(ownerBitLength);
        for (int i = 0; i < recordNum; i++) {
            ownerRecords[i] = IntUtils.nonNegIntToFixedByteArray(i, ownerByteLength);
        }
        return Arrays.stream(z2cParty.setPublicValues(
                ZlDatabase.create(ownerBitLength, ownerRecords).bitPartition(EnvType.STANDARD, false)
            ))
            .map(vector -> (SquareZ2Vector) vector)
            .toArray(SquareZ2Vector[]::new);
    }

    private static byte[][] senderKeyRecords(byte[][] senderElements, int receiverCapacity) {
        int senderCapacity = senderElements.length;
        int elementByteLength = senderElements[0].length;
        byte[][] records = zeroRecords(senderCapacity + receiverCapacity, elementByteLength);
        for (int i = 0; i < senderCapacity; i++) {
            records[i] = BytesUtils.clone(senderElements[i]);
        }
        return records;
    }

    private static byte[][] receiverKeyRecords(int senderCapacity, List<ByteBuffer> receiverSlots,
                                               int elementBitLength) {
        int elementByteLength = elementBitLength / Byte.SIZE;
        byte[][] records = zeroRecords(senderCapacity + receiverSlots.size(), elementByteLength);
        for (int i = 0; i < receiverSlots.size(); i++) {
            records[senderCapacity + i] = BytesUtils.clone(receiverSlots.get(i).array());
        }
        return records;
    }

    private static byte[][] senderPayloadRecords(byte[][] senderElements, int receiverCapacity) {
        return senderKeyRecords(senderElements, receiverCapacity);
    }

    private static byte[][] zeroRecords(int recordNum, int elementByteLength) {
        byte[][] records = new byte[recordNum][];
        for (int i = 0; i < recordNum; i++) {
            records[i] = new byte[elementByteLength];
        }
        return records;
    }

    private static int[] bitNums(int length, int bitNum) {
        int[] bitNums = new int[length];
        Arrays.fill(bitNums, bitNum);
        return bitNums;
    }

    static BitVector realBitVector(boolean[] real) {
        BitVector bitVector = BitVectorFactory.createZeros(real.length);
        for (int i = 0; i < real.length; i++) {
            bitVector.set(i, real[i]);
        }
        return bitVector;
    }

    static BitVector[] payloadBitVectors(EnvType envType, boolean parallel, byte[][] payloads, int elementBitLength) {
        return ZlDatabase.create(elementBitLength, payloads).bitPartition(envType, parallel);
    }

    static byte[][] combinePayloadBitVectors(EnvType envType, boolean parallel, BitVector[] payloadBits) {
        return ZlDatabase.create(envType, parallel, payloadBits).getBytesData();
    }

    static Set<ByteBuffer> addReceiverSet(Set<ByteBuffer> union, List<ByteBuffer> receiverElementList) {
        for (ByteBuffer receiverElement : receiverElementList) {
            union.add(ByteBuffer.wrap(BytesUtils.clone(receiverElement.array())));
        }
        return union;
    }

    static class SenderSlots {
        /**
         * shuffled sender slots.
         */
        final byte[][] elements;
        /**
         * real sender slot flags.
         */
        final boolean[] real;

        SenderSlots(byte[][] elements, boolean[] real) {
            this.elements = elements;
            this.real = real;
        }
    }

    static class ReceiverSlots {
        /**
         * shuffled receiver slots.
         */
        final List<ByteBuffer> elements;
        /**
         * real receiver slot flags.
         */
        final boolean[] real;

        ReceiverSlots(List<ByteBuffer> elements, boolean[] real) {
            this.elements = elements;
            this.real = real;
        }
    }

    static class SortFallbackOutput {
        /**
         * fixed sender-slot emit bits.
         */
        final SquareZ2Vector emit;
        /**
         * fixed sender-slot selected payload bits.
         */
        final SquareZ2Vector[] payloadShares;

        SortFallbackOutput(SquareZ2Vector emit, SquareZ2Vector[] payloadShares) {
            this.emit = emit;
            this.payloadShares = payloadShares;
        }
    }

    private static class SenderSlot {
        /**
         * element.
         */
        private final byte[] element;
        /**
         * real flag.
         */
        private final boolean real;

        private SenderSlot(byte[] element, boolean real) {
            this.element = element;
            this.real = real;
        }
    }

    private static class ReceiverSlot {
        /**
         * element.
         */
        private final ByteBuffer element;
        /**
         * real flag.
         */
        private final boolean real;

        private ReceiverSlot(ByteBuffer element, boolean real) {
            this.element = element;
            this.real = real;
        }
    }
}
