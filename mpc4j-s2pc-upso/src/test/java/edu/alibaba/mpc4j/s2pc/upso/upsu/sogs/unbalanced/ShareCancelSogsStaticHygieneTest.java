package edu.alibaba.mpc4j.s2pc.upso.upsu.sogs.unbalanced;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Static hygiene gates for the SC-SOGS share-cancel path.
 *
 * <p>These tests are not a cryptographic proof. They are regression guards for the two process-leakage mistakes that
 * would immediately invalidate the intended SC-SOGS semantics: receiver-side code learning the sender's private
 * permutation maps, or production code reconstructing share-output hit bits into plaintext hit vectors.</p>
 *
 * @author donghai hou
 * @date 2026/06/19
 */
public class ShareCancelSogsStaticHygieneTest {
    /**
     * Receiver-side production files that must not learn sender-local permutation maps.
     */
    private static final String[] RECEIVER_SIDE_FILES = {
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuReceiver.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/unbalanced/ShareCancelSogsTailReceiver.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/unbalanced/TokenKeyedSogsTailReceiver.java",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share/folded/tcl23/"
            + "Tcl23ByteEccDdhFoldedSharePmPeqtReceiver.java",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share/folded/tcl23/"
            + "Tcl23PsOprfFoldedSharePmPeqtReceiver.java",
    };
    /**
     * Tokens that would indicate sender-local coordinate material reached receiver-side production code.
     */
    private static final String[] FORBIDDEN_RECEIVER_COORDINATE_TOKENS = {
        "rowPermutationMap",
        "columnPermutationMap",
        "permutationMap",
        "originalColumn",
    };
    /**
     * Production source roots that belong to the SC-SOGS share-output relation / tail path.
     */
    private static final String[] SHARE_CANCEL_SOURCE_ROOTS = {
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share",
    };
    /**
     * Production files on the current SC-SOGS share-cancel route.
     */
    private static final String[] SHARE_CANCEL_ROUTE_FILES = {
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuSender.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuReceiver.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuUtils.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/unbalanced/ShareCancelSogsTailSender.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/unbalanced/ShareCancelSogsTailReceiver.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/unbalanced/TokenKeyedSogsTailSender.java",
        "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/unbalanced/TokenKeyedSogsTailReceiver.java",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share/folded/"
            + "AbstractFoldedSharePmPeqtSender.java",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share/folded/"
            + "AbstractFoldedSharePmPeqtReceiver.java",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share/folded/tcl23/"
            + "Tcl23ByteEccDdhFoldedSharePmPeqtSender.java",
        "mpc4j-s2pc-opf/src/main/java/edu/alibaba/mpc4j/s2pc/opf/pmpeqt/share/folded/tcl23/"
            + "Tcl23ByteEccDdhFoldedSharePmPeqtReceiver.java",
    };
    /**
     * Tokens that indicate plaintext hit/miss reconstruction or old open-bit style release in production code.
     */
    private static final String[] FORBIDDEN_PRODUCTION_TOKENS = {
        "getBitVector().xor",
        "hitVector",
        "missVector",
        "choiceArray",
        "openFail",
        "open/fail",
    };
    /**
     * Sender-local coordinate material that must not be serialized or passed into the tail boundary.
     */
    private static final String[] FORBIDDEN_SERIALIZED_COORDINATE_TOKENS = {
        "rowPermutationMap",
        "columnPermutationMap",
        "originalColumn",
        "originalBinPayloads",
    };
    /**
     * Tokens that must never appear in receiver-visible logs or abort texts for the SC-SOGS share-cancel route.
     */
    private static final String[] FORBIDDEN_LOG_ABORT_TOKENS = {
        "rowPermutationMap",
        "columnPermutationMap",
        "originalColumn",
        "originalBinPayloads",
        "choiceArray",
        "openFail",
        "open/fail",
        "peelResult.residualCells",
        "residualCells",
        "singleton",
        "failed position",
        "debug sketch",
    };
    /**
     * Network-visible boundary calls that must remain aggregate-shaped.
     */
    private static final String[] NETWORK_BOUNDARY_CALLS = {
        "DataPacket.fromByteArrayList(",
        "rpc.send(",
        "sendOtherPartyEqualSizePayload(",
        "receiveOtherPartyEqualSizePayload(",
        "coreCotSender.send(",
        "coreCotReceiver.receive(",
    };

