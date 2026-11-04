package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;

/**
 * What support staff see about an account.
 *
 * @param lockedUntil set only while a lockout is in force
 */
public record AdminUserView(UUID id, String email, String fullName, String role, String status,
        Instant emailVerifiedAt, Instant lockedUntil, boolean twoFactorEnabled, Instant createdAt) {

    static AdminUserView from(User user, boolean twoFactorEnabled, Instant now) {
        return new AdminUserView(user.id().value(), user.email().value(), user.fullName(), user.role().name(),
                user.status().name(), user.emailVerifiedAt(), user.isLockedAt(now) ? user.lockedUntil() : null,
                twoFactorEnabled, user.createdAt());
    }
}
