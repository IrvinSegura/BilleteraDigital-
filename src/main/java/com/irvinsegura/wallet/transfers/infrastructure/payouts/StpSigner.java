package com.irvinsegura.wallet.transfers.infrastructure.payouts;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Signs STP requests: STP checks each one against the certificate the platform registered with it. What is signed
 * is the "cadena original", the request's fields in an order STP fixes, joined by {@code |} between {@code ||}.
 */
final class StpSigner {

    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private final PrivateKey privateKey;

    private StpSigner(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * @throws IllegalStateException if the key is not an unencrypted PKCS#8 RSA key in PEM
     */
    static StpSigner fromPem(String pem) {
        String trimmed = pem.strip();
        if (!trimmed.startsWith(PEM_HEADER) || !trimmed.contains(PEM_FOOTER)) {
            throw new IllegalStateException("The STP private key must be an unencrypted PKCS#8 PEM key ("
                    + PEM_HEADER + "); convert it with: openssl pkcs8 -topk8 -nocrypt -in key.pem");
        }
        String body = trimmed.substring(PEM_HEADER.length(), trimmed.indexOf(PEM_FOOTER)).replaceAll("\\s", "");
        try {
            return new StpSigner(KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("The STP private key is not a valid RSA key", e);
        }
    }

    /**
     * The cadena original of these fields; a missing field stays as an empty place between its separators.
     */
    static String originalString(List<String> fields) {
        return "||" + String.join("|", fields.stream().map(field -> field == null ? "" : field).toList()) + "||";
    }

    /**
     * Base64 of the SHA-256 RSA signature of the cadena original, as STP expects it in {@code firma}.
     */
    String sign(List<String> fields) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(originalString(fields).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign the STP request", e);
        }
    }
}
