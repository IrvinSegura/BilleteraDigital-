package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.AccountLifecycleService;
import com.irvinsegura.wallet.identity.application.AuthTokens;
import com.irvinsegura.wallet.identity.application.AuthenticationService;
import com.irvinsegura.wallet.identity.application.EmailVerificationService;
import com.irvinsegura.wallet.identity.application.HumanCheck;
import com.irvinsegura.wallet.identity.application.PasswordService;
import com.irvinsegura.wallet.identity.application.RegisterUserCommand;
import com.irvinsegura.wallet.identity.application.RegistrationService;
import com.irvinsegura.wallet.identity.domain.model.InvalidRefreshTokenException;
import com.irvinsegura.wallet.shared.web.PlainText;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordService passwordService;
    private final HumanCheck humanCheck;
    private final RefreshTokenCookies refreshTokenCookies;
    private final AccountLifecycleService accountLifecycleService;

    AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
            EmailVerificationService emailVerificationService, PasswordService passwordService,
            HumanCheck humanCheck, RefreshTokenCookies refreshTokenCookies,
            AccountLifecycleService accountLifecycleService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.emailVerificationService = emailVerificationService;
        this.passwordService = passwordService;
        this.humanCheck = humanCheck;
        this.refreshTokenCookies = refreshTokenCookies;
        this.accountLifecycleService = accountLifecycleService;
    }

    // Accepted whether or not the email was already registered, so sign-up cannot tell anyone who has an account; the
    // account signs in once the emailed link is followed
    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        humanCheck.require(request.captchaToken());
        registrationService.register(new RegisterUserCommand(request.email(), request.password(), request.fullName(),
                RequestLanguage.of(acceptLanguage)));
        return ResponseEntity.accepted().build();
    }

    /**
     * With {@code X-Session-Mode: cookie} the refresh token is set as an {@code HttpOnly} cookie instead of returned.
     */
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return refreshTokenCookies.respond(httpRequest, authenticationService.login(request.email(),
                request.password(), request.twoFactorCode(), Boolean.TRUE.equals(request.resendVerification()),
                RequestLanguage.of(acceptLanguage)));
    }

    /**
     * Takes the refresh token from the body, or from the cookie when sent with {@code X-Session-Mode: cookie}.
     */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(HttpServletRequest httpRequest,
            @Valid @RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = refreshTokenCookies.read(httpRequest, request == null ? null : request.refreshToken())
                .orElseThrow(InvalidRefreshTokenException::new);
        return refreshTokenCookies.respond(httpRequest, authenticationService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest,
            @Valid @RequestBody(required = false) RefreshTokenRequest request) {
        refreshTokenCookies.read(httpRequest, request == null ? null : request.refreshToken())
                .ifPresent(authenticationService::logout);
        ResponseEntity.HeadersBuilder<?> response = ResponseEntity.noContent();
        if (RefreshTokenCookies.wantsCookie(httpRequest)) {
            response.header(HttpHeaders.SET_COOKIE, refreshTokenCookies.cleared());
        }
        return response.build();
    }

    // The client page behind the emailed link posts the token here; with a GET, mail scanners that open links would
    // verify addresses on their own
    @PostMapping("/email-verification/confirm")
    ResponseEntity<Void> confirmEmail(@Valid @RequestBody TokenRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    /**
     * Follows the link sent to a new address: the account moves to it and every session ends. No session needed, as
     * the link may be opened on another device.
     */
    @PostMapping("/email-change/confirm")
    ResponseEntity<Void> confirmEmailChange(@Valid @RequestBody TokenRequest request) {
        accountLifecycleService.confirmEmailChange(request.token());
        return ResponseEntity.noContent().build();
    }

    // Same answer whether or not the email is registered, so it cannot be used to find out who has an account
    @PostMapping("/password-reset")
    ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        humanCheck.require(request.captchaToken());
        passwordService.requestReset(request.email(), RequestLanguage.of(acceptLanguage));
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
            @NotBlank @Size(max = 100) @Pattern(regexp = PlainText.NO_MARKUP, message = PlainText.NO_MARKUP_MESSAGE) String fullName,
            @Size(max = 2048) String captchaToken) {

        @Override
        public String toString() {
            return "RegisterRequest[email=%s, fullName=%s]".formatted(email, fullName);
        }
    }

    /**
     * @param twoFactorCode      the authenticator app's code or a recovery code, only for accounts with two-factor
     *                           authentication on; without it they answer {@code 403 TWO_FACTOR_REQUIRED}
     * @param resendVerification true to email a new verification link when the answer is
     *                           {@code 403 EMAIL_NOT_VERIFIED}
     */
    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password,
            @Size(max = 20) String twoFactorCode, Boolean resendVerification) {

        @Override
        public String toString() {
            return "LoginRequest[email=%s]".formatted(email);
        }
    }

    // Optional for browser sessions, whose refresh token is in the cookie
    record RefreshTokenRequest(@Size(max = 128) String refreshToken) {

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

    record PasswordResetRequest(@NotBlank @Size(max = 254) String email, @Size(max = 2048) String captchaToken) {

        @Override
        public String toString() {
            return "PasswordResetRequest[email=%s]".formatted(email);
        }
    }

    record ResetPasswordRequest(@NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[token=***]";
        }
    }

    /**
     * OAuth 2.0 style token response (RFC 6749 section 5.1); lifetimes are in seconds. {@code refreshToken} is null
     * for browser sessions, which get it as a cookie.
     */
    record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
            long refreshExpiresIn) {

        static TokenResponse from(AuthTokens tokens) {
            return new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessTokenExpiresIn().toSeconds(),
                    tokens.refreshToken(), tokens.refreshTokenExpiresIn().toSeconds());
        }

        static TokenResponse withoutRefreshToken(AuthTokens tokens) {
            return new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessTokenExpiresIn().toSeconds(),
                    null, tokens.refreshTokenExpiresIn().toSeconds());
        }

        @Override
        public String toString() {
            return "TokenResponse[***]";
        }
    }
}
