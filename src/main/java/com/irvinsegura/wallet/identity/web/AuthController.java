package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.AuthTokens;
import com.irvinsegura.wallet.identity.application.AuthenticationService;
import com.irvinsegura.wallet.identity.application.EmailVerificationService;
import com.irvinsegura.wallet.identity.application.PasswordService;
import com.irvinsegura.wallet.identity.application.RegisterUserCommand;
import com.irvinsegura.wallet.identity.application.RegistrationService;
import com.irvinsegura.wallet.identity.application.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;

    AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
            EmailVerificationService emailVerificationService, PasswordService passwordService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.emailVerificationService = emailVerificationService;
        this.passwordService = passwordService;
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfile profile = registrationService.register(
                new RegisterUserCommand(request.email(), request.password(), request.fullName()));
        return ResponseEntity.created(URI.create(UserController.ME_PATH)).body(UserResponse.from(profile));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authenticationService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // The client page behind the emailed link posts the token here; with a GET, mail scanners that open links would
    // verify addresses on their own
    @PostMapping("/email-verification/confirm")
    ResponseEntity<Void> confirmEmail(@Valid @RequestBody TokenRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    // Same answer whether or not the email is registered, so it cannot be used to find out who has an account
    @PostMapping("/password-reset")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // Size limits stop oversized payloads early; the password rules themselves live in the domain
    record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 100) String fullName) {

        @Override
        public String toString() {
            return "RegisterRequest[email=%s, fullName=%s]".formatted(email, fullName);
        }
    }

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=%s]".formatted(email);
        }
    }

    record RefreshTokenRequest(@NotBlank @Size(max = 128) String refreshToken) {

        @Override
        public String toString() {
            return "RefreshTokenRequest[refreshToken=***]";
        }
    }

    record TokenRequest(@NotBlank @Size(max = 128) String token) {

        @Override
        public String toString() {
            return "TokenRequest[token=***]";
        }
    }

    record PasswordResetRequest(@NotBlank @Size(max = 254) String email) {
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[token=***]";
        }
    }

    /**
     * OAuth 2.0 style token response (RFC 6749 section 5.1); lifetimes are in seconds.
     */
    record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
            long refreshExpiresIn) {

        static TokenResponse from(AuthTokens tokens) {
            return new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessTokenExpiresIn().toSeconds(),
                    tokens.refreshToken(), tokens.refreshTokenExpiresIn().toSeconds());
        }
    }
}
