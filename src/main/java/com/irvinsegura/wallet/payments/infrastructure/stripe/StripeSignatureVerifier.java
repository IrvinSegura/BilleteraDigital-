package com.irvinsegura.wallet.payments.infrastructure.stripe;

import com.irvinsegura.wallet.payments.domain.model.InvalidWebhookException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks the {@code Stripe-Signature} header: {@code t=<unix seconds>,v1=<hex HMAC-SHA256>}, where the HMAC covers
 * {@code <t>.<raw body>} keyed with the endpoint's signing secret. Several {@code v1} values appear while a secret is
 * being rolled; any of them may match.
 */
public class StripeSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    public StripeSignatureVerifier(String secret, Duration tolerance, Clock clock) {
        this.secret = Objects.requireNonNull(secret, "secret must not be null").getBytes(StandardCharsets.UTF_8);
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * @throws InvalidWebhookException if the header is missing or malformed, no signature matches, or the timestamp
     *                                 is older than the tolerance (a replayed request)
     */
    public void verify(String payload, String header) {
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookException("Missing signature");
        }
        Long timestamp = null;
        StringBuilder signatures = new StringBuilder();
        for (String part : header.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if ("t".equals(keyValue[0])) {
                timestamp = parseTimestamp(keyValue[1]);
            } else if ("v1".equals(keyValue[0])) {
                signatures.append(keyValue[1]).append(',');
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidWebhookException("Malformed signature");
        }
        byte[] expected = sign(timestamp + "." + payload);
        boolean matches = false;
        for (String signature : signatures.toString().split(",")) {
            // Constant-time comparison, so the response time reveals nothing about how much of a guess was right
            matches |= MessageDigest.isEqual(expected, decodeHex(signature));
        }
        if (!matches) {
            throw new InvalidWebhookException("Signature does not match");
        }
        if (timestamp < clock.instant().minus(tolerance).getEpochSecond()) {
            throw new InvalidWebhookException("Signature is too old");
        }
    }

    /**
     * The header Stripe would send for this payload at this time; used to test the verifier and the endpoint.
     */
    public String header(String payload, long timestamp) {
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(sign(timestamp + "." + payload));
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is required on every Java platform", e);
        }
    }

    private static Long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] decodeHex(String value) {
        try {
            return HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
