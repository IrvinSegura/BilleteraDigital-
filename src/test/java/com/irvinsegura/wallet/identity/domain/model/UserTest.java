package com.irvinsegura.wallet.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final LockoutPolicy POLICY = new LockoutPolicy(3, Duration.ofMinutes(15));

    private final User user = User.register(Email.of("ana@example.com"), "hash", "  Ana López ", NOW);

    @Test
    void newUsersHaveTheUserRoleAndATrimmedName() {
        assertThat(user.role()).isEqualTo(Role.USER);
        assertThat(user.fullName()).isEqualTo("Ana López");
        assertThat(user.version()).isNull();
        assertThat(user.isLockedAt(NOW)).isFalse();
    }

    @Test
    void rejectsBlankNames() {
        assertThatThrownBy(() -> User.register(Email.of("ana@example.com"), "hash", "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
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

    private static void lock(User user) {
        for (int i = 0; i < POLICY.maxFailedAttempts(); i++) {
            user.recordFailedLogin(NOW, POLICY);
        }
    }
}
