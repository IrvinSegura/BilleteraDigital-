package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.AuthTokens;
import com.irvinsegura.wallet.identity.application.AuthenticationService;
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

    AuthController(RegistrationService registrationService, AuthenticationService authenticationService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
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
