package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.AdminUserPage;
import com.irvinsegura.wallet.identity.application.AdminUserSummary;
import com.irvinsegura.wallet.identity.application.AdminUserView;
import com.irvinsegura.wallet.identity.application.TwoFactorService;
import com.irvinsegura.wallet.identity.application.UserAdministrationService;
import com.irvinsegura.wallet.identity.application.UserSuggestion;
import com.irvinsegura.wallet.identity.domain.port.UserRepository.DailySignups;
import com.irvinsegura.wallet.identity.domain.port.UserRepository.UserCounts;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Every account, the newest first, to browse by {@code status} ({@code ACTIVE}, {@code DISABLED}, {@code CLOSED}) and
     * {@code role} ({@code USER}, {@code SUPPORT}, {@code ADMIN}).
     */
    @GetMapping
    AdminUserPageResponse list(
            @RequestParam(required = false)
            @Pattern(regexp = "ACTIVE|DISABLED|CLOSED", message = "must be ACTIVE, DISABLED or CLOSED") String status,
            @RequestParam(required = false)
            @Pattern(regexp = "USER|SUPPORT|ADMIN", message = "must be USER, SUPPORT or ADMIN") String role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AdminUserPage result = administrationService.listUsers(status, role, page, size);
        return new AdminUserPageResponse(result.items(), result.page(), result.size(), result.hasNext());
    }

    /**
     * How many accounts there are, by state, for the administration overview.
     */
    @GetMapping("/stats")
    UserCounts stats() {
        return administrationService.countUsers();
    }

    /**
     * Customers who signed up each day, for the charts of the administration home. Days with nobody have no row.
     */
    @GetMapping("/signups")
    List<DailySignups> signups(@RequestParam(defaultValue = "30") @Min(1) @Max(180) int days) {
        return administrationService.dailySignups(days);
    }

    // A POST body rather than a query parameter, so the address stays out of access logs and traces
    @PostMapping("/search")
    AdminUserResponse search(@Valid @RequestBody SearchRequest request) {
        return AdminUserResponse.from(administrationService.findByEmail(request.email()));
    }

    /**
     * Up to six users whose email or name contains the text, or whose id starts with it, as the administrator types.
     * A POST for the same reason as the search.
     */
    @PostMapping("/suggestions")
    List<UserSuggestion> suggestions(@Valid @RequestBody SuggestionsRequest request) {
        return administrationService.suggest(request.text());
    }

    /**
     * Creates a staff account, an administrator ({@code ADMIN}) or a support agent ({@code SUPPORT}); customers only
     * come from sign-up. Its owner gets an email with their role, address and a generated password, which nobody else
     * sees.
     */
    @PostMapping
    ResponseEntity<AdminUserResponse> createStaff(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateStaffRequest request) {
        AdminUserView created = administrationService.createStaff(adminId(jwt), request.email(), request.fullName(),
                request.role(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminUserResponse.from(created));
    }

    @GetMapping("/{userId}")
    AdminUserResponse get(@PathVariable UUID userId) {
        return AdminUserResponse.from(administrationService.getUser(userId));
    }

    /**
     * Makes an account a support agent ({@code SUPPORT}) or back into a regular user ({@code USER}), ending its sessions.
     */
    @PostMapping("/{userId}/role")
    AdminUserResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
            @Valid @RequestBody RoleChangeRequest request) {
        return AdminUserResponse.from(administrationService.changeRole(userId, adminId(jwt), request.role(), request.reason()));
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

    record AdminUserPageResponse(List<AdminUserSummary> items, int page, int size, boolean hasNext) {
    }

    record SearchRequest(@NotBlank @Size(max = 254) String email) {
    }

    record SuggestionsRequest(@NotBlank @Size(max = 254) String text) {
    }

    /**
     * @param wasEnabled false if the user had no authenticator app, so nothing changed
     */
    record TwoFactorResetResponse(boolean wasEnabled) {
    }

    // A reason is mandatory: it is what the audit log shows when someone asks why they cannot sign in
    record StatusChangeRequest(@NotBlank @Size(max = 200) String reason) {
    }

    record CreateStaffRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 100) String fullName,
            @NotNull @Pattern(regexp = "ADMIN|SUPPORT", message = "must be ADMIN or SUPPORT") String role,
            @NotBlank @Size(max = 200) String reason) {
    }

    record RoleChangeRequest(@NotNull @Pattern(regexp = "USER|SUPPORT", message = "must be USER or SUPPORT") String role,
            @NotBlank @Size(max = 200) String reason) {
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
