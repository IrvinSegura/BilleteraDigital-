package com.irvinsegura.wallet.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Registered user, its login state and whether it may move money. Only the password hash is kept; the raw password
 * never reaches the domain.
 */
public class User {

    public static final int MAX_FULL_NAME_LENGTH = 100;

    private final UserId id;
    private final Email email;
    private final String fullName;
    private final Role role;
    private final Instant createdAt;
    private final Long version;
    private String passwordHash;
    private UserStatus status;
    private Instant emailVerifiedAt;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    private User(UserId id, Email email, String passwordHash, String fullName, Role role, UserStatus status,
            Instant emailVerifiedAt, int failedLoginAttempts, Instant lockedUntil, Instant createdAt, Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.emailVerifiedAt = emailVerifiedAt;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.version = version;
    }

    /**
     * New accounts start with an unverified email: they can receive money but not send or withdraw it.
     */
    public static User register(Email email, String passwordHash, String fullName, Instant now) {
        return create(email, passwordHash, fullName, Role.USER, null, now);
    }

    /**
     * Only for the administrator account provisioned from configuration; users can never register as admins. The
     * system chose the address itself, so it counts as verified.
     */
    public static User registerAdmin(Email email, String passwordHash, String fullName, Instant now) {
        return create(email, passwordHash, fullName, Role.ADMIN, now, now);
    }

    private static User create(Email email, String passwordHash, String fullName, Role role, Instant emailVerifiedAt,
            Instant now) {
        String name = fullName == null ? "" : fullName.strip();
        if (name.isEmpty() || name.length() > MAX_FULL_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "fullName must have between 1 and %d characters".formatted(MAX_FULL_NAME_LENGTH));
        }
        return new User(UserId.newId(), email, passwordHash, name, role, UserStatus.ACTIVE, emailVerifiedAt, 0, null,
                now, null);
    }

    /**
     * Rebuilds a user from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static User restore(UserId id, Email email, String passwordHash, String fullName, Role role,
            UserStatus status, Instant emailVerifiedAt, int failedLoginAttempts, Instant lockedUntil,
            Instant createdAt, Long version) {
        return new User(id, email, passwordHash, fullName, role, status, emailVerifiedAt, failedLoginAttempts,
                lockedUntil, createdAt, version);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Returns whether anything changed; verifying again keeps the first time.
     */
    public boolean verifyEmail(Instant now) {
        if (emailVerifiedAt != null) {
            return false;
        }
        emailVerifiedAt = now;
        return true;
    }

    /**
     * Replaces the password and clears failed logins, so resetting a forgotten password also lifts a lockout.
     */
    public void changePassword(String newPasswordHash) {
        passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
        clearFailedLogins();
    }

    /**
     * Lifts a lockout before it expires, e.g. when someone locked the account on purpose. Returns whether the
     * account was locked.
     */
    public boolean unlock(Instant now) {
        boolean locked = isLockedAt(now);
        clearFailedLogins();
        return locked;
    }

    /**
     * Returns whether anything changed.
     *
     * @throws AdminCannotBeDisabledException for the administrator, who would have no way back in
     */
    public boolean disable() {
        if (role == Role.ADMIN) {
            throw new AdminCannotBeDisabledException();
        }
        if (status == UserStatus.DISABLED) {
            return false;
        }
        status = UserStatus.DISABLED;
        return true;
    }

    /**
     * Returns whether anything changed.
     */
    public boolean enable() {
        if (status == UserStatus.ACTIVE) {
            return false;
        }
        status = UserStatus.ACTIVE;
        return true;
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
        return clearFailedLogins();
    }

    private boolean clearFailedLogins() {
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

    public UserStatus status() {
        return status;
    }

    public Instant emailVerifiedAt() {
        return emailVerifiedAt;
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
