package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.AdminUserView;
import com.irvinsegura.wallet.identity.application.TwoFactorService;
import com.irvinsegura.wallet.identity.application.UserAdministrationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any user, for support. The ADMIN role is enforced for all of {@code /api/v1/admin/**}; the user's wallets are at
 * {@code /api/v1/admin/users/{userId}/wallets}, served by the accounts module.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = SecurityConfiguration.BEARER_SCHEME)
class AdminUserController {

    private final UserAdministrationService administrationService;
    private final TwoFactorService twoFactorService;

    AdminUserController(UserAdministrationService administrationService, TwoFactorService twoFactorService) {
        this.administrationService = administrationService;
        this.twoFactorService = twoFactorService;
    }

    // A POST body rather than a query parameter, so the address stays out of access logs and traces
    @PostMapping("/search")
    AdminUserResponse search(@Valid @RequestBody SearchRequest request) {
        return AdminUserResponse.from(administrationService.findByEmail(request.email()));
    }

    @GetMapping("/{userId}")
    AdminUserResponse get(@PathVariable UUID userId) {
        return AdminUserResponse.from(administrationService.getUser(userId));
    }

    @PostMapping("/{userId}/unlock")
    AdminUserResponse unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
            @Valid @RequestBody StatusChangeRequest request) {
        return AdminUserResponse.from(administrationService.unlock(userId, adminId(jwt), request.reason()));
    }

    @PostMapping("/{userId}/disable")
    AdminUserResponse disable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
            @Valid @RequestBody StatusChangeRequest request) {
        return AdminUserResponse.from(administrationService.disable(userId, adminId(jwt), request.reason()));
    }

    @PostMapping("/{userId}/enable")
    AdminUserResponse enable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
            @Valid @RequestBody StatusChangeRequest request) {
        return AdminUserResponse.from(administrationService.enable(userId, adminId(jwt), request.reason()));
    }

    /**
     * Turns off two-factor authentication for someone who lost their phone and recovery codes, once support has
     * confirmed who they are. The user is notified.
     */
    @PostMapping("/{userId}/two-factor/reset")
    TwoFactorResetResponse resetTwoFactor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
            @Valid @RequestBody StatusChangeRequest request) {
        return new TwoFactorResetResponse(twoFactorService.reset(adminId(jwt), userId, request.reason()));
    }

    private static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SearchRequest(@NotBlank @Size(max = 254) String email) {
    }

    /**
     * @param wasEnabled false if the user had no authenticator app, so nothing changed
     */
    record TwoFactorResetResponse(boolean wasEnabled) {
    }

    // A reason is mandatory: it is what the audit log shows when someone asks why they cannot sign in
    record StatusChangeRequest(@NotBlank @Size(max = 200) String reason) {
    }

    record AdminUserResponse(UUID id, String email, String fullName, String role, String status,
            boolean emailVerified, Instant emailVerifiedAt, boolean locked, Instant lockedUntil,
            boolean twoFactorEnabled, Instant createdAt) {

        static AdminUserResponse from(AdminUserView user) {
            return new AdminUserResponse(user.id(), user.email(), user.fullName(), user.role(), user.status(),
                    user.emailVerifiedAt() != null, user.emailVerifiedAt(), user.lockedUntil() != null,
                    user.lockedUntil(), user.twoFactorEnabled(), user.createdAt());
        }
    }
}
