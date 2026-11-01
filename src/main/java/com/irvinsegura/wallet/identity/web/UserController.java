package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.EmailVerificationService;
import com.irvinsegura.wallet.identity.application.PasswordService;
import com.irvinsegura.wallet.identity.application.UserProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = SecurityConfiguration.BEARER_SCHEME)
class UserController {

    static final String ME_PATH = "/api/v1/users/me";

    private final UserProfileService userProfileService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;

    UserController(UserProfileService userProfileService, EmailVerificationService emailVerificationService,
            PasswordService passwordService) {
        this.userProfileService = userProfileService;
        this.emailVerificationService = emailVerificationService;
        this.passwordService = passwordService;
    }

    // The user id always comes from the verified token, never from the request, so users only see themselves
    @GetMapping(ME_PATH)
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userProfileService.getProfile(userId(jwt)));
    }

    @PostMapping(ME_PATH + "/email-verification")
    ResponseEntity<Void> resendVerification(@AuthenticationPrincipal Jwt jwt) {
        emailVerificationService.resendVerification(userId(jwt));
        return ResponseEntity.accepted().build();
    }

    /**
     * Ends every session, including the one that made the request, and returns a new one for the caller.
     */
    @PostMapping(ME_PATH + "/password")
    AuthController.TokenResponse changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        return AuthController.TokenResponse.from(
                passwordService.changePassword(userId(jwt), request.currentPassword(), request.newPassword()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }
}
