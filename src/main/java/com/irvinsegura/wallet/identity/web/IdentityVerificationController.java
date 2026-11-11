package com.irvinsegura.wallet.identity.web;

import com.irvinsegura.wallet.identity.application.IdentityVerificationCommand;
import com.irvinsegura.wallet.identity.application.IdentityVerificationService;
import com.irvinsegura.wallet.identity.application.IdentityVerificationView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The customer's own identity verification. Customers only (security configuration): staff don't hold money. The
 * card's data is never sent back, only where the review stands.
 */
@RestController
@Validated
@RequestMapping("/api/v1/users/me/identity-verification")
@SecurityRequirement(name = SecurityConfiguration.BEARER_SCHEME)
class IdentityVerificationController {

    private final IdentityVerificationService verificationService;

    IdentityVerificationController(IdentityVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping
    IdentityVerificationResponse status(@AuthenticationPrincipal Jwt jwt) {
        return IdentityVerificationResponse.from(verificationService.status(userId(jwt)));
    }

    /**
     * Sends the voter card (INE) for review: its data as printed, and three JPEG or PNG pictures of up to 8 MB each
     * (front, back and the holder's face). Answers 422 {@code INVALID_IDENTITY_DOCUMENT} with a {@code reason} when
     * something can't be right, 409 when one is pending or verified or the card is another account's, and 429 after
     * too many attempts in a day.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<IdentityVerificationResponse> submit(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request,
            @RequestParam @NotBlank @Size(max = 60) String firstNames,
            @RequestParam @NotBlank @Size(max = 60) String firstSurname,
            @RequestParam(required = false) @Size(max = 60) String secondSurname,
            @RequestParam @NotBlank @Size(max = 18) String curp,
            @RequestParam @NotBlank @Size(max = 18) String voterKey,
            @RequestParam @NotBlank @Size(max = 9) String cic,
            @RequestParam @NotBlank @Size(max = 9) String citizenId,
            @RequestParam @Min(2000) @Max(2100) int expiryYear,
            @RequestParam MultipartFile front,
            @RequestParam MultipartFile back,
            @RequestParam MultipartFile selfie) throws IOException {
        // Request parameters also come from the address, which proxies and access logs keep: the card's data is
        // only taken from the form body
        if (request.getQueryString() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The card's data goes in the form body, not in the address");
        }
        IdentityVerificationView view = verificationService.submit(userId(jwt), new IdentityVerificationCommand(
                firstNames, firstSurname, secondSurname, curp, voterKey, cic, citizenId, expiryYear,
                front.getBytes(), back.getBytes(), selfie.getBytes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(IdentityVerificationResponse.from(view));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * @param status       {@code NOT_STARTED}, {@code PENDING_REVIEW}, {@code VERIFIED} or {@code REJECTED}
     * @param attemptsLeft verifications that can still be sent today
     */
    record IdentityVerificationResponse(UUID id, String status, String curpLastFour, Instant submittedAt,
            Instant reviewedAt, String rejectionReason, int attemptsLeft) {

        static IdentityVerificationResponse from(IdentityVerificationView view) {
            return new IdentityVerificationResponse(view.id(), view.status(), view.curpLastFour(), view.submittedAt(),
                    view.reviewedAt(), view.rejectionReason(), view.attemptsLeft());
        }
    }
}
