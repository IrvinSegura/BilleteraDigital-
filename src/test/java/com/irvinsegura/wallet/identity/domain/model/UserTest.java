package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final LockoutPolicy POLICY = new LockoutPolicy(3, Duration.ofMinutes(15));

    private final User user = User.register(Email.of("ana@example.com"), "hash", "  Ana López ", Language.ES, NOW);

    @Test
    void newUsersHaveTheUserRoleAndATrimmedName() {
        assertThat(user.role()).isEqualTo(Role.USER);
        assertThat(user.fullName()).isEqualTo("Ana López");
        assertThat(user.version()).isNull();
        assertThat(user.isLockedAt(NOW)).isFalse();
    }

    @Test
    void rejectsBlankNames() {
        assertThatThrownBy(() -> User.register(Email.of("ana@example.com"), "hash", "   ", Language.ES, NOW))
                .isInstanceOf(InvalidProfileException.class);
    }

    @Test
    void locksAfterTooManyFailedLogins() {
        user.recordFailedLogin(NOW, POLICY);
        user.recordFailedLogin(NOW, POLICY);
        assertThat(user.isLockedAt(NOW)).isFalse();

        user.recordFailedLogin(NOW, POLICY);

        assertThat(user.isLockedAt(NOW)).isTrue();
        assertThat(user.lockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void lockExpiresAfterItsDuration() {
        lock(user);

        assertThat(user.isLockedAt(NOW.plus(Duration.ofMinutes(15)).minusMillis(1))).isTrue();
        assertThat(user.isLockedAt(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void failureAfterAnExpiredLockStartsANewCount() {
        lock(user);
        Instant afterLock = NOW.plus(Duration.ofMinutes(16));

        user.recordFailedLogin(afterLock, POLICY);

        assertThat(user.failedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLockedAt(afterLock)).isFalse();
    }

    @Test
    void successfulLoginResetsFailedAttempts() {
        user.recordFailedLogin(NOW, POLICY);

        assertThat(user.recordSuccessfulLogin()).isTrue();
        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.recordSuccessfulLogin()).isFalse();
    }

    @Test
    void newUsersAreActiveButUnverifiedWhileTheAdminIsVerified() {
        assertThat(user.isActive()).isTrue();
        assertThat(user.isEmailVerified()).isFalse();

        User admin = User.registerAdmin(Email.of("admin@example.com"), "hash", "Administrator", NOW);
        assertThat(admin.emailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void verifyingAgainKeepsTheFirstTime() {
        assertThat(user.verifyEmail(NOW)).isTrue();
        assertThat(user.verifyEmail(NOW.plusSeconds(60))).isFalse();
        assertThat(user.emailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void aGeneratedPasswordMustBeReplacedAndAnotherOneEndsThat() {
        User user = User.register(new Email("ana@example.com"), "hash", "Ana", Language.DEFAULT, Instant.EPOCH);
        assertThat(user.mustChangePassword()).isFalse();

        user.giveInitialPassword("generated");
        assertThat(user.passwordHash()).isEqualTo("generated");
        assertThat(user.mustChangePassword()).isTrue();

        user.changePassword("own");
        assertThat(user.mustChangePassword()).isFalse();
    }

    @Test
    void changingThePasswordLiftsTheLockout() {
        lock(user);

        user.changePassword("new hash");

        assertThat(user.passwordHash()).isEqualTo("new hash");
        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.failedLoginAttempts()).isZero();
    }

    @Test
    void unlockSaysWhetherTheAccountWasLocked() {
        user.recordFailedLogin(NOW, POLICY);
        assertThat(user.unlock(NOW)).isFalse();
        assertThat(user.failedLoginAttempts()).isZero();

        lock(user);
        assertThat(user.unlock(NOW)).isTrue();
        assertThat(user.isLockedAt(NOW)).isFalse();
    }

    @Test
    void disableAndEnableReportWhetherTheStatusChanged() {
        assertThat(user.disable()).isTrue();
        assertThat(user.disable()).isFalse();
        assertThat(user.isActive()).isFalse();

        assertThat(user.enable()).isTrue();
        assertThat(user.enable()).isFalse();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void theAdministratorCannotBeDisabled() {
        User admin = User.registerAdmin(Email.of("admin@example.com"), "hash", "Administrator", NOW);

        assertThatThrownBy(admin::disable).isInstanceOf(AdminCannotBeDisabledException.class);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void aClosedAccountStaysClosedWhateverAnAdministratorDoes() {
        user.close();

        assertThat(user.isClosed()).isTrue();
        assertThat(user.isActive()).isFalse();
        assertThat(user.enable()).isFalse();
        assertThat(user.disable()).isFalse();
        assertThat(user.status()).isEqualTo(UserStatus.CLOSED);
    }

    @Test
    void onlyAnActiveAccountOtherThanTheAdministratorCloses() {
        User admin = User.registerAdmin(Email.of("admin@example.com"), "hash", "Administrator", NOW);
        assertThatThrownBy(admin::close).isInstanceOf(AccountCannotBeClosedException.class)
                .extracting("reason").isEqualTo(AccountCannotBeClosedException.ADMIN);

        user.disable();
        assertThatThrownBy(user::close).isInstanceOf(AccountCannotBeClosedException.class)
                .extracting("reason").isEqualTo(AccountCannotBeClosedException.NOT_ACTIVE);
    }

    @Test
    void aNewAddressCountsAsVerifiedAndTheOldOneIsReturned() {
        Instant later = NOW.plus(Duration.ofDays(1));

        Email previous = user.changeEmail(Email.of("ana@new.example.com"), later);

        assertThat(previous.value()).isEqualTo("ana@example.com");
        assertThat(user.email().value()).isEqualTo("ana@new.example.com");
        assertThat(user.emailVerifiedAt()).isEqualTo(later);
    }

    private static void lock(User user) {
        for (int i = 0; i < POLICY.maxFailedAttempts(); i++) {
            user.recordFailedLogin(NOW, POLICY);
        }
    }
}
