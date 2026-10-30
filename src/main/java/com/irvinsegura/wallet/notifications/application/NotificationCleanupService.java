package com.irvinsegura.wallet.notifications.application;

import com.irvinsegura.wallet.notifications.domain.port.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications are a convenience, not a record: statements and the audit log keep what happened, so old
 * notifications are deleted, read or not.
 */
@Service
public class NotificationCleanupService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    NotificationCleanupService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /**
     * @return how many notifications were deleted
     */
    @Transactional
    public int deleteOlderThan(Duration retention) {
        return notificationRepository.deleteCreatedBefore(clock.instant().minus(retention));
    }
}
