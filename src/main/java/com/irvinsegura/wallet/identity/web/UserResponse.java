package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.UserProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * @param phone E.164, {@code null} if the user didn't add one
 */
record UserResponse(UUID id, String email, String fullName, String phone, String role, String status,
        boolean emailVerified, boolean twoFactorEnabled, Instant createdAt) {

    static UserResponse from(UserProfile profile) {
        return new UserResponse(profile.id(), profile.email(), profile.fullName(), profile.phone(), profile.role(),
                profile.status(), profile.emailVerified(), profile.twoFactorEnabled(), profile.createdAt());
    }
}
