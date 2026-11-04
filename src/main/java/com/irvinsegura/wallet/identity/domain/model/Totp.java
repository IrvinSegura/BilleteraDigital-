package com.irvinsegura.wallet.identity.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238) as authenticator apps compute them: HMAC-SHA1, 6 digits, 30-second steps,
 * with the secret shared as Base32.
 */
public final class Totp {

    public static final Duration STEP = Duration.ofSeconds(30);
    static final int DIGITS = 6;
    // One step either side covers a phone clock a little off and a code typed just as it changed
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /**
     * A new random secret, Base32 encoded without padding.
     */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encode(bytes);
    }

    /**
     * The code an authenticator app shows at {@code instant}.
     */
    public static String codeAt(String secret, Instant instant) {
        return code(decode(secret), step(instant));
    }

    /**
     * The step whose code matches, within the allowed drift; empty if none does.
     */
    static OptionalLong matchingStep(String secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = decode(secret);
        long current = step(now);
        for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
            // Constant time, so response times say nothing about how many digits matched
            if (MessageDigest.isEqual(code(key, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    private static long step(Instant instant) {
        return instant.getEpochSecond() / STEP.toSeconds();
    }

    private static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            // Dynamic truncation: the last nibble picks where to read 31 bits from
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is required on every Java platform", e);
        }
    }

    static String encode(byte[] bytes) {
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                encoded.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            encoded.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return encoded.toString();
    }

    static byte[] decode(String encoded) {
        String normalized = encoded.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer bytes = ByteBuffer.allocate(normalized.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : normalized.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 secret");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bytes.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return bytes.array();
    }
}