    @Test
    public void testReceiverSideDoesNotReferenceSenderPermutationMaps() throws IOException {
        Path root = findRepoRoot();
        for (String relativeFile : RECEIVER_SIDE_FILES) {
            String source = Files.readString(root.resolve(relativeFile));
            for (String token : FORBIDDEN_RECEIVER_COORDINATE_TOKENS) {
                Assert.assertFalse(relativeFile + " must not reference sender-local coordinate token: " + token,
                    source.contains(token));
            }
        }
    }

    @Test
    public void testProductionShareCancelPathDoesNotReconstructPlainHitBits() throws IOException {
        Path root = findRepoRoot();
        for (Path sourceFile : listJavaFiles(root, SHARE_CANCEL_SOURCE_ROOTS)) {
            String source = Files.readString(sourceFile);
            for (String token : FORBIDDEN_PRODUCTION_TOKENS) {
                Assert.assertFalse(sourceFile + " must not contain forbidden token: " + token, source.contains(token));
            }
        }
    }

    @Test
    public void testShareCancelTailApiDoesNotAcceptPermutationMaps() {
        for (Method method : ShareCancelSogsTailSender.class.getDeclaredMethods()) {
            if (!method.getName().equals("send")) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                Assert.assertFalse(
                    "ShareCancelSogsTailSender.send must not accept int[] permutation maps",
                    parameterType.isArray() && parameterType.getComponentType().equals(int.class)
                );
            }
        }
    }

    @Test
    public void testShareCancelSenderDoesNotSerializePrivateCoordinates() throws IOException {
        Path root = findRepoRoot();
        Path senderPath = root.resolve(
            "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuSender.java"
        );
        String source = Files.readString(senderPath);
        String tailCall = extractCall(source, "shareCancelTailSender.send(");
        for (String token : FORBIDDEN_SERIALIZED_COORDINATE_TOKENS) {
            Assert.assertFalse(
                "share-cancel tail boundary must not receive sender-local coordinate token: " + token,
                tailCall.contains(token)
            );
        }
        for (String packetCall : extractCalls(source, "DataPacket.fromByteArrayList(")) {
            for (String token : FORBIDDEN_SERIALIZED_COORDINATE_TOKENS) {
                Assert.assertFalse(
                    "SC-SOGS main DataPacket serialization must not reference private coordinate token: " + token,
                    packetCall.contains(token)
                );
            }
        }
    }

    @Test
    public void testShareCancelLogsAndAbortMessagesDoNotExposePrivateState() throws IOException {
        Path root = findRepoRoot();
        String[] callNeedles = {
            "logStepInfo(",
            "logPhaseInfo(",
            "throw new MpcAbortException(",
            "MpcAbortPreconditions.checkArgument(",
        };
        for (String relativeFile : SHARE_CANCEL_ROUTE_FILES) {
            String source = stripJavaComments(Files.readString(root.resolve(relativeFile)));
            for (String callNeedle : callNeedles) {
                for (String call : extractCalls(source, callNeedle)) {
                    for (String token : FORBIDDEN_LOG_ABORT_TOKENS) {
                        Assert.assertFalse(
                            relativeFile + " must not expose private coordinate / residual token in " + callNeedle
                                + ": " + token,
                            call.contains(token)
                        );
                    }
                }
            }
        }
    }

    @Test
    public void testShareCancelReceiverUsesGenericPeelFailureOnly() throws IOException {
        Path root = findRepoRoot();
        Path receiverPath = root.resolve(
            "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuReceiver.java"
        );
        String source = stripJavaComments(Files.readString(receiverPath));
        String peelBlock = extractBetween(
            source, "TokenKeyedSogsSketch.PeelResult peelResult", "Set<ByteBuffer> difference"
        );
        Assert.assertTrue(
            "receiver must collapse peel failure to a generic success check",
            peelBlock.contains("MpcAbortPreconditions.checkArgument(peelResult.success);")
        );
        String[] forbiddenPeelDiagnostics = {
            "peelResult.residualCells",
            "residualCells",
            "singleton",
            "failed position",
            "debug sketch",
        };
        for (String token : forbiddenPeelDiagnostics) {
            Assert.assertFalse(
                "receiver must not inspect or expose peel diagnostic token before output: " + token,
                peelBlock.contains(token)
            );
        }
    }

    @Test
    public void testShareCancelUsesActualAlphaLikeTcl23() throws IOException {
        Path root = findRepoRoot();
        Path senderPath = root.resolve(
            "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuSender.java"
        );
        Path receiverPath = root.resolve(
            "mpc4j-s2pc-upso/src/main/java/edu/alibaba/mpc4j/s2pc/upso/upsu/sogs/sc/ScSogsUpsuReceiver.java"
        );
        String senderSource = stripJavaComments(Files.readString(senderPath));
        String receiverSource = stripJavaComments(Files.readString(receiverPath));
        Assert.assertTrue(
            "sender must derive online alpha from the receiver response length, matching TCL23",
            senderSource.contains("receiverResponsePayload.size() / params.getCiphertextNum()")
        );
        Assert.assertFalse(
            "sender must not bind online alpha to public expectAlpha",
            senderSource.contains("alpha = expectAlpha;")
        );
        Assert.assertFalse(
            "sender must not require response payload counts to match a public alpha",
            senderSource.contains("receiverResponsePayload.size() == alpha * params.getCiphertextNum()")
        );
        Assert.assertFalse(
            "receiver must not bind online alpha to public expectAlpha",
            receiverSource.contains("alpha = expectAlpha;")
        );
        Assert.assertFalse(
            "receiver must not pad bins to public expected max bin size",
            receiverSource.contains("publicMaxBinSize = expectBinSize;")
        );
        Assert.assertFalse(
            "receiver must not reject actual bins against publicMaxBinSize",
            receiverSource.contains("MpcAbortPreconditions.checkArgument(maxBinSize <= publicMaxBinSize);")
        );
        Assert.assertFalse(
            "receiver must not inflate padding to an entire public partition unless the expected bound requires it",
            receiverSource.contains("publicMaxBinSize = alpha * params.getMaxPartitionSizePerBin()")
        );
        Assert.assertTrue(
            "receiver must derive alpha from actual max bin size, matching TCL23",
            receiverSource.contains("alpha = CommonUtils.getUnitNum(maxBinSize")
        );
    }

    @Test
    public void testShareCancelNetworkBoundariesAreOutsideCoordinateLoops() throws IOException {
        Path root = findRepoRoot();
        for (String relativeFile : SHARE_CANCEL_ROUTE_FILES) {
            String source = stripJavaCommentsAndStringLiterals(Files.readString(root.resolve(relativeFile)));
            for (String callNeedle : NETWORK_BOUNDARY_CALLS) {
                int start = source.indexOf(callNeedle);
                while (start >= 0) {
                    Assert.assertFalse(
                        relativeFile + " must not put network boundary " + callNeedle
                            + " inside a row/bin/cell loop",
                        isInsideLoopBlock(source, start)
                    );
                    start = source.indexOf(callNeedle, start + callNeedle.length());
                }
            }
        }
    }

    private static List<Path> listJavaFiles(Path root, String[] relativeRoots) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        for (String relativeRoot : relativeRoots) {
            try (Stream<Path> stream = Files.walk(root.resolve(relativeRoot))) {
                stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
            }
        }
        return javaFiles;
    }

    private static List<String> extractCalls(String source, String needle) {
        List<String> calls = new ArrayList<>();
        int start = source.indexOf(needle);
        while (start >= 0) {
            calls.add(extractCall(source.substring(start), needle));
            start = source.indexOf(needle, start + needle.length());
        }
        return calls;
    }

    private static String extractCall(String source, String needle) {
        int start = source.indexOf(needle);
        if (start < 0) {
            throw new AssertionError("cannot find call: " + needle);
        }
        int end = source.indexOf(");", start);
        if (end < 0) {
            throw new AssertionError("cannot find call end: " + needle);
        }
        return source.substring(start, end + 2);
    }

    private static String extractBetween(String source, String beginNeedle, String endNeedle) {
        int start = source.indexOf(beginNeedle);
        if (start < 0) {
            throw new AssertionError("cannot find begin: " + beginNeedle);
        }
        int end = source.indexOf(endNeedle, start);
        if (end < 0) {
            throw new AssertionError("cannot find end: " + endNeedle);
        }
        return source.substring(start, end);
    }

    private static String stripJavaComments(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                    builder.append(ch);
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inString && !inChar && ch == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (!inString && !inChar && ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            builder.append(ch);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && (inString || inChar)) {
                escaped = true;
                continue;
            }
            if (!inChar && ch == '"') {
                inString = !inString;
            } else if (!inString && ch == '\'') {
                inChar = !inChar;
            }
        }
        return builder.toString();
    }

    private static String stripJavaCommentsAndStringLiterals(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                    builder.append(ch);
                } else {
                    builder.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    builder.append("  ");
                    i++;
                } else {
                    builder.append(ch == '\n' ? ch : ' ');
                }
                continue;
            }
            if (!inString && !inChar && ch == '/' && next == '/') {
                inLineComment = true;
                builder.append("  ");
                i++;
                continue;
            }
            if (!inString && !inChar && ch == '/' && next == '*') {
                inBlockComment = true;
                builder.append("  ");
                i++;
                continue;
            }
            if (escaped) {
                builder.append(' ');
                escaped = false;
                continue;
            }
            if (ch == '\\' && (inString || inChar)) {
                builder.append(' ');
                escaped = true;
                continue;
            }
            if (!inChar && ch == '"') {
                inString = !inString;
                builder.append(' ');
                continue;
            }
            if (!inString && ch == '\'') {
                inChar = !inChar;
                builder.append(' ');
                continue;
            }
            builder.append(inString || inChar ? (ch == '\n' ? ch : ' ') : ch);
        }
        return builder.toString();
    }

    private static boolean isInsideLoopBlock(String source, int index) {
        List<Boolean> loopBlockStack = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                loopBlockStack.add(isLoopHeaderBefore(source, i));
            } else if (ch == '}' && !loopBlockStack.isEmpty()) {
                loopBlockStack.remove(loopBlockStack.size() - 1);
            }
        }
        return loopBlockStack.stream().anyMatch(Boolean::booleanValue);
    }

    private static boolean isLoopHeaderBefore(String source, int braceIndex) {
        int end = braceIndex - 1;
        while (end >= 0 && Character.isWhitespace(source.charAt(end))) {
            end--;
        }
        int start = end;
        int depth = 0;
        while (start >= 0) {
            char ch = source.charAt(start);
            if (ch == ')') {
                depth++;
            } else if (ch == '(') {
                depth--;
            }
            if (depth == 0 && (ch == ';' || ch == '{' || ch == '}')) {
                start++;
                break;
            }
            start--;
        }
        if (start < 0) {
            start = 0;
        }
        String header = source.substring(start, end + 1);
        return header.matches("(?s).*\\b(for|while)\\s*\\(.*");
    }

    private static Path findRepoRoot() {
        Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null) {
            if (Files.isDirectory(path.resolve("mpc4j-s2pc-upso/src/main/java"))
                && Files.isDirectory(path.resolve("mpc4j-s2pc-opf/src/main/java"))) {
                return path;
            }
            path = path.getParent();
        }
        throw new IllegalStateException("cannot locate mpc4j repository root");
    }
}
