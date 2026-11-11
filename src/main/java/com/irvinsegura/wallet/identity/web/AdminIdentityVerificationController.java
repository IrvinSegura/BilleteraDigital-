package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.IdentityVerificationDetail;
import com.irvinsegura.wallet.identity.application.IdentityVerificationPage;
import com.irvinsegura.wallet.identity.application.IdentityVerificationService;
import com.irvinsegura.wallet.identity.domain.model.DocumentSide;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerification;
import com.irvinsegura.wallet.identity.domain.model.IdentityVerificationStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrators review identity verifications. Restricted to the ADMIN role, signed in with two-factor
 * authentication, by the security configuration; every review is audited.
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/identity-verifications")
@SecurityRequirement(name = SecurityConfiguration.BEARER_SCHEME)
class AdminIdentityVerificationController {

    private final IdentityVerificationService verificationService;

    AdminIdentityVerificationController(IdentityVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Pending ones oldest first, so the queue is worked in order; the others newest first.
     */
    @GetMapping
    IdentityVerificationPage list(@RequestParam(required = false) IdentityVerificationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return verificationService.list(status, page, size);
    }

    @GetMapping("/{id}")
    IdentityVerificationDetail get(@PathVariable UUID id) {
        return verificationService.detail(id);
    }

    /**
     * One of the three pictures, as a JPEG. Never cached, and never run as anything but an image.
     */
    @GetMapping("/{id}/images/{side}")
    ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable DocumentSide side) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(verificationService.image(id, side));
    }

    @PostMapping("/{id}/approve")
    IdentityVerificationDetail approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return verificationService.approve(id, adminId(jwt));
    }

    /**
     * The reason is shown to the customer, so they know what to fix.
     */
    @PostMapping("/{id}/reject")
    IdentityVerificationDetail reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody RejectRequest request) {
        return verificationService.reject(id, adminId(jwt), request.reason());
    }

    private static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record RejectRequest(@NotBlank @Size(max = IdentityVerification.MAX_REJECTION_REASON) String reason) {
    }
}
