package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.UserProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = SecurityConfiguration.BEARER_SCHEME)
class UserController {

    static final String ME_PATH = "/api/v1/users/me";

    private final UserProfileService userProfileService;

    UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // The user id always comes from the verified token, never from the request, so users only see themselves
    @GetMapping(ME_PATH)
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userProfileService.getProfile(UUID.fromString(jwt.getSubject())));
    }
}
