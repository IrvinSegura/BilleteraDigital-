package com.irvinsegura.wallet.identity.application;

import com.irvinsegura.wallet.identity.domain.model.User;
import com.irvinsegura.wallet.shared.domain.Language;
import java.time.Instant;
import java.util.UUID;

public record UserProfile(UUID id, String email, String fullName, String phone, Language language, String role,
        String status,
        boolean emailVerified, boolean twoFactorEnabled, boolean mustChangePassword, Instant createdAt) {

    static UserProfile from(User user, boolean twoFactorEnabled) {
        return new UserProfile(user.id().value(), user.email().value(), user.fullName(), user.phone(),
                user.language(), user.role().name(), user.status().name(), user.isEmailVerified(), twoFactorEnabled, user.mustChangePassword(),
                user.createdAt());
    }
}
