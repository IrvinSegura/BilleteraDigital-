package com.irvinsegura.wallet.transfers.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Remembers that a user's request with a given key already produced a resource. Keys are scoped per user, so two
 * clients picking the same key never see each other's results.
 *
 * @param requestHash fingerprint of the request, so reusing a key for a different request is detected
 * @param resourceId  the deposit or transfer the original request created
 */
public record IdempotencyRecord(UUID userId, String key, String requestHash, UUID resourceId, Instant createdAt) {

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    // Unit separator: cannot appear in the fields, so "a|b" + "c" and "a" + "b|c" never hash the same
    private static final String SEPARATOR = "\u001F";

    public IdempotencyRecord {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new IllegalArgumentException("Idempotency key must be 1 to 100 letters, digits, - or _");
        }
    }

    /**
     * SHA-256 of the request fields in a fixed order. Nulls are encoded distinctly from empty strings.
     */
    public static String fingerprint(String... fields) {
        StringBuilder canonical = new StringBuilder();
        for (String field : fields) {
            canonical.append(field == null ? "\u0000" : field).append(SEPARATOR);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    public void requireSameRequest(String otherRequestHash) {
        if (!requestHash.equals(otherRequestHash)) {
            throw new IdempotencyKeyReusedException();
        }
    }
}
