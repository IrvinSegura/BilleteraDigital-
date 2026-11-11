package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the administrators' list of accounts.
 *
 * @param locked whether a lockout for failed sign-ins is in force
 */
public record AdminUserSummary(UUID id, String email, String fullName, String role, String status,
        boolean emailVerified, boolean locked, Instant createdAt) {

    static AdminUserSummary from(User user, Instant now) {
        return new AdminUserSummary(user.id().value(), user.email().value(), user.fullName(), user.role().name(),
                user.status().name(), user.isEmailVerified(), user.isLockedAt(now), user.createdAt());
    }
}
