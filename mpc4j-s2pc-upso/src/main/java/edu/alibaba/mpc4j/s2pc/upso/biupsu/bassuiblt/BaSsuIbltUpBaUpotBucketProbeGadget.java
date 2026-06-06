package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Shape and conversion helpers for the specialized UP-BA-UPOT bucket-probe gadget.
 *
 * @author donghai hou
 * @date 2026/06/05
 */
final class BaSsuIbltUpBaUpotBucketProbeGadget {
    /**
     * SHA-256 digest name.
     */
    private static final String DIGEST_NAME = "SHA-256";
    /**
     * masked row domain.
     */
    private static final byte[] ROW_MASK_DOMAIN = new byte[]{
        'B', 'A', '-', 'S', 'S', 'U', '-', 'U', 'P', 'O', 'T', '-', 'R', 'O', 'W'
    };
    /**
     * same-singleton gate token domain.
     */
    private static final byte[] GATE_TOKEN_DOMAIN = new byte[]{
        'B', 'A', '-', 'S', 'S', 'U', '-', 'U', 'P', 'O', 'T', '-', 'G', 'A', 'T', 'E'
    };
    /**
     * non-matching filler token domain.
     */
    private static final byte[] NOISE_TOKEN_DOMAIN = new byte[]{
        'B', 'A', '-', 'S', 'S', 'U', '-', 'U', 'P', 'O', 'T', '-', 'N', 'O', 'I', 'S', 'E'
    };
    /**
     * fixed bottom action.
     */
    private static final byte ACTION_BOTTOM = 0x00;
    /**
     * release sender singleton action.
     */
    private static final byte ACTION_REMOTE_SINGLETON = 0x01;
    /**
     * release receiver singleton action.
     */
    private static final byte ACTION_LOCAL_SINGLETON = 0x02;
    /**
     * same-singleton release gate action.
     */
    private static final byte ACTION_SHARED_SINGLETON_GATE = 0x03;
    /**
     * at least two COT bits encode the three local states; the third bit keeps the fixed P51/P52 shape.
     */
    private static final int MIN_COT_NUM_PER_PROBE = 3;

    /**
     * private constructor.
     */
    private BaSsuIbltUpBaUpotBucketProbeGadget() {
        // empty
    }

