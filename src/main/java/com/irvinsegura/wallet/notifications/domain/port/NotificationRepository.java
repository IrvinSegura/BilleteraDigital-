package com.irvinsegura.wallet.notifications.domain.port;

import com.irvinsegura.wallet.notifications.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    /**
     * Stores the notification unless one with the same id exists.
     *
     * @return whether it was stored now, false if it already existed
     */
    boolean add(Notification notification);

    Optional<Notification> findById(UUID id);

    /**
     * The user's notifications, newest first.
     */
    NotificationSlice findByUser(UUID userId, boolean unreadOnly, int page, int size);

    long countUnread(UUID userId);

    /**
     * Marks the user's notification as read, keeping the first read time if it was already read.
     *
     * @return false if the user has no notification with that id
     */
    boolean markRead(UUID id, UUID userId, Instant readAt);

    /**
     * @return how many notifications changed from unread to read
     */
    int markAllRead(UUID userId, Instant readAt);

    /**
     * @return how many notifications were deleted
     */
    int deleteCreatedBefore(Instant cutoff);

    record NotificationSlice(List<Notification> items, boolean hasNext) {
    }
}
