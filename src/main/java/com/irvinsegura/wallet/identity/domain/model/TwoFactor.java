package com.irvinsegura.wallet.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * A user's authenticator app. It is pending until the user proves the app works by entering a code, and only then
 * asked for at login. The secret is stored encrypted; callers decrypt it to check a code.
 */
public class TwoFactor {

    private final UserId userId;
    private final String encryptedSecret;
    private final Instant createdAt;
    private final Long version;
    private Status status;
    private Long lastUsedStep;
    private Instant enabledAt;

    private TwoFactor(UserId userId, String encryptedSecret, Status status, Long lastUsedStep, Instant createdAt,
            Instant enabledAt, Long version) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.encryptedSecret = Objects.requireNonNull(encryptedSecret, "encryptedSecret must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastUsedStep = lastUsedStep;
        this.enabledAt = enabledAt;
        this.version = version;
    }

    public static TwoFactor start(UserId userId, String encryptedSecret, Instant now) {
        return new TwoFactor(userId, encryptedSecret, Status.PENDING, null, now, null, null);
    }

    /**
     * Rebuilds it from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static TwoFactor restore(UserId userId, String encryptedSecret, Status status, Long lastUsedStep,
            Instant createdAt, Instant enabledAt, Long version) {
        return new TwoFactor(userId, encryptedSecret, status, lastUsedStep, createdAt, enabledAt, version);
    }

    /**
     * Checks a code against the decrypted secret and uses it up: a code seen once, or an older one, is refused, so
     * a code read over someone's shoulder cannot be replayed.
     */
    public boolean acceptCode(String secret, String code, Instant now) {
        OptionalLong step = Totp.matchingStep(secret, code, now);
        if (step.isEmpty() || (lastUsedStep != null && step.getAsLong() <= lastUsedStep)) {
            return false;
        }
        lastUsedStep = step.getAsLong();
        return true;
    }

    public void enable(Instant now) {
        if (status == Status.ENABLED) {
            throw new TwoFactorAlreadyEnabledException();
        }
        status = Status.ENABLED;
        enabledAt = now;
    }

    public boolean isEnabled() {
        return status == Status.ENABLED;
    }

    public UserId userId() {
        return userId;
    }

    public String encryptedSecret() {
        return encryptedSecret;
    }

    public Status status() {
        return status;
    }

    public Long lastUsedStep() {
        return lastUsedStep;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant enabledAt() {
        return enabledAt;
    }

    public Long version() {
        return version;
    }

    public enum Status {
        // Set up but never confirmed with a code; not asked for at login
        PENDING,
        ENABLED
    }
}
