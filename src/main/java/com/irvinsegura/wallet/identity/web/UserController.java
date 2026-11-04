package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.EmailVerificationService;
import com.irvinsegura.wallet.identity.application.PasswordService;
import com.irvinsegura.wallet.identity.application.SessionService;
import com.irvinsegura.wallet.identity.application.SessionView;
import com.irvinsegura.wallet.identity.application.TwoFactorService;
import com.irvinsegura.wallet.identity.application.TwoFactorSetup;
import com.irvinsegura.wallet.identity.application.UserProfileService;
import com.irvinsegura.wallet.identity.domain.model.AccessToken;
import com.irvinsegura.wallet.shared.web.PlainText;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final TwoFactorService twoFactorService;
    private final SessionService sessionService;
    private final RefreshTokenCookies refreshTokenCookies;

    UserController(UserProfileService userProfileService, EmailVerificationService emailVerificationService,
            PasswordService passwordService, TwoFactorService twoFactorService, SessionService sessionService,
            RefreshTokenCookies refreshTokenCookies) {
        this.userProfileService = userProfileService;
        this.emailVerificationService = emailVerificationService;
        this.passwordService = passwordService;
        this.twoFactorService = twoFactorService;
        this.sessionService = sessionService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    // The user id always comes from the verified token, never from the request, so users only see themselves
    @GetMapping(ME_PATH)
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userProfileService.getProfile(userId(jwt)));
    }

    /**
     * Changes the caller's name and phone. A field left out keeps its value; an empty {@code phone} removes it.
     */
    @PatchMapping(ME_PATH)
    UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userProfileService.updateProfile(userId(jwt), request.fullName(), request.phone()));
    }

    /**
     * The caller's signed-in sessions, the most recently used first; {@code current} marks the one calling.
     */
    @GetMapping(ME_PATH + "/sessions")
    List<SessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
        return sessionService.sessions(userId(jwt), sessionId(jwt)).stream().map(SessionResponse::from).toList();
    }

    /**
     * Ends one session: it can't refresh any more, and its access token expires within minutes.
     */
    @DeleteMapping(ME_PATH + "/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        sessionService.revoke(userId(jwt), sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends every session except the calling one.
     */
    @DeleteMapping(ME_PATH + "/sessions")
    RevokedSessionsResponse revokeOtherSessions(@AuthenticationPrincipal Jwt jwt) {
        return new RevokedSessionsResponse(sessionService.revokeOthers(userId(jwt), sessionId(jwt)));
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
    ResponseEntity<AuthController.TokenResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest, @Valid @RequestBody ChangePasswordRequest request) {
        return refreshTokenCookies.respond(httpRequest,
                passwordService.changePassword(userId(jwt), request.currentPassword(), request.newPassword()));
    }

    /**
     * Starts setting up an authenticator app; two-factor authentication is on only after {@code /confirm}.
     */
    @PostMapping(ME_PATH + "/two-factor")
    ResponseEntity<TwoFactorSetupResponse> beginTwoFactor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordConfirmation request) {
        TwoFactorSetup setup = twoFactorService.begin(userId(jwt), request.currentPassword());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TwoFactorSetupResponse(setup.secret(), setup.otpauthUri()));
    }

    /**
     * Turns two-factor authentication on. The recovery codes in the answer are never shown again.
     */
    @PostMapping(ME_PATH + "/two-factor/confirm")
    RecoveryCodesResponse confirmTwoFactor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TwoFactorCodeRequest request) {
        return new RecoveryCodesResponse(twoFactorService.enable(userId(jwt), request.code()));
    }

    @PostMapping(ME_PATH + "/two-factor/disable")
    ResponseEntity<Void> disableTwoFactor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DisableTwoFactorRequest request) {
        twoFactorService.disable(userId(jwt), request.currentPassword(), request.code());
        return ResponseEntity.noContent().build();
    }

    // How many recovery codes are left, so the client can warn before they run out
    @GetMapping(ME_PATH + "/two-factor")
    TwoFactorStatusResponse twoFactorStatus(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return new TwoFactorStatusResponse(userProfileService.getProfile(userId).twoFactorEnabled(),
                twoFactorService.unusedRecoveryCodes(userId));
    }

    // Tokens issued before sessions were named have no sid; none of the list is then marked current
    private static UUID sessionId(Jwt jwt) {
        String sid = jwt.getClaimAsString(AccessToken.SESSION_CLAIM);
        return sid == null ? null : UUID.fromString(sid);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record PasswordConfirmation(@NotBlank @Size(max = 128) String currentPassword) {

        @Override
        public String toString() {
            return "PasswordConfirmation[***]";
        }
    }

    record TwoFactorCodeRequest(@NotBlank @Size(max = 20) String code) {

        @Override
        public String toString() {
            return "TwoFactorCodeRequest[***]";
        }
    }

    /**
     * @param code the authenticator app's code or a recovery code
     */
    record DisableTwoFactorRequest(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 20) String code) {

        @Override
        public String toString() {
            return "DisableTwoFactorRequest[***]";
        }
    }

    record TwoFactorSetupResponse(String secret, String otpauthUri) {

        @Override
        public String toString() {
            return "TwoFactorSetupResponse[***]";
        }
    }

    record RecoveryCodesResponse(List<String> recoveryCodes) {

        @Override
        public String toString() {
            return "RecoveryCodesResponse[***]";
        }
    }

    record TwoFactorStatusResponse(boolean enabled, int unusedRecoveryCodes) {
    }

    /**
     * @param phone in international format ({@code +525512345678}); spaces and dashes are ignored
     */
    /**
     * @param userAgent  the browser or app as it described itself, {@code null} if unknown
     * @param lastUsedAt when it last renewed its access token
     */
    record SessionResponse(UUID id, String userAgent, String ipAddress, Instant startedAt, Instant lastUsedAt,
            Instant expiresAt, boolean current) {

        static SessionResponse from(SessionView session) {
            return new SessionResponse(session.id(), session.userAgent(), session.ipAddress(), session.startedAt(),
                    session.lastUsedAt(), session.expiresAt(), session.current());
        }
    }

    record RevokedSessionsResponse(int revoked) {
    }

    record UpdateProfileRequest(@Size(max = 100) @Pattern(regexp = PlainText.NO_MARKUP, message = PlainText.NO_MARKUP_MESSAGE) String fullName, @Size(max = 25) String phone) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }
}
