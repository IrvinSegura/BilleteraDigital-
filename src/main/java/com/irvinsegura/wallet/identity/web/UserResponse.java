package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.UserProfile;
import java.time.Instant;
import java.util.UUID;

record UserResponse(UUID id, String email, String fullName, String role, Instant createdAt) {

    static UserResponse from(UserProfile profile) {
        return new UserResponse(profile.id(), profile.email(), profile.fullName(), profile.role(),
                profile.createdAt());
    }
}
