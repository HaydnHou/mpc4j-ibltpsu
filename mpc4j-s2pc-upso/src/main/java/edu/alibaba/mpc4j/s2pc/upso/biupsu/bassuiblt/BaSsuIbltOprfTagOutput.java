package edu.alibaba.mpc4j.s2pc.upso.biupsu.bassuiblt;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.util.Arrays;

/**
 * BA-SSU-IBLT MP-OPRF tag/check output.
 *
 * @author donghai hou
 * @date 2026/06/04
 */
class BaSsuIbltOprfTagOutput {
    /**
     * tag byte length.
     */
    private final int tagByteLength;
    /**
     * check byte length.
     */
    private final int checkByteLength;
    /**
     * tags.
     */
    private final byte[][] tags;
    /**
     * checks.
     */
    private final byte[][] checks;

    BaSsuIbltOprfTagOutput(int tagByteLength, int checkByteLength, byte[][] tags, byte[][] checks) {
        if (tagByteLength <= 0) {
            throw new IllegalArgumentException("tagByteLength must be positive");
        }
        if (checkByteLength <= 0) {
            throw new IllegalArgumentException("checkByteLength must be positive");
        }
        if (tags == null) {
            throw new IllegalArgumentException("tags must be non-null");
        }
        if (checks == null) {
            throw new IllegalArgumentException("checks must be non-null");
        }
        if (tags.length != checks.length) {
            throw new IllegalArgumentException("tags and checks must have equal length");
        }
        this.tagByteLength = tagByteLength;
        this.checkByteLength = checkByteLength;
        this.tags = Arrays.stream(tags)
            .peek(tag -> {
                if (tag == null || tag.length != tagByteLength) {
                    throw new IllegalArgumentException("each tag must have tagByteLength bytes");
                }
            })
            .map(BytesUtils::clone)
            .toArray(byte[][]::new);
        this.checks = Arrays.stream(checks)
            .peek(check -> {
                if (check == null || check.length != checkByteLength) {
                    throw new IllegalArgumentException("each check must have checkByteLength bytes");
                }
            })
            .map(BytesUtils::clone)
            .toArray(byte[][]::new);
    }

    int getBatchSize() {
        return tags.length;
    }

    int getTagByteLength() {
        return tagByteLength;
    }

    int getCheckByteLength() {
        return checkByteLength;
    }

    byte[] getTag(int index) {
        return BytesUtils.clone(tags[index]);
    }

    byte[] getCheck(int index) {
        return BytesUtils.clone(checks[index]);
    }
}
