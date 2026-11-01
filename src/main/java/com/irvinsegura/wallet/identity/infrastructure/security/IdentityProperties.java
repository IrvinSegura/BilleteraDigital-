package com.irvinsegura.wallet.identity.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("wallet.identity")
public record IdentityProperties(
        @Valid @NotNull Jwt jwt,
        @NotNull Duration refreshTokenTtl,
        @Valid @NotNull Lockout lockout,
        @Valid @NotNull Password password,
        Admin admin,
        @NotNull Duration emailVerificationTtl,
        @NotNull Duration passwordResetTtl) {

    /**
     * Key locations accept any Spring resource, e.g. {@code file:/run/secrets/jwt-private.pem}. The private key
     * must be PKCS#8 PEM and the public key X.509 PEM.
     */
    public record Jwt(
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            String privateKeyLocation,
            String publicKeyLocation,
            boolean generateKeysIfMissing) {
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration duration) {
    }

    public record Password(@Min(4) @Max(31) int hashStrength) {
    }

    /**
     * Administrator created at startup when both values are set. The password must follow the normal policy.
     */
    public record Admin(String email, String password) {

        boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "Admin[email=%s]".formatted(email);
        }
    }
}
