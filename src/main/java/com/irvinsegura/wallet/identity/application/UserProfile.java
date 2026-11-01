package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record UserProfile(UUID id, String email, String fullName, String role, String status,
        boolean emailVerified, Instant createdAt) {

    static UserProfile from(User user) {
        return new UserProfile(user.id().value(), user.email().value(), user.fullName(), user.role().name(),
                user.status().name(), user.isEmailVerified(), user.createdAt());
    }
}