    static int rowPayloadByteLength(BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("schedule must be non-null");
        }
        return Math.addExact(1, Math.addExact(schedule.getElementByteLength(), schedule.getAuthTagByteLength()));
    }

    static int maskedRowsByteLength(BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        return Math.multiplyExact(BaSsuIbltUpBaUpotMaskedProbeRows.ROW_NUM, rowPayloadByteLength(schedule));
    }

    static int choiceCorrectionByteLength(int cotNumPerProbe) {
        if (cotNumPerProbe < MIN_COT_NUM_PER_PROBE) {
            throw new IllegalArgumentException("cotNumPerProbe must be at least three");
        }
        return (cotNumPerProbe + Byte.SIZE - 1) / Byte.SIZE;
    }

    static boolean[] receiverChoices(BaSsuIbltUpBaUpotLocalInput localInput, int cotNumPerProbe) {
        if (localInput == null) {
            throw new IllegalArgumentException("localInput must be non-null");
        }
        return choicesForSymbol(toLocalSymbol(localInput.getState()), cotNumPerProbe);
    }

    static byte[] choiceCorrectionPayload(BaSsuIbltUpBaUpotLocalInput localInput,
                                          CotReceiverOutput cotReceiverOutput, int cotOffset,
                                          int cotNumPerProbe) {
        boolean[] desiredChoices = receiverChoices(localInput, cotNumPerProbe);
        validateCotRange(cotReceiverOutput, cotOffset, cotNumPerProbe);
        boolean[] correctionBits = new boolean[cotNumPerProbe];
        for (int i = 0; i < cotNumPerProbe; i++) {
            correctionBits[i] = cotReceiverOutput.getChoice(cotOffset + i) ^ desiredChoices[i];
        }
        return encodeChoiceCorrection(correctionBits);
    }

    static boolean[] decodeChoiceCorrectionPayload(byte[] payload, int cotNumPerProbe) {
        if (payload == null || payload.length != choiceCorrectionByteLength(cotNumPerProbe)) {
            throw new IllegalArgumentException("choice correction payload must match the fixed COT shape");
        }
        boolean[] correctionBits = new boolean[cotNumPerProbe];
        for (int i = 0; i < cotNumPerProbe; i++) {
            correctionBits[i] = ((payload[i >>> 3] >>> (i & 7)) & 1) == 1;
        }
        for (int i = cotNumPerProbe; i < payload.length * Byte.SIZE; i++) {
            if (((payload[i >>> 3] >>> (i & 7)) & 1) == 1) {
                throw new IllegalArgumentException("unused choice correction bits must be zero");
            }
        }
        return correctionBits;
    }

    static BaSsuIbltUpBaUpotMaskedProbeRows encodeRows(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                       BaSsuIbltUpBaUpotPublicInput publicInput,
                                                       BaSsuIbltUpBaUpotLocalInput localInput,
                                                       CotSenderOutput cotSenderOutput, int cotOffset,
                                                       int cotNumPerProbe) {
        return encodeRows(
            schedule, publicInput, localInput, cotSenderOutput, cotOffset, cotNumPerProbe,
            new boolean[cotNumPerProbe]
        );
    }

    static BaSsuIbltUpBaUpotMaskedProbeRows encodeRows(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                       BaSsuIbltUpBaUpotPublicInput publicInput,
                                                       BaSsuIbltUpBaUpotLocalInput localInput,
                                                       CotSenderOutput cotSenderOutput, int cotOffset,
                                                       int cotNumPerProbe, boolean[] choiceCorrectionBits) {
        validateInputs(schedule, publicInput, localInput);
        validateCotRange(cotSenderOutput, cotOffset, cotNumPerProbe);
        validateChoiceCorrection(choiceCorrectionBits, cotNumPerProbe);
        int rowByteLength = rowPayloadByteLength(schedule);
        BaSsuIbltUpBaUpotProbeRow emptyRow = encodeRow(
            schedule, publicInput, localInput, BaSsuIbltUpBaUpotFunctionality.LocalSymbol.EMPTY,
            cotSenderOutput, cotOffset, cotNumPerProbe, choiceCorrectionBits, rowByteLength
        );
        BaSsuIbltUpBaUpotProbeRow singletonRow = encodeRow(
            schedule, publicInput, localInput, BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON,
            cotSenderOutput, cotOffset, cotNumPerProbe, choiceCorrectionBits, rowByteLength
        );
        BaSsuIbltUpBaUpotProbeRow blockedRow = encodeRow(
            schedule, publicInput, localInput, BaSsuIbltUpBaUpotFunctionality.LocalSymbol.BLOCKED,
            cotSenderOutput, cotOffset, cotNumPerProbe, choiceCorrectionBits, rowByteLength
        );
        return BaSsuIbltUpBaUpotMaskedProbeRows.of(emptyRow, singletonRow, blockedRow);
    }

    static BaSsuIbltProductionUnionProbeOutput decodeSelectedRow(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                                 BaSsuIbltUpBaUpotPublicInput publicInput,
                                                                 BaSsuIbltUpBaUpotLocalInput localInput,
                                                                 BaSsuIbltUpBaUpotMaskedProbeRows maskedRows,
                                                                 CotReceiverOutput cotReceiverOutput, int cotOffset,
                                                                 int cotNumPerProbe) {
        validateInputs(schedule, publicInput, localInput);
        validateCotRange(cotReceiverOutput, cotOffset, cotNumPerProbe);
        if (maskedRows == null || maskedRows.rowByteLength() != rowPayloadByteLength(schedule)) {
            throw new IllegalArgumentException("maskedRows must match the public row shape");
        }
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol localSymbol = toLocalSymbol(localInput.getState());
        validateReceiverChoices(localSymbol, cotReceiverOutput, cotOffset, cotNumPerProbe);
        byte[] masked = maskedRows.row(localSymbol).getBytes();
        byte[] mask = receiverRowMask(schedule, publicInput, localSymbol, cotReceiverOutput, cotOffset, cotNumPerProbe);
        byte[] plaintext = BytesUtils.xor(masked, mask);
        return decodePlaintextRow(publicInput, localInput, plaintext);
    }

    static BaSsuIbltProductionUnionProbeOutput decodeSelectedRow(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                                 BaSsuIbltUpBaUpotPublicInput publicInput,
                                                                 BaSsuIbltUpBaUpotLocalInput localInput,
                                                                 BaSsuIbltUpBaUpotMaskedProbeRows maskedRows,
                                                                 CotReceiverOutput cotReceiverOutput, int cotOffset,
                                                                 int cotNumPerProbe, boolean[] choiceCorrectionBits) {
        validateInputs(schedule, publicInput, localInput);
        validateCotRange(cotReceiverOutput, cotOffset, cotNumPerProbe);
        validateChoiceCorrection(choiceCorrectionBits, cotNumPerProbe);
        if (maskedRows == null || maskedRows.rowByteLength() != rowPayloadByteLength(schedule)) {
            throw new IllegalArgumentException("maskedRows must match the public row shape");
        }
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol localSymbol = toLocalSymbol(localInput.getState());
        validateCorrectedReceiverChoices(localSymbol, cotReceiverOutput, cotOffset, cotNumPerProbe,
            choiceCorrectionBits);
        byte[] masked = maskedRows.row(localSymbol).getBytes();
        byte[] mask = receiverRowMask(schedule, publicInput, localSymbol, cotReceiverOutput, cotOffset,
            cotNumPerProbe);
        byte[] plaintext = BytesUtils.xor(masked, mask);
        return decodePlaintextRow(publicInput, localInput, plaintext);
    }

    static BaSsuIbltProductionUnionProbeOutput toOutput(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                        BaSsuIbltUpBaUpotFunctionality.ResultSymbol symbol,
                                                        byte[] singletonElement) {
        if (publicInput == null) {
            throw new IllegalArgumentException("publicInput must be non-null");
        }
        if (symbol == null) {
            throw new IllegalArgumentException("symbol must be non-null");
        }
        return switch (symbol) {
            case BOTTOM -> BaSsuIbltProductionUnionProbeOutput.bottom(
                publicInput.getBucketIndex(), publicInput.getElementByteLength()
            );
            case SINGLETON -> BaSsuIbltProductionUnionProbeOutput.singleton(
                publicInput.getBucketIndex(), singletonElement, publicInput.getElementByteLength()
            );
        };
    }

    private static BaSsuIbltUpBaUpotProbeRow encodeRow(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                                       BaSsuIbltUpBaUpotPublicInput publicInput,
                                                       BaSsuIbltUpBaUpotLocalInput localInput,
                                                       BaSsuIbltUpBaUpotFunctionality.LocalSymbol receiverSymbol,
                                                       CotSenderOutput cotSenderOutput, int cotOffset,
                                                       int cotNumPerProbe, boolean[] choiceCorrectionBits,
                                                       int rowByteLength) {
        byte[] plaintext = plaintextRowForReceiverSymbol(publicInput, localInput, receiverSymbol, rowByteLength);
        byte[] mask = senderRowMask(
            schedule, publicInput, receiverSymbol, cotSenderOutput, cotOffset, cotNumPerProbe, choiceCorrectionBits
        );
        return BaSsuIbltUpBaUpotProbeRow.of(BytesUtils.xor(plaintext, mask));
    }

    private static byte[] plaintextRowForReceiverSymbol(BaSsuIbltUpBaUpotPublicInput publicInput,
                                                        BaSsuIbltUpBaUpotLocalInput senderLocalInput,
                                                        BaSsuIbltUpBaUpotFunctionality.LocalSymbol receiverSymbol,
                                                        int rowByteLength) {
        BaSsuIbltUpBaUpotFunctionality.LocalSymbol senderSymbol = toLocalSymbol(senderLocalInput.getState());
        if (receiverSymbol == BaSsuIbltUpBaUpotFunctionality.LocalSymbol.EMPTY) {
            return senderSymbol == BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON
                ? remoteSingletonRow(publicInput, senderLocalInput, rowByteLength)
                : bottomRow(rowByteLength);
        }
        if (receiverSymbol == BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON) {
            return switch (senderSymbol) {
                case EMPTY -> localSingletonRow(rowByteLength);
                case SINGLETON -> sharedSingletonGateRow(
                    gateToken(publicInput, senderLocalInput, publicInput.getAuthTagByteLength()),
                    rowByteLength
                );
                case BLOCKED -> sharedSingletonGateRow(
                    noiseToken(publicInput, senderLocalInput, publicInput.getAuthTagByteLength()),
                    rowByteLength
                );
            };
        }
        return bottomRow(rowByteLength);
    }

    private static BaSsuIbltProductionUnionProbeOutput decodePlaintextRow(
        BaSsuIbltUpBaUpotPublicInput publicInput, BaSsuIbltUpBaUpotLocalInput localInput, byte[] plaintext) {
        if (plaintext == null || plaintext.length != 1 + publicInput.getElementByteLength()
            + publicInput.getAuthTagByteLength()) {
            throw new IllegalArgumentException("plaintext row must match the public shape");
        }
        int elementOffset = 1;
        int tokenOffset = elementOffset + publicInput.getElementByteLength();
        byte action = plaintext[0];
        if (action == ACTION_REMOTE_SINGLETON
            && localInput.getState() == BaSsuIbltUpBaUpotLocalInput.State.EMPTY) {
            return BaSsuIbltProductionUnionProbeOutput.singleton(
                publicInput.getBucketIndex(),
                Arrays.copyOfRange(plaintext, elementOffset, tokenOffset),
                publicInput.getElementByteLength()
            );
        }
        if (action == ACTION_LOCAL_SINGLETON
            && localInput.getState() == BaSsuIbltUpBaUpotLocalInput.State.SINGLETON) {
            byte[] element = localInput.elementCopy();
            return BaSsuIbltProductionUnionProbeOutput.singleton(
                publicInput.getBucketIndex(), element, publicInput.getElementByteLength()
            );
        }
        if (action == ACTION_SHARED_SINGLETON_GATE
            && localInput.getState() == BaSsuIbltUpBaUpotLocalInput.State.SINGLETON) {
            byte[] openedToken = Arrays.copyOfRange(plaintext, tokenOffset, plaintext.length);
            byte[] localToken = gateToken(publicInput, localInput, publicInput.getAuthTagByteLength());
            if (Arrays.equals(openedToken, localToken)) {
                return BaSsuIbltProductionUnionProbeOutput.singleton(
                    publicInput.getBucketIndex(), localInput.elementCopy(), publicInput.getElementByteLength()
                );
            }
        }
        return BaSsuIbltProductionUnionProbeOutput.bottom(
            publicInput.getBucketIndex(), publicInput.getElementByteLength()
        );
    }

    private static byte[] bottomRow(int rowByteLength) {
        byte[] plaintext = new byte[rowByteLength];
        plaintext[0] = ACTION_BOTTOM;
        return plaintext;
    }

    private static byte[] remoteSingletonRow(BaSsuIbltUpBaUpotPublicInput publicInput,
                                             BaSsuIbltUpBaUpotLocalInput localInput, int rowByteLength) {
        byte[] plaintext = new byte[rowByteLength];
        plaintext[0] = ACTION_REMOTE_SINGLETON;
        System.arraycopy(localInput.elementCopy(), 0, plaintext, 1, publicInput.getElementByteLength());
        return plaintext;
    }

    private static byte[] localSingletonRow(int rowByteLength) {
        byte[] plaintext = new byte[rowByteLength];
        plaintext[0] = ACTION_LOCAL_SINGLETON;
        return plaintext;
    }

    private static byte[] sharedSingletonGateRow(byte[] token, int rowByteLength) {
        byte[] plaintext = new byte[rowByteLength];
        plaintext[0] = ACTION_SHARED_SINGLETON_GATE;
        System.arraycopy(token, 0, plaintext, rowByteLength - token.length, token.length);
        return plaintext;
    }

    private static byte[] senderRowMask(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                        BaSsuIbltUpBaUpotPublicInput publicInput,
                                        BaSsuIbltUpBaUpotFunctionality.LocalSymbol rowSymbol,
                                        CotSenderOutput cotSenderOutput, int cotOffset, int cotNumPerProbe,
                                        boolean[] choiceCorrectionBits) {
        byte[][] keys = new byte[cotNumPerProbe][];
        for (int i = 0; i < cotNumPerProbe; i++) {
            boolean correctedChoice = choiceBit(rowSymbol, i) ^ choiceCorrectionBits[i];
            keys[i] = correctedChoice ? cotSenderOutput.getR1(cotOffset + i)
                : cotSenderOutput.getR0(cotOffset + i);
        }
        return rowMask(schedule, publicInput, rowSymbol, keys);
    }

    private static byte[] receiverRowMask(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                          BaSsuIbltUpBaUpotPublicInput publicInput,
                                          BaSsuIbltUpBaUpotFunctionality.LocalSymbol rowSymbol,
                                          CotReceiverOutput cotReceiverOutput, int cotOffset, int cotNumPerProbe) {
        byte[][] keys = new byte[cotNumPerProbe][];
        for (int i = 0; i < cotNumPerProbe; i++) {
            keys[i] = cotReceiverOutput.getRb(cotOffset + i);
        }
        return rowMask(schedule, publicInput, rowSymbol, keys);
    }

    private static byte[] rowMask(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                  BaSsuIbltUpBaUpotPublicInput publicInput,
                                  BaSsuIbltUpBaUpotFunctionality.LocalSymbol rowSymbol, byte[][] keys) {
        return expand(ROW_MASK_DOMAIN, schedule, publicInput, rowSymbol.ordinal(), rowPayloadByteLength(schedule), keys);
    }

    private static byte[] gateToken(BaSsuIbltUpBaUpotPublicInput publicInput,
                                    BaSsuIbltUpBaUpotLocalInput localInput, int outputByteLength) {
        return token(GATE_TOKEN_DOMAIN, publicInput, localInput, outputByteLength);
    }

    private static byte[] noiseToken(BaSsuIbltUpBaUpotPublicInput publicInput,
                                     BaSsuIbltUpBaUpotLocalInput localInput, int outputByteLength) {
        return token(NOISE_TOKEN_DOMAIN, publicInput, localInput, outputByteLength);
    }

    private static byte[] token(byte[] domain, BaSsuIbltUpBaUpotPublicInput publicInput,
                                BaSsuIbltUpBaUpotLocalInput localInput, int outputByteLength) {
        if (localInput == null) {
            throw new IllegalArgumentException("localInput must be non-null");
        }
        localInput.validatePublicInput(publicInput);
        byte[] auth = localInput.authCopy();
        MessageDigest digest = digest();
        digest.update(domain);
        updatePublicInput(digest, publicInput);
        updateInt(digest, localInput.cellCount());
        updateBytes(digest, localInput.elementCopy());
        updateBytes(digest, localInput.tagCopy());
        updateBytes(digest, localInput.checkCopy());
        updateBytes(digest, auth);
        return Arrays.copyOf(digest.digest(), outputByteLength);
    }

    private static byte[] expand(byte[] domain, BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                 BaSsuIbltUpBaUpotPublicInput publicInput, int rowOrdinal,
                                 int outputByteLength, byte[][] keys) {
        byte[] output = new byte[outputByteLength];
        int offset = 0;
        int counter = 0;
        while (offset < output.length) {
            MessageDigest digest = digest();
            digest.update(domain);
            updateSchedule(digest, schedule);
            updatePublicInput(digest, publicInput);
            updateInt(digest, rowOrdinal);
            updateInt(digest, outputByteLength);
            updateInt(digest, counter);
            for (byte[] key : keys) {
                updateBytes(digest, key);
            }
            byte[] block = digest.digest();
            int copyLength = Math.min(block.length, output.length - offset);
            System.arraycopy(block, 0, output, offset, copyLength);
            offset += copyLength;
            counter++;
        }
        return output;
    }

    private static void validateInputs(BaSsuIbltUpBaUpotOfflineSchedule schedule,
                                       BaSsuIbltUpBaUpotPublicInput publicInput,
                                       BaSsuIbltUpBaUpotLocalInput localInput) {
        if (schedule == null || publicInput == null || localInput == null) {
            throw new IllegalArgumentException("schedule, publicInput, and localInput must be non-null");
        }
        schedule.validate(publicInput);
        localInput.validatePublicInput(publicInput);
    }

    private static void validateCotRange(CotSenderOutput cotSenderOutput, int cotOffset, int cotNumPerProbe) {
        if (cotSenderOutput == null) {
            throw new IllegalArgumentException("cotSenderOutput must be non-null");
        }
        validateCotRange(cotSenderOutput.getNum(), cotOffset, cotNumPerProbe);
    }

    private static void validateCotRange(CotReceiverOutput cotReceiverOutput, int cotOffset, int cotNumPerProbe) {
        if (cotReceiverOutput == null) {
            throw new IllegalArgumentException("cotReceiverOutput must be non-null");
        }
        validateCotRange(cotReceiverOutput.getNum(), cotOffset, cotNumPerProbe);
    }

    private static void validateCotRange(int cotNum, int cotOffset, int cotNumPerProbe) {
        if (cotNumPerProbe < MIN_COT_NUM_PER_PROBE) {
            throw new IllegalArgumentException("cotNumPerProbe must be at least three");
        }
        if (cotOffset < 0 || cotOffset > cotNum || cotOffset + cotNumPerProbe > cotNum) {
            throw new IllegalArgumentException("COT range must cover one full probe");
        }
    }

    private static void validateReceiverChoices(BaSsuIbltUpBaUpotFunctionality.LocalSymbol localSymbol,
                                                CotReceiverOutput cotReceiverOutput, int cotOffset,
                                                int cotNumPerProbe) {
        boolean[] expectedChoices = choicesForSymbol(localSymbol, cotNumPerProbe);
        for (int i = 0; i < cotNumPerProbe; i++) {
            if (cotReceiverOutput.getChoice(cotOffset + i) != expectedChoices[i]) {
                throw new IllegalArgumentException("receiver COT choices must be bound to the local bucket state");
            }
        }
    }

    private static void validateCorrectedReceiverChoices(BaSsuIbltUpBaUpotFunctionality.LocalSymbol localSymbol,
                                                         CotReceiverOutput cotReceiverOutput, int cotOffset,
                                                         int cotNumPerProbe, boolean[] choiceCorrectionBits) {
        boolean[] expectedChoices = choicesForSymbol(localSymbol, cotNumPerProbe);
        for (int i = 0; i < cotNumPerProbe; i++) {
            boolean correctedChoice = cotReceiverOutput.getChoice(cotOffset + i) ^ choiceCorrectionBits[i];
            if (correctedChoice != expectedChoices[i]) {
                throw new IllegalArgumentException("corrected receiver COT choices must bind the local bucket state");
            }
        }
    }

    private static byte[] encodeChoiceCorrection(boolean[] correctionBits) {
        byte[] payload = new byte[choiceCorrectionByteLength(correctionBits.length)];
        for (int i = 0; i < correctionBits.length; i++) {
            if (correctionBits[i]) {
                payload[i >>> 3] |= (byte) (1 << (i & 7));
            }
        }
        return payload;
    }

    private static void validateChoiceCorrection(boolean[] correctionBits, int cotNumPerProbe) {
        if (correctionBits == null || correctionBits.length != cotNumPerProbe) {
            throw new IllegalArgumentException("choice correction bits must match cotNumPerProbe");
        }
    }

    private static boolean[] choicesForSymbol(BaSsuIbltUpBaUpotFunctionality.LocalSymbol symbol,
                                              int cotNumPerProbe) {
        if (cotNumPerProbe < MIN_COT_NUM_PER_PROBE) {
            throw new IllegalArgumentException("cotNumPerProbe must be at least three");
        }
        boolean[] choices = new boolean[cotNumPerProbe];
        for (int i = 0; i < cotNumPerProbe; i++) {
            choices[i] = choiceBit(symbol, i);
        }
        return choices;
    }

    private static boolean choiceBit(BaSsuIbltUpBaUpotFunctionality.LocalSymbol symbol, int cotBitIndex) {
        int ordinal = symbol.ordinal();
        if (cotBitIndex == 0) {
            return (ordinal & 1) != 0;
        }
        if (cotBitIndex == 1) {
            return (ordinal & 2) != 0;
        }
        return false;
    }

    private static BaSsuIbltUpBaUpotFunctionality.LocalSymbol toLocalSymbol(
        BaSsuIbltUpBaUpotLocalInput.State state) {
        return switch (state) {
            case EMPTY -> BaSsuIbltUpBaUpotFunctionality.LocalSymbol.EMPTY;
            case SINGLETON -> BaSsuIbltUpBaUpotFunctionality.LocalSymbol.SINGLETON;
            case BLOCKED -> BaSsuIbltUpBaUpotFunctionality.LocalSymbol.BLOCKED;
        };
    }

    private static void updateSchedule(MessageDigest digest, BaSsuIbltUpBaUpotOfflineSchedule schedule) {
        updateBytes(digest, schedule.getProfileId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        updateInt(digest, schedule.getRetryNum());
        updateInt(digest, schedule.getMaxProbeNum());
        updateInt(digest, schedule.getTableLength());
        updateInt(digest, schedule.getElementByteLength());
        updateInt(digest, schedule.getTagByteLength());
        updateInt(digest, schedule.getCheckByteLength());
        updateInt(digest, schedule.getAuthTagByteLength());
    }

    private static void updatePublicInput(MessageDigest digest, BaSsuIbltUpBaUpotPublicInput publicInput) {
        updateBytes(digest, publicInput.getProfileId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        updateInt(digest, publicInput.getRetryId());
        updateInt(digest, publicInput.getBucketIndex());
        updateInt(digest, publicInput.getProbeOrdinal());
        updateInt(digest, publicInput.getElementByteLength());
        updateInt(digest, publicInput.getTagBitLength());
        updateInt(digest, publicInput.getCheckBitLength());
        updateInt(digest, publicInput.getAuthTagBitLength());
    }

    private static void updateBytes(MessageDigest digest, byte[] bytes) {
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance(DIGEST_NAME);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
