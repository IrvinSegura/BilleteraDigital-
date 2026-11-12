package com.irvinsegura.wallet.support.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * What can be sent in a chat: pictures, PDF documents and MP4 videos, nothing that could run in a reader's browser
 * (SVG and HTML are the usual ways). The type comes from the file's first bytes, since a client can claim anything.
 */
public final class AttachmentRules {

    public static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final int MAX_NAME_LENGTH = 150;
    private static final String DEFAULT_NAME = "file";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FTYP = "ftyp".getBytes(StandardCharsets.US_ASCII);
    private static final int WEBP_OFFSET = 8;
    private static final int FTYP_OFFSET = 4;

    private AttachmentRules() {
    }

    /**
     * Checks the file and describes it.
     *
     * @throws AttachmentNotAllowedException if it is empty, too big or of another type
     */
    public static SupportAttachment describe(UUID conversationId, String fileName, byte[] content) {
        if (content.length == 0 || content.length > MAX_BYTES) {
            throw new AttachmentNotAllowedException();
        }
        String contentType = typeOf(content).orElseThrow(AttachmentNotAllowedException::new);
        return new SupportAttachment(UUID.randomUUID(), conversationId, safeName(fileName), contentType, content.length);
    }

    static Optional<String> typeOf(byte[] content) {
        if (startsWith(content, 0, JPEG)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(content, 0, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, WEBP_OFFSET, WEBP)) {
            return Optional.of("image/webp");
        }
        if (startsWith(content, 0, PDF)) {
            return Optional.of("application/pdf");
        }
        if (startsWith(content, FTYP_OFFSET, FTYP)) {
            return Optional.of("video/mp4");
        }
        return Optional.empty();
    }

    // Only the last part of a path, without what could break a header or a file system, and keeping the extension
    static String safeName(String fileName) {
        String name = fileName == null ? "" : fileName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1)
                .replaceAll("[\\p{Cntrl}\"<>:|?*]", "")
                .strip();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            return DEFAULT_NAME;
        }
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
        return name.substring(0, MAX_NAME_LENGTH - extension.length()) + extension;
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
