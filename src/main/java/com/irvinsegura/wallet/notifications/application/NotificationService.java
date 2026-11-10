package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.model.NotificationNotFoundException;
import com.irvinsegura.wallet.notifications.domain.port.LiveNotifier;
import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The user's inbox. Every query is scoped to the user id from the access token, so users only see their own.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final LiveNotifier liveNotifier;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    NotificationService(NotificationRepository notificationRepository, LiveNotifier liveNotifier,
            ApplicationEventPublisher events, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.liveNotifier = liveNotifier;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationRepository.NotificationSlice list(UUID userId, boolean unreadOnly, int page, int size) {
        return notificationRepository.findByUser(userId, unreadOnly, page, size);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countUnread(userId);
    }

    /**
     * Marking twice is not an error; the first read time is kept.
     *
     * @throws NotificationNotFoundException if the user has no notification with that id
     */
    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        if (!notificationRepository.markRead(notificationId, userId, clock.instant())) {
            throw new NotificationNotFoundException();
        }
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, clock.instant());
    }

    /**
     * @throws NotificationNotFoundException if the user has no notification with that id
     */
    @Transactional
    public void delete(UUID notificationId, UUID userId) {
        if (!notificationRepository.delete(notificationId, userId)) {
            throw new NotificationNotFoundException();
        }
    }

    /**
     * Clears the inbox of what the user already read; unread ones stay.
     */
    @Transactional
    public int deleteRead(UUID userId) {
        return notificationRepository.deleteRead(userId);
    }

    /**
     * Stores a notification and requests its email in the caller's transaction (an event listener's). A redelivered
     * event finds the notification already stored and sends no second email.
     */
    void deliver(Notification notification) {
        if (notificationRepository.add(notification)) {
            events.publishEvent(new NotificationCreated(notification.id(), notification.userId()));
            // Only once it is committed, or an app could fetch the inbox before the notification is in it
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        liveNotifier.push(notification);
                    }
                });
            } else {
                liveNotifier.push(notification);
            }
        }
    }
}
