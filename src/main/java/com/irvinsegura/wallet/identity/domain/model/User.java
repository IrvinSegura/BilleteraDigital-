package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Registered user, its login state and whether it may move money. Only the password hash is kept; a raw password
 * never reaches the domain.
 */
public class User {

    public static final int MAX_FULL_NAME_LENGTH = 100;

    // E.164: a plus sign, a country code that doesn't start with 0, and at most 15 digits in all
    private static final Pattern PHONE = Pattern.compile("\\+[1-9]\\d{7,14}");

    private final UserId id;
    private Email email;
    private String fullName;
    private String phone;
    private Language language;
    private Role role;
    private final Instant createdAt;
    private final Long version;
    private String passwordHash;
    private boolean mustChangePassword;
    private UserStatus status;
    private Instant emailVerifiedAt;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    private User(UserId id, Email email, String passwordHash, String fullName, String phone, Language language,
            Role role, UserStatus status, Instant emailVerifiedAt, int failedLoginAttempts, Instant lockedUntil, Instant createdAt,
            Long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.phone = phone;
        this.language = Objects.requireNonNull(language, "language must not be null");
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
    public static User register(Email email, String passwordHash, String fullName, Language language, Instant now) {
        return create(email, passwordHash, fullName, language, Role.USER, null, now);
    }

    /**
     * A staff account an administrator creates: an administrator or a support agent, never a customer, which only
     * sign-up creates. It has no password until the email with its account data goes out, see
     * {@link #giveInitialPassword}. The administrator vouches for the address, so it counts as verified: otherwise the
     * emailed password could not sign in.
     *
     * @param passwordHash what is stored until then, which nothing matches
     */
    public static User registerStaff(Email email, String passwordHash, String fullName, Role role, Instant now) {
        if (role == Role.USER) {
            throw new IllegalArgumentException("Staff accounts are ADMIN or SUPPORT");
        }
        return create(email, passwordHash, fullName, Language.DEFAULT, role, now, now);
    }

    /**
     * Only for the administrator account provisioned from configuration; users can never register as admins. The
     * system chose the address itself, so it counts as verified.
     */
    public static User registerAdmin(Email email, String passwordHash, String fullName, Instant now) {
        return create(email, passwordHash, fullName, Language.DEFAULT, Role.ADMIN, now, now);
    }

    private static User create(Email email, String passwordHash, String fullName, Language language, Role role,
            Instant emailVerifiedAt, Instant now) {
        return new User(UserId.newId(), email, passwordHash, normalizeFullName(fullName), null, language, role,
                UserStatus.ACTIVE, emailVerifiedAt, 0, null, now, null);
    }

    /**
     * Rebuilds a user from storage. {@code version} is the optimistic lock value; null means not persisted yet.
     */
    public static User restore(UserId id, Email email, String passwordHash, String fullName, String phone,
            Language language, Role role, UserStatus status, Instant emailVerifiedAt, int failedLoginAttempts, Instant lockedUntil,
            Instant createdAt, Long version, boolean mustChangePassword) {
        User user = new User(id, email, passwordHash, fullName, phone, language, role, status, emailVerifiedAt,
                failedLoginAttempts, lockedUntil, createdAt, version);
        user.mustChangePassword = mustChangePassword;
        return user;
    }

    /**
     * Changes the name and the phone; {@code null} leaves each as it is, and a blank phone removes it. Spaces,
     * dashes, dots and parentheses in the phone are ignored.
     *
     * @return the fields that changed: {@code FULL_NAME}, {@code PHONE}
     * @throws InvalidProfileException if the name is blank or too long, or the phone isn't in E.164
     */
    public List<String> updateProfile(String newFullName, String newPhone) {
        List<String> changed = new ArrayList<>();
        if (newFullName != null) {
            String name = normalizeFullName(newFullName);
            if (!name.equals(fullName)) {
                fullName = name;
                changed.add("FULL_NAME");
            }
        }
        if (newPhone != null) {
            String number = newPhone.replaceAll("[\\s().-]", "");
            if (!number.isEmpty() && !PHONE.matcher(number).matches()) {
                throw new InvalidProfileException("phone must be in international format, e.g. +525512345678");
            }
            String stored = number.isEmpty() ? null : number;
            if (!Objects.equals(stored, phone)) {
                phone = stored;
                changed.add("PHONE");
            }
        }
        return List.copyOf(changed);
    }

    /**
     * Returns whether anything changed.
     */
    public boolean changeLanguage(Language newLanguage) {
        if (language == Objects.requireNonNull(newLanguage, "language must not be null")) {
            return false;
        }
        language = newLanguage;
        return true;
    }

    private static String normalizeFullName(String fullName) {
        String name = fullName == null ? "" : fullName.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_FULL_NAME_LENGTH) {
            throw new InvalidProfileException(
                    "fullName must have between 1 and %d characters".formatted(MAX_FULL_NAME_LENGTH));
        }
        return name;
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
     * Replaces the password and clears failed logins, so resetting a forgotten password also lifts a lockout. A
     * generated password is gone with it: from here on, only its owner knows the password.
     */
    public void changePassword(String newPasswordHash) {
        passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash must not be null");
        mustChangePassword = false;
        clearFailedLogins();
    }

    /**
     * Sets a password generated for its owner, who has to replace it: it also travels by email.
     */
    public void giveInitialPassword(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        mustChangePassword = true;
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
     * Makes an account a support agent or back into a regular user. Returns whether anything changed.
     *
     * @throws RoleChangeNotAllowedException for the administrator, or to make someone one: there is only one, provisioned
     *                                       from configuration
     */
    public boolean changeRole(Role newRole) {
        if (role == Role.ADMIN || newRole == Role.ADMIN) {
            throw new RoleChangeNotAllowedException();
        }
        if (role == newRole) {
            return false;
        }
        role = newRole;
        return true;
    }

    /**
     * Returns whether anything changed. A closed account stays closed.
     *
     * @throws AdminCannotBeDisabledException for the administrator, who would have no way back in
     */
    public boolean disable() {
        if (role == Role.ADMIN) {
            throw new AdminCannotBeDisabledException();
        }
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        status = UserStatus.DISABLED;
        return true;
    }

    /**
     * Returns whether anything changed. Only a disabled account comes back: a closed one was its owner's decision.
     */
    public boolean enable() {
        if (status != UserStatus.DISABLED) {
            return false;
        }
        status = UserStatus.ACTIVE;
        return true;
    }

    public boolean isClosed() {
        return status == UserStatus.CLOSED;
    }

    /**
     * The owner's own decision; whatever else must be empty first is checked by the caller.
     *
     * @throws AccountCannotBeClosedException for the administrator, and for an account that is not active
     */
    public void close() {
        if (role == Role.ADMIN) {
            throw new AccountCannotBeClosedException(AccountCannotBeClosedException.ADMIN);
        }
        if (status != UserStatus.ACTIVE) {
            throw new AccountCannotBeClosedException(AccountCannotBeClosedException.NOT_ACTIVE);
        }
        status = UserStatus.CLOSED;
    }

    /**
     * Replaces the address once the owner followed the link sent to it, so the new one counts as verified.
     *
     * @return the address it had
     */
    public Email changeEmail(Email newEmail, Instant now) {
        Email previous = email;
        email = Objects.requireNonNull(newEmail, "newEmail must not be null");
        emailVerifiedAt = Objects.requireNonNull(now, "now must not be null");
        return previous;
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

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public String passwordHash() {
        return passwordHash;
    }

    /**
     * Null unless this is a staff account still on the password generated for it.
     */
    public String fullName() {
        return fullName;
    }

    public Language language() {
        return language;
    }

    public String phone() {
        return phone;
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
