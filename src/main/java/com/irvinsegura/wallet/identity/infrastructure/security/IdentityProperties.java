package com.irvinsegura.wallet.identity.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
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
        @NotNull Duration passwordResetTtl,
        @Valid @NotNull TwoFactor twoFactor,
        @Valid @NotNull Captcha captcha,
        @Valid @NotNull Verification verification) {

    /**
     * Identity verification with a voter card (INE).
     *
     * @param requiredForWithdrawals whether money can leave for a bank account only with a verified identity
     * @param maxAttemptsPerDay      verifications a customer can send in 24 hours
     */
    public record Verification(boolean requiredForWithdrawals, @Min(1) int maxAttemptsPerDay) {
    }

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

    /**
     * @param issuer        name authenticator apps show next to the code
     * @param encryptionKey Base64 of the 32-byte AES key that encrypts app secrets; changing it invalidates every
     *                      app already set up
     */
    public record TwoFactor(@NotBlank String issuer, String encryptionKey) {

        @Override
        public String toString() {
            return "TwoFactor[issuer=%s]".formatted(issuer);
        }
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration duration) {
    }

    public record Password(@Min(4) @Max(31) int hashStrength, @Valid @NotNull BreachCheck breachCheck) {
    }

    /**
     * Refuses passwords listed by Have I Been Pwned. When the service is slow or down the password is accepted,
     * since the local rules still apply.
     */
    public record BreachCheck(boolean enabled, @NotNull URI baseUrl, @NotNull Duration timeout) {
    }

    /**
     * Cloudflare Turnstile on sign-up and password reset requests. The client app needs the matching site key.
     *
     * @param secretKey the widget's secret key; required when enabled
     */
    public record Captcha(boolean enabled, String secretKey, @NotNull URI verifyUrl, @NotNull Duration timeout) {

        @Override
        public String toString() {
            return "Captcha[enabled=%s, verifyUrl=%s, timeout=%s]".formatted(enabled, verifyUrl, timeout);
        }
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
