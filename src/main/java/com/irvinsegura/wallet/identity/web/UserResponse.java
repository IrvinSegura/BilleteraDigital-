package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.UserProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * @param phone    E.164, {@code null} if the user didn't add one
 * @param language language of the user's emails, {@code es} or {@code en}
 * @param mustChangePassword the account still has the password it was emailed, and can do nothing else until it
 *                           replaces it
 */
record UserResponse(UUID id, String email, String fullName, String phone, String language, String role, String status,
        boolean emailVerified, boolean twoFactorEnabled, boolean mustChangePassword, Instant createdAt) {

    static UserResponse from(UserProfile profile) {
        return new UserResponse(profile.id(), profile.email(), profile.fullName(), profile.phone(),
                profile.language().tag(), profile.role(), profile.status(), profile.emailVerified(), profile.twoFactorEnabled(), profile.mustChangePassword(),
                profile.createdAt());
    }
}
