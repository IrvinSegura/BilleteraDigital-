package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record UserProfile(UUID id, String email, String fullName, String phone, String role, String status,
        boolean emailVerified, boolean twoFactorEnabled, Instant createdAt) {

    static UserProfile from(User user, boolean twoFactorEnabled) {
        return new UserProfile(user.id().value(), user.email().value(), user.fullName(), user.phone(),
                user.role().name(), user.status().name(), user.isEmailVerified(), twoFactorEnabled, user.createdAt());
    }
}
