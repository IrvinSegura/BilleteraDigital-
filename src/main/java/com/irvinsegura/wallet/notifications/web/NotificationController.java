package com.irvinsegura.wallet.notifications.web;

import com.irvinsegura.wallet.notifications.application.NotificationService;
import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearerAuth")
class NotificationController {

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
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

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
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

    record UnreadCountResponse(long count) {
    }
}
