package com.irvinsegura.wallet.notifications.web;

import com.irvinsegura.wallet.notifications.application.NotificationPreferencesService;
import com.irvinsegura.wallet.notifications.application.NotificationService;
import com.irvinsegura.wallet.notifications.domain.model.EmailPreferences;
import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferencesService preferencesService;
    private final NotificationStreamHub streamHub;

    NotificationController(NotificationService notificationService,
            NotificationPreferencesService preferencesService, NotificationStreamHub streamHub) {
        this.notificationService = notificationService;
        this.preferencesService = preferencesService;
        this.streamHub = streamHub;
    }

    @GetMapping
    NotificationPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        NotificationRepository.NotificationSlice slice = notificationService.list(userId(jwt), unreadOnly, page, size);
        return new NotificationPageResponse(slice.items().stream().map(NotificationResponse::from).toList(), page,
                size, slice.hasNext());
    }

    /**
     * Server-sent events: {@code ready} once connected, then a {@code notification} event ({@code id}, {@code type},
     * {@code title}) as each notification arrives. The stream closes after a few minutes; reconnect with a current
     * access token.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                // nginx would otherwise buffer the stream and deliver events late
                .header("X-Accel-Buffering", "no")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(streamHub.open(userId(jwt)));
    }

    // Cheap enough for clients to poll for a badge
    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(notificationService.countUnread(userId(jwt)));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        notificationService.markRead(notificationId, userId(jwt));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(userId(jwt));
    }

    /**
     * Deletes every notification the user already read.
     */
    @DeleteMapping("/read")
    DeletedResponse deleteRead(@AuthenticationPrincipal Jwt jwt) {
        return new DeletedResponse(notificationService.deleteRead(userId(jwt)));
    }

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        notificationService.delete(notificationId, userId(jwt));
    }

    /**
     * Which notifications also arrive by email. Security notices and failed operations always do.
     */
    @GetMapping("/preferences")
    PreferencesResponse preferences(@AuthenticationPrincipal Jwt jwt) {
        return PreferencesResponse.from(preferencesService.get(userId(jwt)));
    }

    @PutMapping("/preferences")
    PreferencesResponse updatePreferences(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PreferencesRequest request) {
        UUID userId = userId(jwt);
        // Absent from apps older than this setting: they keep what the user chose before
        boolean signIns = request.emailSignIns() != null ? request.emailSignIns()
                : preferencesService.get(userId).signIns();
        return PreferencesResponse.from(preferencesService.update(userId,
                new EmailPreferences(request.emailMoneyIn(), request.emailMoneyOut(), signIns)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /**
     * @param emailMoneyIn  email for deposits and transfers received
     * @param emailMoneyOut email for transfers sent and withdrawals
     * @param emailSignIns  email for each sign-in; optional, absent keeps the current choice
     */
    record PreferencesRequest(@NotNull Boolean emailMoneyIn, @NotNull Boolean emailMoneyOut, Boolean emailSignIns) {
    }

    record PreferencesResponse(boolean emailMoneyIn, boolean emailMoneyOut, boolean emailSignIns) {

        static PreferencesResponse from(EmailPreferences preferences) {
            return new PreferencesResponse(preferences.moneyIn(), preferences.moneyOut(), preferences.signIns());
        }
    }

    record NotificationResponse(UUID id, String type, String title, String body, UUID referenceId, boolean read,
            Instant createdAt, Instant readAt) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.id(), notification.type().name(), notification.title(),
                    notification.body(), notification.referenceId(), notification.isRead(),
                    notification.createdAt(), notification.readAt());
        }
    }

    record NotificationPageResponse(List<NotificationResponse> items, int page, int size, boolean hasNext) {
    }

    record DeletedResponse(int deleted) {
    }

    record UnreadCountResponse(long count) {
    }
}
