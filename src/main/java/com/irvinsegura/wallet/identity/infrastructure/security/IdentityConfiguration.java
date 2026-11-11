package com.irvinsegura.wallet.identity.infrastructure.security;

import com.irvinsegura.wallet.identity.domain.model.LockoutPolicy;
import com.irvinsegura.wallet.identity.domain.model.OneTimeTokenPolicy;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationPolicy;
import com.irvinsegura.wallet.identity.domain.model.RefreshTokenPolicy;
import com.irvinsegura.wallet.identity.domain.port.BreachedPasswords;
import com.irvinsegura.wallet.identity.domain.port.HumanVerifier;
import com.irvinsegura.wallet.identity.domain.port.SecretCipher;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfiguration {

    @Bean
    LockoutPolicy lockoutPolicy(IdentityProperties properties) {
        return new LockoutPolicy(properties.lockout().maxFailedAttempts(), properties.lockout().duration());
    }

    @Bean
    IdentityVerificationPolicy identityVerificationPolicy(IdentityProperties properties) {
        IdentityProperties.Verification verification = properties.verification();
        return new IdentityVerificationPolicy(verification.requiredForWithdrawals(), verification.maxAttemptsPerDay());
    }

    @Bean
    RefreshTokenPolicy refreshTokenPolicy(IdentityProperties properties) {
        return new RefreshTokenPolicy(properties.refreshTokenTtl());
    }

    @Bean
    OneTimeTokenPolicy oneTimeTokenPolicy(IdentityProperties properties) {
        return new OneTimeTokenPolicy(properties.emailVerificationTtl(), properties.passwordResetTtl());
    }

    @Bean
    BreachedPasswords breachedPasswords(IdentityProperties properties) {
        IdentityProperties.BreachCheck check = properties.password().breachCheck();
        return check.enabled() ? new PwnedPasswordsClient(check.baseUrl(), check.timeout()) : BreachedPasswords.none();
    }

    // Startup fails without a secret key rather than every sign-up failing later
    @Bean
    HumanVerifier humanVerifier(IdentityProperties properties) {
        IdentityProperties.Captcha captcha = properties.captcha();
        if (!captcha.enabled()) {
            return HumanVerifier.none();
        }
        if (captcha.secretKey() == null || captcha.secretKey().isBlank()) {
            throw new IllegalStateException("wallet.identity.captcha.secret-key (TURNSTILE_SECRET_KEY) is required "
                    + "when the captcha is enabled");
        }
        return new TurnstileVerifier(captcha.verifyUrl(), captcha.secretKey(), captcha.timeout());
    }

    // Startup fails on a missing or malformed key instead of every two-factor setup failing later
    @Bean
    SecretCipher secretCipher(IdentityProperties properties) {
        String key = properties.twoFactor().encryptionKey();
        byte[] decoded;
        try {
            decoded = key == null ? new byte[0] : Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            decoded = new byte[0];
        }
        if (decoded.length != AesGcmSecretCipher.KEY_BYTES) {
            throw new IllegalStateException("wallet.identity.two-factor.encryption-key (TWO_FACTOR_ENCRYPTION_KEY) "
                    + "must be the Base64 of 32 random bytes, e.g. from `openssl rand -base64 32`");
        }
        return new AesGcmSecretCipher(decoded);
    }
}
