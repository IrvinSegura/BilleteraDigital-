package com.irvinsegura.wallet.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Registered user and its login state. Only the password hash is kept; the raw password never reaches the domain.
 */
public class User {

    public static final int MAX_FULL_NAME_LENGTH = 100;

    private final UserId id;
    private final Email email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final Instant createdAt;
    private final Long version;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    private User(UserId id, Email email, String passwordHash, String fullName, Role role, int failedLoginAttempts,
            Instant lockedUntil, Instant createdAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.version = version;
    }

    public static User register(Email email, String passwordHash, String fullName, Instant now) {
        return create(email, passwordHash, fullName, Role.USER, now);
    }

    /**
     * Only for the administrator account provisioned from configuration; users can never register as admins.
     */
    public static User registerAdmin(Email email, String passwordHash, String fullName, Instant now) {
        return create(email, passwordHash, fullName, Role.ADMIN, now);
    }

    private static User create(Email email, String passwordHash, String fullName, Role role, Instant now) {
        String name = fullName == null ? "" : fullName.strip();
        if (name.isEmpty() || name.length() > MAX_FULL_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "fullName must have between 1 and %d characters".formatted(MAX_FULL_NAME_LENGTH));
        }
        return new User(UserId.newId(), email, passwordHash, name, role, 0, null, now, null);
    }

    /**
     * Rebuilds a user from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static User restore(UserId id, Email email, String passwordHash, String fullName, Role role,
            int failedLoginAttempts, Instant lockedUntil, Instant createdAt, Long version) {
        return new User(id, email, passwordHash, fullName, role, failedLoginAttempts, lockedUntil, createdAt, version);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void recordFailedLogin(Instant now, LockoutPolicy policy) {
        // An expired lock starts a fresh count instead of locking again on the next mistake
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            failedLoginAttempts = 0;
            lockedUntil = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxFailedAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
        }
    }

    /**
     * Clears the failed attempts. Returns whether anything changed, so callers can skip a needless write.
     */
    public boolean recordSuccessfulLogin() {
        if (failedLoginAttempts == 0 && lockedUntil == null) {
            return false;
        }
        failedLoginAttempts = 0;
        lockedUntil = null;
        return true;
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public Role role() {
        return role;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long version() {
        return version;
    }
}
